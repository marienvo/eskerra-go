#!/usr/bin/env bash
# Regenerate Android launcher and store icons from branding/logo-e.svg.
# Run from the eskerra-go repo root: ./scripts/generate-brand-app-icons.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RENDER_SCRIPT="$ROOT/scripts/render-logo-e-pngs.sh"
RES_DIR="$ROOT/app/src/main/res"
BRAND_DIR="$ROOT/branding"
# Android launcher and Play Store icon colors.
LAUNCHER_BACKGROUND="#FFFFFF"
LAUNCHER_FOREGROUND="#CB4D49"
SPLASH_FOREGROUND="#FFFFFF"
LOGO_SIZE_DP=58
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
  echo $(((canvas * LOGO_SIZE_DP + 54) / 108))
}

normalize_png() {
  local source="$1"
  local destination="$2"
  magick "$source" -define png:color-type=6 -depth 8 -strip "$destination"
}

make_mark() {
  local source="$1"
  local color="$2"
  local destination="$3"
  magick "$source" -fill "$color" -colorize 100 \
    -define png:color-type=6 -depth 8 -strip \
    "$destination"
}

make_foreground() {
  local source_dir="$1"
  local canvas="$2"
  local mark_size="$3"
  local destination="$4"
  magick "$source_dir/logo-e-${mark_size}.png" \
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
      -fill "$LAUNCHER_BACKGROUND" -draw "circle $center,$center $center,0" \
      "$LAUNCHER_PNG_DIR/logo-e-${mark_size}.png" -gravity center -composite \
      -define png:color-type=6 -depth 8 -strip \
      "$destination"
  else
    magick -size "${canvas}x${canvas}" xc:none \
      -fill "$LAUNCHER_BACKGROUND" \
      -draw "roundrectangle 0,0 $edge,$edge $radius,$radius" \
      "$LAUNCHER_PNG_DIR/logo-e-${mark_size}.png" -gravity center -composite \
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
  local spec density adaptive_size legacy_size mark_size output_dir store_mark_size
  local -a density_specs=(
    "ldpi:81:36"
    "mdpi:108:48"
    "hdpi:162:72"
    "xhdpi:216:96"
    "xxhdpi:324:144"
    "xxxhdpi:432:192"
  )
  local -a render_sizes=(44 58 87 116 174 232 19 26 39 52 77 103 275 512)

  require_command magick
  require_command identify
  require_command install

  if [[ ! -x "$RENDER_SCRIPT" ]]; then
    echo "error: PNG renderer not executable: $RENDER_SCRIPT" >&2
    exit 1
  fi

  ICON_TEMP_DIR="$(mktemp -d)"
  PNG_DIR="$ICON_TEMP_DIR/png"
  LAUNCHER_PNG_DIR="$ICON_TEMP_DIR/launcher-png"
  SPLASH_PNG_DIR="$ICON_TEMP_DIR/splash-png"
  OUTPUT_DIR="$ICON_TEMP_DIR/output"
  mkdir -p "$OUTPUT_DIR" "$LAUNCHER_PNG_DIR" "$SPLASH_PNG_DIR"
  "$RENDER_SCRIPT" "$PNG_DIR" "${render_sizes[@]}"
  for mark_size in "${render_sizes[@]}"; do
    make_mark \
      "$PNG_DIR/logo-e-${mark_size}.png" \
      "$LAUNCHER_FOREGROUND" \
      "$LAUNCHER_PNG_DIR/logo-e-${mark_size}.png"
    make_mark \
      "$PNG_DIR/logo-e-${mark_size}.png" \
      "$SPLASH_FOREGROUND" \
      "$SPLASH_PNG_DIR/logo-e-${mark_size}.png"
  done

  for spec in "${density_specs[@]}"; do
    IFS=: read -r density adaptive_size legacy_size <<<"$spec"
    mark_size="$(scaled_size "$adaptive_size")"
    output_dir="$OUTPUT_DIR/mipmap-$density"
    mkdir -p "$output_dir"

    make_foreground \
      "$LAUNCHER_PNG_DIR" \
      "$adaptive_size" \
      "$mark_size" \
      "$output_dir/ic_launcher_app_foreground.png"
    make_foreground \
      "$SPLASH_PNG_DIR" \
      "$adaptive_size" \
      "$mark_size" \
      "$output_dir/ic_launcher_brand_foreground.png"
    validate_dimensions "$output_dir/ic_launcher_app_foreground.png" "$adaptive_size"
    validate_dimensions "$output_dir/ic_launcher_brand_foreground.png" "$adaptive_size"
    validate_foreground_bounds "$output_dir/ic_launcher_app_foreground.png" "$mark_size"
    validate_foreground_bounds "$output_dir/ic_launcher_brand_foreground.png" "$mark_size"

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

  store_mark_size="$(scaled_size 512)"
  magick -size 512x512 "xc:$LAUNCHER_BACKGROUND" \
    "$LAUNCHER_PNG_DIR/logo-e-${store_mark_size}.png" -gravity center -composite \
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
      "$OUTPUT_DIR/mipmap-$density/ic_launcher_app_foreground.png" \
      "$output_dir/ic_launcher_app_foreground.png"
    install -m 0644 \
      "$OUTPUT_DIR/mipmap-$density/ic_launcher_brand_foreground.png" \
      "$output_dir/ic_launcher_brand_foreground.png"
    install -m 0644 \
      "$OUTPUT_DIR/mipmap-$density/ic_launcher_round.png" \
      "$output_dir/ic_launcher_round.png"
  done

  install -m 0644 "$OUTPUT_DIR/playstore-icon.png" "$BRAND_DIR/playstore-icon.png"
  install -m 0644 "$OUTPUT_DIR/ic_launcher-web.png" "$BRAND_DIR/ic_launcher-web.png"

  echo "Done. Android launcher icons regenerated; the splash keeps its black background."
}

main "$@"
