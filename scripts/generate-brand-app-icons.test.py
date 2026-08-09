#!/usr/bin/env python3
"""Validate committed Android brand assets without requiring ImageMagick."""

from __future__ import annotations

import math
import re
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
LOGO_SIZE_DP = 58
NOTE_INPUT_BACKGROUND = (49, 43, 43, 255)
LAUNCHER_BACKGROUND = (49, 43, 43, 255)
LOGO_CENTER = (125, 128)
LOGO_STROKE_WIDTH = 10

DENSITIES = {
    "ldpi": (81, 36),
    "mdpi": (108, 48),
    "hdpi": (162, 72),
    "xhdpi": (216, 96),
    "xxhdpi": (324, 144),
    "xxxhdpi": (432, 192),
}

# The circular track center is canvas-centered. The e's leftward crossbars make
# its overall ink bounds intentionally asymmetric; lock those bounds explicitly.
EXPECTED_FOREGROUND_BOUNDS = {
    "ldpi": (18, 18, 60, 61),
    "mdpi": (25, 25, 80, 82),
    "hdpi": (37, 38, 119, 122),
    "xhdpi": (50, 52, 160, 163),
    "xxhdpi": (76, 78, 240, 245),
    "xxxhdpi": (102, 104, 320, 327),
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
    return (canvas * LOGO_SIZE_DP + 54) // 108


def contains_blue_ink(image: PngImage) -> bool:
    return any(
        image.pixel(x, y)[3] > 0
        and image.pixel(x, y)[2] > image.pixel(x, y)[1] > image.pixel(x, y)[0]
        for y in range(image.height)
        for x in range(image.width)
    )


def contains_white_ink(image: PngImage) -> bool:
    return any(
        image.pixel(x, y)[3] > 0
        and image.pixel(x, y)[0] > 180
        and image.pixel(x, y)[1] > 140
        and image.pixel(x, y)[2] > 140
        and abs(image.pixel(x, y)[1] - image.pixel(x, y)[2]) <= 3
        for y in range(image.height)
        for x in range(image.width)
    )


def contains_launcher_red(image: PngImage) -> bool:
    return any(
        image.pixel(x, y)[3] > 0
        and image.pixel(x, y)[0] > image.pixel(x, y)[1]
        and image.pixel(x, y)[0] > image.pixel(x, y)[2]
        for y in range(image.height)
        for x in range(image.width)
    )


def uses_only_brand_red_hue_or_neutral(image: PngImage) -> bool:
    for y in range(image.height):
        for x in range(image.width):
            red, green, blue, alpha = image.pixel(x, y)
            if alpha == 0 or red == green == blue:
                continue
            if not red > green == blue:
                return False
    return True


def point_on_cubic(
    points: tuple[tuple[float, float], ...],
    t: float,
) -> tuple[float, float]:
    u = 1 - t
    return (
        u**3 * points[0][0]
        + 3 * u**2 * t * points[1][0]
        + 3 * u * t**2 * points[2][0]
        + t**3 * points[3][0],
        u**3 * points[0][1]
        + 3 * u**2 * t * points[1][1]
        + 3 * u * t**2 * points[2][1]
        + t**3 * points[3][1],
    )


def minimum_distance_to_cubic(
    point: tuple[float, float],
    cubic: tuple[tuple[float, float], ...],
) -> float:
    def distance_squared(t: float) -> float:
        candidate = point_on_cubic(cubic, t)
        return (point[0] - candidate[0]) ** 2 + (point[1] - candidate[1]) ** 2

    samples = 1000
    best_index = min(
        range(samples + 1),
        key=lambda index: distance_squared(index / samples),
    )
    lower = max(0.0, (best_index - 1) / samples)
    upper = min(1.0, (best_index + 1) / samples)
    for _ in range(80):
        first = lower + (upper - lower) / 3
        second = upper - (upper - lower) / 3
        if distance_squared(first) < distance_squared(second):
            upper = second
        else:
            lower = first
    return math.sqrt(distance_squared((lower + upper) / 2))


class BrandIconTest(unittest.TestCase):
    def test_launcher_background_matches_brand_tinted_note_input(self) -> None:
        self.assertEqual(LAUNCHER_BACKGROUND, NOTE_INPUT_BACKGROUND)
        self.assertGreater(LAUNCHER_BACKGROUND[0], LAUNCHER_BACKGROUND[1])
        self.assertEqual(LAUNCHER_BACKGROUND[1], LAUNCHER_BACKGROUND[2])

    def test_editable_e_mark_uses_three_exact_concentric_tracks(self) -> None:
        root = ElementTree.parse(BRAND_DIR / "logo-e.svg").getroot()
        self.assertEqual(root.attrib["viewBox"], "35 38 180 180")
        paths = root.findall("{http://www.w3.org/2000/svg}path")
        self.assertEqual(len(paths), 3)

        tracks = ((113, 51), (128, 66), (143, 81))
        path_data: list[str] = []
        terminals: list[tuple[float, float]] = []
        for path, (horizontal_y, radius) in zip(paths, tracks, strict=True):
            data = " ".join(path.attrib["d"].split())
            path_data.append(data)
            self.assertRegex(data, rf"^M 42 {horizontal_y} H 162 C ")

            upper = re.search(
                rf"C (?:[-\d.]+ ){{4}}([-\d.]+) ([-\d.]+) "
                rf"A {radius} {radius} 0 0 0 ([-\d.]+) 98",
                data,
            )
            self.assertIsNotNone(upper)
            lower = re.search(
                rf"M ([-\d.]+) 158 A {radius} {radius} 0 0 0 "
                rf"([-\d.]+) ([-\d.]+)$",
                data,
            )
            self.assertIsNotNone(lower)
            assert upper is not None and lower is not None

            points = (
                (float(upper[1]), float(upper[2])),
                (float(upper[3]), 98.0),
                (float(lower[1]), 158.0),
                (float(lower[2]), float(lower[3])),
            )
            for x, y in points:
                self.assertAlmostEqual(
                    math.hypot(x - LOGO_CENTER[0], y - LOGO_CENTER[1]),
                    radius,
                    delta=0.001,
                )
            terminals.append((float(lower[2]), float(lower[3])))

            terminal_angle = math.degrees(
                math.atan2(
                    float(lower[3]) - LOGO_CENTER[1],
                    float(lower[2]) - LOGO_CENTER[0],
                )
            )
            self.assertAlmostEqual(terminal_angle, 35.823, delta=0.001)

        self.assertEqual(
            [tracks[index][1] - tracks[index - 1][1] for index in range(1, 3)],
            [15, 15],
        )

        outer_transition = re.search(
            r"^M 42 143 H 162 C ([-\d.]+) ([-\d.]+) ([-\d.]+) "
            r"([-\d.]+) ([-\d.]+) ([-\d.]+)",
            path_data[2],
        )
        self.assertIsNotNone(outer_transition)
        assert outer_transition is not None
        outer_transition_points = (
            (162.0, 143.0),
            (float(outer_transition[1]), float(outer_transition[2])),
            (float(outer_transition[3]), float(outer_transition[4])),
            (float(outer_transition[5]), float(outer_transition[6])),
        )
        terminal_clearance = (
            minimum_distance_to_cubic(terminals[0], outer_transition_points)
            - LOGO_STROKE_WIDTH
        )
        self.assertAlmostEqual(terminal_clearance, 5, delta=0.01)

    def test_white_launcher_and_splash_foreground_matches_density_and_safe_zone(self) -> None:
        for density, (adaptive_size, _) in DENSITIES.items():
            with self.subTest(density=density):
                image = inspect_png(
                    RES_DIR / f"mipmap-{density}" / "ic_launcher_brand_foreground.png"
                )
                self.assertEqual((image.width, image.height), (adaptive_size, adaptive_size))
                left, top, right, bottom = image.alpha_bounds()
                safe_size = scaled_safe_size(adaptive_size)
                self.assertLessEqual(right - left + 1, safe_size)
                self.assertLessEqual(bottom - top + 1, safe_size)
                self.assertEqual(
                    (left, top, right, bottom),
                    EXPECTED_FOREGROUND_BOUNDS[density],
                )
                self.assertTrue(contains_white_ink(image))

    def test_legacy_icons_match_density_and_masks(self) -> None:
        for density, (_, legacy_size) in DENSITIES.items():
            for filename in ("ic_launcher.png", "ic_launcher_round.png"):
                with self.subTest(density=density, filename=filename):
                    image = inspect_png(RES_DIR / f"mipmap-{density}" / filename)
                    self.assertEqual((image.width, image.height), (legacy_size, legacy_size))
                    self.assertEqual(image.pixel(0, 0)[3], 0)
                    self.assertTrue(contains_launcher_red(image))
                    self.assertTrue(uses_only_brand_red_hue_or_neutral(image))
                    self.assertTrue(contains_white_ink(image))

    def test_store_and_web_exports_have_expected_geometry(self) -> None:
        play_store = inspect_png(BRAND_DIR / "playstore-icon.png")
        self.assertEqual((play_store.width, play_store.height), (512, 512))
        self.assertEqual(play_store.pixel(0, 0), LAUNCHER_BACKGROUND)
        self.assertTrue(uses_only_brand_red_hue_or_neutral(play_store))
        self.assertTrue(contains_white_ink(play_store))
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
            "foreground": "@mipmap/ic_launcher_brand_foreground",
            "monochrome": "@mipmap/ic_launcher_brand_foreground",
        }
        for filename in ("ic_launcher.xml", "ic_launcher_round.xml"):
            root = ElementTree.parse(RES_DIR / "mipmap-anydpi-v26" / filename).getroot()
            layers = {
                child.tag: child.attrib[f"{{{ANDROID_NS}}}drawable"] for child in root
            }
            self.assertEqual(layers, expected_layers)

        launcher_colors = ElementTree.parse(
            RES_DIR / "values" / "ic_launcher_background.xml"
        ).getroot()
        self.assertEqual(launcher_colors[0].text.strip(), "#312B2B")

        splash_colors = ElementTree.parse(RES_DIR / "values" / "colors.xml").getroot()
        self.assertEqual(splash_colors[0].text.strip(), "#000000")

        splash = ElementTree.parse(RES_DIR / "drawable" / "ic_splash_logo.xml").getroot()
        self.assertEqual(
            splash.attrib[f"{{{ANDROID_NS}}}drawable"],
            "@mipmap/ic_launcher_brand_foreground",
        )
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
