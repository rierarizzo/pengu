# Pengu — Developer Guide

A hands-on map of the codebase for someone joining the project. It tells you
**where things live**, **where to change things**, and **which Kotlin / Compose
Multiplatform ideas** the code relies on. For the *why* behind decisions, read
[`architecture-decisions.md`](architecture-decisions.md); this file is the *where*.

> Pengu is a Compose Multiplatform client for the Nuvio backend that speaks
> the Stremio addon protocol and plays video with mediamp (mpv on desktop,
> ExoPlayer on Android). Desktop and Android are both fully wired targets.

---

## 1. The shape of the project

```
app/desktopApp   Composition root: window, mpv player (mediamp), main()
app/androidApp   Composition root: Activity, ExoPlayer player (mediamp)
app/player       Shared PlayerService adapter over mediamp (both roots use it)
app/shared       Compose UI + ViewModels (the whole product UI)
core             Pure domain: models, repository contracts, rules, constants
data             Implementations: Stremio addons, Nuvio backend, local files,
                 optional TMDB enrichment
```

The one rule that matters:

```
ui  ──▶ core ◀── data
```

- `core` depends on nothing (no UI, no network, no platform). Keep it that way.
- `data` depends on `core` only; `app:shared` depends on `core` and `app:player`.
- **Only the composition roots** (`app:desktopApp`, `app/androidApp`) may import
  `data`. If you ever find yourself wanting `data` from a screen, you want a new
  repository interface in `core` instead.

This is why the codebase is testable: the UI talks to interfaces, and tests
inject in-memory fakes.

`data` also has a `jvmSharedMain` source set, shared by its `jvmMain` and
`androidMain` targets, where the file helpers both platforms use live once (see
§7).

---

## 2. Where to look when you want to change something

| I want to… | Start here |
|---|---|
| Add or change a screen section (Home, Search…) | `app/shared/.../ui/shell/AppSection.kt`, `SectionHost.kt` |
| Add a pushed screen (detail, player…) | `app/shared/.../ui/shell/ContentNavigator.kt` (`ContentRoute`), `ContentArea.kt` |
| Change the player overlay (header, seek bar, controls) | `app/shared/.../ui/player/` (`PlayerControls.kt`, `PlayerSeekBar.kt`) |
| Change the desktop player's engine options (mpv) | `app/desktopApp/.../player/` (`MpvPlaybackOptions.kt`), `core/.../repository/SettingsRepository.kt`, the Playback page in `SettingsScreen.kt` |
| Add or change a settings category | `app/shared/.../ui/screens/SettingsCategory.kt`, `SettingsScreen.kt` |
| Manage installed addons | `core/.../repository/AddonRepository.kt`, `data/.../backend/NuvioAddonRepository.kt`, `app/shared/.../ui/screens/AddonsSettings.kt` |
| Change a screen's logic/state | `app/shared/.../ui/<feature>/<Feature>ViewModel.kt` + `<Feature>Screen.kt` |
| Share behavior across data sections | `app/shared/.../ui/common/` (`AccountScopedViewModel`, `WatchedBadgeSync`) |
| Change colors, spacing, type, motion | `app/shared/.../ui/theme/` (`Tokens.kt`, `AppTheme.kt`, `AppType.kt`, `Motion.kt`, `WindowSize.kt`, `PosterSize.kt`) |
| Add a theme | `AppTheme` / `AppSettings.theme` in `core/.../repository/SettingsRepository.kt`, `AppThemes` in `ui/theme/Tokens.kt`, picker in `SettingsScreen.kt` |
| Change the typeface | `AppTypeface` / `AppSettings.typeface` in `core/.../repository/SettingsRepository.kt`, `appFontFamily` in `ui/theme/AppTheme.kt`, picker in `SettingsScreen.kt`, the TTFs in `app/shared/.../composeResources/font/` |
| Reuse a widget (buttons, posters, search field) | `app/shared/.../ui/components/` |
| Change a domain model or add one | `core/.../core/model/` |
| Use a shared constant (content type, catalog extra, threshold) | `core/.../core/model/` (`ContentTypes`, `CatalogExtras`, `ProgressThresholds`) |
| Change a repository contract | `core/.../core/repository/` |
| Change how metadata/streams/subtitles are fetched | `data/.../data/addons/` |
| Change HTTP caching, compression or DNS (cold-start speed) | `data/.../data/platform/` (`HttpDefaults.kt`, `Platform.jvm.kt`, `Platform.android.kt`) |
| Change the optional TMDB enrichment (id resolution, artwork, recommendations) | `data/.../data/tmdb/`, `core/.../model/Tmdb.kt` |
| Change the Nuvio backend behavior | `data/.../data/backend/` |
| Change local/offline mode | `data/.../data/local/` and `data/.../data/store/` |
| Add a backend | `data/.../data/BackendFactory.kt` (`BackendKind`, `createBackend`) |
| Add a player engine | Implement `core/.../player/PlayerService.kt`; wire it in a composition root |
| Change platform behavior (files, back, video surface) | `app/shared/src/{commonMain,jvmMain,androidMain}/.../ui/platform/` and `ui/video/` |
| Change app wiring / startup | `app/desktopApp/.../Main.kt`, `app/androidApp/.../MainActivity.kt` |
| Add a user-visible string | `app/shared/src/commonMain/composeResources/values/strings.xml` |

---

## 3. Follow one request end to end

Example: user taps a poster on Home and presses **Watch now**.

1. `HomeScreen` → `navigator.push(ContentRoute.Detail(item))` (`ContentNavigator`).
2. `ContentArea` renders `DetailScreen`; `DetailViewModel.load()` calls
   `MetadataRepository.detail(...)`.
3. The concrete repo is `StremioMetadataRepository` (wired in `Main.kt`), which
   asks the enabled addons and returns the first hit.
4. Pressing **Watch now** opens `StreamsDialog` → `StreamsViewModel` →
   `StreamRepository.all(...)` → `StremioStreamRepository` (aggregates addons).
5. Selecting a stream pushes `ContentRoute.Player(PlaybackTarget…)`.
6. `PlayerScreen` calls `PlayerService.open(target)`. Both composition roots
   install a `MediampPlayerService`: mediamp's mpv backend on desktop, its
   ExoPlayer backend on Android.
7. `PlaybackProgressReporter` saves progress through `ProgressRepository` while
   playing.

Every arrow above crosses an interface, never a concrete class.

---

## 4. Shared building blocks

These exist so the same concern is not re-implemented per screen:

- **`AccountScopedViewModel`** (`ui/common`) — every data section extends it. It
  owns the "load when signed in and a profile is chosen, clear when signed out"
  rule; subclasses implement `onAccountReady()` / `onAccountCleared()`.
- **`WatchedBadgeSync`** (`ui/common`) — resolves watched badges and drops stale
  results, so Home and Library do not duplicate the request-ticket logic.
- **`ReorderableCardColumn`** (`ui/components`) — a short list of cards reordered
  by dragging the handle of one of them; the caller owns the list and persists
  the new order once, on drop. Used by Settings → Addons; reuse it for any other
  card list that needs an order.
- **`StremioAddonRepositories`** (`data/addons`) — builds the catalog, metadata,
  stream and subtitle repositories over one `AddonClientPool`, so a cached
  `StremioAddonClient` (and its addon manifest) is shared instead of rebuilt per
  repository.
- **`ProfileScope`** (`data/backend`) — `AccountRepository.requireProfileId()` /
  `requireActiveProfile()`, used by every profile-scoped Nuvio repository.
- **`AddonSnapshotStore`** (`core` contract, `data/store` file
  implementation) — the last known addon list, per profile. It keeps every
  installed addon, enabled or not, so `AddonsSettings` can manage them while
  `all()` filters the disabled ones out. It is the local-first layer:
  `NuvioAddonRepository` answers from its snapshot and refreshes behind it, and
  degrades to "nothing stored" when the file is missing or corrupt. The feed
  itself is not persisted: Home always loads its catalogs.
- **`VideoOutput`** (`core/player`) — typed marker for the player's platform
  output; `VideoSurfaceRenderer` (in `app/player`) extends it and draws the video
  as Compose content, whichever engine is wired in.
- **`ContentTypes` / `CatalogExtras` / `ProgressThresholds` / `releaseYear()`**
  (`core/model`) — the protocol vocabulary and thresholds, defined once.
- **Design tokens** (`theme/Tokens.kt`) — the theme palettes (`BrandPalette`
  values in `AppThemes`), spacing, sizes (including the content/form/message max
  widths), alphas and scrims. `PenguTheme(theme)` provides the selected
  palette through `LocalBrandPalette`; `Modifier.focusRing`
  (`components/Interaction.kt`) draws the shared keyboard focus ring.
- **`runCatchingCancellable`** (`core/util`) — `runCatching` that never swallows
  coroutine cancellation.

---

## 5. Kotlin / Compose Multiplatform ideas used here

Read these once and the code stops looking magical.

**`expect` / `actual`** — declare a platform API in `commonMain`, implement it per
target. Examples: `PlatformBackHandler`, `PlayerWindowMode`, `VideoSurface`,
`createHttpClient`, `FilePicker`. When you add a platform-specific behavior, add the `expect` in
`commonMain` and the `actual`s in `jvmMain` / `androidMain`.

**`StateFlow` + `ViewModel`** — a ViewModel holds an immutable `data class …UiState`
and exposes `val state: StateFlow<…>`. Screens collect it with
`collectAsStateWithLifecycle()`. Never mutate state from a composable; launch a
coroutine in `viewModelScope` instead.

**Immutable UI state** — always `state.update { it.copy(...) }`, never partial
mutation. This is why `HomeUiState`, `LibraryUiState`, etc. are `data class`es.

**Sealed interfaces for extensibility** — `SignInMethod`, `SignInRequest`,
`ContentRoute`. Adding a case forces every `when` to handle it (the compiler
helps you).

**`CompositionLocal`** — app-wide values provided at the root:
`LocalAppServices`, `LocalWindowSize`, `LocalMotionEnabled`, `LocalSnackbarHost`.
Use it for *cross-cutting* values, not for passing data between two composables
(prefer parameters).

**Compose resources** — strings and fonts come from
`composeResources/values/strings.xml` via `stringResource(Res.string.…)`. Do not
hardcode user-facing text; it breaks localization. The generated `Res` is
`internal` to `:app:shared`, so a composition root (a different module) cannot
read it — keep such literals local (see `WINDOW_TITLE` in `Main.kt`).

**Modifier order matters** — `Modifier.padding().background()` is not the same as
`background().padding()`. The code follows "clip → background → click → padding"
for custom surfaces (see `components/Interaction.kt`).

**Drag reordering** — Foundation has no reorderable `Column` API, so
`ReorderableCardColumn` builds one from `pointerInput` +
`detectDragGestures` on the handle, `onGloballyPositioned` to measure the card
centers, `targetIndex()` to pick the insertion slot and
`animateFloatAsState` to open the gap. Only the handle starts the drag; the card
itself stays inert so nested controls and the page scroll keep their gestures.

**Draw-phase reads** — `motionLayer`/`motionBorder` read animated values inside
`graphicsLayer` / `drawWithContent` lambdas, so animations don't recompose per
frame. Pass `{ state.value }`, not `state.value`.

**`remember` / `derivedStateOf` / `rememberUpdatedState`** — `derivedStateOf` for
values computed from other state (e.g. "load more" in `CatalogScreen`);
`rememberUpdatedState` when a lambda captured in `remember` needs the latest
value (used in `DiscoverFeed`). Capturing a changing value in `remember` is the
classic Compose bug.

**Cancellation** — use `runCatchingCancellable` (in `core/util`) instead of
`runCatching` inside coroutines, so a cancelled screen doesn't turn cancellation
into a fake error.

**KMP source sets** — `commonMain` is shared; `jvmMain` and `androidMain` hold
platform code. Desktop-only code lives in `app/desktopApp` (a JVM module), not in
`app/shared/jvmMain`, unless it is UI. `data/jvmSharedMain` is shared by the JVM
and Android targets: the I/O dispatcher, the system language tag, the file
helpers, the on-disk HTTP cache storage (Ktor's `FileStorage` over `kotlinx.io`)
and the gzip decoder live there once.

---

## 6. Conventions

- **Formatting/lint**: `.editorconfig` (4 spaces, LF) + `ktlint`. Run
  `./gradlew ktlintCheck` (or `ktlintFormat`). CI fails on lint.
- **Naming**: Compose functions are `PascalCase`; everything else `camelCase`.
- **Comments**: explain *why*, not *what*. The codebase is already full of good
  examples — match that tone.
- **Documentation rule** (`AGENTS.md`): any commit that changes behavior must
  update `docs/architecture-decisions.md` in the same commit.
- **No `TODO`/`FIXME` left behind** — the codebase currently has none; keep it
  that way.

---

## 7. Testing

Tests mirror the module they test:

- `core/src/commonTest` — pure rules (`WatchedRule`, `PlaybackTarget`,
  `Cancellable`, models).
- `data/src/commonTest` — parsers (`StremioParsersTest`), mappers, PostgREST
  escaping, the cache-storage decorator and the Nuvio stack against a fake HTTP
  client (`NuvioTestStack.kt`). `data/src/jvmTest` covers the file stores and the
  on-disk HTTP cache end to end (`DiskHttpCacheTest`: gzip, a cache hit and the
  fallback lifetime, against a `MockEngine`).
- `app/shared/src/commonTest` — ViewModels against fakes in
  `ui/support/Fakes.kt`.
- `app/desktopApp/src/test` — the image decoder (`MitchellImageDecoder`).

Run them:

```bash
./gradlew :core:jvmTest :data:jvmTest :app:shared:jvmTest :app:desktopApp:test
```

When adding a ViewModel, add a `Fake<Repo>` in `ui/support/Fakes.kt` if one does
not exist, and drive it with `StandardTestDispatcher` + `advanceUntilIdle()`
(see `DiscoverViewModelTest`).

---

## 8. Extension recipes

**Add a backend**
1. Implement the `core` repository interfaces under `data/.../backend` or a new
   package.
2. Add a value to `BackendKind` and a branch to `createBackend` in
   `BackendFactory.kt`.
3. Wire it in the composition roots. No UI changes.

**Add an addon resource (e.g. a new Stremio resource)**
1. Add the model in `core/.../model`.
2. Parse it in `data/.../addons/StremioParsers.kt`.
3. Fetch it in `StremioAddonClient`, then expose it through a `core` repository.

**Add a sign-in method**
1. Add a subtype to `SignInMethod` and `SignInRequest` (sealed) in
   `core/.../repository/AccountRepository.kt`.
2. Handle it in the backend's `AccountRepository` implementation.
3. Render a form for it where `signInMethods` is read
   (`OnboardingScreen`, `SettingsScreen`). The compiler will point at every
   `when`.

**Add a player engine**
1. Implement `core/.../player/PlayerService.kt`.
2. Wire it in the composition root (`Main.kt` on desktop, `MainActivity.kt` on
   Android) and return the engine's surface through `videoOutput`. The UI already
   reads `canPlay` to disable playback on targets without an engine.

**Add a setting**
1. Add a field to `AppSettings` (`core/.../repository/SettingsRepository.kt`).
2. Add the getter/setter to `SettingsRepository` and the key in
   `JsonSettingsRepository`.
3. Add the control to the matching page in `SettingsScreen.kt`; `SettingsCategory`
   picks the page. Use `SettingsChipRow` for a short option set and
   `SettingsSelectRow` for a longer one. The JSON file stays hand-editable.

**Add a theme**
1. Add a value to `AppTheme`, a palette to `AppThemes` (`ui/theme/Tokens.kt`) and
   a label to the `themeLabels` map in `SettingsScreen`'s `ThemeSection`, plus
   the string in `strings.xml`. The picker iterates `AppTheme.entries`, so the
   new option appears on its own; every other surface reads `LocalBrandPalette`
   or a scheme role and needs no change.
2. If the theme changes the Material roles a palette does not carry (the error
   containers, the secondary family), promote them to `BrandPalette` first, so
   the scheme stays the single mapping.

**Add a data section**
1. Create `ui/<feature>/<Feature>ViewModel.kt`, extending `AccountScopedViewModel`.
2. Create `<Feature>Screen.kt` that collects its state.
3. Add the section to `AppSection` and render it from `SectionHost`.

---

## 9. Known rough edges (where to be careful)

- **Large UI files**: `DetailScreen.kt` and `PlayerScreen.kt` are still big even
  after splits. Split them further when you touch them. The home billboard is
  split across `Hero.kt` (entry points and pager), `HeroLayers.kt` (artwork,
  washes, copy) and `HeroOverlays.kt` (swipe, paginator, preloader), and
  "Continue watching" moved to `ContinueWatching.kt`.
- **`AppServices` is a service locator** reached through `LocalAppServices`.
  ViewModels take dependencies explicitly (good); screens read the global. In
  tests, prefer constructing the ViewModel directly.
- **`ktlintFormat` on Windows** may trip over generated Compose sources; use
  `ktlintCheck`, which excludes them (the filter is configured in the root
  `build.gradle.kts`).
