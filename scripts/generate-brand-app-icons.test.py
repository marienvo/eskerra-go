#!/usr/bin/env python3
"""Validate committed Android brand assets without requiring ImageMagick."""

from __future__ import annotations

import shutil
import struct
import subprocess
import tempfile
import unittest
import zlib
from dataclasses import dataclass
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parent.parent
RES_DIR = ROOT / "app" / "src" / "main" / "res"
BRAND_DIR = ROOT / "branding"
RENDERER = ROOT / "scripts" / "render-logo-e-pngs.sh"
ANDROID_NS = "http://schemas.android.com/apk/res/android"

DENSITIES = {
    "ldpi": (81, 36),
    "mdpi": (108, 48),
    "hdpi": (162, 72),
    "xhdpi": (216, 96),
    "xxhdpi": (324, 144),
    "xxxhdpi": (432, 192),
}


@dataclass(frozen=True)
class PngImage:
    width: int
    height: int
    pixels: bytes

    def pixel(self, x: int, y: int) -> tuple[int, int, int, int]:
        offset = (y * self.width + x) * 4
        return tuple(self.pixels[offset : offset + 4])  # type: ignore[return-value]

    def alpha_bounds(self) -> tuple[int, int, int, int]:
        points = [
            (x, y)
            for y in range(self.height)
            for x in range(self.width)
            if self.pixel(x, y)[3] > 0
        ]
        if not points:
            raise AssertionError("expected non-transparent pixels")
        xs, ys = zip(*points, strict=True)
        return min(xs), min(ys), max(xs), max(ys)


def paeth_predictor(left: int, above: int, upper_left: int) -> int:
    estimate = left + above - upper_left
    left_distance = abs(estimate - left)
    above_distance = abs(estimate - above)
    upper_left_distance = abs(estimate - upper_left)
    if left_distance <= above_distance and left_distance <= upper_left_distance:
        return left
    if above_distance <= upper_left_distance:
        return above
    return upper_left


def inspect_png(path: Path) -> PngImage:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise AssertionError(f"invalid PNG signature: {path}")

    width = height = 0
    bit_depth = color_type = -1
    compressed_parts: list[bytes] = []
    offset = 8
    while offset + 12 <= len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        chunk_type = data[offset + 4 : offset + 8]
        chunk_start = offset + 8
        chunk_end = chunk_start + length
        if chunk_end + 4 > len(data):
            raise AssertionError(f"truncated PNG chunk in {path}")
        chunk = data[chunk_start:chunk_end]
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, compression, filtering, interlace = (
                struct.unpack(">IIBBBBB", chunk)
            )
            if (compression, filtering, interlace) != (0, 0, 0):
                raise AssertionError(f"unsupported PNG encoding in {path}")
        elif chunk_type == b"IDAT":
            compressed_parts.append(chunk)
        elif chunk_type == b"IEND":
            break
        offset = chunk_end + 4

    if width <= 0 or height <= 0:
        raise AssertionError(f"missing PNG dimensions in {path}")
    if bit_depth != 8 or color_type != 6:
        raise AssertionError(
            f"expected 8-bit RGBA PNG, got depth={bit_depth} type={color_type}: {path}"
        )

    bytes_per_pixel = 4
    stride = width * bytes_per_pixel
    inflated = zlib.decompress(b"".join(compressed_parts))
    expected_length = (stride + 1) * height
    if len(inflated) != expected_length:
        raise AssertionError(f"unexpected PNG data length in {path}")

    pixels = bytearray(width * height * bytes_per_pixel)
    prior = bytearray(stride)
    for y in range(height):
        row_offset = y * (stride + 1)
        filter_type = inflated[row_offset]
        raw = inflated[row_offset + 1 : row_offset + 1 + stride]
        reconstructed = bytearray(stride)
        for index, value in enumerate(raw):
            left = reconstructed[index - bytes_per_pixel] if index >= bytes_per_pixel else 0
            above = prior[index]
            upper_left = prior[index - bytes_per_pixel] if index >= bytes_per_pixel else 0
            if filter_type == 0:
                result = value
            elif filter_type == 1:
                result = value + left
            elif filter_type == 2:
                result = value + above
            elif filter_type == 3:
                result = value + ((left + above) // 2)
            elif filter_type == 4:
                result = value + paeth_predictor(left, above, upper_left)
            else:
                raise AssertionError(f"unsupported PNG filter {filter_type} in {path}")
            reconstructed[index] = result & 0xFF
        start = y * stride
        pixels[start : start + stride] = reconstructed
        prior = reconstructed

    return PngImage(width, height, bytes(pixels))


def scaled_safe_size(canvas: int) -> int:
    return (canvas * 66 + 54) // 108


def contains_blue_ink(image: PngImage) -> bool:
    return any(
        image.pixel(x, y)[3] > 0
        and image.pixel(x, y)[2] > image.pixel(x, y)[1] > image.pixel(x, y)[0]
        for y in range(image.height)
        for x in range(image.width)
    )


class BrandIconTest(unittest.TestCase):
    def test_adaptive_foregrounds_match_density_and_safe_zone(self) -> None:
        for density, (adaptive_size, _) in DENSITIES.items():
            with self.subTest(density=density):
                image = inspect_png(
                    RES_DIR / f"mipmap-{density}" / "ic_launcher_foreground.png"
                )
                self.assertEqual((image.width, image.height), (adaptive_size, adaptive_size))
                left, top, right, bottom = image.alpha_bounds()
                safe_size = scaled_safe_size(adaptive_size)
                self.assertLessEqual(right - left + 1, safe_size)
                self.assertLessEqual(bottom - top + 1, safe_size)
                self.assertLessEqual(abs((left + right + 1) - adaptive_size), 2)
                self.assertLessEqual(abs((top + bottom + 1) - adaptive_size), 2)
                self.assertTrue(contains_blue_ink(image))

    def test_legacy_icons_match_density_and_masks(self) -> None:
        for density, (_, legacy_size) in DENSITIES.items():
            for filename in ("ic_launcher.png", "ic_launcher_round.png"):
                with self.subTest(density=density, filename=filename):
                    image = inspect_png(RES_DIR / f"mipmap-{density}" / filename)
                    self.assertEqual((image.width, image.height), (legacy_size, legacy_size))
                    self.assertEqual(image.pixel(0, 0)[3], 0)
                    self.assertTrue(contains_blue_ink(image))

    def test_store_and_web_exports_have_expected_geometry(self) -> None:
        play_store = inspect_png(BRAND_DIR / "playstore-icon.png")
        self.assertEqual((play_store.width, play_store.height), (512, 512))
        self.assertEqual(play_store.pixel(0, 0), (0, 0, 0, 255))
        self.assertTrue(contains_blue_ink(play_store))
        self.assertLess((BRAND_DIR / "playstore-icon.png").stat().st_size, 1024 * 1024)

        web = inspect_png(BRAND_DIR / "ic_launcher-web.png")
        self.assertEqual((web.width, web.height), (512, 512))
        self.assertEqual(web.pixel(0, 0)[3], 0)
        left, top, right, bottom = web.alpha_bounds()
        self.assertGreaterEqual((right - left + 1) / web.width, 0.9)
        self.assertGreaterEqual((bottom - top + 1) / web.height, 0.9)

    def test_adaptive_xml_and_backgrounds_stay_aligned(self) -> None:
        expected_layers = {
            "background": "@color/ic_launcher_background",
            "foreground": "@mipmap/ic_launcher_foreground",
            "monochrome": "@mipmap/ic_launcher_foreground",
        }
        for filename in ("ic_launcher.xml", "ic_launcher_round.xml"):
            root = ElementTree.parse(RES_DIR / "mipmap-anydpi-v26" / filename).getroot()
            layers = {
                child.tag: child.attrib[f"{{{ANDROID_NS}}}drawable"] for child in root
            }
            self.assertEqual(layers, expected_layers)

        for filename, resource_name in (
            ("ic_launcher_background.xml", "ic_launcher_background"),
            ("colors.xml", "splash_background"),
        ):
            root = ElementTree.parse(RES_DIR / "values" / filename).getroot()
            colors = {child.attrib["name"]: (child.text or "").strip() for child in root}
            self.assertEqual(colors[resource_name], "#000000")

        splash = ElementTree.parse(RES_DIR / "drawable" / "ic_splash_logo.xml").getroot()
        self.assertEqual(splash.attrib[f"{{{ANDROID_NS}}}drawable"], "@mipmap/ic_launcher_foreground")
        self.assertEqual(splash.attrib[f"{{{ANDROID_NS}}}inset"], "35%")

    def test_pipeline_is_local_and_ci_runs_this_guardrail(self) -> None:
        generator = (ROOT / "scripts" / "generate-brand-app-icons.sh").read_text()
        renderer = RENDERER.read_text()
        for script in (generator, renderer):
            self.assertNotIn("../notebox", script)
            self.assertNotIn("/notebox/", script)
        self.assertIn("branding/logo-e.svg", renderer)

        workflow = (ROOT / ".github" / "workflows" / "android-ci.yml").read_text()
        self.assertIn("python3 scripts/generate-brand-app-icons.test.py", workflow)

    @unittest.skipUnless(
        shutil.which("magick") and shutil.which("identify"),
        "ImageMagick is unavailable; committed PNG validation still ran",
    )
    def test_live_svg_renderer_when_imagemagick_is_available(self) -> None:
        with tempfile.TemporaryDirectory(prefix="eskerra-go-logo-test-") as output_dir:
            subprocess.run(
                [str(RENDERER), output_dir, "32", "128"],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )
            for size in (32, 128):
                image = inspect_png(Path(output_dir) / f"logo-e-{size}.png")
                self.assertEqual((image.width, image.height), (size, size))
                left, top, right, bottom = image.alpha_bounds()
                self.assertGreaterEqual((right - left + 1) / size, 0.9)
                self.assertGreaterEqual((bottom - top + 1) / size, 0.9)


if __name__ == "__main__":
    unittest.main(verbosity=2)
