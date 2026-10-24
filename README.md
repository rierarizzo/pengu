<p align="center">
  <img src="branding/pengu-rounded.png" width="120" alt="Pengu">
</p>

<h1 align="center">Pengu</h1>

A Compose Multiplatform client for Windows 11, Android (and Linux later) that
connects to the **Nuvio** backend, is compatible with the **Stremio addon
protocol**, and plays with **mediamp** (mpv on desktop, ExoPlayer on Android).
Complementary to Nuvio, not a replacement.

This repository is the **Compose Multiplatform** implementation, migrated from
the original Flutter app. Desktop and Android are both wired up end to end
against the same shared code; each composition root picks its player engine.

## Stack

| Layer | Technology |
| --- | --- |
| UI | Compose Multiplatform + Material 3, Roboto or Inter |
| State | `androidx.lifecycle` ViewModel + `StateFlow` |
| HTTP | Ktor client (CIO for the backend, OkHttp for addons) |
| JSON | kotlinx.serialization (defensive `JsonElement` parsing) |
| Images | Coil 3 |
| Playback | mediamp (mpv on desktop, ExoPlayer on Android), isolated behind `PlayerService` |
| Accounts/sync | Nuvio API (Supabase) |
| Sources | Stremio addons (HTTP/JSON) |
| Metadata | Stremio addons, optionally enriched with TMDB |

## Modules

```
app/desktopApp   composition root: window, mpv player (mediamp), main()
app/androidApp   composition root: Activity, ExoPlayer player (mediamp)
app/shared       Compose UI and ViewModels
core             pure domain: models, repository contracts, watched rules
data             Stremio, Nuvio and local implementations
branding         the mark and every icon derived from it (see branding/README.md)
docs             the decision record, mirrored in Spanish
```

`scripts/` holds the generators (the in-app Material glyphs and the brand icons);
`branding` is the only copy of the mark — everything else is derived.

The dependency direction is `ui -> domain` and `data -> domain`; only the
composition roots reference `data`. See
[`docs/architecture-decisions.md`](docs/architecture-decisions.md) for the full
decision record.

## Running

- Desktop: `./gradlew :app:desktopApp:run`
- Android: `./gradlew :app:androidApp:assembleDebug`

Metadata comes from the Stremio addons. Setting a TMDB API key in
Settings → Metadata enables the optional enrichment: missing artwork, synopses
and cast are filled in, and the "You may also like" row is built from TMDB's
recommendations (the addon's own list is the fallback when there is no key).
The phone billboard also prefers TMDB's language-neutral (textless) poster over
the wide backdrop, which a narrow screen crops too aggressively.

Development shortcuts (environment variables, read for the run only):

- `PENGU_BACKEND=local` runs everything on this machine with no account.
- `PENGU_LOCAL_ADDONS=<manifest-url>[,<manifest-url>...]` gives local mode
  catalogs.
- `NUVIO_EMAIL` / `NUVIO_PASSWORD` signs in without going through Settings.
- `--play=<url-or-path>` opens straight into the player.

## Tests

- Domain: `./gradlew :core:jvmTest`
- Data: `./gradlew :data:jvmTest`
- UI/ViewModels: `./gradlew :app:shared:jvmTest`
- Desktop player helpers: `./gradlew :app:desktopApp:test`

## Quality gates

- Lint and formatting: `./gradlew ktlintCheck` (apply with `./gradlew ktlintFormat`).
- CI (`.github/workflows/ci.yml`) runs lint, the three test suites, the desktop
  compile and the Android debug build.

## References

- Stremio addon protocol: `https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md`
- Nuvio backend (self-host): `https://github.com/NuvioMedia/self-host`
- Compose Multiplatform: `https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform.html`

## Acknowledgements

The UI/UX draws inspiration from HBO Max. Some implementation details were
informed by reviewing the Nuvio and Animeko codebases.

This product uses the TMDB API but is not endorsed or certified by TMDB.

## License

Pengu is licensed under the [GNU General Public License v3.0](LICENSE)
(SPDX: `GPL-3.0`).

Bundled third-party components (fonts, icons and the player engine used for
playback) keep their own licenses; see [`licenses/`](licenses/README.md).
