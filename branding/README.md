# Branding assets

The Pengu mark: a penguin head in 3/4 profile on a dark tile, with a rim light.

## Rules

- **The dark tile is part of the mark, not a background.** The face is white, so it
  needs the dark ground. Never invert the colours and never drop the tile without
  replacing it: the mark stops reading as a penguin (measured: on a light
  background without the tile only the orange beak survives).
- Placing the icon on a light UI is fine as is (`#000` on `#fff` is 21:1). If the
  platform does not mask icons itself, use `pengu-rounded.*`.
- Avoid adding shapes. Volume comes from gradients and one fold, so the geometry
  stays at 3 paths plus the fold.

## Files

| file | use |
|---|---|
| `pengu.svg` | master, full square, 512×512 intrinsic |
| `pengu.png` | Google Play store icon: 512×512, PNG-32, sRGB, full square |
| `pengu-rounded.svg` / `.png` | same art with a 23 % corner radius, for desktop and in-app use |

The store icon follows the [Play icon spec](https://developer.android.com/distribute/google-play/resources/icon-design-specifications):
full square with no mask and no drop shadow (Google applies a 30 % corner radius
and its own shadow), art on the 75 % keyline. The current art occupies 63 % of
the tile.

## Palette

| role | values |
|---|---|
| tile | radial gradient `#191E2A` → `#000000` (centre at 36 %, 30 %) |
| face / cheek | linear `#FFFFFF` → `#E3EAF2` / `#DCE4EE` |
| beak | linear `#FFC24A` → `#CF7E08` |
| beak fold | `#B8690A` at 50 % over the beak |
| rim light | `#2B2E35`, fades to transparent at both ends |

## Regenerating

`pengu.png` is a 512 px render of `pengu.svg` (any vector tool, or
`npx sharp-cli -i pengu.svg -o pengu.png resize 512 512`). Keep sRGB and the
alpha channel.

The desktop window and taskbar icon is
`app/desktopApp/src/main/resources/pengu-icon.png`, a 256 px render of
`pengu-rounded.svg`, loaded by `Main.kt`.

The Android launcher icon is derived from the same geometry, not redrawn:

- `app/androidApp/src/main/res/drawable/ic_launcher_background.xml` — the tile gradient.
- `app/androidApp/src/main/res/drawable-v24/ic_launcher_foreground.xml` — the mark,
  fitted to a 70 dp circumscribed circle inside the 108 dp adaptive canvas
  (group `scale 0.07188`, `translate 23.68 21.12`), flat mid-tones because the
  gradients are invisible at launcher sizes.
- `app/androidApp/src/main/res/mipmap-*/ic_launcher[_round].png` — legacy icons
  (rounded tile and circle) at 48–192 px.
