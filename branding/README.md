# Eskerra Go branding

`logo-e.svg` is the editable source for the Android app logo. It is a local copy:
this repository does not read from or link to another Eskerra codebase.

Render transparent PNGs at arbitrary sizes with:

```bash
./scripts/render-logo-e-pngs.sh /tmp/eskerra-go-logo 32 512
```

The renderer requires ImageMagick 7 (`magick` and `identify`). App-specific
launcher, splash, and store assets are regenerated with:

```bash
./scripts/generate-brand-app-icons.sh
```

The generator updates every launcher density and the 512px Play Store and
transparent web exports. The launcher uses a white e-logo on `#E35D5D`; its
separate `ic_launcher_brand_foreground` also provides the splash's white e-logo
on its unchanged black background. Replace `logo-e.svg` and rerun this command
after changing the mark. Do not add a link or copy step to another repository.

Launcher and store compositions size the mark to 58dp inside Android's 108dp
adaptive canvas, leaving deliberate breathing room inside circular masks.

CI validates the committed PNG geometry and Android resource references with
Python's standard library, so it does not require ImageMagick. The live SVG
renderer smoke test runs when ImageMagick is available and is skipped otherwise.
