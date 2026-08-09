# Eskerra Go branding

`logo-e.svg` is the editable source for the Android app logo. It is a local copy:
this repository does not read from or link to another Eskerra codebase.

Render transparent PNGs at arbitrary sizes with:

```bash
./scripts/render-logo-e-pngs.sh /tmp/eskerra-go-logo 32 512
```

The renderer requires ImageMagick 7 (`magick` and `identify`). App-specific
launcher, splash, and store assets are generated separately by the Android icon
generator.
