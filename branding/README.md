# Branding assets

The Pengu mark: a play button that doubles as a penguin's beak, split into its two
mandibles. The store, launcher and window icons all derive from `pengu.svg`.

## Rules

- **`pengu.svg` is the only master.** Every other file is derived; do not edit the
  generated ones by hand (they carry a header saying so).
- **The mark fills 72 % of the tile** (65 % wide), centred — 1.513× the original
  hand-off, which sat at 48 %. Above that the play triangle crowds the Play
  keyline; below it the icon reads as a dot in a taskbar drawn at 24–32 px.
- **The tips are round, radius 64.** A 128-wide round-join stroke draws the
  triangle, so the radius is the stroke; the triangle itself is scaled 0.849 about
  its centre so the outer hull keeps the hand-off's 488-unit height. Growing the
  radius on the unscaled path would pull the tips in and shrink the mark.
- **The mandible tones are stepped shading.** `#F5B02A` upper, `#E79D1B` across the
  seam, `#D98A0B` lower, as three flat bands cut along the seam's own diagonal: the
  gradient axis is perpendicular to that seam and centred on it, and each cut is a
  duplicated hard stop, so nothing is blurred. `#F5B02A` and `#D98A0B` are also the
  app's `accent` and `accentContainer` (`Tokens.kt`), so the UI chrome and the icon
  agree. The Android adaptive foreground carries all three bands too: a
  VectorDrawable cannot gradient a stroke, so each band is its own copy of the beak
  clipped to its own slice of the axis, cut at the master's stop offsets and painted
  dark to light. Its scale is fitted from the master, so the hull fills 60 % of the
  72 dp mask — less than the rasters' 72 %, because the launcher crops the 108 dp
  foreground to that mask and scales it up, so an equal fill reads heavier than the
  square tile. `ANDROID_MARK_FILL` is the one knob; 60 % measured the same
  glyph-to-tile ratio as the Play Store icon on a Pixel drawer. A hand-set scale
  outlives the artwork it was tuned for.
- **The tile is `#0B0B0B`.** Light UI, dark UI and the store all get the same tile.

## Files

| file | use |
|---|---|
| `pengu.svg` | master: full square, 1024 viewBox, 512×512 intrinsic |
| `pengu.png` | Google Play store icon: 512×512, PNG-32, sRGB, full square |
| `pengu-rounded.svg` / `.png` | 22 % corner radius, for surfaces that mask themselves (legacy launchers, in-app) |
| `pengu-frameless.svg` | the mark alone at 85 %, the source of the desktop rasters |
| `pengu.ico` | Windows icon for the packaged `.exe`: 16/20/24/30/32/36/40/48/60/64/72/96/128/256, BMP up to 32 px and PNG above |

The store icon follows the [Play icon spec](https://developer.android.com/distribute/google-play/resources/icon-design-specifications):
full square, no corner radius and no drop shadow (Google applies both).

**The desktop icons are the exception to the tile.** On a taskbar the tile costs
the mark a fifth of its size, so `pengu-frameless.svg` drops it and draws the mark
at 85 % with a dark outline one device pixel wide. Without that outline the two
oranges drop to 1.7:1 against the Windows light taskbar (`#F3F3F3`) and wash out;
with it they hold 8.6:1 on the dark theme and stay crisp on the light one.

## Regenerating

Everything except the master is generated, in two ordered steps — the second reads
what the first writes:

    node scripts/gen_brand_icons.mjs     # text: SVGs and Android drawables
    node scripts/gen_brand_rasters.mjs   # rasters; needs `npm i --no-save sharp`

The first rewrites `pengu-rounded.svg`, `pengu-frameless.svg`,
`app/androidApp/src/main/res/drawable/ic_launcher_background.xml`,
`app/androidApp/src/main/res/drawable-v24/ic_launcher_foreground.xml` and
`app/androidApp/src/main/res/drawable/ic_launcher_monochrome.xml`.

The second rewrites the rasters. They are committed because the builds consume
them, but they are outputs and the header of each says so:

| output | source | sizes |
|---|---|---|
| `branding/pengu.png` | `pengu.svg` | 512 |
| `branding/pengu-rounded.png` | `pengu-rounded.svg` | 512 |
| `app/androidApp/src/main/res/mipmap-*/ic_launcher.png` | `pengu-rounded.svg` | 48/72/96/144/192 |
| `app/androidApp/src/main/res/mipmap-*/ic_launcher_round.png` | `pengu-rounded.svg` with `rx="512"` | 48/72/96/144/192 |
| `app/desktopApp/src/main/resources/pengu-icon/pengu-N.png` | `pengu-frameless.svg`, re-stroked per size | 16/20/24/32/40/48/256 |
| `branding/pengu.ico` | the desktop ladder | 14 entries, BMP up to 32 px and PNG above |

The desktop outline is one device pixel, so the frameless SVG is re-stroked for
every size; the script derives the width from the mark's scale, so the two cannot
drift. It also measures the store PNG and fails if the mark is not ~72 % of the
tile, which is the check that catches a changed drawing nobody re-fitted.

`Main.kt` hands the desktop ladder to the window as `iconImages` so Windows picks
the size that matches the current DPI instead of rescaling one bitmap, and
`nativeDistributions.windows.iconFile` passes `pengu.ico` to jpackage.

The ladder lives inside the module on purpose: it is a runtime resource. Pointing
a `srcDir` at `branding/` made the IDE report that folder as part of the desktop
app's `main` source set.

## Updating the mark

Rare enough to forget the order. Six steps:

1. **Edit `pengu.svg` only**, and keep the pieces the generator reads: `<path
   id="beak">` (its `stroke-width` is the corner radius), `<linearGradient
   id="plumage">` with a duplicated stop pair per hard cut, and the 1024 tile
   `<rect>`. Do not break them: the generator throws rather than guess.
2. **Re-fit the hull to 488 units tall, centred on (518.5, 512).** That is what puts
   the mark at 72 % of the tile; a new drawing is almost never that size, so scale it
   — the current 0.849 is that fit baked into the path.
3. Run both generators. The raster step fails if the mark is not ~72 % of the store
   tile, so a missing re-fit cannot pass unnoticed.
4. **Look at it small**: 16/24/32 px on light and dark backgrounds, plus the 512
   store PNG.
5. **Android: uninstall, then install.** Launchers cache icons and `install -r`
   often does not refresh them. Themed icons live off the generated monochrome
   layer; if a Samsung keeps drawing a blank icon, check `adb shell settings get
   system current_sec_appicon_theme_package` — a corrupt theme package name there
   makes the launcher fail to load adaptive icons.
6. **Update `docs/architecture-decisions.md` and its `_ES.md` mirror in the same
   commit**, including the numbers that hang off the geometry: the 72 %/65 %
   footprint and the 60 % Android fill.
