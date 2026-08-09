#!/usr/bin/env bash
# Regenerate Android launcher, splash-source, and store icons from branding/logo-e.svg.
# Run from the eskerra-go repo root: ./scripts/generate-brand-app-icons.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RENDER_SCRIPT="$ROOT/scripts/render-logo-e-pngs.sh"
RES_DIR="$ROOT/app/src/main/res"
BRAND_DIR="$ROOT/branding"
ICON_BACKGROUND="#000000"
ICON_TEMP_DIR=""

cleanup() {
  if [[ -n "$ICON_TEMP_DIR" && -d "$ICON_TEMP_DIR" ]]; then
    rm -rf -- "$ICON_TEMP_DIR"
  fi
}

trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "error: required command not found: $1" >&2
    exit 1
  fi
}

scaled_size() {
  local canvas="$1"
  echo $(((canvas * 66 + 54) / 108))
}

normalize_png() {
  local source="$1"
  local destination="$2"
  magick "$source" -define png:color-type=6 -depth 8 -strip "$destination"
}

make_foreground() {
  local canvas="$1"
  local mark_size="$2"
  local destination="$3"
  magick "$PNG_DIR/logo-e-${mark_size}.png" \
    -background none -gravity center -extent "${canvas}x${canvas}" \
    -define png:color-type=6 -depth 8 -strip \
    "$destination"
}

make_legacy_icon() {
  local canvas="$1"
  local mark_size="$2"
  local shape="$3"
  local destination="$4"
  local center edge radius
  center=$((canvas / 2))
  edge=$((canvas - 1))
  radius=$(((canvas + 3) / 4))

  if [[ "$shape" == "round" ]]; then
    magick -size "${canvas}x${canvas}" xc:none \
      -fill "$ICON_BACKGROUND" -draw "circle $center,$center $center,0" \
      "$PNG_DIR/logo-e-${mark_size}.png" -gravity center -composite \
      -define png:color-type=6 -depth 8 -strip \
      "$destination"
  else
    magick -size "${canvas}x${canvas}" xc:none \
      -fill "$ICON_BACKGROUND" \
      -draw "roundrectangle 0,0 $edge,$edge $radius,$radius" \
      "$PNG_DIR/logo-e-${mark_size}.png" -gravity center -composite \
      -define png:color-type=6 -depth 8 -strip \
      "$destination"
  fi
}

validate_dimensions() {
  local path="$1"
  local expected="$2"
  local dimensions
  dimensions="$(identify -format '%wx%h' "$path")"
  if [[ "$dimensions" != "${expected}x${expected}" ]]; then
    echo "error: expected ${expected}x${expected}, got $dimensions: $path" >&2
    exit 1
  fi
}

validate_foreground_bounds() {
  local path="$1"
  local maximum="$2"
  local bounds width height
  bounds="$(magick "$path" -alpha extract -threshold 0 -trim -format '%wx%h' info:)"
  width="${bounds%x*}"
  height="${bounds#*x}"
  if ((width > maximum || height > maximum)); then
    echo "error: foreground exceeds ${maximum}x${maximum} safe bounds: $path ($bounds)" >&2
    exit 1
  fi
}

main() {
  local spec density adaptive_size legacy_size mark_size output_dir
  local -a density_specs=(
    "ldpi:81:36"
    "mdpi:108:48"
    "hdpi:162:72"
    "xhdpi:216:96"
    "xxhdpi:324:144"
    "xxxhdpi:432:192"
  )
  local -a render_sizes=(50 66 99 132 198 264 22 29 44 59 88 117 313 512)

  require_command magick
  require_command identify
  require_command install

  if [[ ! -x "$RENDER_SCRIPT" ]]; then
    echo "error: PNG renderer not executable: $RENDER_SCRIPT" >&2
    exit 1
  fi

  ICON_TEMP_DIR="$(mktemp -d)"
  PNG_DIR="$ICON_TEMP_DIR/png"
  OUTPUT_DIR="$ICON_TEMP_DIR/output"
  mkdir -p "$OUTPUT_DIR"
  "$RENDER_SCRIPT" "$PNG_DIR" "${render_sizes[@]}"

  for spec in "${density_specs[@]}"; do
    IFS=: read -r density adaptive_size legacy_size <<<"$spec"
    mark_size="$(scaled_size "$adaptive_size")"
    output_dir="$OUTPUT_DIR/mipmap-$density"
    mkdir -p "$output_dir"

    make_foreground \
      "$adaptive_size" \
      "$mark_size" \
      "$output_dir/ic_launcher_foreground.png"
    validate_dimensions "$output_dir/ic_launcher_foreground.png" "$adaptive_size"
    validate_foreground_bounds "$output_dir/ic_launcher_foreground.png" "$mark_size"

    mark_size="$(scaled_size "$legacy_size")"
    make_legacy_icon \
      "$legacy_size" \
      "$mark_size" \
      "regular" \
      "$output_dir/ic_launcher.png"
    make_legacy_icon \
      "$legacy_size" \
      "$mark_size" \
      "round" \
      "$output_dir/ic_launcher_round.png"
    validate_dimensions "$output_dir/ic_launcher.png" "$legacy_size"
    validate_dimensions "$output_dir/ic_launcher_round.png" "$legacy_size"
  done

  magick -size 512x512 "xc:$ICON_BACKGROUND" \
    "$PNG_DIR/logo-e-313.png" -gravity center -composite \
    -define png:color-type=6 -depth 8 -strip \
    "$OUTPUT_DIR/playstore-icon.png"
  normalize_png "$PNG_DIR/logo-e-512.png" "$OUTPUT_DIR/ic_launcher-web.png"
  validate_dimensions "$OUTPUT_DIR/playstore-icon.png" 512
  validate_dimensions "$OUTPUT_DIR/ic_launcher-web.png" 512

  for spec in "${density_specs[@]}"; do
    IFS=: read -r density adaptive_size legacy_size <<<"$spec"
    output_dir="$RES_DIR/mipmap-$density"
    mkdir -p "$output_dir"
    install -m 0644 \
      "$OUTPUT_DIR/mipmap-$density/ic_launcher.png" \
      "$output_dir/ic_launcher.png"
    install -m 0644 \
      "$OUTPUT_DIR/mipmap-$density/ic_launcher_foreground.png" \
      "$output_dir/ic_launcher_foreground.png"
    install -m 0644 \
      "$OUTPUT_DIR/mipmap-$density/ic_launcher_round.png" \
      "$output_dir/ic_launcher_round.png"
  done

  install -m 0644 "$OUTPUT_DIR/playstore-icon.png" "$BRAND_DIR/playstore-icon.png"
  install -m 0644 "$OUTPUT_DIR/ic_launcher-web.png" "$BRAND_DIR/ic_launcher-web.png"

  echo "Done. Android app icons regenerated from $BRAND_DIR/logo-e.svg"
}

main "$@"
