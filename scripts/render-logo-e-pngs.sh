#!/usr/bin/env bash
# Render the Android-local editable logo SVG to transparent square PNG files.
# Usage: ./scripts/render-logo-e-pngs.sh [output-dir] [size ...]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="$ROOT/branding/logo-e.svg"
OUTPUT_DIR="${1:-/tmp/eskerra-go-logo-e-pngs}"

if [[ $# -gt 0 ]]; then
  shift
fi

if [[ $# -gt 0 ]]; then
  SIZES=("$@")
else
  SIZES=(32 64 128 256 512 1024)
fi

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "error: required command not found: $1" >&2
    exit 1
  fi
}

require_command magick
require_command identify

if [[ ! -f "$SOURCE" ]]; then
  echo "error: missing SVG source: $SOURCE" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

for size in "${SIZES[@]}"; do
  if [[ ! "$size" =~ ^[1-9][0-9]*$ ]]; then
    echo "error: PNG size must be a positive integer: $size" >&2
    exit 1
  fi

  destination="$OUTPUT_DIR/logo-e-${size}.png"
  magick -density 768 -background none "$SOURCE" \
    -resize "${size}x${size}" \
    -gravity center -extent "${size}x${size}" \
    -define png:color-type=6 -depth 8 -strip \
    "$destination"

  dimensions="$(identify -format '%wx%h' "$destination")"
  if [[ "$dimensions" != "${size}x${size}" ]]; then
    echo "error: expected ${size}x${size}, got $dimensions: $destination" >&2
    exit 1
  fi

  echo "Rendered $destination"
done
