# Branding assets

The Pengu mark: a play button that doubles as a penguin's beak, split into its two
mandibles. The store, launcher and window icons all derive from `pengu.svg`.

## Rules

- **`pengu.svg` is the only master.** Every other file is derived; do not edit the
  generated ones by hand (they carry a header saying so).
- **The mark fills 72 % of the tile** (63.6 % wide), centred — 1.513× the original
  hand-off, which sat at 48 %. Above that the play triangle crowds the Play
  keyline; below it the icon reads as a dot in a taskbar drawn at 24–32 px.
- **The two tones are the mandibles.** `#F5B02A` upper, `#D98A0B` lower, split by
  an `#0B0B0B` hairline that is masked with the beak's own silhouette so it always
  reaches the outline at both ends. They are also the app's `accent` and
  `accentContainer` (`Tokens.kt`), so the UI chrome and the icon agree.
- **The tile is `#0B0B0B`.** Light UI, dark UI and the store all get the same tile.

## Files

| file | use |
|---|---|
| `pengu.svg` | master: full square, 1024 viewBox, 512×512 intrinsic |
| `pengu.png` | Google Play store icon: 512×512, PNG-32, sRGB, full square |
| `pengu-rounded.svg` / `.png` | 22 % corner radius, for surfaces that mask themselves (legacy launchers, in-app) |
| `pengu-frameless.svg` | the mark alone at 85 %, the source of the desktop rasters |
| `pengu.ico` | Windows icon for the packaged `.exe`: 14 sizes, BMP at 16–32 px and PNG above |

The store icon follows the [Play icon spec](https://developer.android.com/distribute/google-play/resources/icon-design-specifications):
full square, no corner radius and no drop shadow (Google applies both).

**The desktop icons are the exception to the tile.** On a taskbar the tile costs
the mark a fifth of its size, so `pengu-frameless.svg` drops it and draws the mark
at 85 % with a dark outline one device pixel wide. Without that outline the two
oranges drop to 1.7:1 against the Windows light taskbar (`#F3F3F3`) and wash out;
with it they hold 8.6:1 on the dark theme and stay crisp on the light one.

## Regenerating

Everything except the master is generated. Ask the generator for the text
artifacts — it reads the geometry from `pengu.svg`, so the paths are never typed
twice:

    node scripts/gen_brand_icons.mjs

That rewrites `pengu-rounded.svg`, `pengu-frameless.svg`,
`app/androidApp/src/main/res/drawable/ic_launcher_background.xml` and
`app/androidApp/src/main/res/drawable-v24/ic_launcher_foreground.xml`.

The rasters are committed outputs because the builds consume them. Render them
with any SVG rasteriser (`npx sharp-cli -i in.svg -o out.png resize N N`):

| output | source | sizes |
|---|---|---|
| `branding/pengu.png` | `pengu.svg` | 512 |
| `branding/pengu-rounded.png` | `pengu-rounded.svg` | 512 |
| `app/androidApp/src/main/res/mipmap-*/ic_launcher.png` | `pengu-rounded.svg` | 48/72/96/144/192 |
| `app/androidApp/src/main/res/mipmap-*/ic_launcher_round.png` | `pengu-rounded.svg` with `rx="512"` | 48/72/96/144/192 |
| `app/desktopApp/src/main/resources/pengu-icon/pengu-N.png` | `pengu-frameless.svg`, outline recomputed per size | 16/20/24/32/40/48/256 |
| `branding/pengu.ico` | the same desktop renders | 14 entries |

`Main.kt` hands the desktop ladder to the window as `iconImages` so Windows picks
the size that matches the current DPI instead of rescaling one bitmap, and
`nativeDistributions.windows.iconFile` passes `pengu.ico` to jpackage.

The ladder lives inside the module on purpose: it is a runtime resource. Pointing
a `srcDir` at `branding/` made the IDE report that folder as part of the desktop
app's `main` source set.
