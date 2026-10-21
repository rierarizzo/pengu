# Pengu — Guía para desarrolladores

Mapa práctico del código para alguien que se incorpora al proyecto. Te dice
**dónde vive cada cosa**, **dónde cambiar cosas** y **qué ideas de Kotlin /
Compose Multiplatform** usa el código. Para el *porqué* de las decisiones, lee
[`architecture-decisions.md`](architecture-decisions.md); este archivo es el *dónde*.

> Pengu es un cliente Compose Multiplatform para el backend Nuvio que habla el
> protocolo de addons de Stremio y reproduce vídeo con mediamp (mpv en desktop,
> ExoPlayer en Android). Desktop y Android son ambos targets completamente cableados.

---

## 1. La forma del proyecto

```
app/desktopApp   Composition root: ventana, reproductor mpv (mediamp), main()
app/androidApp   Composition root: Activity, reproductor ExoPlayer (mediamp)
app/player       Adaptador compartido de PlayerService sobre mediamp (lo usan ambos roots)
app/shared       UI Compose + ViewModels (toda la UI del producto)
core             Dominio puro: modelos, contratos de repositorio, reglas, constantes
data             Implementaciones: addons Stremio, backend Nuvio, ficheros
                 locales, enriquecimiento opcional con TMDB
```

La única regla que importa:

```
ui  ──▶ core ◀── data
```

- `core` no depende de nada (ni UI, ni red, ni plataforma). Mantenlo así.
- `data` depende solo de `core`; `app:shared` depende de `core` y `app:player`.
- **Solo los composition roots** (`app:desktopApp`, `app/androidApp`) pueden
  importar `data`. Si algún día quieres `data` desde una pantalla, lo que quieres
  es una interfaz de repositorio nueva en `core`.

Por eso el código es testeable: la UI habla con interfaces y los tests inyectan
fakes en memoria.

`data` tiene además un source set `jvmSharedMain`, compartido por sus targets
`jvmMain` y `androidMain`, donde viven una sola vez los helpers de ficheros que
usan ambas plataformas (ver §7).

---

## 2. Dónde mirar cuando quieres cambiar algo

| Quiero… | Empieza por |
|---|---|
| Añadir o cambiar una sección (Home, Search…) | `app/shared/.../ui/shell/AppSection.kt`, `SectionHost.kt` |
| Añadir una pantalla apilada (detalle, reproductor…) | `app/shared/.../ui/shell/ContentNavigator.kt` (`ContentRoute`), `ContentArea.kt` |
| Cambiar el overlay del reproductor (cabecera, barra de progreso, controles) | `app/shared/.../ui/player/` (`PlayerControls.kt`, `PlayerSeekBar.kt`) |
| Cambiar las opciones del motor de escritorio (mpv) | `app/desktopApp/.../player/` (`MpvPlaybackOptions.kt`), `core/.../repository/SettingsRepository.kt`, la página Playback en `SettingsScreen.kt` |
| Añadir o cambiar una categoría de ajustes | `app/shared/.../ui/screens/SettingsCategory.kt`, `SettingsScreen.kt` |
| Administrar los addons instalados | `core/.../repository/AddonRepository.kt`, `data/.../backend/NuvioAddonRepository.kt`, `app/shared/.../ui/screens/AddonsSettings.kt` |
| Cambiar la lógica/estado de una pantalla | `app/shared/.../ui/<feature>/<Feature>ViewModel.kt` + `<Feature>Screen.kt` |
| Compartir comportamiento entre secciones | `app/shared/.../ui/common/` (`AccountScopedViewModel`, `WatchedBadgeSync`) |
| Cambiar colores, espaciado, tipografía, movimiento | `app/shared/.../ui/theme/` (`Tokens.kt`, `AppTheme.kt`, `AppType.kt`, `Motion.kt`, `WindowSize.kt`, `PosterSize.kt`) |
| Cambiar la paleta | `CoffeePalette` en `ui/theme/Tokens.kt`; `PenguTheme` en `AppTheme.kt` la mapea al esquema de Material |
| Cambiar la tipografía | `AppTypeface` / `AppSettings.typeface` en `core/.../repository/SettingsRepository.kt`, `appFontFamily` en `ui/theme/AppTheme.kt`, selector en `SettingsScreen.kt`, los TTFs en `app/shared/.../composeResources/font/` |
| Reutilizar un widget (botones, pósters, campo de búsqueda) | `app/shared/.../ui/components/` |
| Cambiar o añadir un modelo de dominio | `core/.../core/model/` |
| Usar una constante compartida (tipo de contenido, extra, umbral) | `core/.../core/model/` (`ContentTypes`, `CatalogExtras`, `ProgressThresholds`) |
| Cambiar un contrato de repositorio | `core/.../core/repository/` |
| Cambiar cómo se piden metadata/streams/subtítulos | `data/.../data/addons/` |
| Cambiar la caché HTTP, la compresión o el DNS (velocidad de arranque en frío) | `data/.../data/platform/` (`HttpDefaults.kt`, `Platform.jvm.kt`, `Platform.android.kt`) |
| Cambiar el enriquecimiento opcional con TMDB (resolución de ids, artwork, recomendaciones) | `data/.../data/tmdb/`, `core/.../model/Tmdb.kt` |
| Cambiar el comportamiento del backend Nuvio | `data/.../data/backend/` |
| Cambiar el modo local/offline | `data/.../data/local/` y `data/.../data/store/` |
| Añadir un backend | `data/.../data/BackendFactory.kt` (`BackendKind`, `createBackend`) |
| Añadir un motor de reproducción | Implementa `core/.../player/PlayerService.kt`; conéctalo en un composition root |
| Cambiar comportamiento de plataforma (ficheros, back, superficie de vídeo) | `app/shared/src/{commonMain,jvmMain,androidMain}/.../ui/platform/` y `ui/video/` |
| Cambiar el cableado / arranque | `app/desktopApp/.../Main.kt`, `app/androidApp/.../MainActivity.kt` |
| Añadir un texto visible al usuario | `app/shared/src/commonMain/composeResources/values/strings.xml` |

---

## 3. Sigue una petición de principio a fin

Ejemplo: el usuario toca un póster en Home y pulsa **Watch now**.

1. `HomeScreen` → `navigator.push(ContentRoute.Detail(item))` (`ContentNavigator`).
2. `ContentArea` renderiza `DetailScreen`; `DetailViewModel.load()` llama a
   `MetadataRepository.detail(...)`.
3. El repo concreto es `StremioMetadataRepository` (cableado en `Main.kt`), que
   pregunta a los addons activos y devuelve el primero que responde.
4. Al pulsar **Watch now** se abre `StreamsDialog` → `StreamsViewModel` →
   `StreamRepository.all(...)` → `StremioStreamRepository` (agrega addons).
5. Al elegir un stream se apila `ContentRoute.Player(PlaybackTarget…)`.
6. `PlayerScreen` llama a `PlayerService.open(target)`. Ambos composition roots
   instalan un `MediampPlayerService`: el backend mpv de mediamp en desktop, su
   backend ExoPlayer en Android.
7. `PlaybackProgressReporter` guarda el progreso a través de `ProgressRepository`
   mientras se reproduce.

Cada flecha cruza una interfaz, nunca una clase concreta.

---

## 4. Piezas compartidas

Existen para no reimplementar la misma preocupación en cada pantalla:

- **`AccountScopedViewModel`** (`ui/common`) — toda sección de datos lo extiende.
  Es dueño de la regla "carga cuando hay sesión y perfil elegido, limpia al
  cerrar sesión"; las subclases implementan `onAccountReady()` /
  `onAccountCleared()`.
- **`WatchedBadgeSync`** (`ui/common`) — resuelve los badges de "visto" y
  descarta resultados obsoletos, para que Home y Library no dupliquen la lógica
  de ticket de petición.
- **`ReorderableCardColumn`** (`ui/components`) — una lista corta de cards que se
  reordena arrastrando el handle de una de ellas; el llamador posee la lista y
  persiste el nuevo orden una sola vez, al soltar. La usa Ajustes → Addons;
  reutilizable para cualquier otra lista de cards que necesite un orden.
- **`StremioAddonRepositories`** (`data/addons`) — construye los repos de
  catálogos, metadata, streams y subtítulos sobre un único `AddonClientPool`, así
  un `StremioAddonClient` cacheado (y su manifiesto) se comparte en vez de
  reconstruirse por repositorio.
- **`ProfileScope`** (`data/backend`) — `AccountRepository.requireProfileId()` /
  `requireActiveProfile()`, usado por cada repositorio Nuvio con scope de perfil.
- **`AddonSnapshotStore`** (contrato en `core`, implementación de fichero en
  `data/store`) — la última lista de addons conocida, por perfil. Guarda todos
  los instalados, activos o no, para que `AddonsSettings` pueda administrarlos
  mientras `all()` filtra los desactivados. Es la capa local-first:
  `NuvioAddonRepository` responde desde su snapshot y refresca detrás, y degrada
  a "nada guardado" si el fichero falta o está corrupto. El feed no se persiste:
  Home siempre carga sus catálogos.
- **`VideoOutput`** (`core/player`) — marcador tipado de la salida de plataforma
  del reproductor; `VideoSurfaceRenderer` (en `app/player`) lo extiende
  y dibuja el vídeo como contenido Compose, sea cual sea el motor cableado.
- **`ContentTypes` / `CatalogExtras` / `ProgressThresholds` / `releaseYear()`**
  (`core/model`) — el vocabulario del protocolo y los umbrales, definidos una vez.
- **Tokens de diseño** (`theme/Tokens.kt`) — la paleta (`BrandPalette` como
  `CoffeePalette`), espaciado, tamaños (incluidos
  los anchos máximos de contenido/formulario/mensaje), alphas y scrims.
  `PenguTheme` proporciona la paleta por `LocalBrandPalette`;
  `Modifier.focusRing` (`components/Interaction.kt`) dibuja el anillo de foco
  compartido.
- **`runCatchingCancellable`** (`core/util`) — `runCatching` que nunca se traga la
  cancelación de corrutinas.

---

## 5. Ideas de Kotlin / Compose Multiplatform que usa el código

Léelas una vez y el código deja de parecer mágico.

**`expect` / `actual`** — declara una API de plataforma en `commonMain` e
impleméntala por target. Ejemplos: `PlatformBackHandler`, `PlayerWindowMode`,
`VideoSurface`, `createHttpClient`, `FilePicker`. Cuando añadas comportamiento
específico de
plataforma, añade el `expect` en `commonMain` y los `actual` en `jvmMain` /
`androidMain`.

**`StateFlow` + `ViewModel`** — un ViewModel guarda un `data class …UiState`
inmutable y expone `val state: StateFlow<…>`. Las pantallas lo recogen con
`collectAsStateWithLifecycle()`. Nunca mutes el estado desde un composable;
lanza una corrutina en `viewModelScope`.

**Estado de UI inmutable** — siempre `state.update { it.copy(...) }`, nunca
mutación parcial. Por eso `HomeUiState`, `LibraryUiState`, etc. son `data class`.

**Interfaces sealed para extensibilidad** — `SignInMethod`, `SignInRequest`,
`ContentRoute`. Añadir un caso obliga a cada `when` a manejarlo (el compilador te
ayuda).

**`CompositionLocal`** — valores de toda la app provistos en la raíz:
`LocalAppServices`, `LocalWindowSize`, `LocalMotionEnabled`, `LocalSnackbarHost`.
Úsalo para valores *transversales*, no para pasar datos entre dos composables
(prefiere parámetros).

**Recursos de Compose** — los textos y fuentes salen de
`composeResources/values/strings.xml` vía `stringResource(Res.string.…)`. No
hardcodees texto visible; rompe la localización. El `Res` generado es `internal`
de `:app:shared`, así que un composition root (otro módulo) no puede leerlo;
mantén esos literales en local (ver `WINDOW_TITLE` en `Main.kt`).

**El orden de los `Modifier` importa** — `Modifier.padding().background()` no es
lo mismo que `background().padding()`. El código sigue "clip → background →
click → padding" para superficies personalizadas (ver
`components/Interaction.kt`).

**Reordenar arrastrando** — Foundation no tiene una API de `Column`
reordenable, así que `ReorderableCardColumn` la construye con `pointerInput` +
`detectDragGestures` en el handle, `onGloballyPositioned` para medir los centros
de las cards, `targetIndex()` para elegir el hueco y `animateFloatAsState` para
abrirlo. Solo el handle inicia el drag; la card queda inerte para que los
controles internos y el scroll de la página conserven sus gestos.

**Lecturas en fase de dibujo** — `motionLayer`/`motionBorder` leen valores
animados dentro de lambdas de `graphicsLayer` / `drawWithContent`, así las
animaciones no recomponen por frame. Pasa `{ state.value }`, no `state.value`.

**`remember` / `derivedStateOf` / `rememberUpdatedState`** — `derivedStateOf`
para valores calculados a partir de otro estado (p. ej. "cargar más" en
`CatalogScreen`); `rememberUpdatedState` cuando una lambda capturada en
`remember` necesita el valor más reciente (usado en `DiscoverFeed`). Capturar un
valor que cambia dentro de `remember` es el bug clásico de Compose.

**Cancelación** — usa `runCatchingCancellable` (en `core/util`) en lugar de
`runCatching` dentro de corrutinas, para que una pantalla cancelada no convierta
la cancelación en un error falso.

**Source sets KMP** — `commonMain` es compartido; `jvmMain` y `androidMain`
guardan código de plataforma. El código solo de desktop vive en
`app/desktopApp` (un módulo JVM), no en `app/shared/jvmMain`, salvo que sea UI.
`data/jvmSharedMain` es compartido por los targets JVM y Android: el dispatcher
de E/S, el idioma del sistema, los helpers de ficheros, el almacenamiento en
disco de la caché HTTP (el `FileStorage` de Ktor sobre `kotlinx.io`) y el
decodificador gzip viven ahí una sola vez.

---

## 6. Convenciones

- **Formato/lint**: `.editorconfig` (4 espacios, LF) + `ktlint`. Ejecuta
  `./gradlew ktlintCheck` (o `ktlintFormat`). La CI falla con lint.
- **Nombres**: las funciones Compose son `PascalCase`; el resto `camelCase`.
- **Comentarios**: explican el *porqué*, no el *qué*. El código ya tiene buenos
  ejemplos — iguala ese tono.
- **Regla de documentación** (`AGENTS.md`): todo commit que cambie comportamiento
  debe actualizar `docs/architecture-decisions.md` en el mismo commit.
- **Sin `TODO`/`FIXME`** — ahora mismo no hay ninguno; manténlo así.

---

## 7. Testing

Los tests reflejan el módulo que prueban:

- `core/src/commonTest` — reglas puras (`WatchedRule`, `PlaybackTarget`,
  `Cancellable`, modelos).
- `data/src/commonTest` — parsers (`StremioParsersTest`), mappers, escapado de
  PostgREST, el decorador del almacenamiento de caché y el stack Nuvio contra un
  cliente HTTP falso (`NuvioTestStack.kt`). `data/src/jvmTest` cubre los stores de
  fichero y la caché HTTP en disco de extremo a extremo (`DiskHttpCacheTest`:
  gzip, un acierto de caché y la vida de respaldo, contra un `MockEngine`).
- `app/shared/src/commonTest` — ViewModels contra fakes en
  `ui/support/Fakes.kt`.
- `app/desktopApp/src/test` — el decodificador de imágenes.

Ejecútalos:

```bash
./gradlew :core:jvmTest :data:jvmTest :app:shared:jvmTest :app:desktopApp:test
```

Al añadir un ViewModel, añade un `Fake<Repo>` en `ui/support/Fakes.kt` si no
existe, y contrólalo con `StandardTestDispatcher` + `advanceUntilIdle()` (ver
`DiscoverViewModelTest`).

---

## 8. Recetas de extensión

**Añadir un backend**
1. Implementa las interfaces de repositorio de `core` bajo `data/.../backend` o
   un paquete nuevo.
2. Añade un valor a `BackendKind` y una rama a `createBackend` en
   `BackendFactory.kt`.
3. Conéctalo en los composition roots. Sin cambios en la UI.

**Añadir un recurso de addon (p. ej. un recurso Stremio nuevo)**
1. Añade el modelo en `core/.../model`.
2. Parsea en `data/.../addons/StremioParsers.kt`.
3. Pídelo en `StremioAddonClient` y expónlo a través de un repositorio de `core`.

**Añadir un método de inicio de sesión**
1. Añade un subtipo a `SignInMethod` y `SignInRequest` (sealed) en
   `core/.../repository/AccountRepository.kt`.
2. Manéjalo en la implementación de `AccountRepository` del backend.
3. Renderiza un formulario donde se lee `signInMethods` (`OnboardingScreen`,
   `SettingsScreen`). El compilador señalará cada `when`.

**Añadir un motor de reproducción**
1. Implementa `core/.../player/PlayerService.kt`.
2. Conéctalo en el composition root (`Main.kt` en desktop, `MainActivity.kt` en
   Android) y devuelve la superficie del motor por `videoOutput`. La UI ya lee
   `canPlay` para desactivar la reproducción en targets sin motor.

**Añadir un ajuste**
1. Añade un campo a `AppSettings` (`core/.../repository/SettingsRepository.kt`).
2. Añade el getter/setter a `SettingsRepository` y la clave en
   `JsonSettingsRepository`.
3. Añade el control a la página correspondiente en `SettingsScreen.kt`;
   `SettingsCategory` elige la página. Usa `SettingsChipRow` para un conjunto
   corto de opciones y `SettingsSelectRow` para uno largo. El fichero JSON sigue
   siendo editable a mano.

**Cambiar la paleta**
1. Edita `CoffeePalette` (`ui/theme/Tokens.kt`). Cualquier otra superficie lee
   `LocalBrandPalette` o un rol del esquema, así que se repinta sin tocar la UI.
   No hay ajuste de tema: la app trae una sola paleta.
2. Si el cambio necesita roles de Material que la paleta no lleva (los
   contenedores de error, la familia secondary), súbelos antes a `BrandPalette`,
   para que el esquema siga siendo el único mapeo.

**Añadir una sección de datos**
1. Crea `ui/<feature>/<Feature>ViewModel.kt`, extendiendo `AccountScopedViewModel`.
2. Crea `<Feature>Screen.kt` que recoja su estado.
3. Añade la sección a `AppSection` y renderízala desde `SectionHost`.

---

## 9. Bordes peligrosos conocidos (donde tener cuidado)

- **Archivos de UI grandes**: `DetailScreen.kt` y `PlayerScreen.kt` siguen
  siendo grandes incluso tras los splits. Divídelos más cuando los toques. El
  billboard de Home está repartido entre `Hero.kt` (puntos de entrada y pager),
  `HeroLayers.kt` (artwork, washes, copy) y `HeroOverlays.kt` (swipe, paginador,
  preloader), y "Continue watching" se movió a `ContinueWatching.kt`.
- **`AppServices` es un service locator** al que se llega vía `LocalAppServices`.
  Los ViewModels reciben dependencias explícitas (bien); las pantallas leen el
  global. En tests, prefiere construir el ViewModel directamente.
- **`ktlintFormat` en Windows** puede tropezar con las fuentes generadas de
  Compose; usa `ktlintCheck`, que las excluye (el filtro está configurado en el
  `build.gradle.kts` raíz).
