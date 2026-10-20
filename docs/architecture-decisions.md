# Architecture decisions

This document records the decisions taken while migrating the original Flutter
app (reference only; it has since been removed) to Compose Multiplatform.
Desktop (Windows) and Android are both wired up end to end against the same
shared code; each composition root picks its own player engine.

## Goal

Keep the product behaviour and the layering of the Flutter app, but express it
with Kotlin Multiplatform best practices: pure domain, replaceable data
adapters, an isolated player, and a UI that never talks to HTTP, the filesystem
or the playback engine directly.

## Modules and dependency direction

```
:core      pure domain (models, repository contracts, watched rules, player contract)
:data      implementations (Stremio addons, Nuvio/Supabase, local JSON store)
:app:fonts   alternate typeface (IBM Plex Sans) assets as Compose Resources
:app:player  shared PlayerService adapter over mediamp's player contract
:app:shared  Compose UI + ViewModels (depends on :core, :app:player and :app:fonts)
:app:desktopApp  composition root: mpv player (mediamp), window control, main()
:app:androidApp  composition root: ExoPlayer player (mediamp), MainActivity
```

`ui -> domain` and `data -> domain` (dependency inversion): the data layer
implements the contracts declared in `:core`, and `:data` is only referenced by
the composition roots. Swapping a backend or the player changes one module and
the composition root, never the UI.

`:core` and `:data` are Kotlin Multiplatform (`commonMain` + `jvmMain` +
`androidMain`). Domain and data code lives in `commonMain`; only the HTTP
engine, the filesystem and the player touch platform source sets through
`expect`/`actual` (`data/platform/Platform.kt`). The JVM and Android file
helpers share a `jvmSharedMain` source set (`data/platform/PlatformFileIO.kt`),
so the atomic read/write/delete logic lives once.

## Domain (`:core`)

- **Models are plain data classes**, with no database-column mapping and no JSON
  parsing. Row parsing lives in `:data` (`NuvioMappers.kt`), so the models can be
  produced by any backend.
- **One repository per capability**, mirroring the Flutter refactor:
  `AccountRepository`, `LibraryRepository`, `ProgressRepository`,
  `AddonRepository`, plus the addon contracts (`CatalogRepository`,
  `MetadataRepository`, `StreamRepository`, `SubtitleRepository`).
- **Observability with coroutines.** Where Flutter used `Listenable`, the
  Kotlin contracts expose `StateFlow`. `AccountRepository` exposes a single
  `StateFlow<AccountState>` (session, profiles, active profile, loading and
  error); `LibraryRepository`/`ProgressRepository` expose a `StateFlow<Long>`
  change counter that screens collect to reload. This removes the
  `SessionController` patch entirely.
- **Profiles are optional** (`AccountState.requiresProfile`) and mandatory when
  required: there is no default profile, because a write saved to the wrong
  profile is wrong data, not missing data.
- **Watched logic is pure** (`watched/WatchedRule.kt`) and the resolver
  (`watched/WatchedBadges.kt`) keeps the bounded-concurrency, cached,
  background behaviour of the original.
- **Sign-in is a request, not a signature.** `AccountRepository.signIn` takes a
  `SignInRequest` (sealed; today only `EmailPassword`) and the backend declares
  what it offers through `signInMethods`, so the sign-in UI renders the matching
  form instead of assuming email + password. Adding OAuth or a magic link means
  a new subtype, not a contract change; a multi-step flow (device pairing) would
  still need a session object on top of this.
- **Shared vocabulary lives with the models.** `ContentTypes`, `CatalogExtras`,
  `ProgressThresholds` and `releaseYear()` name the protocol strings and
  thresholds the domain, data and UI layers compare, so a typo or a drifting
  literal cannot change behavior. `ContentType`, `Season` and `Episode` are
  value classes over the protocol token and the two numbers, so a content type
  cannot be passed where another `String` is expected, or an episode where a
  season is, without the compiler noticing.
- **`PlayerService` is a contract**, with a neutral `PlayerState` and a
  `videoOutput: VideoOutput?` handle so the UI can embed the native surface
  without depending on the engine. `VideoOutput` is an empty marker; the shared
  `VideoSurfaceRenderer` extends it and draws the video as Compose content.

## Data (`:data`)

- **Ktor client** (CIO on desktop, OkHttp on Android) instead of `package:http`.
- **kotlinx.serialization** with the `JsonElement` API and small defensive
  coercion helpers (`JsonUtils.kt`), mirroring `json_utils.dart`: third-party
  payloads degrade to `null`/empty instead of throwing.
- **Addon clients are pooled.** `AddonClientPool` keeps one cached
  `StremioAddonClient` per addon URL, and the composition roots build the four
  Stremio repositories through `StremioAddonRepositories`, which hands them the
  same pool. A manifest is then fetched once per process, not once per
  repository, even when catalog rows and metadata lookups hit the same addons
  back to back. A client also joins a concurrent GET for the same URL, so two
  screens asking for the same catalog share one round-trip.
- **Stremio**: `StremioAddonClient` plus the four repositories. Same rules as
  the original: metadata is first-wins across addons, streams/subtitles are
  aggregated in parallel, and catalog `extra` values are encoded while keeping
  `=`/`&` raw. The "similar" row picks a catalog that can filter by genre and
  skips catalogs whose required extras cannot be provided (a search term, a list
  of ids), then falls back to the generic catalog, so a title is not left
  without the row.
- **Nuvio/Supabase**: raw HTTP split by responsibility, instead of one client
  class: `NuvioTransport` (verbs and base URL), `NuvioDiscovery`
  (`/.well-known/nuvio` and the publishable key), `NuvioAuth` (session, sign-in,
  refresh, sign-out) and `NuvioRest` (PostgREST select/insert/update/delete and the
  `sync_*` RPCs). Repositories take the slice they need. Session tokens live in
  `data/`, never in the domain.
- **Local store**: `JsonFileStore`/`JsonMapStore` and the session / snapshot /
  watched-series stores. Path resolution is a platform `expect`
  (`%APPDATA%\Pengu` on Windows, XDG elsewhere). Writes go through a
  sibling temporary file and are renamed into place, so an interrupted write
  cannot leave a truncated document, and a failed write is reported instead of
  being swallowed. Read-modify-write cycles in the local repositories are
  serialized with a `Mutex`. The session file is restricted to the current user
  where the OS allows it; that is best-effort hardening, not a keychain.
- **Settings** live in `settings.json` as a flat JSON object with dotted keys,
  like VS Code's `settings.json` (`animations.enabled`, `player.resumePlayback`,
  `player.autoPlayNext`, `poster.size`, `labels.hidden`, `catalog.showType`,
  `metadata.tmdbApiKey`, `metadata.tmdbLanguage`).
  `JsonSettingsRepository` reads it defensively, keeps unknown keys on write and
  can `reload()` after a manual edit; the settings screen groups the toggles into
  categories (`SettingsCategory`, in `ui/screens`), edits the same file, shows
  its path and can open it in the OS editor. A wide window renders the categories
  as a grouped sidebar beside the selected page; a compact window renders them as
  a flat index and pushes each page as a `ContentRoute.SettingsPage`, so a phone
  walks into one category at a time while each setting is defined
  once.
- **Settings controls are app components, not stock Material.** A toggle row is
  itself the target and its switch is the shared compact `AppSwitch` (40x22,
  accent track), not Material's 52x32 control; it paints its track and thumb in
  one `Canvas`, so moving an addon card cannot leave a stale switch layer; a
  short option set is
  a chip row, a longer one opens a popover on desktop and a bottom sheet on
  touch (`isDesktop`, in `ui/platform`); the dividers are inset to the row copy;
  and a group is a borderless card on the page background, filled with the
  `surfaceCard` palette role so the index and the pages share one warm tone.
  The categories are a
  stack of those cards (`SettingsNavCard`) instead of icon rows: the compact
  index shows a semibold label and a chevron with no description and no group
  labels — the phone title bar names the screen and the cards carry the rest —
  while the wide sidebar keeps the groups, drops the chevron and marks the open
  page with an accent tint over the card and an accent label. Inside a page, the
  section labels are semibold and the row titles step up to semibold too, so the
  hierarchy reads the same as the index; the picker reads as a field (no fill,
  `outline` border) so it stays legible on the card, and a selected chip or the
  popover uses `surfaceSelected`. A setting then looks like
  the app rather than a form, while every colour still comes from the scheme.
- **An addon card splits on a phone.** Below the `WindowSize.COMPACT`
  breakpoint the identity row keeps the logo, name, version and the three
  controls, and the manifest description moves to its own full-width line under
  it. Inline next to that stack a description only had ~100dp to wrap in, so a
  normal blurb ran to a dozen lines and the card grew past the screen; the full
  width fits it in two or three. That line then folds to `AppSynopsis.MAX_LINES`
  and expands on tap (`hasVisualOverflow`, the same measure `HeroSynopsis` uses),
  so even a long manifest blurb cannot stretch the card. A wide window keeps the
  description inline, where the row has room for it.
- **`labels.hidden`** suppresses the title and year captions under every
  poster (Home, catalog grids, Discover, Search, the detail's "More like this"
  and the library) so the artwork reads on its own; a label-less card collapses
  to the artwork's 2:3 ratio (`POSTER_ART_ASPECT`) instead of stretching the art
  to fill the freed caption space, so the poster keeps the base size the setting
  defines. The Home rows append the catalog's content type to its name with a
  dot (`Popular · Movies`), switched off by `catalog.showType`; the billboard
  metadata is unaffected.
- **TMDB enrichment is optional and presentation-only.** A key set in Settings
  (`metadata.tmdbApiKey`, with `metadata.tmdbLanguage` for the answer language,
  system locale by default) turns on a second metadata provider used only by the
  detail screen and the home billboard, and it never replaces an identifier
  (`MetaDetail.id` and `MetaVideo.id` still address streams). Two merge rules,
  one per screen (`MetadataEnrichment.kt`): the home billboard *fills gaps*, so
  TMDB only adds what the catalog preview left empty and the billboard paints
  immediately; the **detail page gives TMDB priority**, because there one
  consistent source is worth more than the fastest first paint. There,
  `DetailUiState.isResolvingArtwork` keeps the hero from painting anything —
  artwork, logo, metadata line, synopsis — until the merge that has every source
  has landed, so a phone goes straight from nothing to the textless poster and
  never shows the addon's backdrop first (the same reason the synopsis already
  reserved its space instead of showing the preview copy). The addon keeps the
  IMDb score, which this TMDB payload does not carry.
  `TmdbMetadataRepository` resolves any content id to a TMDB one — an explicit
  `tmdb:` id, IMDb/TVDB
  through `/find`, otherwise a title-and-year search that rejects an ambiguous
  match — then fetches the detail with its images, external ids and credits. A
  title only TMDB knows (a recommendation) is retried on the addons through the
  IMDb id TMDB reports, and that id then drives its streams, so a `tmdb:`
  preview still plays. Both
  the resolved id and the payload are cached for the process. The addon answer is
  published first and the enrichment lands right after, so a slow lookup never
  delays the page's data (only its paint). The "You may also like" row is built from TMDB's
  recommendations (its similar list as a fallback), and falls back to the addon's
  own similar catalog when there is no key or no match. Without a key the
  repository short-circuits and no request leaves the app. The key lives in
  `settings.json` like every other preference, so it stays hand-editable; it is
  masked in the UI and never logged, and TMDB's attribution is shown in the card.
- **Session renewal**: `NuvioAuth` renews the token before a request when it
  has expired, and `NuvioRest` renews once more when the backend answers `401`.
  Renewals are serialized with a mutex so parallel requests do not refresh
  twice. Engines are configured with finite connect/request/socket timeouts.
- **`BackendKind` + `createBackend`** is the single place that knows the
  concrete implementations, replacing the Flutter `backend_factory.dart`.
- **Parallel I/O and caching.** Independent reads run together instead of in a
  row: Home loads watch progress, the library and the catalog list concurrently,
  then fetches the catalogs in batches of four and paints after the first one,
  with the featured artwork already enriched, so a phone does not wait for the
  slowest catalog; re-entering the section keeps the loaded rows and refreshes
  behind them. Library loads the library and its progress together; the watched-badge
  resolver reads the two progress sources in parallel and its metadata lookups
  are bounded but concurrent; `watched_items` chunks are queried together;
  catalog manifests are fetched in parallel and cached per addon (a manifest is
  immutable, and the disk cache below keeps it across launches). The addon list
  is cached per profile uuid, so the watched-badge sync does not re-query it for
  every series. Account-scoped ViewModels (Home,
  Library, Search (and its discover feed), Calendar) wait for a profile before
  loading rather than loading without one and failing.
- **Addon responses are cached on disk.** `createAddonHttpClient()`
  (`data/platform`) installs Ktor's `HttpCache` over a `FileStorage` in the
  platform cache directory — never in the config directory, because a cache is
  disposable and on Windows must not land in a roaming profile. A manifest or a
  catalog page the addon declared as cacheable is served from disk, and
  `Cache-Control: no-cache` still revalidates with `If-None-Match` /
  `If-Modified-Since` instead of being served blind.
  `CacheStorageWithFallbackTtl` gives the responses that declare nothing at all
  — no `Cache-Control`, no `Expires`, no validator, which a large part of the
  Stremio ecosystem sends — a five-minute lifetime, the same window Home already
  uses to consider content fresh; anything the addon did declare wins, and
  `no-cache`/`must-revalidate` are never overridden. Addon traffic is
  deliberately the only cached traffic: `createHttpClient()`, used by the Nuvio
  backend and TMDB, has no cache, because a stale `library_items` or
  `watch_progress` read would show the user data they just changed.
- **Catalog JSON is requested gzipped.** Neither engine adds `Accept-Encoding`,
  so `StremioAddonClient` asks for `gzip` itself and decodes the body
  (`gzipDecode`, implemented once for the JVM and Android targets). A catalog
  page shrinks about fivefold, which is most of the bytes a cold start would
  otherwise download; the disk cache keeps storing the compressed body.
- **The addon list is answered from a local snapshot.** `NuvioAddonRepository`
  serves the last list stored by `FileAddonSnapshotStore` instead of waiting for
  the profile's `addons` query, then refreshes it in the background and
  announces a real change through `AddonRepository.changes`, which Home listens
  to before reloading its rows. The list used to sit in front of every catalog
  request — session, profile, `addons` query, manifest, catalog — so this removes
  one round-trip from the cold path; a device without a network keeps working
  from the snapshot. The snapshot keeps every installed addon, enabled or not;
  `all()` filters the disabled ones out for the content path.
- **Addons are managed from Settings.** `AddonRepository` exposes
  `installed()`, `install`, `setEnabled`, `remove`, `reorder` and `canManage`
  next to the content read. `NuvioAddonRepository` validates a pasted URL by
  fetching its manifest through the cached addon client before writing, stores
  the normalized manifest URL and name, and scopes every write by `profile_id`
  (`NuvioRest.update` sends PostgREST `PATCH`; `remove` sends `DELETE`). A
  `stremio://` install link is normalized to `https://` before that check.
  Every write re-reads the list, refreshes the snapshot and emits `changes`, so
  Home reloads its catalogs after an addon is toggled, reordered or removed. The
  Settings → Addons page opens the install form in a dialog and shows the addons
  as cards: logo, name, version, full manifest description, enable switch, a
  remove icon and the drag handle. Only the handle starts a drag, so the card itself is
  inert (a click does nothing) and the switch or the remove action keeps its own
  target. `ReorderableCardColumn` (`ui/components`) owns the gesture and the
  animated gap; the page persists the new order once, on drop. Move up/down
  remain as accessibility actions on the card, not visible buttons. Each card
  shows the manifest's logo and description, with the URL only as a fallback
  when the manifest cannot be read. A backend that cannot write (local mode)
  lists its fixed set read-only and hides the install action and the card
  controls.
- **Home paints the last session before the first request.**
  `FileHomeSnapshotStore` keeps the rows and the billboard that were on screen
  (`HomeSnapshot`, keyed by profile id) and `HomeViewModel` publishes them before
  the load starts. The network answer then replaces the rows batch by batch and
  keeps the billboard already on screen, so the hero never swaps from the stored
  artwork to a fresh pick; a failed load leaves the snapshot visible instead of
  an error page. The snapshot is rewritten after every successful load, and it
  carries the instant the billboard set was picked: a set older than six hours is
  re-picked on a cold start (the rows still paint at once, the new set replaces
  the hero once it is enriched), so the billboard does not stay frozen for weeks
  while the first frame stays instant. A re-entry keeps the pick time, because it
  refreshes the rows behind and must not make the set look fresher than it is.
- **The billboard source is a setting.** `hero.source` is `smart` (the default:
  every loaded row, mixed and ranked by the app) or `manual`, and
  `hero.catalogs` holds up to two `CatalogRef.key`s picked in Settings → Home
  billboard. Manual narrows the pool the pick draws from and falls back to every
  row when none of the picked catalogs is loaded, so an addon removed elsewhere
  cannot leave Home without a billboard; every catalog still gets its own row
  either way. Home listens to the setting (`distinctUntilChanged` on the two
  fields), so switching it re-picks immediately instead of waiting for the next
  rotation. The catalog list for the picker is read from the addons in the
  settings screen, not stored: only the keys are persisted, which keeps
  `settings.json` hand-editable and free of stale names.
- **Android resolves IPv4 first and bypasses the system proxy.** `IPv4FirstDns`
  sorts the DNS answers, so a host with a broken AAAA record does not stall every
  connect until IPv6 times out; `Proxy.NO_PROXY` stops a proxy that only knows a
  corporate network from failing every public addon request.

## UI (`:app:shared`)

- **No DI framework.** `AppServices` is a data holder provided through
  `LocalAppServices`; the composition roots build it. This is the direct
  equivalent of the Flutter `AppServices` `InheritedWidget`.
- **ViewModel + `StateFlow`.** Each section/route has a `ViewModel` that owns
  its state and launches work in `viewModelScope`. The UI collects with
  `collectAsStateWithLifecycle()`, so collection stops when a screen leaves the
  lifecycle.
- **Shared screen behavior.** Data sections extend `AccountScopedViewModel`
  (`ui/common`), which owns the "load when signed in and a profile is chosen,
  clear on sign-out" rule, and use `WatchedBadgeSync` for the watched-badge
  lookup, so neither is reimplemented per screen. Its account observer starts
  on `Dispatchers.Main`, not `Main.immediate`: the latter runs inline when the
  ViewModel is built on the main thread, which fired the load hook from the base
  constructor before the subclass state existed. Forcing a dispatch makes the
  first emission wait until construction has finished.
- **Design tokens are data, not code.** `theme/Tokens.kt` holds the palettes
  (`BrandPalette` / `AppThemes`), spacing, sizes, alphas and scrims;
  `theme/AppTheme.kt` holds only the Material
  colour scheme, typography and shapes. `AppSizes` owns the content/form/message
  widths and the border/progress sizes, `AppAlpha` owns every overlay opacity,
  and `Modifier.focusRing` (in `Interaction.kt`) gives every focusable control
  the same keyboard ring.
- **Icons are vendored, not a dependency.** `ui/icons/MaterialIcons.kt` holds
  the Material Symbols Sharp (Apache-2.0, Google) glyphs the whole UI uses as
  `ImageVector`s, each built lazily from the official SVG path through
  `PathParser`; `scripts/gen_icons.mjs` regenerates the file from the
  `@material-symbols/svg-400` package. Sharp is the style that squares the
  corners off, which matches the app's flat, tint-only icon language better than
  a rounder set. The glyphs live on a 960x960 grid whose y axis runs from -960
  to 0, so each vector is built inside a group translated by +960 while call
  sites still size it at 24dp. The back arrow and the
  sign-in/out glyphs carry `autoMirror = true`,
  which is what Material's auto-mirrored variants did. The Windows caption
  buttons keep their own native-shaped glyphs (`WindowsCaptionIcons` in
  `:app:desktopApp`), so the bar reads as Windows chrome. The app does not depend on
  `materialIconsExtended` at all, so only the used glyphs ship; the glyphs are
  single-colour paths, so a selected state is a tint, not a fill swap.
  The `AppIconSize` scale in `theme/Tokens.kt` is the single source of icon
  size: `md` (24dp) is Material 3's default for fields and list affordances,
  `sm` (20dp) is for compact and in-button glyphs, `nav` (28dp, shared with `lg`)
  is the navigation rail and the bottom bar, whose icon-only items need more
  weight, and `lg` (28dp) is for prominent controls over artwork. Call sites size
  every icon through those tokens, so the rail and the bottom bar stay on the
  same optical size.
- **Compose lifecycle.** `PenguApp` reuses the host's
  `ViewModelStoreOwner` when there is one — an Android `Activity`, so section
  ViewModels survive a configuration change — and falls back to a
  composition-scoped owner where there is none, such as desktop. Section
  ViewModels live in it and survive section switches (replacing Flutter's
  `IndexedStack` state retention); the selected section and the route stack live
  in a `ShellViewModel` for the same reason, and `PlatformBackHandler` (Android)
  pops the stack on system back. Pushed routes get their
  own `ViewModelStoreOwner` (`ContentArea`) that is cleared on pop, so transient
  ViewModels do not leak.
- **Onboarding and session bootstrap.** `PenguApp` resolves the persisted
  session once, before anything else: it shows a splash until that finishes, then
  the onboarding (no session) or the shell, so a returning user never sees the
  sign-in for a frame. It is a hard gate, so the sections no longer carry a
  "not signed in" state: signing out returns to the onboarding. The form is
  shared with the Settings account card and driven by `signInMethods`, so a
  backend that signs in another way renders another form. The desktop
  `NUVIO_EMAIL` / `NUVIO_PASSWORD` shortcut is passed in as an `autoSignIn`
  request, applied only when there is no stored session.
- **Adaptive layout.** `PenguApp` measures the window once with
  `BoxWithConstraints` and publishes a `WindowSize` (compact / medium /
  expanded, at Material's 600 and 840 dp breakpoints) plus the window width,
  through `LocalWindowSize` / `LocalWindowWidth`, so a surface picks a layout
  without measuring anything itself. Compact windows (phones) always get an
  icon-only bottom bar that floats over the content; wider windows always get a
  left navigation rail, whose width the content reserves.
  The poster rows scale their card width down on compact windows (the user's
  default / large choice applies there) and the grids keep a fixed
  three-per-row layout on a phone, the Home hero swaps
  the wide billboard for the poster crop without the title logo, the detail
  billboard is capped on an expanded window so the content stays close, and the
  stream picker clamps to the app window when it opens as a dialog, because a
  dialog opens its own, while a phone gets it as a bottom sheet. The detail
  backdrop only darkens the artwork, with no blur: a full-viewport blur re-renders
  the whole screen on every scroll frame and is ignored below Android 12, so the
  scroll-linked effect is a plain scrim instead. The wide
  Home billboard takes its height from the screen work area
  (`HOME_HERO_SCREEN_FRACTION`, 55% of it), not from the window, so it keeps the
  height it has on a maximized window: resizing on either axis only reveals more
  or less of the artwork, and the sections below stay where they are. The
  first section is pulled up by `HOME_CONTENT_LIFT` (32dp) so it sits close to
  the billboard's faded bottom instead of a full header gap below it; the lift is
  a layout modifier that overlaps the hero and reports the reduced height, so the
  scroll extent stays right, and it stays clear of the paginator and the copy at
  the billboard's bottom. Section
  headers keep a fixed height too: the title sets it and the trailing see-all icon is centred
  on it and overflows into the padding, so the gap to the row below is the same
  with and without the button. A Home catalog appends its type to the title
  (`Popular · Movies`) instead of carrying a separate subtitle. Compact
  windows also use a tighter screen padding (16dp) and grid gap, and the detail
  actions are icon-only (with a `contentDescription`) at the call-to-action's
  height, so the row lines up.
- **The Home hero draws from every catalog.** `pickFeatured` (in
  `HomeViewModel`) takes the pool of every row's items, dedupes by `type:id`,
  balances movies and series, and shuffles, so the billboard is not tied to the
  first catalog. The featured set is picked from the rows on screen at the first
  content paint and enriched before it, then left alone as later batches append
  rows, so the billboard never changes under the user. Well-rated titles
  (IMDb >= 6, unknown passes) come first and low ratings are only used when there
  is not enough good content. The six
  slides auto-advance every `HERO_AUTO_ADVANCE_INTERVAL` (7 s, in the usual 5-8 s
  carousel range), and a manual change pauses that rotation for
  `HERO_MANUAL_COOLDOWN` (60 s): the watcher keys on `PagerState.isScrollInProgress`
  and marks every scroll the carousel did not start itself, so it catches both a
  drag on a phone and a wheel / trackpad swipe on desktop, and the timer yields to
  the user until they go idle before the hero resumes on its own. The pager is
  infinite (it starts near the middle of the range and `page % items.size` maps a
  page back to its item), so the auto-advance always moves forward: the last
  slide does not scroll back through every page to reach the first. The advance
  snaps with a spring (`motionSpring`) and respects `LocalMotionEnabled`, so
  "disable animations" jumps to the target like every other transition. The
  screen
  re-picks the whole set every
  `FEATURED_ROTATION_INTERVAL` (10 minutes) while Home is visible, preferring
  items that are not already on screen, so it changes over time instead of on
  every recomposition. The selection goes through an injectable `Random`, so
  tests are deterministic.
- **Home virtualizes its sections.** The hero, "Continue watching" and every
  catalog row live in one `LazyColumn` (each rail is its own `LazyRow`), so a
  phone with many addons composes only the sections on screen instead of every
  rail at once. The first section is pulled up over the hero's faded bottom by
  shifting that single item (`pullUp`), which shifts every later section with it.
- **Billboards share one copy grammar.** The Home billboard and the detail
  hero render title, metadata, synopsis and genres through the same composables
  (`MediaTitle`, `MetadataRow`, `HeroSynopsis`, `HeroGenres` and the
  `metadataParts` builder, all in `ui/components/MediaHero.kt`), so the two
  screens cannot drift in type, colour, separators or measure: the
  synopsis is capped at `AppSynopsis.maxWidth` inside `HeroSynopsis`, and
  `AppType.heroBody` sizes and weights it (17sp / 15sp on a phone, Normal) on
  both. Only the running order differs by screen: the Home billboard reads
  title, metadata, synopsis then actions, while the detail billboard keeps
  actions above the synopsis. The detail billboard passes `expandable`, so a
  synopsis that overflows its three lines expands to the full text on tap and
  collapses on a second tap; the Home billboard keeps the fixed teaser. They also differ where the content does: the
  Home billboard folds the content type, the parsed release year and only the
  first genre into the metadata line (`Movie • year • first genre • ★ rating`)
  while the detail hero keeps the runtime there and shows genres under the
  synopsis; and the Home copy uses a tighter vertical rhythm because it sits over
  cropped artwork. Metadata is one builder, so the star, separators, the type
  name and rating formatting cannot diverge.
- **Billboards share their icon actions.** `HeroActionRow` and
  `HeroActionState` (`ui/components/HeroActions.kt`) render the library and
  watched buttons next to the call to action, so Home and the detail screen
  cannot drift in icon, size, colour or state. Both billboards lead with a
  labelled play call to action (`ActionButton` + `action_stream_now`), so Home
  and Detail read the same stream / save grammar from one shared string: Home
  uses the transparent text style (`ActionButtonStyle.Text`) while Detail keeps
  the filled amber `Accent`. The home hero carries no "more
  info" action: tapping anywhere on it opens the title through `heroTap`
  (`ui/home/HeroOverlays.kt`), which runs on the initial pass without consuming,
  ignores a release that followed more than the touch slop, and ignores a
  release a child already consumed, so a tap on a control never opens the title
  as well. On a phone the row becomes a full-width footer — play pinned to the
  left edge and the library toggle to the right, inside the screen padding — so
  it reads apart from the copy; a wide window keeps the actions together,
  aligned with the text above them. The copy block takes the play button's
  content inset (`HERO_COPY_TEXT_INSET`), so the title, metadata and synopsis
  share a left edge with the play glyph instead of the button's transparent
  container edge. The spacers around an action row discount
  the control's transparent vertical padding (`HeroActionRowInset`, in
  `ui/components/HeroActions.kt`), the band between the 48dp touch target and
  its smaller glyph, so the icon row reads as close to the text as the
  text-to-text gaps do instead of 12dp wider; the detail hero applies it above
  and below its row, and the phone copy applies it to the metadata-to-actions
  gap, where there is no synopsis. The compact copy keeps a 48dp bottom margin,
  lower than the wide one's 100dp, so the footer sits low, just above the
  paginator dots.
  Home's library toggle is optimistic in `HomeViewModel` and reconciled through
  `LibraryRepository.changes`, so a title added on either screen shows as added
  on both.
- **The detail episode picker scrolls.** The season buttons live in their own
  `LazyRow` under the "Episodes" heading, with the same padding as the episode
  rail, so a series with many seasons stays reachable instead of crowding the
  header. The episode rail resets to its first card on a season change, so
  switching from S1 mid-list does not open S2 at the same offset.
- **The detail page lists a title's metadata as a table.** The "Details" section
  renders label/value rows — Released, Country, Status, Original language,
  Seasons, Episodes, Budget and Revenue — each split by a hairline and only for
  the rows a source actually filled. TMDB reports status in English, so it is
  mapped to a localized string; budget and revenue are whole US-dollar amounts.
  Cast and crew reuse the catalog's caption roles (`PosterCaption`), with the
  character dimmed like a poster's year, so a person card and a poster read the
  same.
- **Edge-to-edge and system insets.** Android runs the activity edge-to-edge
  (`enableEdgeToEdge`, with light system-bar icons forced because the app always
  paints dark), and the `Scaffold` no longer reserves the system bars
  (`contentWindowInsets` is zero). Screens place the insets themselves through
  `safeTopInset` / `safeBottomInset` (`ui/theme/WindowSize.kt`, backed by
  `WindowInsets.statusBars` / `navigationBars`, zero on desktop) or the
  `statusBarsPadding` / `navigationBarsPadding` / `safeDrawingPadding`
  modifiers. The shell floats the compact bottom bar over the content, which runs
  edge-to-edge behind it and keeps only the system bars' space, so the bar can
  fold away on scroll without leaving a gap; a non-scrolling section (Calendar)
  keeps the bar's height, and a wide window reserves the rail's width. The
  navigation bars carry their own inset; the detail backdrop, the
  player and the onboarding/profile gates paint behind the bars while their
  controls sit below them; and the Home billboard scrolls with the content (it
  is never pinned) in a plain `Column` + `verticalScroll`, not a `LazyColumn`:
  lazy recycling re-composes each row (and disposes the hero) as it crosses the
  viewport, which costs more than it saves, so Home keeps a row for every
  browsable catalog the addons declare instead of capping the count (the cap
  lives on the items per row, not on the rows). On desktop the artwork spans 75%
  of the window width, pinned to the top-right corner of a frame with a fixed
  height, and cropped
  (`Crop`, top-aligned); widening the window reveals more artwork instead of
  stretching the billboard taller, so the sections below keep their place. It is
  washed by
  `BrandPalette.background` gradients: a left fade that dissolves the artwork's
  left edge under the copy, a long bottom fade (clear over the upper half, then
  a gentle two-segment ramp) that melts into "Continue watching" without a hard
  seam, a subtle top fade, a right-edge vignette, and a flat
  `AppAlpha.HERO_IMAGE_DIM` kept over the artwork so it reads darker than the
  raw image. On a phone the billboard spans the full width and crops the same
  backdrop into the app's cover ratio (`POSTER_CARD_ASPECT`, about 1.72 times its
  width, centre-aligned) instead of showing
  the portrait poster whole, and washes it with the same flat dim and a bottom
  fade that starts higher than the wide one's, both folded into a single
  gradient so the artwork is darkened in one full-screen fill, so the taller
  copy sits deeper in the dark: the copy is
  left-aligned and anchored above the action footer, the title always prefers
  the logo (scaled to the copy width by `AppLogoSize.opticalHeight`, text only as
  a fallback) and the synopsis is
  dropped, since there is no room to read it under the logo; a tap on the
  artwork or the copy opens the title. On a wide
  window the copy column is
  bottom-anchored 100dp above the hero edge, up to 840dp wide, and shares the
  sections' left padding. The synopsis is a teaser — capped at the shared
  720dp measure (`AppSynopsis.maxWidth`, about 80 characters), three lines at
  most, the rest behind "More info". The same cap lives in `HeroSynopsis`, so
  Home and Detail wrap the description at the same width. The carousel
  paginator is a row of equal 7dp dots, centred on the billboard's bottom edge:
  cream for the active page and grey for the rest. On a phone it sits higher
  (32dp), clear of the gesture bar. The pager is only a gesture and offset
  driver: its pages are empty, and the artwork and the copy are drawn above it
  as separate layers for the settled page and, while a swipe runs, its
  neighbour. Keeping the alpha per primitive (a backdrop layer, the shared
  washes, a copy layer) instead of over a whole composed page keeps the billboard
  off the offscreen-buffer path a page-wide alpha would take, and the washes are
  hoisted out of the slides so a cross-fade no longer draws a second full-screen
  gradient. The visible neighbour is resolved through `derivedStateOf` on
  `pagerState.currentPageOffsetFraction`, and the alpha and slide are read inside
  each `graphicsLayer`, so a swipe updates layers instead of recomposing every
  composed page each frame. The swipe cross-fades rather than slides: each layer
  dissolves through the page background into its neighbour. The copy (title,
  metadata, synopsis, actions) slides a little against the swipe
  (`HERO_CONTENT_SLIDE_MAX`, 64dp) so the direction reads, while the artwork
  drifts a small fraction of the page width (`HERO_BACKDROP_PARALLAX`, 5.5%) at a
  slight overscan (`HERO_BACKDROP_SCALE`, 1.14), so the two slides read as layers
  instead of a flat double exposure. The settled page is drawn last among the
  layers, so its buttons
  own the hits, and a drag that starts on one of those buttons is forwarded to
  the pager from the action row (the copy layers sit above the pager, and
  siblings do not share the hit path), so the swipe still works there while a tap
  falls through. On a
  wide window the rail floats over the content,
  which reserves its width, so the navigation never overlaps it.
- **Typography.** Inter is bundled through Compose Resources in `:app:shared`
  and is the typeface rendered. IBM Plex Sans stays isolated in its own
  `:app:fonts` module so the two typefaces can be swapped for comparison through
  `USE_IBM_PLEX_SANS` in `PenguTheme`. Inter Tight is bundled alongside Inter and
  applied only to the billboard metadata line (`AppType.heroMeta`), where its
  tighter fit keeps the dot-separated `Series • 2026 • Drama` ribbon to one line.
  Every target renders the same
  typeface, and text is written in `sp` so
  Android's system font size keeps working. `LocalDensity` is deliberately
  never overridden: that is the common hack, and it lies to every component
  about the font scale.
  Material's `display*` roles are sized for a big screen, so `AppType` steps
  them down one role on a compact window (hero title, screen titles, hero
  synopsis); desktop and tablet keep the full scale. The whole Material scale
  runs lighter than stock (`Bold` to `Medium`, `SemiBold` to `Normal`), so
  headings sit at Medium while titles, labels and body sit at Normal; `body*`
  bottoms out at `Normal` because no lighter weight is bundled, so a genuinely
  thinner body needs `inter_light.ttf` added to `:app:shared` (or
  `ibm_plex_sans_light.ttf` to `:app:fonts` while comparing). No type
  slot carries a colour: text colour comes from the colour scheme at the call
  site, as Material intends. The reading palette is white-based —
  `BrandPalette.textPrimary` is `#FFFFFF` and the secondary roles
  (`textSecondary` for `onSurfaceVariant`, `posterYear`) are white with reduced
  alpha — so hierarchy comes from opacity instead of a second grey, and the
  amber accent stays reserved for brand/attention rather than body copy.
  `AppType` is the closed set of text roles the UI reads — `screenTitle`,
  `dialogTitle`, `sectionTitle`, `cardTitle`, `itemTitle`, `body`,
  `bodySecondary`, `caption`, `label`, plus the hero and button roles —
  so no call site picks a Material slot directly and a title, a section heading
  and a row never drift apart. Two levels are deliberate: `sectionTitle`
  (`titleLarge` stepped to 18sp, 16sp on a phone) heads a screen section, while
  `cardTitle` and `itemTitle` (`titleMedium`) head a card or an interactive row.
  `PosterCaption` steps `titleSmall` / `bodySmall` up 1sp with the meta
  weight at Medium.
- **Localisation.** Every user-facing string lives in
  `composeResources/values/strings.xml` and is read with
  `stringResource(Res.string.key)`, so adding a locale is a new `values-*` folder
  instead of a code change. Failure messages go through
  `Throwable.toUserMessage()`, which returns a `StringResource` (not a `String`)
  so the mapping stays unit-testable while the caller resolves the text. Only
  locale-independent formatting stays in code: separators, timecodes, file
  sizes, content types and the release-date assembly. Directional chevrons go
  through `Modifier.mirrorInRtl()`, since Material ships no auto-mirrored
  chevron; arrows use the `AutoMirrored` icons.
- **Navigation** is a small typed stack (`ContentNavigator` + `ContentRoute`)
  rendered inside the shell content area. Sections keep the navigation
  visible; a pushed route (detail, catalog, settings page) hides it and paints
  its own background, so an immersive hero such as the detail one fills the whole
  window without the section underneath showing through — the same behaviour as
  the Flutter nested `Navigator`, without adding a navigation library. The chrome
  is custom on both classes: a compact window gets an icon-only bottom bar, a wide
  window a left navigation rail. Both share one item (`SectionNavItem`), so they
  behave identically: the glyph carries the state (amber when selected, brighter
  on hover, focus or press) with no state-layer box, the shared focus ring marks
  a keyboard/D-pad target, and the item is announced as a selected tab. The rail
  drops the labels to stay narrow, centers the profile avatar and the section
  items in the window, and paints the page background instead of a panel colour,
  so it blends into the content. The glyphs are Material Symbols Sharp
  (`MaterialIcons`), so the selected state is the tint alone. The glyph is sized
  `AppIconSize.nav` (28dp) rather than the 24dp in-row default, because the item
  shows nothing else. The bar adds a hairline `outlineVariant` top edge and
  stands 64dp tall
  (`bottomBarHeight`), not Material's 80dp label height that it has no label to
  fill. Selection reads from the first frame instead of appearing only once the
  item is focused. The compact bar is an overlay over the edge-to-edge content,
  so hiding it never leaves a gap. It folds away while the content is scrolled
  down and animates back on a scroll up: a `nestedScroll` connection on the
  shell's content box (`AppChrome.scrollCollapsed`, `collapseChromeOnScroll`)
  flips it once the content has travelled `AppSizes.scrollCollapseThreshold`.
  Switching a section or opening/leaving a route expands it again. The wide rail
  never scrolls away, since it is the window's chrome rather than a strip
  floating over the content. `AppSection.navigation` is what the chrome lists;
  Calendar keeps its route and screen but stays out of it while its agenda
  rewrite is pending.
- **The stream picker lists sources, not artwork.** `StreamsDialog` opens as a
  bottom sheet on a phone and as a dialog on a wider window (desktop never gets a
  sheet, matching the settings selects), and both host the same `StreamsPanel`, so
  the content cannot drift between the two. The panel opens straight into the
  sources: the 200dp billboard is gone, since the title was already chosen one
  screen back, and the header is the title plus one `year • N versions` line.
  There is no filter field: the addons already rank their own sources, and a field
  over a dozen rows only adds a step between opening the picker and playing. The
  whole row plays its source — the play glyph belongs to the row instead of being
  its own 48dp target — and the copy action only exists when the stream has a
  link, so a phone gets one tap target per source. Rows paint no surface (hairline
  dividers read the list as one surface) and the addon's text is never clamped:
  its `name` and `description` carry the format the user configured — languages,
  subtitles, quality, size — so truncating them would hide what the picker is
  for, at the cost of uneven row heights. Only the meta line, which the app
  composes itself, stays on one line and carries the addon and the declared
  `behaviorHints.videoSize` without parsing the addon's formatted text. Sources
  are grouped by addon in the order the repository returned them, because the
  addon's own ranking is the truth and the UI never re-sorts; the group header
  only shows when more than one addon answered, since a single one would repeat
  the same label down the list. The view model collapses the copies several
  addons return, keyed by the url, then `infoHash#fileIdx`, then the external url,
  so the same release is offered once. A failed load offers Refresh inside the
  message itself, and a failed refresh keeps the previous list with an inline
  notice, so the picker never loses its sources silently. Rows carry the "Play"
  action label, so a screen reader announces the action instead of a column of
  identically named buttons.
- **Search owns the discover feed.** It has a free-text search across every
  catalog that declares the `search` extra, debounced, run in parallel and
  deduped across addons, split into Movies / Series / Other rails; and — while
  the query is empty — the discover feed underneath the bar. The search field is
  pinned at the top with the same `screenPadding` as the other sections' first
  row and a `md` gap below it. The feed flattens
  every catalog the enabled addons declare into a single mixed grid, like the
  Library, so the catalog boundaries stay invisible. Only browsable catalogs are
  used: one whose extras are required (a search term, a list of ids, ...) cannot
  be fetched without a query, so it is skipped instead of asking and getting an
  empty page. The catalogs are interleaved across addons before the cap, so one
  addon with many catalogs does not crowd out the rest, and the items are
  interleaved round-robin and deduped, so the grid alternates sources instead of
  showing one catalog as a block. It pages with `skip` as the grid is scrolled,
  so it keeps growing instead of stopping at the first page. Discover is no
  longer a section. **Calendar** paints a month grid with the upcoming
  episodes of the Library's series, and is kept out of the navigation until its
  agenda rewrite lands; see the limitation below.
- **Theme** follows Material 3 and is data, not code. `BrandPalette`
  (`theme/Tokens.kt`) is a data class holding one theme's raw colours, `AppThemes`
  maps the persisted `AppTheme` to a palette, and `PenguTheme(theme)` picks it
  and provides it through `LocalBrandPalette`, then builds the full
  `darkColorScheme` from it (surface-container tiers, `outline`/`outlineVariant`,
  `inverse*` and the error containers). The palette is internal and only fills
  the scheme, so stock Material3 components and custom surfaces stay on the same
  colours; `tertiary` mirrors the accent, so no cold hue sneaks into a warm
  theme. The scrims and the native window chrome read `LocalBrandPalette` too, so
  a theme change repaints the hero gradients and the title bar in the same frame.
  `AppThemes.Coffee` is the default and only theme for now: a warm
  cream/amber/burnt-orange palette, selected in Settings → Appearance → Theme and
  persisted as `appearance.theme` in `settings.json`. The Inter typeface loaded
  from Compose Resources and the small, square-ish shape scale (`AppCorner` feeds
  `MaterialTheme.shapes`: 1/2/3/4/6dp; the app reads square, not rounded) stay
  theme-independent. Dialogs and bottom sheets take the same scale
  (`shapes.extraLarge` and `AppBottomSheetShape`) instead of Material's 28dp
  default. The UI reads semantic colours from
  `MaterialTheme.colorScheme`; `AppColors` keeps the media overlays the scheme
  has no role for: `mediaScrim` and the hero carousel indicator (muted when idle,
  cream when active). `AppSizes.controlHeight` /
  `minTouchTarget` keep interactive targets at Material's 48dp minimum. Custom
  clickable surfaces draw the Material ripple state layer (`clickableSurface`),
  so focus and press feedback stays visible, and carry a button role so a custom
  surface is announced correctly. Poster tiles opt out of that state layer
  (`indication = null`) and signal hover with the scale/border animation
  instead, so a hovered poster is not greyed. Settings panels use the Material
  `Card`. A small component layer in `ui/components` keeps the Material defaults
  off the screen: `ActionButton` picks its container, content and disabled
  colours from an `ActionButtonStyle` weight (Filled on the inverse surface,
  Accent on the amber `primary`/`onPrimary` for the detail billboard's
  "Stream Now",
  Tonal on a translucent on-surface fill, Outline on `outline`, Text
  label-only), leaves the content colour to the button's own `LocalContentColor`
  so a disabled label dims with its container, pins elevation to zero (the app
  is flat), sets the label to a SemiBold `labelLarge` (the type scale pins
  every slot to Normal, so buttons opt back in), one size up on a call to
  action, with a glyph that scales by emphasis — 24dp on a call to action, 20dp
  inline — caps it to one ellipsised
  line, and steps the whole control down slightly while pressed; a `selected`
  flag turns the low-emphasis weights amber for the season picker, and an
  optional `trailingIcon` adds a trailing glyph. `AppTextField` pins the form fields to `shapes.small` and an
  amber-on-focus border, with no fill of its own, so the field reads the
  surface it is placed on like every other flat control, plus an
  `AppSearchField` variant (leading magnifier, clear action) for the Search
  section. `AppIconButton` is the one icon control — media headers, the player
  (back, play/pause, seek, subtitles, audio, fullscreen and the centred play
  button), the detail screen's library/rate/watched toggles and the text fields'
  clear/password actions — so every one gets the 48dp target, an optional
  `mediaScrim` and a load dim, and carries its state on the glyph rather than on
  the surface: no background box or ripple, the icon brightens on hover or focus
  and turns amber when selected; no screen calls a raw Material
  `IconButton`, `TextButton` or `OutlinedButton`. Every control reads scheme
  roles and the shared tokens only, so none is styled at the call site. Focus is
  reserved for keyboard and D-pad input: the shared `AppSizes.focusRing`-wide
  ring is drawn only when `LocalInputModeManager` reports a non-touch mode, so a
  mouse click keeps the clean, box-less look while a remote or a gamepad shows
  the target, and the icon-only controls (`AppIconButton`, the rail) also
  brighten their glyph on hover or focus. The near-black page background (`#0B0803`) is the single source of
  truth: the scheme, the native window background and every page-fade gradient
  end on it. The
  app is deliberately **dark-only** — every `AppTheme` is a dark palette, and
  there is no light scheme and no dynamic colour — because the UI is built
  around dark artwork and scrims; the player keeps a fixed white-on-black control
  palette over video, with the seek and volume progress as the only theme-accent
  exception. Material3 `visualDensity` stays at the platform default.
- **Images with Coil 3**, the multiplatform image loader. Remote artwork goes
  through two shared composables in `ui/components`: `PosterImage` for posters
  and thumbnails, and `RemoteImage` for the rest (backdrops, hero art, cast
  photos, title logos). Both pin `ContentScale` and `FilterQuality`, because
  Coil draws with `DefaultFilterQuality` (bilinear) and aliases a downscaled
  poster. Artwork uses `FilterQuality.Medium` (mipmapped bilinear), which is the
  right sampling for minification, and logos use `FilterQuality.High` (bicubic)
  so their edges stay crisp. A full-screen backdrop uses `FilterQuality.Low`
  (plain bilinear, `BackdropFilterQuality`): it is drawn near its own scale,
  where mipmaps cost more than they save. On desktop the decode side is fixed too: Coil
  3.6.3's `SkiaImageDecoder` resizes with `SamplingMode.DEFAULT`, which Skiko
  defines as nearest-neighbour, so `app/desktopApp` installs
  `MitchellImageDecoder`, a cubic decoder that keeps Coil's size math and
  reduces once at decode time. Together they both sharpen the artwork and cut
  the bitmap memory the renderer has to scale every frame. Title logos are
  contained in a box — an optical height cap plus a width guard
  (`AppLogoSize`), drawn with `ContentScale.Fit` — so a square logo cannot
  tower over the copy and a very wide one cannot overflow its column. The box
  hugs the artwork rather than fixing the height, so the gap to the metadata line
  is the same for every logo: a fixed height centred each image in the box and
  left a gap that grew with its aspect. On a phone
  the guard drops to 60% of the window (`maxWidthForWindow`), so a wide wordmark
  cannot take over the screen, and `opticalHeight` caps the height to the copy
  width, so a square logo does not tower and the two shapes read at a similar
  weight. The
  logos are then trimmed to the bounding box of their non-transparent pixels
  (`TrimTransparentTransformation`, a platform pixel scan behind a Coil
  `Transformation`), because providers ship them on a fixed transparent canvas:
  a series wordmark can be a 50px strip inside an 800x310 PNG, and `Fit` would
  scale that whole canvas. Trimming makes the box measure artwork, not padding,
  and the Home preloader carries the same transformation so both requests share
  Coil's cache entry. Logos also decode at their own resolution
  (`Size.ORIGINAL`, capped at `LogoMaxBitmapSize`): deriving the decode size from
  the box would shrink the content to the canvas's share of the box, so a padded
  wordmark was decoded below the size it is drawn at and then upscaled, which
  read as jagged. The Home
  hero preloads the next slide's backdrop and logo through the singleton loader,
  sized to the billboard, and re-arms as the page advances, so swiping paints
  from cache instead of starting a network round-trip mid-gesture without the
  decode spike that enqueuing the whole set caused on entry.
- **Motion** is centralised in `ui/theme/Motion.kt`: `AppMotion` holds the
  durations and both the utility and the Material emphasized easings (entering
  screens use emphasized decelerate, exiting ones emphasized accelerate), and
  `LocalMotionEnabled` gates every animation. Specs
  built through `AppMotion.spec`/`motionTween` collapse to zero duration when it
  is off, and `Modifier.motionLayer` applies scale and alpha in the draw phase,
  so motion never relayouts or recomposes per frame. Tabs swap with no
  transition; the section
  stays composed while a route is open (so its scroll is never saved and
  restored, which lost the within-item offset and scrolled it back up) and the
  route fades in over it, leaving at once when popped. The rail floats over
  the content instead of taking layout width, because hiding it used to grow
  the content viewport and clamp the section scroll; sections reserve its width
  with a constant padding, so the viewport never changes. The size animation is
  disabled (`.using(null)`) because it relayouts every frame. Posters scale on
  hover and dialogs fade/scale in. Loading screens are skeletons driven by a
  single shimmer read in the draw phase: the poster grid derives its cell count
  from the viewport, so it never stops halfway down a wide window, and the
  static gradient stops are built once instead of on every frame. Home shows a
  full-screen loading indicator until the first catalog batch lands, then paints
  the billboard and the rows together. The detail hero does not paint the library fallback as if it were the final
  artwork: it reserves the logo's space without drawing a placeholder, and the
  logo and backdrop fade in when the image itself is ready (`RemoteImage(fadeIn)`),
  so a slow load never pops. The player is deliberately outside the route
  transition because it drives a single shared engine; its entrance is the
  pre-roll's own dissolve (see Player).
- **The phone billboard fits a textless poster instead of cropping one.** A wide
  backdrop is 16:9 and the compact billboard is a tall cover, so cropping one
  into the other cuts the composition apart; a language-neutral poster is
  already portrait and carries no baked-in title, so the live logo sits over the
  art instead of a crop of it. TMDB's enrichment supplies one (`textlessPoster`,
  its highest-resolution `iso_639_1 == null` poster), and `heroArtwork` selects it on
  a compact window, fitted whole into a frame that is exactly the poster's size
  (`1 / POSTER_ART_ASPECT`) — falling back to the backdrop, then the poster, both
  cropped — while the wide billboard keeps the backdrop it always used. The whole
  compact wash is a single gradient (`heroCompact`): it dims the middle, darkens
  the top without ever reaching the page background (so the cover stays visible
  to the very top) and releases into that background at the bottom to meet the
  section below. The three washes it replaces were the same colour, so they fold
  into one full-screen fill through the composite alpha, and a fitted slide
  does not drift during a swipe, because the parallax overscan would trim its
  sides again. The detail backdrop makes the same textless choice on a phone,
  cropped to fill, and there drops the left-to-right readability scrim so the
  artwork is only darkened at the bottom, under the copy. It also waits for the
  enrichment to settle before painting, so the choice is never re-made on screen.
  The Home preloader goes through the same helper, so it always
  warms the URL the layer will draw, and the featured set is enriched before the
  billboard's first paint, so it never shows the catalog artwork and then swaps
  to TMDB's.
- **Poster size** is a single setting (`PosterSize`: default / large, 210 / 230
  dp) that only drives the horizontal poster rows — the Home catalogs, the
  search results and "More like this" — where a card can be resized without a
  grid side effect; a row keeps the width as-is and derives its height from the
  artwork's `width / (2/3)` plus a fixed caption block
  (`PosterCaption.blockHeight`, in `dp` from the caption type so it tracks the
  font scale), or just the artwork when the labels are hidden. The reserve is a
  height, not a ratio: a ratio shrinks with a phone's narrow cell until the
  caption eats into the artwork and crops its sides. Grids (Library, the full
  catalog, Discover) ignore the setting and keep a fixed layout: `gridCells` lays
  out three posters per row on a phone and fits as many `GRID_CELL_WIDTH` cells
  as it can on a wider window, so a phone never shows a size the user did not
  choose. On a compact window the rail width shrinks by `COMPACT_SCALE` (0.65).
- **Window**: the desktop window keeps its **native frame** (`WS_CAPTION`,
  `WS_THICKFRAME`) but erases the OS-drawn non-client area, so the Compose title
  bar is the caption. On Windows a native `WindowProc` (JNA, via
  `SetWindowLongPtr`) answers `WM_NCCALCSIZE` — return `0`, shrinking the client
  rect by the frame metrics so a maximized window does not bleed — and
  `WM_NCHITTEST`: `HTCAPTION` over the bar, `HTMINBUTTON`/`HTMAXBUTTON`/`HTCLOSE`
  over the buttons and `HTLEFT…` over the edges. The caption buttons take
  priority over the top resize border, so the top strip over them still lights
  them up (and the full-height buttons reach the window's top edge) instead of
  resizing. That is what preserves native
  drag, Aero Snap, maximize, resize and the Windows 11 Snap Layouts flyout. The
  Compose content lives in a Skia child window, so a second procedure hooked on
  it returns the button hit results and mirrors the non-client mouse messages
  back into the content: the Compose buttons animate and handle the click while
  the parent owns the caption and the borders. The frame (`WindowsWindowProc` in
  `:app:desktopApp`) follows Animeko's implementation, trimmed to the pieces the
  app needs (no touch/pen bridge, no Windows 10 glass, no per-node Compose hit
  test). `AppSizes.titleBarHeight` and `AppWindowChrome` feed the bar; the window
  opens at 1280x720 and that is also its **minimum** (set as `minimumSize` on the
  AWT window). Keeping the native frame is what preserves snap, maximize, the
  window animations and high contrast. macOS and Linux keep the native
  decoration; only **fullscreen** is routed through `WindowController` there,
  which the player owns, and it exposes the screen work-area height the Home
  billboard is sized from.

## Player

`PlayerService` is implemented in the composition roots by `MediampPlayerService`,
a thin subclass of the shared `MediampPlayerAdapter` (`:app:player`) over
**[mediamp](https://github.com/open-ani/mediamp)**. The adapter holds the contract
logic once; each root only wires its engine (mpv or ExoPlayer): the surface, the
volume and the external-subtitle handling. The engine stays out of the shared UI
and is picked per target.

On desktop, mediamp resolves to its **mpv** backend: it renders the video into
the Compose/Skia canvas (D3D11 on Windows, OpenGL on Linux, Metal on macOS) and
draws it through `VideoSurfaceRenderer`, so the video is ordinary Compose
content and the Compose controls stack on top of it in the same window — a
native `wid` surface or a separate overlay window would either cover the
controls or drift out of sync. External addon subtitles are added with the
underlying mpv command, and mediamp's per-platform runtime bundles a matching
libmpv and FFmpeg (LGPL on Windows/macOS, GPL-3.0 on Linux).

On Android, mediamp resolves to its **ExoPlayer (media3)** backend: it embeds a
`PlayerView` through `AndroidView` behind the same `VideoSurfaceRenderer` handle,
so the Compose controls stack on top of the video there too. media3 only accepts
side-loaded subtitle files when the media is prepared, so selecting an external
addon subtitle reloads the same source with the file attached and resumes at the
current position; embedded tracks, audio selection and position/duration come
through the same mediamp API. ExoPlayer exposes no mediamp
`AudioLevelController`, so the volume is set on the native player.

Commands, position, tracks and volume go through mediamp's `MediampPlayer` API in
both cases. Watch progress reporting, resume, subtitles/audio selection and
next-episode autoplay are unchanged.

The overlay (`ui/player`) follows the reference player layout: a two-line header
(title plus `S01E01 · Episode` for an episode), a full-bleed hairline seek bar
with the played part in the theme accent, a thumb and a time bubble shown while
the pointer is over it or while scrubbing, and a single borderless control row
underneath. The seek bar thickens on hover, seeks on the first press and follows
the drag without waiting for the release, and its timecode leaves the minutes
unpadded (`0:03 / 56:01`). The volume glyph follows the level and doubles as the
mute toggle, and the inline volume slider only expands while the group is
hovered, so the row keeps the reference's one-icon rhythm on desktop; the
keyboard keeps volume on the arrow keys and mute on `M`. Subtitles turn amber
while a track is selected. The controls fade out during playback and are not
composed while hidden, so an invisible overlay never catches a click. The
player glyphs stay white on black; only the seek and volume accents read
`colorScheme.primary`.

Choosing a stream opens the player onto a **pre-roll** instead of a black frame:
`PlayerSplash` (`ui/player`) paints the title's own backdrop under a flat wash
(`AppScrim.playerSplash`) with the title logo in the middle. The logo is not a
spinner and carries no label — it breathes between 45 % and full opacity, the way
Stremio's loading screen reads, so the wait looks like *that title* loading. The
artwork comes from `MetadataRepository.detail` for the target's content id instead
of a new `PlaybackTarget` field: `PlaybackTarget` stays a playback contract, the
screen the user came from just painted the same URLs (so it is normally a warm
HTTP cache hit), and the lookup also covers autoplay-next and a target opened
straight from a link. It is best-effort — a raw `--play` source has no metadata,
so the splash falls back to the black frame and draws no title, and without a
logo the name stands in for it.

The wait itself lives in `rememberPlayerPreRoll` (`ui/player`), which owns the
artwork, the readiness rule, the dissolve and the hand-off to the controls, so
`PlayerScreen` keeps only the video, the overlay and the lifecycle. It holds the
splash for at least 1 s and until the player reports a painted frame with no
buffering, and gives up at 4 s — whichever comes first, because on a source that
cannot fill the lookahead the extra wait buys nothing and the buffering indicator
is the better answer. A painted frame is `position > 0`: a parsed duration arrives
before the engine has a picture, and gating on the duration revealed onto a black
frame. On top of that it waits for `PlayerState.bufferedPosition` to sit 2 s ahead
of the playhead, which is the actual pre-buffer rather than a timer: mediamp
exposes the buffer on both engines (mpv's demuxer cache end, ExoPlayer's buffered
position) and the adapter publishes `null` when the backend has no cache to fill,
so a local file only needs the frame and a live stream — at the buffer edge by
definition — is never asked for lookahead it cannot have. A clip shorter than the
lookahead only waits for what it has. An engine error cuts the wait short.

It then dissolves with `AppMotion.DISSOLVE` (the one duration above `SLOW`, since
a layer the size of the screen reads as a cut at a component's timing) while it
recedes 5 %, so the video arrives from behind the artwork instead of through a
plain alpha ramp. The splash is on screen from the first frame and only the
backdrop settles in (a fade plus a 3 % zoom): fading the whole layer up from black
was what made the entrance read as a cut, because the screen behind the player is
the section, not the title the user came from. Alpha and scale are read in the
draw phase through `motionLayer`, so the dissolve never relayouts or recomposes
the artwork. Once it ends the splash leaves the composition — its logo pulse stops
instead of animating behind two hours of video — and the controls wait 250 ms, so
the reveal itself is only the video. Every duration sits next to the screen and
every animation is gated by `LocalMotionEnabled`.

A small set of **engine options** is exposed in Settings (Playback page, desktop
only) because they are the levers that matter when a title looks wrong: the HDR
**tone-mapping curve** (`auto`, `bt.2390`, `hable`, `mobius`), **debanding**, and
**hardware decoding** (a switch to software decode, the first thing to try when a
source shows wrong colours or a black picture). The list is deliberately short and
renderer-aware: curves that only exist in `vo=gpu-next` (`spline`, `bt.2446a`,
HDR10+ dynamic curves) are not offered, because the desktop surface draws through
the libmpv render API, which only implements the legacy GPU backend. The values
live in `AppSettings` (`player.toneMapping`, `player.deband`,
`player.hardwareDecoding` in `settings.json`), are mapped to mpv's spelling by a
pure `MpvPlaybackOptions` (`app:desktopApp`, unit-tested), and are applied twice:
as construction-time defaults through mediamp's `configureOptions` hook, and again
before every open, so a change lands on the next video instead of the next launch.
`auto` is sent rather than skipped — returning to it from another curve has to
reset the option on a live player. Android hides the panel: its engine has no
equivalent knob, and a switch that does nothing is worse than no switch.

Closing the player is also when the screen the user is about to see is computed.
The final position is written from `AppServices.appScope`, not from the
composable's own scope (which is cancelled as the player leaves, which used to
drop the write), and only once that write has landed does it tick
`PlaybackUpdates.closed`. Home observes the tick and re-reads progress to repaint
"Continue watching" against the library items it already had, so a title left
half-watched appears at once instead of on the next load. The tick is a separate
signal on purpose: `ProgressRepository.changes` bumps on every periodic save
(every few seconds of playback), and a screen that listened to it would re-read
progress all through the film. The row itself holds one card per title: progress
is stored per episode, so the ViewModel keeps only the newest `lastWatched` of
each `contentId` — and filters mid-way after that, so a finished last episode
hides the series — while the per-episode rows stay in the repository for resume
and the watched badge.

The player owns the screen on a phone: `PlayerScreen` calls the platform
`PlayerWindowMode` (`ui/platform`), which locks the host activity to sensor
landscape and hides the system bars (immersive), restoring both when the player
leaves; the bars come back on a swipe. Android 16+ ignores the orientation lock
on large screens. The Android activity declares the orientation `configChanges`,
so rotating does not recreate it (and does not restart the video). The overlay
hides the fullscreen toggle where the window cannot fullscreen itself
(`WindowController.supportsFullScreen`), which is Android; desktop keeps it.

On desktop, fullscreen is a user toggle routed through `WindowController`, and
leaving the player always exits it so the navigation returns. On Windows the
controller flips the window styles through a native helper
(`WindowsWindowUtils.setUndecoratedFullscreen`): it saves the styles, rect and
maximized state, removes `WS_CAPTION`/`WS_THICKFRAME`, resizes to the monitor and
restores everything on exit. Because the frame stays native there is no exclusive
fullscreen, no maximize dance and no pixel nudge; the title bar simply leaves the
composition while fullscreen. The other desktop targets still use the
`Floating`/exclusive workarounds, which can be removed once they move to the
custom frame too.

Background playback is not wired yet, so `MainActivity.onStop` pauses the player
unless the stop is a configuration change; that callback is the seam where a
media session and a foreground service would keep playing instead.
`PlayerService.pause()` exists for that lifecycle call and never *starts*
playback, unlike `playOrPause()`.

## Testing

`commonTest` covers the pure domain (playback target, watched rules, year
parsing, badge resolver with fakes) and the defensive parsers/mappers.
`jvmTest` covers the file stores and the local repositories. `:app:shared`
`commonTest` covers the section ViewModels with in-memory repositories and
`Dispatchers.setMain`.

## Known differences / limitations

- The desktop player uses **mediamp**'s mpv backend, which bundles a matching
  libmpv and FFmpeg per platform: no system install and no `PENGU_LIBMPV` are
  needed. The Linux runtime is GPL-3.0 (mpv's X11 output); Windows and macOS are
  LGPL.
- Android uses **mediamp**'s ExoPlayer (media3) backend, which pulls media3 and
  Compose UI into the APK but needs no bundled native runtime. media3 only
  accepts side-loaded subtitle files when the media is prepared, so choosing an
  external addon subtitle reloads the source and briefly re-buffers at the same
  position; embedded tracks, audio selection and the rest are unaffected.
- The Nuvio addon metadata/edit screens are available from Settings → Addons
  (install, enable, reorder and remove). Local mode keeps its fixed
  `PENGU_LOCAL_ADDONS` list read-only, because there is no backend to write to.
- **Calendar** uses a Cinemeta convention when an addon exposes it: a
  `calendar-videos` catalog (Cinemeta and AIOMetadata declare it) returns
  `metasDetailed` — the full meta per series — for the library's series in one
  request, which is why `CatalogRepository.detailedItems` exists. When no addon
  exposes it, the section falls back to resolving each series through the
  metadata addons behind a semaphore, so the first episodes appear without
  waiting for the slowest one. Either way only episodes inside a rolling 180-day
  window are kept, episodes without a release date are skipped, and the grid
  fills the window instead of scrolling.

## Known bugs

- **Dolby Vision profile 5 plays with purple and green colours on desktop.**
  Only profile 5: it is single-layer ICtCp with no compatibility id
  (`dv_bl_signal_compatibility_id: 0`) and declares no usable colour signalling,
  so a renderer without Dolby Vision reshaping has nothing to fall back on.
  Profiles 7 and 8 are fine — they carry an HDR10 base layer
  (`compatibility_id: 1`) that the legacy path tone-maps — which is why "it says
  DV" and "it is a Web-DL" are both the wrong criterion: the profile is. Verified
  with `ffprobe` on the samples that fail
  (`Hyper.Knife.S01E01…DV.H.265-ASiANLAD`, `Animals.2026.2160p.NF.WEB-DL…DV.H.265-FLUX`:
  `dv_profile: 5`) and on one that looks right (`dv_profile: 8`). The fix is the
  renderer, not the decoder: mpv with `--vo=gpu-next` renders both correctly and
  `--vo=gpu` breaks the profile-5 one, and the libmpv render API (the desktop
  surface here) only implements the legacy GPU backend. Nuvio sidesteps it by
  handing mpv a native child window (`vo=gpu-next` + `wid`) instead of a render
  API surface. Because the profile is only readable from the container, the app
  cannot detect it from a release name, so there is no cheap mitigation.

## Improvement backlog

Items that need a design decision, touch several modules, or both, so they are
not a one-file change. They are grouped by the axis they change, not by effort.

### Backends

- **Stremio is a full backend, not just a login.** Swapping Nuvio for Stremio
  already fits the `BackendKind` + `createBackend` seam and needs no `ui/`
  change, but a usable account also needs `LibraryRepository`,
  `ProgressRepository` and `AddonRepository` against the Stremio datastore.
  The auth adapter is the small part; the data repositories are the bulk.

### Default catalogs

- **A first-party catalog source, on by default.** Home would ship six rows
  built from TMDB (trending, popular films, popular series, top rated,
  upcoming, and a curated genre row) so the app is useful on first launch,
  without a catalog addon installed. The addons stay: they keep adding rows and
  resolving playback, they are not removed. This needs a small server of its
  own, because the TMDB API key has to live there and never in the client (an
  embedded key is extractable). The six rows are composed and cached in one
  response; the cache is per-row TTL (trending expires in hours, popular and
  top rated in days), stale-while-revalidate so no one waits on an expiry, and
  single-flight so a cold row costs one TMDB request instead of one per waiting
  client, bounded and caching empty responses too. Images are already cached by
  Coil (memory + disk); the lever is the requested size, which `TmdbMapper`
  already sets (`w500` / `w1280`).

  Two things need designing. TMDB's list endpoints do not return `imdb_id`, so
  tapping a catalog item needs the `/external_ids` lookup before the addons can
  resolve streams (the `TmdbIdResolver` machinery exists for the detail screen);
  without a cache the Home paints fast but every tap pays an extra hop. And the
  TMDB terms of use have to be checked for cache lifetime, commercial use and a
  single shared key, with the server adding its own per-client rate limiting.
  The in-repo seam is `BackendKind` + `createBackend`; `CatalogRef` is
  addon-shaped today, so the default rows need a source-agnostic ref or a
  `CatalogRepository` decorator that merges both sources and dedupes by `key`.
  A separate `:server` module keeps the client talking only to its own DTOs,
  never to TMDB's raw JSON.

- **Dropping Nuvio for self-hosted accounts is a separate decision.** Owning
  user creation replaces `NuvioAccountRepository` with password hashing, session
  and refresh handling, and the per-row authorization Supabase provided, plus
  migrating existing users. It shares the `:server` module but not the catalog
  work, and it is worth doing only when the identity itself is the goal, not as
  a way to make the default catalogs work.

### Playback

- **The push into the player still cuts.** The pre-roll's own dissolve is clean,
  but the entry into it is not: the route that opened the player is dropped (the
  player renders outside the route transition), and the backdrop is fetched only
  after the player composes, so the first frames can be the bare black splash
  waiting for the artwork. Two candidate fixes: carry the backdrop and logo the
  detail screen already holds through `StreamsRequest` → `PlaybackTarget` (or
  prefetch them when the stream picker opens), and crossfade from the outgoing
  screen by keeping it composed for the length of the dissolve.
- **The Linux runtime, when the Linux target is addressed.** The desktop
  composition root declares only the Windows libmpv/FFmpeg runtimes
  (`mediamp-mpv-runtime-windows-x64`, `mediamp-ffmpeg-runtime-windows-x64`); a
  Linux build adds `mediamp-mpv-runtime-linux-x64` and
  `mediamp-ffmpeg-runtime-linux-x64` next to them. The Linux runtime is GPL-3.0,
  not LGPL, so the distribution's licensing has to account for it.
### Product / UX

- **Mobile polish and release hardening.** The compact layout works, but it is
  not tuned surface by surface yet: spacing and proportions still need a pass on
  real phones. The Android release build is minified
  (`isMinifyEnabled` + `isShrinkResources`) and ships the library baseline
  profiles (Compose and friends) through `profileinstaller`, but there is no app
  profile of its own yet: generating one needs the `androidx.baselineprofile`
  plugin with a macrobenchmark run on a device.
- **Keyboard / D-pad navigation.** `Interaction.kt` covers hover, focus and
  pressed states, and the player has shortcuts, but there is no focus traversal
  across the Home rails and no spatial navigation model. A TV target makes this
  mandatory rather than nice to have.
