# Third-party licenses

Pengu itself is licensed under the GNU General Public License v3.0 (see the
[`LICENSE`](../LICENSE) file). It bundles or links the third-party components
below, which keep their own licenses.

| Component | Use | License |
| --- | --- | --- |
| [mediamp](https://github.com/open-ani/mediamp) (`mediamp-mpv`) | desktop playback: drives mpv and draws the video into the Compose canvas | Apache-2.0 |
| [mediamp](https://github.com/open-ani/mediamp) (`mediamp-exoplayer`) | Android playback: drives AndroidX Media3 (ExoPlayer) and embeds a `PlayerView` | Apache-2.0 |
| [AndroidX Media3](https://github.com/androidx/media) | Android playback engine pulled in by the mediamp ExoPlayer backend | Apache-2.0 |
| mediamp mpv runtime (Windows/macOS) | bundled libmpv + FFmpeg, built without GPL-only components | LGPL-2.1-or-later (see [libmpv-LGPL-2.1.txt](libmpv-LGPL-2.1.txt)) |
| mediamp mpv runtime (Linux) | bundled libmpv + FFmpeg; uses mpv's GPL-only X11 output | GPL-3.0 |
| Roboto | UI typeface (default) | SIL Open Font License 1.1 ([Roboto-OFL.txt](Roboto-OFL.txt)) |
| Inter | Alternative UI typeface, picked in Settings → Appearance | SIL Open Font License 1.1 ([Inter-OFL.txt](Inter-OFL.txt)) |
| [Material Symbols](https://github.com/google/material-design-icons) | vendored UI icon set (`MaterialIcons.kt`, Sharp style) | Apache-2.0 ([Material-Symbols-Apache-2.0.txt](Material-Symbols-Apache-2.0.txt)) |

The desktop player engine is **mediamp's mpv backend**: it loads libmpv through
a JNI wrapper and renders into the Compose/Skia canvas, so the video is normal
Compose content. On Windows and macOS the bundled libmpv is LGPL and is loaded
dynamically, which lets the user replace it; the Linux build of mpv's X11 output
has no LGPL relicensing, so the Linux runtime is GPL-3.0.

The Android player engine is **mediamp's ExoPlayer backend**: it links AndroidX
Media3 (Apache-2.0) and embeds a `PlayerView`, and bundles no native runtime of
its own.
