# Decisiones de arquitectura

Este documento recoge las decisiones tomadas al migrar la app original de
Flutter (solo como referencia; ya se ha eliminado) a Compose Multiplatform.
Desktop (Windows) y Android están completamente cableados contra el mismo código
compartido; cada composition root elige su propio motor de reproducción.

## Objetivo

Mantener el comportamiento del producto y el capas de la app de Flutter, pero
expresarlo con las buenas prácticas de Kotlin Multiplatform: dominio puro,
adaptadores de datos intercambiables, un reproductor aislado y una UI que nunca
habla directamente con HTTP, el sistema de ficheros ni el motor de reproducción.

## Módulos y dirección de dependencias

```
:core      pure domain (models, repository contracts, watched rules, player contract)
:data      implementations (Stremio addons, Nuvio/Supabase, local JSON store)
:app:player  adaptador compartido de PlayerService sobre el contrato de mediamp
:app:shared  Compose UI + ViewModels (depends on :core and :app:player)
:app:desktopApp  composition root: reproductor mpv (mediamp), control de ventana, main()
:app:androidApp  composition root: reproductor ExoPlayer (mediamp), MainActivity
```

`ui -> domain` y `data -> domain` (inversión de dependencias): la capa de datos
implementa los contratos declarados en `:core`, y los composition roots son los
únicos que referencian `:data`. Cambiar un backend o el reproductor cambia un
módulo y el composition root, nunca la UI.

`:core` y `:data` son Kotlin Multiplatform (`commonMain` + `jvmMain` +
`androidMain`). El código de dominio y datos vive en `commonMain`; solo el motor
HTTP, el sistema de ficheros y el reproductor tocan source sets de plataforma a
través de `expect`/`actual` (`data/platform/Platform.kt`). Los helpers de
ficheros de JVM y Android comparten un source set `jvmSharedMain`
(`data/platform/PlatformFileIO.kt`), así que la lógica atómica de
leer/escribir/borrar vive una sola vez.

## Dominio (`:core`)

- **Los modelos son data classes planas**, sin mapeo de columnas de base de
  datos ni parsing de JSON. El parsing de filas vive en `:data`
  (`NuvioMappers.kt`), así que los modelos los puede producir cualquier backend.
- **Un repositorio por capacidad**, reflejando el refactor de Flutter:
  `AccountRepository`, `LibraryRepository`, `ProgressRepository`,
  `AddonRepository`, más los contratos de addons (`CatalogRepository`,
  `MetadataRepository`, `StreamRepository`, `SubtitleRepository`).
- **Observabilidad con corrutinas.** Donde Flutter usaba `Listenable`, los
  contratos de Kotlin exponen `StateFlow`. `AccountRepository` expone un único
  `StateFlow<AccountState>` (sesión, perfiles, perfil activo, carga y error);
  `LibraryRepository`/`ProgressRepository` exponen un contador de cambios
  `StateFlow<Long>` que las pantallas recogen para recargar. Esto elimina por
  completo el parche `SessionController`.
- **Los perfiles son opcionales** (`AccountState.requiresProfile`) y obligatorios
  cuando se requieren: no hay perfil por defecto, porque una escritura guardada
  en el perfil equivocado son datos incorrectos, no datos que faltan.
- **La lógica de "visto" es pura** (`watched/WatchedRule.kt`) y el resolver
  (`watched/WatchedBadges.kt`) mantiene el comportamiento original de
  concurrencia acotada, caché y segundo plano.
- **El inicio de sesión es una petición, no una firma.**
  `AccountRepository.signIn` recibe un `SignInRequest` (sealed; hoy solo
  `EmailPassword`) y el backend declara lo que ofrece mediante `signInMethods`,
  así que la UI de inicio de sesión renderiza el formulario correspondiente en
  vez de asumir email + contraseña. Añadir OAuth o un magic link significa un
  subtipo nuevo, no un cambio de contrato; un flujo de varios pasos
  (emparejamiento de dispositivo) aún necesitaría un objeto de sesión por encima.
- **El vocabulario compartido vive con los modelos.** `ContentTypes`,
  `CatalogExtras`, `ProgressThresholds` y `releaseYear()` nombran las cadenas de
  protocolo y los umbrales que comparan el dominio, los datos y la UI, para que
  una errata o un literal que se desvía no pueda cambiar el comportamiento.
  `ContentType`, `Season` y `Episode` son value classes sobre el token de
  protocolo y los dos números, de modo que un tipo de contenido no se puede
  pasar donde se espera otro `String`, ni un episodio donde va una temporada,
  sin que el compilador lo note.
- **`PlayerService` es un contrato**, con un `PlayerState` neutro y un handle
  `videoOutput: VideoOutput?` para que la UI pueda incrustar la superficie nativa
  sin depender del motor. `VideoOutput` es un marcador vacío; el
  `VideoSurfaceRenderer` compartido lo extiende y dibuja el vídeo como contenido
  Compose.

## Datos (`:data`)

- **Cliente Ktor** (CIO para backend/TMDB, OkHttp para addons en ambas
  plataformas) en lugar de `package:http`.
- **kotlinx.serialization** con la API `JsonElement` y pequeños helpers
  defensivos de coerción (`JsonUtils.kt`), reflejando `json_utils.dart`: los
  payloads de terceros degradan a `null`/vacío en lugar de lanzar.
- **Los clientes de addons se agrupan.** `AddonClientPool` guarda un
  `StremioAddonClient` cacheado por URL de addon, y los composition roots
  construyen los cuatro repositorios de Stremio a través de
  `StremioAddonRepositories`, que les entrega el mismo pool. Así un manifiesto se
  pide una vez por proceso, no una vez por repositorio, incluso cuando las filas
  de catálogo y las búsquedas de metadata golpean los mismos addons seguidos.
  Además, un cliente une un GET concurrente de la misma URL, así dos pantallas que
  piden el mismo catálogo comparten una sola vuelta de red.
- **Stremio**: `StremioAddonClient` más los cuatro repositorios. Mismas reglas
  que el original: la metadata es "el primero que responde gana" entre addons,
  streams/subtítulos se agregan en paralelo, y los valores `extra` de los
  catálogos se codifican manteniendo `=`/`&` sin codificar. La fila "similar"
  elige un catálogo que pueda filtrar por género y salta los catálogos cuyos
  extras obligatorios no se pueden aportar (un término de búsqueda, una lista de
  ids), y luego cae al catálogo genérico, para no dejar un título sin la fila.
- **Nuvio/Supabase**: HTTP crudo dividido por responsabilidad, en lugar de una
  única clase cliente: `NuvioTransport` (verbos y URL base), `NuvioDiscovery`
  (`/.well-known/nuvio` y la clave publicable), `NuvioAuth` (sesión, inicio de
  sesión, refresco, cierre) y `NuvioRest` (select/insert/update/delete de PostgREST y
  las RPC `sync_*`). Los repositorios toman la parte que necesitan. Los tokens de
  sesión viven en `data/`, nunca en el dominio.
- **Almacén local**: `JsonFileStore`/`JsonMapStore` y los stores de sesión /
  lista de addons / series vistas. La resolución de rutas es un `expect` de
  plataforma (`%APPDATA%\Pengu` en Windows, XDG en el resto). Las escrituras
  pasan por un fichero temporal hermano y se renombran en su sitio, así que una
  escritura interrumpida no puede dejar un documento truncado, y una escritura
  fallida se reporta en vez de tragarse. Los ciclos de leer-modificar-escribir de
  los repositorios locales se serializan con un `Mutex`. El fichero de sesión se
  restringe al usuario actual donde el SO lo permite; es endurecimiento
  best-effort, no un keychain.
- **Los ajustes** viven en `settings.json` como un objeto JSON plano con claves
  punteadas, como el `settings.json` de VS Code (`appearance.typeface`, `animations.enabled`,
  `window.rememberSize`, `window.width`, `window.height`, `window.maximized`,
  `player.resumePlayback`, `player.autoPlayNext`, `player.streamUrlCache`, `poster.size`,
  `labels.hidden`, `catalog.showType`, `metadata.tmdbApiKey`,
  `metadata.tmdbLanguage`).
  `JsonSettingsRepository` lo lee de
  forma defensiva, conserva las claves desconocidas al escribir y puede
  `reload()` tras una edición manual; la pantalla de ajustes agrupa los toggles
  en categorías (`SettingsCategory`, en `ui/screens`), edita el mismo fichero,
  muestra su ruta y puede abrirlo en el editor del SO. Las páginas se agrupan en
  cuatro bloques, como una app de ajustes de escritorio: **Cuenta**, **General**,
  **Acerca de** y **Avanzado**. General contiene Playback, Apariencia (tipografía,
  movimiento y el tamaño de ventana del escritorio), Home (billboard, tamaño de póster,
  etiquetas de fila),
  Addons y Metadata; Acerca de contiene la página de producto, licencia y
  créditos; Avanzado contiene el fichero de configuración, las acciones de caché
  y la prueba de
  reproducción. Una página nunca mezcla dos asuntos sin relación, y los ajustes de
  aspecto comparten la página Apariencia. Una ventana ancha pinta los grupos en una barra lateral junto a la
  página seleccionada; una ventana compacta pinta los mismos grupos como índice y
  apila cada página como `ContentRoute.SettingsPage`, así que un móvil entra en una
  categoría cada vez mientras cada ajuste se define una sola vez.
- **La ventana de escritorio recuerda su geometría.** `window.width`,
  `window.height` y `window.maximized` se escriben al cerrar (y solo si
  `window.rememberSize` está activo) y se leen antes de que arranque la
  composición, porque la ventana existe antes de que corra la carga de ajustes de
  la app. El tamaño guardado es el último **flotante**: una ventana maximizada
  reporta sus límites maximizados, así que des-maximizar tiene que volver al
  tamaño que eligió el usuario, no al de la pantalla. No se guarda nada hasta que
  se ha cerrado una ventana, así que una instalación nueva sigue abriendo con el
  tamaño por defecto de la app.
- **En Avanzado viven dos acciones de caché.** "Clear image cache" vacía las
  cachés de memoria y disco de Coil (`clearImageCache`), así que cada póster,
  fondo y logo se vuelve a pedir en el siguiente pintado. "Clear continue
  watching cache" borra los resultados de "serie vista" recordados para el perfil
  activo y marca `PlaybackUpdates.cachesCleared`, lo que hace que Home relea el
  feed — el progreso nunca se cachea, así que la fila se reconstruye con lo que
  el backend tiene ahora. Ambas son botones normales: el texto de la subsección
  dice qué se borra, y un fallo acaba en la línea de error de la página como
  cualquier otra escritura.
- **Los controles de ajustes son componentes de la app, no Material de serie.**
  Una fila de toggle es ella misma el target y su switch es el `AppSwitch`
  compacto compartido (40x22, track de acento), no el control de 52x32 de
  Material; dibuja track y thumb en un único `Canvas`, así mover una card de
  addon no puede dejar una capa obsoleta del switch; un conjunto corto de opciones son chips, uno largo abre un popover en desktop y
  una bottom sheet en táctil (`isDesktop`, en `ui/platform`); los divisores se
  insetan al texto de la fila; y un grupo es una tarjeta sin borde sobre el
  fondo de página, rellena con el rol `surfaceCard` para que el índice y las
  páginas compartan un mismo tono cálido. Las categorías son una pila de esas
  tarjetas (`SettingsNavCard`) en vez de filas con ícono: tanto el índice
  compacto como la barra lateral leen los mismos cuatro grupos; el compacto
  muestra la etiqueta del grupo y una tarjeta en semibold con chevron y sin
  descripción — la barra de título del móvil nombra la pantalla y las tarjetas
  llevan el resto —, mientras la barra lateral ancha quita el chevron y marca la
  página abierta con un tinte de acento sobre la tarjeta y el label
  de acento. Dentro de una página, las etiquetas de sección van en semibold y
  los títulos de fila suben también a semibold, para que la jerarquía se lea
  igual que el índice; el picker se lee como un campo (sin relleno, borde
  `outline`) para mantenerse legible sobre la tarjeta, y un chip seleccionado o
  el popover usan `surfaceSelected`. Así un ajuste se ve como la app y no como
  un formulario, mientras cada color sigue saliendo del esquema.
- **Una card de addon se parte en un móvil.** Bajo el breakpoint
  `WindowSize.COMPACT` la fila de identidad conserva el logo, el nombre, la
  versión y los tres controles, y la descripción del manifest pasa a su propia
  línea a todo el ancho, debajo. En línea junto a esa pila la descripción solo
  tenía ~100dp para envolver, así que un texto normal llegaba a una docena de
  líneas y la card crecía más que la pantalla; el ancho completo la resuelve en
  dos o tres. Esa línea se pliega luego a `AppSynopsis.MAX_LINES` y se expande al
  toque (`hasVisualOverflow`, la misma medida que usa `HeroSynopsis`), así que ni
  una descripción larga del manifest puede estirar la card. Una ventana ancha
  mantiene la descripción en línea, donde la fila tiene sitio para ella.
- **`labels.hidden`** suprime las etiquetas de título y año bajo cada
  póster (Home, rejillas de catálogo, Discover, Search, el "More like this" del
  detalle y la biblioteca) para que el artwork se lea solo; un card sin labels
  colapsa al ratio 2:3 del artwork (`POSTER_ART_ASPECT`) en vez de estirar la
  imagen para llenar el hueco liberado, así el póster conserva el tamaño base que
  define el ajuste. Las filas de Home añaden el tipo de contenido al nombre del
  catálogo con un punto (`Popular · Movies`), y `catalog.showType` lo oculta; la
  metadata del billboard no se ve afectada.
- **El enriquecimiento con TMDB es opcional y solo de presentación.** Una clave
  puesta en Ajustes (`metadata.tmdbApiKey`, con `metadata.tmdbLanguage` para el
  idioma de respuesta, el del sistema por defecto) activa un segundo proveedor de
  metadata que solo usan la pantalla de detalle y el billboard de Home, y nunca
  reemplaza un identificador (`MetaDetail.id` y `MetaVideo.id` siguen
  direccionando los streams). Dos reglas de merge, una por pantalla
  (`MetadataEnrichment.kt`): el billboard de Home *rellena huecos*, así que TMDB
  solo añade lo que el preview del catálogo dejó vacío y el billboard pinta de
  inmediato; la **pantalla de detalle da prioridad a TMDB**, porque ahí una sola
  fuente consistente vale más que el primer pintado más rápido. Allí la página
  se publica **una sola vez**, cuando ya respondieron el addon y TMDB
  (`DetailUiState.hasLoaded`), así que nada se pinta desde una fuente y se
  repinta desde otra: el hero (artwork, logo, línea de metadata, sinopsis), las
  filas de cast y crew, la tabla de detalles y las tarjetas de episodio son
  finales la primera vez que aparecen, y un teléfono pasa directo de nada al
  póster sin texto (la misma razón por la que la sinopsis ya reservaba su espacio
  en vez de enseñar el copy del preview). TMDB también
  aporta el crew — dirección y guion, leído de `credits` en una película y de
  `aggregate_credits` en una serie, que es la única forma que conserva los roles
  cuando se juntan los directores de todos los episodios —, y el addon conserva
  solo lo que direcciona el playback. El nombre localizado de TMDB también gana
  al del addon, así la página nombra el título igual que su arte y su texto. La
  nota es el único campo que hace de fallback en la otra dirección: TMDB trae la
  nota de su público, no la de IMDb, así que solo rellena cuando ningún addon
  reportó una, la misma regla que usa el cliente de Nuvio.
  `TmdbMetadataRepository`
  resuelve cualquier content id a uno de TMDB — un id `tmdb:` explícito,
  IMDb/TVDB vía `/find`, y si no una búsqueda por título y año que rechaza un
  match ambiguo — y luego pide el detalle con sus imágenes, ids externos y
  créditos. Un título que solo conoce TMDB (una recomendación) se reintenta en
  los addons con el id de IMDb que TMDB reporta, y ese id pasa a manejar sus
  streams, así que un preview `tmdb:` igual reproduce. Tanto el id resuelto como
  el payload se cachean durante el proceso.
  La respuesta del addon y el enriquecimiento se esperan juntos, así que una
  búsqueda lenta retrasa la página en vez de ser pintada por encima. La fila
  "More like
  this" se construye con las recomendaciones de TMDB (su lista similar como
  respaldo) y cae al catálogo similar del addon cuando no hay clave o no hay
  match. Sin clave el repositorio corta y ninguna petición sale de la app. La
  clave vive en `settings.json` como el resto de preferencias, así que sigue
  siendo editable a mano; se enmascara en la UI y nunca se registra, y la
  atribución de TMDB se muestra en la card.
- **Renovación de sesión**: `NuvioAuth` renueva el token antes de una petición
  cuando ha caducado, y `NuvioRest` renueva una vez más cuando el backend
  responde `401`. Las renovaciones se serializan con un mutex para que peticiones
  paralelas no refresquen dos veces. Los motores se configuran con timeouts
  finitos de conexión/petición/socket.
- **`BackendKind` + `createBackend`** es el único sitio que conoce las
  implementaciones concretas, reemplazando `backend_factory.dart` de Flutter.
- **I/O en paralelo y caché.** Las lecturas independientes se ejecutan juntas en
  lugar de en fila. Home resuelve los catálogos con una ventana deslizante de
  cuatro peticiones (una fila que responde libera su hueco de inmediato) mientras
  lee el historial de reproducción en paralelo, y publica el feed una sola vez —
  filas, "Continuar viendo" y el billboard ya enriquecido —, así que la pantalla
  pasa de su estado de carga al conjunto entero de una vez. Al volver a la sección conserva las filas
  cargadas y refresca detrás, y los manifiestos de addons se precalientan en
  cuanto hay perfil. Library carga la biblioteca y su progreso juntos; el
  resolver de badges lee las dos fuentes de progreso en paralelo y sus búsquedas
  de metadata son acotadas pero concurrentes; los chunks de `watched_items` se
  consultan juntos; los manifiestos de catálogo se piden en paralelo y se cachean
  por addon (un manifiesto es inmutable, y la caché en disco de abajo lo conserva
  entre arranques cuando el addon declara frescura). La lista de addons se cachea por uuid de perfil, así que la
  sincronización de badges no la reconsulta para cada
  serie. Los ViewModels con scope de cuenta (Home, Library, Search y su feed de
  discover) esperan a un perfil antes de cargar en vez de cargar sin
  él y fallar.
- **Las respuestas de los addons se cachean en disco.** `createAddonHttpClient()`
  (`data/platform`) instala el `HttpCache` de Ktor sobre un `FileStorage` en el
  directorio de caché de la plataforma — nunca en el de configuración, porque una
  caché es desechable y en Windows no debe acabar en un perfil móvil. Un
  manifiesto o una página de catálogo que el addon marcó como cacheable se sirve
  desde disco, y `Cache-Control: no-cache` sigue revalidando con `If-None-Match` /
  `If-Modified-Since` en lugar de servirse a ciegas. La frescura es cosa del
  addon: una respuesta que no declara nada — sin `Cache-Control`, sin `Expires` y
  sin validador, que es lo que manda buena parte del ecosistema Stremio — se
  vuelve a pedir en la siguiente lectura, así que Home siempre muestra lo que los
  addons responden ahora. El tráfico de addons es
  deliberadamente el único cacheado: `createHttpClient()`, usado por el backend
  Nuvio y TMDB, no tiene caché, porque servir un `library_items` o un
  `watch_progress` viejo le mostraría al usuario datos que acaba de cambiar. El
  cliente de addons corre sobre **OkHttp en ambas plataformas** y el del backend
  sobre CIO en desktop / OkHttp en Android; CIO es solo HTTP/1.1, así que un Home
  en frío abriría una conexión TLS por catálogo, y varios suelen vivir en el mismo
  host.
- **El JSON de catálogo se pide comprimido con gzip.** Ningún motor añade
  `Accept-Encoding`, así que `StremioAddonClient` pide `gzip` él mismo y decodifica
  el cuerpo (`gzipDecode`, implementado una vez para los targets JVM y Android).
  Una página de catálogo encoge unas cinco veces, que son la mayoría de los bytes
  que un arranque en frío descargaría de otro modo; la caché en disco sigue
  guardando el cuerpo comprimido.
- **La lista de addons se responde desde un snapshot local.**
  `NuvioAddonRepository` sirve la última lista guardada por
  `FileAddonSnapshotStore` en vez de esperar la query `addons` del perfil, luego
  la refresca en segundo plano y anuncia un cambio real por
  `AddonRepository.changes`, que Home escucha antes de recargar sus filas. La
  lista estaba antes delante de cada petición de catálogo — sesión, perfil, query
  de `addons`, manifiesto, catálogo — así que esto quita un round-trip del camino
  frío; un dispositivo sin red sigue funcionando desde el snapshot. El snapshot
  guarda todos los addons instalados, activos o no; `all()` filtra los
  desactivados para el camino de contenido.
- **Los addons se administran desde Ajustes.** `AddonRepository` expone
  `installed()`, `install`, `setEnabled`, `remove`, `reorder` y `canManage`
  junto a la lectura de contenido. `NuvioAddonRepository` valida la URL pegada
  leyendo su manifiesto con el cliente de addons cacheado antes de escribir,
  guarda la URL normalizada del manifiesto y su nombre, y acota cada escritura
  por `profile_id` (`NuvioRest.update` envía `PATCH` de PostgREST; `remove`
  envía `DELETE`). Un enlace de instalación `stremio://` se normaliza a
  `https://` antes de esa comprobación. Cada escritura vuelve a leer la lista,
  actualiza el snapshot y emite `changes`, así que Home recarga sus catálogos
  cuando un addon se activa, desactiva, reordena o elimina. La página Ajustes →
  Addons abre el formulario de instalación en un diálogo y muestra los addons
  como cards: logo, nombre, versión, descripción completa del manifiesto, switch
  de activación, icono de eliminar y handle de arrastre. Solo el handle inicia el
  drag, así que la card es inerte (un clic no hace nada) y el switch o la acción
  de eliminar conservan su propio objetivo. `ReorderableCardColumn`
  (`ui/components`) posee el gesto y el hueco animado; la página persiste el
  nuevo orden una sola vez, al soltar. Move up/down siguen existiendo como
  acciones de accesibilidad en la card, no como botones visibles. Cada card
  muestra el logo y la descripción del manifiesto, con la URL solo como respaldo
  cuando el manifiesto no se puede leer. Un backend que no puede escribir (modo
  local) lista su conjunto fijo en solo lectura y oculta la acción de instalar y
  los controles de la card.
- **Home se carga, nunca se restaura.** Nada del feed se persiste: las filas y
  el billboard viven en el `HomeViewModel` durante la sesión, así que un arranque
  en frío (o un cambio de perfil) muestra la pantalla de carga hasta que todos los
  catálogos han respondido y entonces pinta el feed entero de una vez. No hay un
  pintado guardado que pueda quedar viejo ni reemplazarse bajo el usuario, y un
  catálogo editado en el addon aparece en la siguiente carga en vez de desde una
  copia guardada. Ese primer pintado es la pantalla entera: las filas,
  "Continuar viendo" (su lectura del backend corre junto a los catálogos) y el
  billboard ya enriquecido con el artwork de TMDB, así que nada se rellena después
  de que el usuario esté mirando. Lo único que sigue llegando más tarde es la
  propia imagen del billboard, así que la pantalla mantiene su overlay de carga
  hasta que el primer slide está decodificado (con un timeout corto, y con el feed
  compuesto detrás para que sus imágenes ya estén cargando): el hero nunca muestra
  el fondo pelado y luego se llena. Volver a Home dentro de `HOME_REFRESH_INTERVAL` reutiliza lo que
  está en pantalla; pasado ese tiempo refresca detrás de las filas cargadas,
  reemplazándolas una sola vez al final. Un perfil distinto es un Home distinto:
  elegirlo descarta las filas anteriores en vez de refrescar encima de ellas. El
  set del billboard se elige del conjunto completo y se mantiene durante la
  sesión (los slides siguen auto-avanzando); solo cambiar la fuente del billboard
  lo vuelve a elegir.
- **La fuente del billboard es un ajuste.** `hero.source` es `smart` (el
  defecto: todas las filas cargadas, mezcladas y ordenadas por la app) o
  `manual`, y `hero.catalogs` guarda hasta dos `CatalogRef.key` elegidos en
  Ajustes → Home billboard. Manual acota el pool del que se elige y cae a todas
  las filas cuando ninguno de los catálogos elegidos está cargado, así que un
  addon eliminado en otro sitio no puede dejar Home sin billboard; cada catálogo
  sigue teniendo su fila en cualquier caso. Home escucha el ajuste
  (`distinctUntilChanged` sobre los dos campos), así que cambiarlo vuelve a
  elegir al instante en vez de esperar a la siguiente carga. La lista de
  catálogos del selector se lee de los addons en la pantalla de ajustes, no se
  guarda: solo se persisten las claves, lo que mantiene `settings.json`
  editable a mano y sin nombres obsoletos.
- **Ambas plataformas resuelven IPv4 primero y evitan el proxy del sistema.**
  `IPv4FirstDns` (`jvmSharedMain`) ordena las respuestas DNS, así que un host con
  un registro AAAA roto no bloquea cada conexión hasta que IPv6 caduque;
  `Proxy.NO_PROXY` evita que un proxy que solo conoce una red corporativa haga
  fallar cada petición a un addon público. Vive junto a los clientes de addons,
  que corren sobre OkHttp, así que desktop y Android comparten el resolver.

## UI (`:app:shared`)

- **Sin framework de DI.** `AppServices` es un contenedor de datos provisto
  mediante `LocalAppServices`; los composition roots lo construyen. Es el
  equivalente directo del `InheritedWidget` `AppServices` de Flutter.
- **ViewModel + `StateFlow`.** Cada sección/ruta tiene un `ViewModel` que posee
  su estado y lanza trabajo en `viewModelScope`. La UI recoge con
  `collectAsStateWithLifecycle()`, así que la colección se detiene cuando una
  pantalla sale del ciclo de vida.
- **Comportamiento de pantalla compartido.** Las secciones de datos extienden
  `AccountScopedViewModel` (`ui/common`), que posee la regla "carga cuando hay
  sesión y un perfil elegido, limpia al cerrar sesión", y usan `WatchedBadgeSync`
  para la búsqueda de badges, así que ninguno se reimplementa por pantalla. Su
  observador de cuenta arranca en `Dispatchers.Main`, no en `Main.immediate`:
  este último se ejecuta inline cuando el ViewModel se construye en el hilo
  principal, lo que disparaba el hook de carga desde el constructor de la
  superclase antes de que existiera el estado de la subclase. Forzar un dispatch
  hace que la primera emisión espere a que termine la construcción.
- **Los tokens de diseño son datos, no código.** `theme/Tokens.kt` contiene las
  paletas (`BrandPalette` / `CoffeePalette`), el espaciado, los tamaños, los alphas y
  los scrims; `theme/AppTheme.kt`
  contiene solo el esquema de color de Material, la tipografía y las formas.
  `AppSizes` posee los anchos de contenido/formulario/mensaje y los tamaños de
  borde/progreso, `AppAlpha` posee toda la opacidad de overlays, y
  `Modifier.focusRing` (en `Interaction.kt`) da a cada control enfocable el mismo
  anillo de teclado.
- **Los iconos se vendorizan, no son una dependencia.** `ui/icons/MaterialIcons.kt`
  contiene los glifos de Material Symbols Sharp (Apache-2.0, Google) que usa toda
  la UI como `ImageVector`s, cada uno construido de forma perezosa desde el path
  SVG oficial mediante `PathParser`; `scripts/gen_icons.mjs` regenera el fichero
  desde el paquete `@material-symbols/svg-400`. Sharp es el estilo que
  rectifica las esquinas, y encaja mejor con el lenguaje de iconos plano y de
  solo tinte de la app que un set más redondeado. Los glifos viven en un grid de
  960x960 cuyo eje y va de -960 a 0, así que cada vector se construye dentro de
  un grupo trasladado +960 mientras los sitios de uso lo siguen dimensionando a
  24dp. La flecha de volver y los
  glifos de inicio/cierre de sesión llevan `autoMirror = true`, que es lo que
  hacían las variantes auto-espejadas de Material. Los botones de caption de
  Windows conservan sus glifos nativos (`WindowsCaptionIcons` en
  `:app:desktopApp`), para que la barra se lea como chrome de Windows. La app no
  depende en absoluto de `materialIconsExtended`, así que solo se empaquetan los
  glifos usados; son trazos de un solo color, así que el estado seleccionado es
  un tinte, no un cambio de relleno. La escala
  `AppIconSize` de `theme/Tokens.kt` es la única fuente del tamaño de icono:
  `md` (24dp) es el default de Material 3 para campos y listas,
  `sm` (20dp) es para glifos compactos y dentro de botones, `nav` (28dp,
  compartido con `lg`) es el rail de navegación y la barra inferior, cuyos items
  solo-iconos necesitan más peso, y `lg` (28dp) es para controles prominentes
  sobre artwork. Todos los sitios de uso dimensionan cada icono mediante esos
  tokens, para que el rail y la barra inferior queden en el mismo tamaño óptico.
- **Ciclo de vida de Compose.** `PenguApp` reutiliza el `ViewModelStoreOwner`
  del host cuando existe — una `Activity` de Android, para que los ViewModels de
  sección sobrevivan a un cambio de configuración — y cae a un owner con scope
  de composición donde no lo hay, como desktop. Los ViewModels de sección viven
  en él y sobreviven a los cambios de sección (reemplazando la retención de
  estado del `IndexedStack` de Flutter); la sección seleccionada y la pila de
  rutas viven en un `ShellViewModel` por la misma razón, y `PlatformBackHandler`
  (Android) desapila con el back del sistema. Las rutas apiladas tienen su propio
  `ViewModelStoreOwner` (`ContentArea`) que se limpia al desapilar, así que los
  ViewModels transitorios no se filtran.
- **Onboarding y arranque de sesión.** `PenguApp` resuelve la sesión
  persistida una vez, antes que nada: muestra un splash hasta que termina, luego
  el onboarding (sin sesión) o el shell, para que un usuario recurrente nunca vea
  el inicio de sesión durante un frame. Es una puerta dura, así que las secciones
  ya no llevan un estado "sin sesión": cerrar sesión vuelve al onboarding. El
  formulario se comparte con la tarjeta de cuenta de Ajustes y se guía por
  `signInMethods`, así que un backend que inicia sesión de otra forma renderiza
  otro formulario. El atajo de desktop `NUVIO_EMAIL` / `NUVIO_PASSWORD` se pasa
  como petición `autoSignIn`, aplicada solo cuando no hay sesión almacenada.
- **Layout adaptable.** `PenguApp` mide la ventana una vez con
  `BoxWithConstraints` y publica un `WindowSize` (compact / expanded, en
  el breakpoint de Material de 600 dp) más el ancho de ventana, a través
  de `LocalWindowSize` / `LocalWindowWidth`, para que una superficie elija un
  layout sin medir nada por sí misma. Las ventanas compactas (teléfonos) reciben
  siempre una barra inferior solo-iconos que flota sobre el contenido; las
  ventanas más anchas reciben siempre un rail de navegación izquierdo, cuyo
  ancho reserva el contenido.
  Las filas de pósters reducen su ancho de tarjeta en ventanas compactas (la
  elección default / large del usuario aplica ahí) y las rejillas mantienen un
  layout fijo de tres por fila en el teléfono, el
  hero de Home cambia el billboard ancho por el recorte del póster sin el logo
  del título, el billboard de detalle se limita en ventana expandida para que el
  contenido quede cerca, y el selector de streams se ajusta a la ventana de la app
  cuando abre como diálogo, porque abre la suya, mientras un teléfono lo recibe
  como bottom sheet. El fondo del detalle solo oscurece el artwork, sin blur:
  un blur a pantalla completa re-renderiza toda la pantalla en cada frame de
  scroll y se ignora por debajo de Android 12, así que el efecto ligado al scroll
  es un scrim plano. El billboard ancho de Home toma su altura del área de
  trabajo de la pantalla (`HOME_HERO_SCREEN_FRACTION`, 55% de ella), no de la
  ventana, así que conserva la altura que tiene en una ventana maximizada:
  redimensionar en cualquier eje solo revela más o menos artwork, y las
  secciones de abajo se quedan en su sitio. La primera sección se sube con
  `HOME_CONTENT_LIFT` (32dp) para que quede cerca del fondo difuminado del
  billboard en vez de a una cabecera entera por debajo; el lift es un modificador
  de layout que solapa el hero y reporta la altura reducida, así que el alcance
  del scroll sigue bien, y se mantiene libre del paginador y del copy en la parte
  baja del billboard. Las cabeceras de sección mantienen también
  una altura fija: el título la fija y el
  icono "ver todo" de la derecha se centra en ella y desborda hacia el padding,
  así que el hueco con la fila de abajo es el mismo con y sin botón. Un catálogo
  de Home añade su tipo al título (`Popular · Movies`) en vez de llevar un
  subtítulo aparte. Las ventanas compactas también usan un padding de pantalla
  más ajustado
  (16dp) y un gap de rejilla menor, y las acciones de detalle son solo-icono (con
  `contentDescription`) a la altura del call-to-action, para que la fila quede
  alineada.
- **El hero de Home toma de todos los catálogos.** `pickFeatured` (en
  `HomeViewModel`) arma el pool con los ítems de todas las filas, deduplica por
  `type:id`, balancea películas y series y baraja, así que el billboard no queda
  atado al primer catálogo. El set destacado se elige del conjunto completo de
  filas y se enriquece desde TMDB antes del pintado, con la pantalla de carga
  todavía arriba, así que el hero aparece una vez y no se cambia después. Los títulos bien calificados (IMDb >= 6, desconocido pasa) van
  primero y los de baja calificación solo se usan cuando no alcanza el buen
  contenido. Los seis slides auto-avanzan cada `HERO_AUTO_ADVANCE_INTERVAL`
  (7 s, dentro del rango usual de 5-8 s para carruseles), y un cambio manual
  pausa esa rotación durante `HERO_MANUAL_COOLDOWN` (60 s): el watcher se apoya en
  `PagerState.isScrollInProgress` y marca todo scroll que el carrusel no inició
  él mismo, así cubre tanto un drag en teléfono como un swipe de rueda / trackpad
  en desktop, y el timer cede ante el usuario hasta que queda inactivo, momento en
  que el hero vuelve a moverse solo. El pager es infinito (arranca cerca de la
  mitad del rango y `page % items.size` mapea una página de vuelta a su ítem), así
  el auto-avance siempre va hacia adelante: el último slide no recorre todas las
  páginas hacia atrás para llegar a la primera. El avance hace snap con un
  spring (`motionSpring`) y respeta `LocalMotionEnabled`, así que "desactivar
  animaciones" salta al objetivo como el resto de transiciones. El set se mantiene
  durante la sesión: solo cambiar la fuente del billboard en Ajustes lo vuelve a
  elegir, priorizando los ítems que no están en pantalla, así no se reordena
  mientras se está mirando. La selección usa un `Random`
  inyectable, así los tests son deterministas.
- **Home virtualiza sus secciones.** El hero, "Continuar viendo" y cada fila de
  catálogo viven en un único `LazyColumn` (cada rail es su propio `LazyRow`), así
  un teléfono con muchos addons compone solo las secciones en pantalla en vez de
  todos los rails a la vez. La primera sección se sube sobre el difuminado del
  hero desplazando ese único ítem (`pullUp`), lo que sube con él todas las
  siguientes.
- **Los billboards comparten una gramática de copy.** El billboard de Home y el
  hero de detalle renderizan título, metadata, sinopsis y géneros a través de los
  mismos composables (`MediaTitle`, `MetadataRow`, `HeroSynopsis`, `HeroGenres` y
  el builder `metadataParts`, todos en `ui/components/MediaHero.kt`), así que las
  dos pantallas no pueden desviarse en tipo, color, separadores ni medida: la
  sinopsis se limita a `AppSynopsis.maxWidth` dentro de `HeroSynopsis`, y
  `AppType.heroBody` la dimensiona y pondera (17sp / 15sp en teléfono, Normal) en
  ambos. Solo difiere el orden por pantalla: el billboard de Home lee título,
  metadata, sinopsis y luego acciones, mientras que el de detalle mantiene las
  acciones por encima de la sinopsis. El billboard de detalle pasa
  `expandable`, así que una sinopsis que desborda sus tres líneas se expande al
  texto completo con un toque y se colapsa con otro; el de Home mantiene el
  teaser fijo. También difieren donde difiere el
  contenido: el billboard de Home integra el tipo de contenido, el año de
  estreno ya parseado y solo el primer género en la línea de metadata
  (`Movie • año • primer género • ★ rating`)
  mientras que el hero de detalle mantiene ahí la
  duración y muestra los géneros bajo la sinopsis; y el copy de Home usa un ritmo
  vertical más ajustado porque se apoya sobre artwork recortado. La metadata es
  un único builder, así que la estrella, los separadores, el nombre del tipo y el
  formato de la valoración no pueden divergir.
- **Los billboards comparten sus acciones de icono.** `HeroActionRow` y
  `HeroActionState` (`ui/components/HeroActions.kt`) renderizan los botones de
  biblioteca y visto junto al call to action, así que Home y el detalle
  no pueden desviarse en icono, tamaño, color ni estado. Ambos billboards abren
  con un call to action de play con etiqueta (`ActionButton` + `action_stream_now`),
  así que Home y detalle leen la misma gramática stream / guardar desde una
  cadena compartida: Home usa el estilo de texto transparente
  (`ActionButtonStyle.Text`) mientras detalle mantiene el `Accent` ámbar relleno.
  El hero de Home
  no lleva acción de "más info": tocar en cualquier punto abre el título mediante
  `heroTap` (`ui/home/HeroOverlays.kt`), que corre en la pasada inicial sin
  consumir, ignora un release que superó el touch slop e ignora un release que un
  hijo ya consumió, así que tocar un control nunca abre el título además. En un
  teléfono la fila pasa a ser un footer a ancho completo — el play pegado al
  borde izquierdo y el toggle de biblioteca al derecho, dentro del padding de
  pantalla — así que se lee aparte del copy; en ventana ancha las acciones se
  mantienen juntas, alineadas con el texto de arriba. El bloque de copy toma el
  inset de contenido del botón de play (`HERO_COPY_TEXT_INSET`), así que título,
  metadata y sinopsis comparten borde izquierdo con el glifo del play en vez de
  con el borde del contenedor transparente. Los spacers alrededor de
  una fila de acciones descuentan el padding vertical transparente del control
  (`HeroActionRowInset`, en `ui/components/HeroActions.kt`), la banda entre el
  touch target de 48dp y su glifo menor, así que la fila de iconos se lee tan
  cerca del texto como los gaps texto-a-texto en vez de 12dp más lejos; el hero
  de detalle lo aplica arriba y abajo de su fila, y el copy del teléfono lo
  aplica al gap metadata-acciones, donde no hay sinopsis. El copy compacto
  mantiene un margen inferior de 48dp, menor que los 100dp del ancho, para que
  el footer quede bajo, justo sobre los puntos del paginador.
  El toggle de biblioteca de Home es optimista en `HomeViewModel` y se reconcilia
  con `LibraryRepository.changes`, así que un título añadido en cualquiera de las
  dos pantallas se ve como añadido en ambas.
- **El selector de temporadas del detalle se desplaza.** Los botones de temporada
  viven en su propio `LazyRow` bajo el encabezado de episodios, con el mismo
  padding que el rail de episodios, así una serie con muchas temporadas sigue
  siendo alcanzable en vez de amontonar la cabecera. El rail de episodios vuelve
  a su primera tarjeta al cambiar de temporada, así pasar de S1 a mitad de lista
  no abre S2 en el mismo offset.
- **La tarjeta de un episodio es un still 16:9 más el texto que los addons no
  traen.** La tarjeta mide 320dp y lleva el still, el título, una línea separada
  por puntos (nota, duración, fecha de emisión) y hasta tres líneas de sinopsis
  con dos siempre reservadas, así el rail solo crece hasta la tarjeta más alta de
  la temporada y una sinopsis corta no encoge su tarjeta. Un móvil baja el ancho
  con el `COMPACT_SCALE` de los rails de pósters, así una tarjeta de episodio
  queda en proporción con los pósters de arriba y de abajo en vez de llenar la
  pantalla. Un video de Stremio no
  tiene sinopsis, nota ni duración, así que eso viene de TMDB:
  `TmdbRepository.season` pide `/tv/{id}/season/{n}`, y el selector de episodios
  solo carga la temporada abierta, cacheada por temporada en el ViewModel
  (`DetailUiState.episodeDetails`, con la temporada como clave) y por payload en
  `TmdbMetadataRepository`, así moverse entre temporadas cuesta una petición por
  cada una y los ids de episodio que abren los streams siguen siendo los del
  addon. El parser sigue leyendo `overview`/`description`, `runtime` y `rating`
  de un video cuando un addon los manda fuera de esquema; ese texto se usa
  primero porque ya está y es final, y TMDB rellena solo lo que falta. La nota es
  la del público de TMDB, no la de IMDb: los addons no publican nota por
  episodio, y a diferencia de la del hero aquí no hay a qué recurrir. Mientras la
  temporada abierta está en vuelo, el rail pinta
  tarjetas del tamaño de una real y vacías (`EpisodeCardSkeleton`, guiado por
  `DetailUiState.seasonsLoading`) en vez del still y el título del addon, que
  TMDB reemplazaría un momento después — la misma regla de un solo autor que el
  resto de la página sigue con `hasLoaded`. Las tarjetas siguen siendo pulsables:
  abrir los streams no depende de ese texto. Sin clave nadie espera, porque la
  respuesta del addon ya es la final. La tarjeta también marca el estado de
  visionado: el `WatchedBadge` compartido cuando la cuenta marcó el episodio (o
  el título entero), y la barra de `LinearProgressIndicator` de la app en el
  borde inferior del still cuando la reproducción quedó a medias.
  `episodeWatchState` (`DetailSections.kt`) construye ambas cosas desde
  `ProgressRepository`: un episodio reproducido más allá de
  `ProgressThresholds.COMPLETION` cuenta como visto, el mismo umbral que usan los
  badges de los pósters, y un episodio visto pierde su barra — una barra y un
  check dicen lo mismo dos veces. El rail lo lee por su cuenta, siguiendo el
  flujo `changes` del repositorio, porque solo este rail lo muestra y el toggle de
  visto del hero es optimista y vive en `DetailScreen`.
- **La página de detalle lista la metadata como una tabla.** La sección
  "Details" renderiza filas de etiqueta/valor — Released, Country, Status,
  Original language, Seasons, Episodes, Budget y Revenue — separadas por un
  hairline y solo para las filas que una fuente rellenó de verdad. TMDB da el
  status en inglés, así que se mapea a un string localizado; budget y revenue son
  importes enteros en dólares. El cast y el crew reutilizan los roles de caption
  del catálogo (`PosterCaption`), con el personaje atenuado como el año de un
  póster, para que una ficha de persona y un póster se lean igual.
- **Edge-to-edge e insets del sistema.** Android ejecuta la activity edge-to-edge
  (`enableEdgeToEdge`, con iconos de barra de sistema claros forzados porque la
  app siempre pinta oscuro), y el `Scaffold` ya no reserva las barras del sistema
  (`contentWindowInsets` es cero). Las pantallas colocan los insets ellas mismas
  mediante `safeTopInset` / `safeBottomInset` (`ui/theme/WindowSize.kt`,
  respaldados por `WindowInsets.statusBars` / `navigationBars`, cero en desktop)
  o los modificadores `statusBarsPadding` / `navigationBarsPadding` /
  `safeDrawingPadding`. El shell flota la barra inferior compacta sobre el
  contenido, que corre a sangre por detrás y solo reserva el espacio de las
  barras del sistema, así que la barra puede plegarse al hacer scroll sin dejar
  hueco, y una ventana ancha reserva el ancho del rail. Las barras de navegación
  llevan su propio inset; el fondo del detalle, el reproductor y las puertas de
  onboarding/perfil pintan tras las barras mientras sus controles se sientan
  debajo; y el billboard de
  Home se desplaza con el contenido (nunca se fija) en un `Column` +
  `verticalScroll` plano, no un `LazyColumn`: el reciclado lazy recompone cada
  fila (y libera el hero) al cruzar el viewport, lo que cuesta más de lo que
  ahorra, así que Home mantiene una fila por cada catálogo navegable que
  declaran los addons en vez de limitar el número (el límite está en los ítems
  por fila, no en las filas). En desktop el artwork abarca el 75% del ancho de la
  ventana, anclado a la esquina superior derecha de un
  marco de altura fija, y se recorta (`Crop`, alineado arriba); ensanchar la
  ventana revela más artwork en lugar de estirar el billboard, así que las
  secciones de abajo mantienen su sitio. Lo lavan degradados de
  `BrandPalette.background`: un fundido izquierdo que disuelve el borde izquierdo
  del artwork bajo el copy, un fundido inferior largo (transparente en la mitad
  superior, luego una rampa suave de dos tramos) que se funde con "Continue
  watching" sin costura dura, un fundido superior sutil, una viñeta en el borde
  derecho, y un `AppAlpha.HERO_IMAGE_DIM` plano mantenido sobre el artwork para
  que se lea más oscuro que la imagen cruda. En un teléfono el billboard abarca
  el ancho completo y recorta el mismo backdrop en la proporción del cover de la
  app (`POSTER_CARD_ASPECT`, unas 1.72 veces su ancho, alineada al centro) en vez
  de mostrar el póster vertical entero, y lo lava con
  el mismo dim plano y un fundido inferior que arranca más arriba que el del
  ancho, ambos fundidos en un solo gradiente, así que el artwork se oscurece en
  un único fill a pantalla completa, para que el copy más alto quede
  más metido en lo oscuro: el copy queda alineado
  a la izquierda y anclado sobre el
  footer de acciones, el título prefiere siempre el logo
  (escalado al ancho del copy por `AppLogoSize.opticalHeight`, el texto
  solo como reserva) y la sinopsis se omite, porque no hay sitio para leerla bajo
  el logo; un toque en el artwork o en el copy abre el título. En ventana ancha la columna de copy
  está anclada abajo a 100dp del borde del hero, hasta 840dp de ancho, y comparte
  el padding izquierdo de las secciones. La sinopsis es un teaser — limitada a la
  medida compartida de 720dp (`AppSynopsis.maxWidth`, unos 80 caracteres), tres
  líneas como máximo, el resto tras "More info". El mismo límite vive en
  `HeroSynopsis`, así que Home y Detail envuelven la descripción al mismo ancho.
  El paginador del carrusel es una fila de puntos iguales de 7dp, centrada en el
  borde inferior del billboard: crema para la página activa y gris para el resto.
  En un teléfono se sienta más arriba (32dp), libre de la barra de gestos. El
  pager es solo un driver de gesto y offset: sus páginas están vacías, y el
  artwork y el copy se dibujan encima como capas separadas para la página
  asentada y, mientras corre un swipe, su vecina. Mantener el alpha por primitiva
  (una capa de backdrop, las washes compartidas, una capa de copy) en vez de
  sobre una página compuesta entera saca al billboard del camino de buffer
  offscreen que tomaría un alpha de página completa, y las washes se elevan fuera
  de los slides, así que un cross-fade ya no dibuja un segundo gradiente a
  pantalla completa. La vecina visible se resuelve con `derivedStateOf` sobre
  `pagerState.currentPageOffsetFraction`, y el alpha y el deslizamiento se leen
  dentro de cada `graphicsLayer`, así que un swipe actualiza layers en vez de
  recomponer cada página compuesta por frame. El swipe hace cross-fade en vez de
  deslizar: cada capa se disuelve a través del fondo de página en su vecina. El
  copy (título, metadata, sinopsis, acciones) se desliza un poco contra el swipe
  (`HERO_CONTENT_SLIDE_MAX`, 64dp) para que se lea la dirección, mientras que el
  artwork se desplaza una fracción menor del ancho de página
  (`HERO_BACKDROP_PARALLAX`, 5.5%) con un ligero overscan (`HERO_BACKDROP_SCALE`,
  1.14), así que los dos slides se leen como capas en vez de una doble exposición
  plana. La página asentada se dibuja última entre las capas, para que sus
  botones posean los hits, y un drag que arranca en uno de esos botones se
  reenvía al pager desde la fila de acciones (las capas de copy están sobre el
  pager, y los hermanos no comparten el hit path), así que el swipe sigue
  funcionando ahí mientras un tap cae al botón. En ventana ancha el rail flota
  sobre el contenido, que reserva su ancho, así que la navegación nunca lo
  solapa.
- **Tipografía.** Dos tipografías se incluyen mediante Compose Resources en
  `:app:shared`: **Roboto** (la de por defecto) e Inter. Ambas traen los
  mismos cuatro pesos (Regular, Medium, SemiBold, Bold) y pasan por el mismo mapa
  de pesos, así que `appearance.typeface` cambia las formas sin tocar la escala:
  `PenguTheme(typeface)` resuelve la familia una vez por composición y toda la UI la lee
  a través de `MaterialTheme.typography`. La línea de metadata del billboard
  (`AppType.heroMeta`) lleva un
  tracking negativo pequeño, así que la cinta `Series • 2026 • Drama` queda en
  una línea.
  Cada target renderiza la
  misma tipografía, y el texto se escribe en `sp` para que el tamaño de fuente
  del sistema de Android siga funcionando. `LocalDensity` nunca se sobrescribe
  deliberadamente: ese es el hack común, y miente a cada componente sobre la
  escala de fuente. Los roles `display*` de Material están dimensionados para
  pantalla grande, así que `AppType` los baja un rol en ventana compacta (título
  de hero, títulos de pantalla, sinopsis de hero); desktop y tablet mantienen la
  escala completa. Toda la escala de Material corre más ligera que la de serie
  (`Bold` a `Medium`, `SemiBold` a `Normal`), así que los encabezados se sientan
  en Medium mientras títulos, cuerpo y labels grandes se sientan en Normal. Los
  roles pequeños (`bodySmall`, `labelMedium`, `labelSmall`) se quedan en Medium:
  a 12sp los tallos de Regular son lo bastante finos como para que
  el antialiasing en escala de grises — Compose Desktop no tiene ClearType — los
  haga leerse como irregulares. `body*` llega
  como mínimo a `Normal` porque no se incluye ningún peso más ligero, así que un
  cuerpo realmente más fino necesita añadir `roboto_light.ttf` a
  `:app:shared`. Ningún slot de
  tipo lleva color: el color del texto viene del esquema de color en el punto de
  llamada, como pretende Material. La paleta de lectura está basada en blanco —
  `BrandPalette.textPrimary` es `#FFFFFF` y los roles secundarios (`textSecondary`
  para `onSurfaceVariant`, `posterYear`) son blanco con alpha reducido — así que
  la jerarquía viene de la opacidad en lugar de un segundo gris, y el acento
  ámbar queda reservado para marca/atención en vez de copy de cuerpo.
  `AppType` es el conjunto cerrado de roles de texto que lee la UI —
  `screenTitle`, `dialogTitle`, `sectionTitle`, `cardTitle`, `itemTitle`, `body`,
  `bodySecondary`, `caption`, `label`, más los roles de hero y botón —
  así que ningún punto de llamada elige un slot de Material directamente y un
  título, un encabezado de sección y una fila no se desvían entre sí. Dos niveles
  son deliberados: `sectionTitle` (`titleLarge` bajado a 18sp, 16sp en teléfono)
  encabeza una sección de pantalla, mientras que `cardTitle` e `itemTitle`
  (`titleMedium`) encabezan una tarjeta o una fila interactiva.
  `PosterCaption` sube `titleSmall` / `bodySmall` 1sp con el peso de meta en
  Medium.
- **Localización.** Cada cadena visible al usuario vive en
  `composeResources/values/strings.xml` y se lee con
  `stringResource(Res.string.key)`, así que añadir un idioma es una carpeta
  `values-*` nueva en vez de un cambio de código. Los mensajes de fallo pasan por
  `Throwable.toUserMessage()`, que devuelve un `StringResource` (no un `String`)
  para que el mapeo siga siendo testeable mientras el llamador resuelve el texto.
  Solo el formato independiente del idioma queda en código: separadores,
  timecodes, tamaños de fichero, tipos de contenido y el ensamblado de la fecha
  de estreno. Los chevrons direccionales pasan por `Modifier.mirrorInRtl()`, ya
  que Material no trae un chevron auto-espejado; las flechas usan los iconos
  `AutoMirrored`.
- **La navegación** es una pequeña pila tipada (`ContentNavigator` +
  `ContentRoute`) renderizada dentro del área de contenido del shell. Las
  secciones mantienen la navegación visible; una ruta apilada (detalle, catálogo,
  página de ajustes) la oculta y pinta su propio fondo, así que un hero inmersivo
  como el de detalle llena toda la ventana sin que la sección de debajo se
  transparente — el mismo comportamiento que el `Navigator` anidado de Flutter,
  sin añadir una librería de navegación. El chrome es custom en las dos clases:
  una ventana compacta recibe una barra inferior solo-iconos; una ventana ancha
  un rail de navegación izquierdo. Ambas comparten un mismo item
  (`SectionNavItem`), así que se
  comportan igual: el glifo lleva el estado (ámbar al seleccionar, más claro en
  hover, foco o press) sin caja de state layer, el anillo de foco compartido
  marca el target de teclado/D-pad, y el item se anuncia como pestaña
  seleccionada. El rail elimina las etiquetas para mantenerse estrecho, centra el
  avatar de perfil y los items de sección en la ventana, y pinta el fondo de
  página en vez de un color de panel, así que se funde con el contenido. Los
  glifos son Material Symbols Sharp (`MaterialIcons`), así que el estado
  seleccionado es solo el tinte. El glifo se dimensiona con `AppIconSize.nav`
  (28dp) en vez del default de 24dp de las filas, porque el item no muestra nada
  más. La barra añade un borde hairline `outlineVariant` arriba y mide
  64dp de alto
  (`bottomBarHeight`), no los 80dp de la barra con etiqueta que no tiene etiqueta
  que llenar. La selección se ve desde el primer frame en vez de aparecer solo
  cuando el item recibe foco. La barra compacta es un overlay sobre el contenido
  a sangre, así que ocultarla nunca deja hueco. Se repliega mientras se hace
  scroll hacia abajo y vuelve con animación al hacer scroll hacia arriba: una
  conexión `nestedScroll` en la caja de contenido del shell
  (`AppChrome.scrollCollapsed`, `collapseChromeOnScroll`) la cambia en cuanto el
  contenido recorre `AppSizes.scrollCollapseThreshold`. Cambiar de sección o
  abrir/cerrar una ruta la expande de nuevo. El rail ancho nunca se oculta,
  porque es el chrome de la ventana y no una franja flotando sobre el contenido.
- **El selector de streams lista fuentes, no artwork.** `StreamsDialog` abre como
  bottom sheet en un teléfono y como diálogo en una ventana ancha (desktop nunca
  usa sheet, igual que los selects de ajustes), y ambos alojan el mismo
  `StreamsPanel`, así que el contenido no puede divergir. El panel abre directo en
  las fuentes: el billboard de 200dp desapareció, porque el título ya se eligió
  una pantalla atrás, y el header es el título más una línea `año • N versions`.
  No hay campo de filtro: los addons ya rankean sus propias fuentes, y un campo
  sobre una docena de filas solo agrega un paso entre abrir el selector y
  reproducir. Toda la fila reproduce su fuente — el glifo de play pertenece a la
  fila en vez de ser su propio target de 48dp — y la acción de copiar solo existe
  cuando el stream tiene link, así que un teléfono tiene un solo target por
  fuente. Las filas no pintan superficie (los separadores hairline hacen leer la
  lista como una sola superficie) y el texto del addon nunca se recorta: su
  `name` y `description` llevan el formato que configuró el usuario — idiomas,
  subtítulos, calidad, tamaño — así que truncarlos ocultaría justo lo que el
  selector existe para mostrar, a costa de alturas de fila desparejas. Solo la
  línea de meta, que compone la propia app, queda en una línea y lleva el addon y
  el `behaviorHints.videoSize` declarado sin parsear el texto formateado del
  addon. Las fuentes se agrupan por addon en el orden en que el repositorio las
  devolvió, porque el ranking del addon es la verdad y la UI nunca re-ordena; el
  header de grupo solo aparece cuando respondió más de un addon, ya que con uno
  solo repetiría la misma etiqueta hacia abajo. El view model colapsa las copias
  que devuelven varios addons, usando la url, luego `infoHash#fileIdx`, luego la
  url externa, así que el mismo release se ofrece una sola vez. Una carga fallida
  ofrece Refresh dentro del propio mensaje, y un refresh fallido conserva la
  lista anterior con un aviso inline, así que el selector nunca pierde sus
  fuentes en silencio. Las filas llevan la etiqueta de acción "Play", así que un
  lector de pantalla anuncia la acción en vez de una columna de botones con el
  mismo nombre.
- **Las acciones del póster viven en un solo componente.** `MediaActions` +
  `MediaActionsHost` (`ui/components/MediaActions.kt`) dan a cada póster el mismo
  menú de clic derecho / pulsación larga, así Home, Library, Catalog, Search,
  Discover y "More like this" no pueden divergir. En desktop ancla un
  `DropdownMenu` en la posición del puntero con solo las opciones; en táctil abre
  una bottom sheet con el título, su metadata, la sinopsis (ajustada a su
  contenido) y las mismas opciones como filas alineadas a la izquierda, de ancho
  completo y sin contorno de botón. El menú
  ofrece los toggles de biblioteca y visto, y "More info"; cuando el llamador ya
  conoce esos estados los pasa y, si no, el host los resuelve al abrir. Una
  tarjeta de "Continue watching" los sustituye por "Watch from beginning",
  "Remove" y "More info". Quitar borra todas las filas de
  progreso del título (`ProgressRepository.remove`), no solo el episodio más
  nuevo, así un episodio anterior sin terminar no puede devolver la fila;
  después la señal de playback repinta Home. "Watch from beginning" lleva
  `startFromBeginning` por `StreamsRequest` hasta `PlaybackTarget`, donde el
  reporter de progreso se salta la posición guardada.
- **Los bloques de sinopsis se ajustan a su texto.** `HeroSynopsis` solo reserva
  altura mientras no tiene texto (el estado de carga del hero del detalle); una
  vez está la descripción, el bloque toma las líneas que realmente usa, así una
  sinopsis de dos líneas no deja una tercera vacía. La bottom sheet de acciones
  del póster sigue la misma regla.
- **Search es dueña del feed de discover.** Tiene una búsqueda de texto libre en
  cada catálogo que declara el extra `search`, con debounce, ejecutada en
  paralelo y deduplicada entre addons, dividida en raíles Movies / Series /
  Other; y — mientras la consulta está vacía — el feed de discover debajo de la
  barra. El campo de búsqueda queda fijado arriba con el mismo `screenPadding`
  que la primera fila de las demás secciones y un margen `md` por debajo. El feed
  aplana cada catálogo que declaran los addons activos en una única rejilla mixta,
  como la Library, para que los límites de catálogo queden
  invisibles. Solo se usan catálogos navegables: uno cuyos extras son
  obligatorios (un término de búsqueda, una lista de ids, ...) no se puede pedir
  sin una consulta, así que se salta en vez de pedir y obtener una página vacía.
  Los catálogos se intercalan entre addons antes del límite, así que un addon con
  muchos catálogos no desplaza a los demás, y los items se intercalan round-robin
  y se deduplican, así que la rejilla alterna fuentes en vez de mostrar un
  catálogo como un bloque. Pagina con `skip` según se desplaza la rejilla, así
  que sigue creciendo en vez de parar en la primera página. Discover ya no es una
  sección.
- **El tema** sigue Material 3 y es dato, no código. `BrandPalette`
  (`theme/Tokens.kt`) es una data class con los colores crudos de la app,
  publicada como la única `CoffeePalette`; `PenguTheme` la proporciona por
  `LocalBrandPalette` y luego construye el `darkColorScheme` completo a partir de
  ella (niveles surface-container, `outline`/`outlineVariant`, `inverse*` y los
  contenedores de error). La paleta es interna y solo rellena el esquema, así que
  los componentes de serie de Material3 y las superficies custom quedan en los
  mismos colores; `tertiary` refleja el acento, así que ningún tono frío se cuela
  en la paleta cálida. Los scrims y el chrome nativo de la ventana también leen
  `LocalBrandPalette`. No hay ajuste de tema: la paleta es fija hasta que
  merezca la pena añadir una segunda. La tipografía
  cargada desde Compose Resources (Roboto por defecto, Inter como
  alternativa, elegida en Ajustes → Apariencia) y la escala de formas pequeña y
  tirando a cuadrada (`AppCorner` alimenta `MaterialTheme.shapes`: 1/2/3/4/6dp; la app se
  lee cuadrada, no redondeada) no dependen de la paleta. Los diálogos y las bottom
  sheets usan la misma escala (`shapes.extraLarge` y `AppBottomSheetShape`) en
  vez del default de 28dp de Material. La UI lee colores semánticos
  de `MaterialTheme.colorScheme`; `AppColors` guarda los overlays de media para
  los que el esquema no tiene rol: `mediaScrim` y el indicador del carrusel del
  hero (apagado cuando está inactivo, crema cuando está activo).
  `AppSizes.controlHeight` / `minTouchTarget` mantienen los targets
  interactivos en el mínimo de 48dp de Material. Las superficies clicables custom
  dibujan la capa de estado ripple de Material (`clickableSurface`), así que el
  feedback de foco y pulsación sigue visible, y llevan rol de botón para que una
  superficie custom se anuncie correctamente. El puntero sobre una superficie
  interactiva se vuelve cursor de mano (`handCursor`), algo que ni `clickable`
  ni ningún control de Material ponen: `clickableSurface` lo aplica una vez para
  toda superficie custom, y los pocos controles que no pasan por él (el botón,
  el switch, el ítem del dropdown, la fila seleccionable del menú de pistas, la
  barra de avance del reproductor, la sinopsis expandible y el asa de arrastre de
  los addons) lo aplican por su cuenta. Un control deshabilitado conserva la
  flecha, ya que no se puede clickear. Los tiles de póster renuncian a esa
  capa de estado (`indication = null`) y señalan el hover con la animación de
  escala/borde, para que un póster con hover no se agrisé. Los paneles de ajustes
  usan el `Card` de Material. Una pequeña capa de componentes en `ui/components`
  mantiene los defaults de Material fuera de la pantalla: `ActionButton` elige su
  contenedor, contenido y colores deshabilitados de un peso `ActionButtonStyle`
  (Filled sobre la superficie inversa, Accent sobre el `primary`/`onPrimary`
  ámbar para el "Stream Now" del billboard de detalle, Tonal sobre un relleno
  translúcido
  de on-surface, Outline sobre `outline`, Text solo etiqueta), deja el color de
  contenido al `LocalContentColor` del propio botón para que una etiqueta
  deshabilitada se atenúe con su contenedor, fija la elevación a cero (la app es
  plana), fija la etiqueta a un `labelLarge` en SemiBold (la escala
  tipográfica fija todos los slots en Normal, así que los botones vuelven a
  optar por él), un tamaño más en un call to action, con un glifo que escala por
  énfasis — 24dp en un call
  action, 20dp inline — la limita a una línea con elipsis, y baja ligeramente
  todo el control mientras se presiona; un flag `selected` vuelve ámbar los
  pesos de baja
  énfasis para el selector de temporadas, y un `trailingIcon` opcional añade un
  glifo al final. `AppTextField` fija los campos de formulario a
  `shapes.small` y un borde ámbar al enfocar, sin relleno propio, así que el
  campo lee la superficie sobre la que se coloca como el resto de controles
  planos, con una variante `AppSearchField` (lupa a la izquierda, acción de
  limpiar) para la sección Search. `AppIconButton` es el único control de icono —
  cabeceras de media, el reproductor (atrás, play/pausa, avance, subtítulos,
  audio, pantalla completa y el botón de play centrado), los toggles de
  biblioteca/puntuar/visto del detalle y las acciones de limpiar/contraseña de
  los campos — así que todos reciben el target de 48dp, un `mediaScrim` opcional y
  un atenuado de carga, y llevan su estado en el glifo y no en la superficie: sin
  caja de fondo ni ripple; el ícono se ilumina en hover o foco y se vuelve ámbar
  al seleccionarse; ninguna pantalla llama a un `IconButton`,
  `TextButton` u `OutlinedButton` de Material en crudo. Cada control lee solo
  roles del esquema y tokens compartidos, así que ninguno se estiliza en el punto
  de llamada. El foco se reserva para teclado y D-pad: el anillo compartido del
  ancho `AppSizes.focusRing` se dibuja solo cuando `LocalInputModeManager`
  reporta un modo distinto de touch, así que el clic de mouse conserva el look
  limpio sin caja mientras un control remoto o un gamepad muestra el target; y
  los solo-icono (`AppIconButton`, el rail) además iluminan el glifo en hover o
  foco. El fondo de
  página casi negro (`#0B0803`) es la única fuente de verdad: el esquema, el
  fondo nativo de la ventana y cada degradado de fundido de página terminan en
  él. La app es deliberadamente **solo oscura** — la paleta es oscura, y no hay
  esquema claro ni color dinámico — porque la UI está construida alrededor de
  artwork oscuro y scrims;
  el reproductor mantiene una paleta fija de controles blanco-sobre-negro sobre
  el vídeo, con el progreso de la barra y del volumen como única excepción que
  lee el acento del tema. La `visualDensity` de Material3 queda en el default de
  plataforma.
- **Imágenes con Coil 3**, el cargador de imágenes multiplataforma. El artwork
  remoto pasa por dos composables compartidos en `ui/components`: `PosterImage`
  para pósters y thumbnails, y `RemoteImage` para el resto (backdrops, arte de
  hero, fotos de reparto, logos de título). Ambos fijan `ContentScale` y
  `FilterQuality`, porque Coil dibuja con `DefaultFilterQuality` (bilineal) y
  aliasea un póster reducido. El artwork usa `FilterQuality.Medium` (bilineal con
  mipmap), que es el muestreo correcto para minificación, y los logos usan
  `FilterQuality.High` (bicúbico) para que sus bordes queden nítidos. Un backdrop
  a pantalla completa usa `FilterQuality.Low` (bilineal plano,
  `BackdropFilterQuality`): se dibuja cerca de su propia escala, donde los
  mipmaps cuestan más de lo que aportan. En desktop
  el lado de decodificación también está corregido: el `SkiaImageDecoder` de Coil
  3.6.3 redimensiona con `SamplingMode.DEFAULT`, que Skiko define como vecino más
  cercano, así que `app/desktopApp` instala `MitchellImageDecoder`, un
  decodificador cúbico que mantiene las matemáticas de tamaño de Coil y reduce
  una vez en el momento de decodificar. Juntos afilan el artwork y reducen la
  memoria de bitmap que el renderer tiene que escalar cada frame. Los logos de
  título se contienen en una caja — un tope de altura óptica más una guarda de
  ancho (`AppLogoSize`), dibujados con `ContentScale.Fit` — para que un logo
  cuadrado no se eleve sobre el copy y uno muy ancho no desborde su columna. La
  caja se ajusta al artwork en vez de fijar la altura, así que el hueco hasta la
  línea de metadata es el mismo para todos: una altura fija centraba cada imagen
  en la caja y dejaba un hueco que crecía con su aspecto. En
  un teléfono la guarda baja al 60% de la ventana (`maxWidthForWindow`), para que
  un wordmark ancho no se apodere de la pantalla, y `opticalHeight` limita la
  altura al ancho del copy, para que un logo cuadrado no se eleve y ambas formas
  lean con un peso similar. Los logos además se recortan al bounding box de sus píxeles no
  transparentes (`TrimTransparentTransformation`, un escaneo de píxeles por
  plataforma detrás de una `Transformation` de Coil), porque los proveedores los
  entregan sobre un lienzo transparente fijo: un wordmark de serie puede ser una
  franja de 50px dentro de un PNG de 800x310, y `Fit` escalaría todo el lienzo.
  Recortarlos hace que la caja mida artwork, no padding, y el precargador de Home
  lleva la misma transformación para que ambas peticiones compartan la entrada
  de caché de Coil. Se decodifican además a una caja fija (`LogoMaxBitmapSize`,
  a la vez tamaño de la petición y tope): derivar el tamaño de
  decodificación de la caja encogía el contenido a la porción del lienzo, así que
  un wordmark con padding se decodificaba por debajo del tamaño al que se dibuja
  y luego se escalaba hacia arriba, lo que se veía dentado. Deliberadamente no es
  `Size.ORIGINAL`, porque muchos logos de TMDB son **SVG** — una serie de 2025
  puede traer solo un SVG — y un SVG tiene un viewBox, no píxeles, así que
  `ORIGINAL` rasterizaría un wordmark de 107 unidades a 107px. La app depende de
  `coil-svg`; en escritorio ese decodificador se registra *antes* de
  `MitchellImageDecoder`, que reclama toda fuente y no puede rasterizar SVG desde
  sus bytes, así que un SVG que llegaba a él nunca se pintaba. Un logo que aun
  así falla (una URL muerta, un addon que borró el fichero) cae al nombre, el
  mismo titular que muestra un título sin logo, en vez de dejar el billboard sin
  título. El hero de Home
  precarga el backdrop y el logo de la
  siguiente diapositiva a
  través del loader singleton, dimensionados al billboard, y se re-arma según
  avanza la página, para que deslizar pinte desde caché en vez de empezar una
  vuelta de red a mitad de gesto sin el pico de decodes que causaba encolar todo
  el set al entrar.
- **El movimiento** está centralizado en `ui/theme/Motion.kt`: `AppMotion` guarda
  las duraciones y tanto las easings utilitarias como las emphasized de Material
  (las pantallas que entran usan emphasized decelerate, las que salen emphasized
  accelerate), y `LocalMotionEnabled` controla cada animación. Las specs
  construidas con `AppMotion.spec`/`motionTween` colapsan a duración cero cuando
  está apagado, y `Modifier.motionLayer` aplica escala y alpha en la fase de
  dibujo, así que el movimiento nunca relayouta ni recompone por frame. Las
  pestañas cambian sin transición; la sección queda compuesta mientras una ruta
  está abierta (para que su scroll nunca se guarde y restaure, lo que perdía el
  offset dentro del item y lo subía) y la ruta se funde encima, saliendo de
  inmediato al desapilar. El rail flota sobre el contenido en vez de tomar ancho
  de layout, porque ocultarlo agrandaba el viewport de contenido y recortaba el
  scroll de la sección; las secciones reservan su ancho con un padding constante,
  así que el viewport nunca cambia. La animación de tamaño está desactivada
  (`.using(null)`) porque relayouta cada frame. Los pósters escalan al hover y
  los diálogos se funden/escalan al entrar. Las pantallas de carga son skeletons
  movidos por un único shimmer leído en la fase de dibujo: la rejilla de pósters
  deriva su número de celdas del viewport, así que nunca se detiene a mitad de
  una ventana ancha, y los stops de degradado estáticos se construyen una vez en
  vez de cada frame. Home muestra una pantalla de carga a pantalla completa hasta
  que todos los catálogos y el historial de reproducción han respondido y el
  artwork del primer slide del billboard está decodificado; luego pinta el hero,
  las filas y "Continuar viendo" juntos. El hero de detalle no pinta el fallback de la biblioteca como
  si fuera el artwork final: reserva el espacio del logo sin dibujar un
  placeholder, y el logo y el backdrop se funden cuando la imagen está lista
  (`RemoteImage(fadeIn)`), así que una carga lenta nunca aparece de golpe. El
  reproductor está deliberadamente fuera de la transición de ruta porque maneja
  un único motor compartido; su entrada es la propia disolución del pre-roll
  (ver Reproductor).
- **El billboard del teléfono encaja un póster sin texto en vez de recortarlo.**
  Un backdrop ancho es 16:9 y el billboard compacto es una portada alta, así que
  recortar uno dentro del otro deshace la composición; un póster neutral de
  idioma ya es vertical y no lleva título incrustado, así que el logo en vivo se
  apoya sobre el arte en vez de sobre un recorte. El enriquecimiento de TMDB
  aporta uno (`textlessPoster`, su póster con `iso_639_1 == null` de mayor
  resolución), y `heroArtwork` lo elige en ventana compacta, encajado entero en un
  marco que es exactamente el tamaño del póster (`1 / POSTER_ART_ASPECT`) — con
  caída al backdrop y luego al póster, ambos recortados — mientras el billboard
  ancho conserva el backdrop de siempre. Todo el wash compacto es un único
  gradiente (`heroCompact`): atenúa el centro, oscurece la parte de arriba sin
  llegar nunca al fondo de página (así la portada se ve hasta el borde superior)
  y se libera hacia ese fondo abajo para encontrarse con la sección siguiente.
  Los tres washes que reemplaza eran del mismo color, así que se pliegan en un
  solo pase de pantalla completa mediante el alpha compuesto, y una diapositiva
  encajada no se desplaza al deslizar, porque el overscan del parallax volvería a
  recortar sus lados. El backdrop del detalle toma la misma decisión de póster
  sin texto en un teléfono, recortado para llenar, y ahí suelta el scrim de
  legibilidad de izquierda a derecha, así que el arte solo se oscurece abajo,
  bajo el copy. Además espera a que el enriquecimiento se resuelva antes de
  pintar, así que la decisión nunca se rehace en pantalla.
  El preloader de Home pasa por
  el mismo helper, así que siempre precarga la URL que la capa va a dibujar. El
  set destacado se enriquece antes del pintado y Home mantiene su overlay de carga
  hasta que la imagen del primer slide está decodificada, así que el billboard
  nunca se ve con el fondo pelado.
- **Tamaño de póster** es un único ajuste (`PosterSize`: default / large,
  210 / 230 dp) que solo gobierna las filas horizontales de pósters — los
  catálogos de Home, los resultados de búsqueda y "More like this" — donde una
  tarjeta se puede redimensionar sin efecto colateral en la rejilla; una fila
  conserva el ancho tal cual y deriva su altura del `width / (2/3)` del artwork
  más un bloque de caption fijo (`PosterCaption.blockHeight`, en `dp` desde el
  tipo del caption para que siga el font scale), o solo el artwork cuando los
  labels están ocultos. La reserva es una altura, no un ratio: un ratio se encoge
  con la celda estrecha del teléfono hasta que el caption se come el artwork y
  recorta sus lados. Las rejillas (Library, catálogo completo, Discover) ignoran
  el ajuste y mantienen un layout fijo: `gridCells` coloca tres pósters por fila
  en el teléfono y encaja tantas celdas de `GRID_CELL_WIDTH` como pueda en
  ventana ancha, así que un teléfono nunca muestra un tamaño que el usuario no
  eligió. En ventana compacta el ancho del raíl se reduce con `COMPACT_SCALE`
  (0.65).
- **Ventana**: la ventana de desktop conserva su **marco nativo** (`WS_CAPTION`,
  `WS_THICKFRAME`) pero borra el área non-client dibujada por el SO, así que la
  barra de título Compose es el caption. En Windows un `WindowProc` nativo (JNA,
  vía `SetWindowLongPtr`) responde `WM_NCCALCSIZE` — devolver `0`, encogiendo el
  rect de cliente con las métricas del marco para que una ventana maximizada no
  se salga — y `WM_NCHITTEST`: `HTCAPTION` sobre la barra,
  `HTMINBUTTON`/`HTMAXBUTTON`/`HTCLOSE` sobre los botones y `HTLEFT…` sobre los
  bordes. Los botones de caption tienen prioridad sobre el borde superior de
  redimensionado, así que la franja de arriba sobre ellos igual los ilumina (y
  los botones a altura completa llegan al borde superior de la ventana) en vez
  de redimensionar. Eso es lo que preserva el drag nativo, Aero Snap, maximizar,
  redimensionar y el flyout de Snap Layouts de Windows 11. El contenido Compose
  vive en una ventana hija Skia, así que un segundo procedimiento enganchado a
  ella devuelve los resultados de hit de los botones y refleja los mensajes de
  ratón non-client de vuelta al contenido: los botones Compose animan y manejan
  el clic mientras el padre es dueño del caption y de los bordes. El marco
  (`WindowsWindowProc` en `:app:desktopApp`) sigue la implementación de Animeko,
  recortada a lo que la app necesita (sin puente touch/pen, sin glass de Windows
  10, sin hit test por nodo de Compose). `AppSizes.titleBarHeight` y
  `AppWindowChrome` alimentan la barra; la ventana se abre a 1280x720 y ese es
  también su **mínimo** (fijado como `minimumSize` en la ventana AWT). Mantener
  el marco nativo es lo que preserva snap, maximizar, las animaciones de ventana
  y el alto contraste. macOS y Linux conservan la decoración nativa; allí solo se
  enruta **pantalla completa** por `WindowController`, que posee el reproductor,
  y expone la altura del área de trabajo de la pantalla desde la que se
  dimensiona el billboard de Home.

## Branding

- **Un solo maestro; todo lo demás se genera.** `branding/pengu.svg` es la marca —
  un botón de play que a la vez es el pico de un pingüino, con las mandíbulas
  escalonadas en bandas — y `branding/pengu.png` es su render de 512 px para la
  ficha de Play. `scripts/gen_brand_icons.mjs` deriva de esa geometría las variantes
  redondeada y sin fondo y los tres drawables de Android, y
  `scripts/gen_brand_rasters.mjs` renderiza desde esos SVG los PNG y el `.ico`
  commiteados, así que ningún path se escribe dos veces ni ningún tamaño se adivina;
  `branding/README.md` recoge la paleta, los tamaños y las reglas, incluida la lista
  de seis pasos para cambiar la marca.
- **La marca ocupa el 72 % del tile** (72 % de ancho: al ir rotada su contorno es
  cuadrado), centrada. Por encima de eso el triángulo aprieta la keyline de Play;
  por debajo, el icono se lee como un punto en una barra de tareas dibujada a 24–32 px.
- **El pico apunta al noreste.** La marca va girada 45° respecto a la derecha del
  botón de play: apuntar a la derecha es el glifo que usa cualquier otro reproductor,
  y la diagonal mantiene la lectura de play sin dejar de distinguir a Pengu en una
  cuadrícula de iconos. Es una cometa, no un triángulo: las otras dos esquinas están
  en la diagonal opuesta, así que los dos picos van sobre un mismo eje y los lados de
  la mandíbula chica son el par corto.
- **Las puntas son redondas.** Un trazo de 128 con unión redonda dibuja la cometa,
  así que el trazo es el radio de la esquina (64, el doble del 32 del hand-off); la
  forma va rotada y escalada sobre su centroide para que el contorno exterior mida
  488 unidades en su lado mayor. El radio 64 se lee más sólido a tamaño de barra
  de tareas, y el ajuste es lo que evita que se lea más pequeño: solo subir el radio
  retrae las puntas.
- **Los tonos de las mandíbulas son el acento de la app.** `#F5B02A` y `#D98A0B` son
  también `accent` y `accentContainer` en `Tokens.kt`, así que el chrome de la app y
  el icono del launcher son los mismos dos naranjas en vez de dos aproximaciones. La
  separación es un path relleno —suave desde una esquina, una rodilla redondeada y una
  bajada empinada hasta la otra esquina— en vez de un degradado, así que el pliegue se
  lee como un pliegue quebrado y la rodilla conserva su propio radio mientras los tonos
  tocan el contorno exacto. No lleva ni línea ni sombra incrustadas. No se dibuja nada
  más: la spec de iconos de Play hace que Play aplique el radio de esquina y la
  sombra, así que una línea dentro del artwork solo es suciedad a 48 px. El
  foreground de Android lleva los mismos dos paths, sin clip que mantener sincronizado;
  la capa monochrome conserva solo el contorno.
- **El icono de tienda es cuadrado completo.** Según la spec de iconos de Play el
  asset no lleva radio de esquina ni sombra (Google aplica ambas); `pengu-rounded.*`
  existe para las superficies que no enmascaran iconos.
- **Todos los iconos de launcher derivan de esa geometría.** El foreground
  adaptativo es un vector drawable con los mismos paths, ajustado por
  `gen_brand_icons.mjs` para que el contorno ocupe el 60 % de la máscara de 72 dp y
  sus esquinas caigan a 27 dp — dentro del círculo seguro de 66 dp. La escala se
  calcula desde el contorno del maestro, no se escribe a mano: una escala fija
  conserva el artwork para el que se ajustó y encoge en silencio los siguientes.
  Android lleva menos relleno que el 72 % de los rasters porque el launcher recorta
  el foreground de 108 dp a la máscara y lo amplía, así que un relleno igual se lee
  más pesado al lado de otras apps; el 60 % iguala su relación glifo/tile.
  `ic_launcher_background.xml` lleva el color del tile, `ic_launcher_monochrome.xml`
  la silueta para iconos con tema, y los `mipmap-*/ic_launcher[_round].png`
  heredados son renders de las variantes redondeada y circular.
- **Los iconos de escritorio quitan el tile.** `branding/pengu-frameless.svg` pone la
  marca al 85 % con un borde oscuro de un píxel de dispositivo, porque el tile solo
  tiene sentido como cara de icono y en una barra de tareas le cuesta a la marca un
  quinto de su tamaño — mientras que los naranjas desnudos bajan a 1,7:1 en la barra
  clara de Windows. La escalera vive en
  `app/desktopApp/src/main/resources/pengu-icon/` (es un recurso de runtime, así que
  pertenece al módulo), se entrega a la ventana como `iconImages`, y
  `branding/pengu.ico` es lo que jpackage incrusta en el `.exe` empaquetado.

## Reproductor
## Reproductor
## Reproductor

`PlayerService` lo implementan en los composition roots `MediampPlayerService`,
una subclase fina del `MediampPlayerAdapter` compartido (`:app:player`) sobre
**[mediamp](https://github.com/open-ani/mediamp)**. El adaptador guarda la
lógica del contrato una sola vez; cada root solo cablea su motor (mpv o
ExoPlayer): la superficie, el volumen y el manejo de subtítulos externos. El
motor queda fuera de la UI compartida y se elige por target.

En desktop, mediamp resuelve a su backend **mpv**: rinde el vídeo dentro del
lienzo Compose/Skia (D3D11 en Windows, OpenGL en Linux, Metal en macOS) y lo
dibuja vía `VideoSurfaceRenderer`, así que el vídeo es contenido Compose normal y
los controles Compose se apilan encima en la misma ventana — una superficie `wid`
nativa o una ventana overlay aparte taparían los controles o se
desincronizarían. Los subtítulos externos de addons se añaden con el comando
subyacente de mpv, y el runtime por plataforma de mediamp incluye un libmpv y
FFmpeg a juego (LGPL en Windows/macOS, GPL-3.0 en Linux).

En Android, mediamp resuelve a su backend **ExoPlayer (media3)**: embebe un
`PlayerView` con `AndroidView` tras el mismo handle `VideoSurfaceRenderer`, así
que los controles Compose se apilan encima del vídeo. media3 solo acepta
ficheros de subtítulos externos cuando el medio se prepara, así que seleccionar
un subtítulo externo de addon recarga la misma fuente con el fichero adjunto y
retoma en la posición actual; las pistas embebidas, la selección de audio y la
posición/duración pasan por la misma API de mediamp. ExoPlayer no expone el
`AudioLevelController` de mediamp, así que el volumen se fija en el reproductor
nativo.

Los comandos, la posición, las pistas y el volumen pasan por la API
`MediampPlayer` de mediamp en ambos casos. El reporte de progreso, el resume, la
selección de subtítulos/audio y el autoplay del siguiente episodio no cambian.

El overlay (`ui/player`) sigue la maqueta del reproductor de referencia: una
cabecera de dos líneas (título y `S01E01 · Episodio` cuando es un episodio), una
barra de progreso de trazo fino a todo el ancho con la parte reproducida en el
acento del tema, un pulgar y una burbuja de tiempo que aparecen al pasar el
puntero o al arrastrar, y una única fila de controles sin cajas debajo. La barra
engorda al hover, busca desde el primer pulsado y sigue el arrastre sin esperar
a que se suelte, y su timecode no rellena los minutos con cero
(`0:03 / 56:01`). El glifo de volumen sigue el nivel y hace de mute, y el control
deslizante de volumen solo se expande mientras se pasa el puntero por el grupo,
así que la fila conserva el ritmo de un solo icono de la referencia en desktop;
el teclado mantiene el volumen en las flechas y el mute en `M`. El icono de
subtítulos se tiñe de ámbar cuando hay una pista seleccionada. Los controles se
desvanecen durante la reproducción y no se componen mientras están ocultos, así
que un overlay invisible nunca se come un clic. Los glifos del reproductor
siguen en blanco sobre negro; solo los acentos de progreso y volumen leen
`colorScheme.primary`.

Elegir un stream abre el reproductor en un **pre-roll** en vez de un fotograma
negro: `PlayerSplash` (`ui/player`) pinta el backdrop del propio título bajo un
velo plano (`AppScrim.playerSplash`) con el logo de la obra en el centro. El logo
no es un spinner y no lleva etiqueta — respira entre el 45 % y la opacidad
completa, como lee la pantalla de carga de Stremio, así que la espera se ve como
*ese título* cargando. El artwork sale de `MetadataRepository.detail` con el id
de contenido del target, en lugar de un campo nuevo en `PlaybackTarget`:
`PlaybackTarget` sigue siendo un contrato de reproducción, la pantalla de la que
viene el usuario acaba de pintar las mismas URLs (así que normalmente es un
acierto de la caché HTTP) y esa consulta también cubre el autoplay del siguiente
episodio y un target abierto directamente desde un enlace. Es best-effort: una
fuente cruda del atajo `--play` no tiene metadata, así que el splash cae al
fondo negro y no pinta título, y sin logo el nombre hace de él.

La espera vive en `rememberPlayerPreRoll` (`ui/player`), que es dueño del artwork,
la regla de readiness, la disolución y el traspaso a los controles, así que
`PlayerScreen` se queda solo con el vídeo, el overlay y el ciclo de vida. Mantiene
el splash un mínimo de 1 s y hasta que el reproductor reporta un fotograma pintado
sin buffering, y desiste a los 4 s: lo que llegue antes, porque en una fuente que
no puede llenar el lookahead la espera extra no compra nada y el indicador de
buffering es mejor respuesta. Un fotograma pintado es `position > 0`: la duración
parseada llega antes de que el motor tenga imagen, y condicionar a la duración
destapaba sobre un fotograma negro. Encima de eso espera a que
`PlayerState.bufferedPosition` esté 2 s por delante del playhead, que es el
pre-buffer de verdad y no un cronómetro: mediamp expone el buffer en ambos motores
(el final de la caché del demuxer de mpv, la posición bufferizada de ExoPlayer) y
el adaptador publica `null` cuando el backend no tiene caché que llenar, así que
un fichero local solo necesita el fotograma y a un stream en vivo — que por
definición está en el borde del buffer — nunca se le pide un lookahead que no
puede tener. Un clip más corto que el lookahead solo espera lo que tiene. Un error
del motor acorta la espera.

Después se disuelve con `AppMotion.DISSOLVE` (la única duración por encima de
`SLOW`, porque una capa del tamaño de la pantalla se lee como un corte con el
tiempo de un componente) mientras retrocede un 5 %, así que el vídeo llega desde
detrás del artwork en vez de a través de una rampa de alpha plana. El splash está
en pantalla desde el primer fotograma y solo el backdrop se asienta (fundido más
un zoom del 3 %): fundir la capa entera desde negro era lo que hacía que la
entrada se leyera como un corte, porque la pantalla que hay detrás del
reproductor es la sección, no el título del que viene el usuario. El alpha y la
escala se leen en la fase de dibujo con `motionLayer`, así que la disolución nunca
recompone ni rehace el layout del artwork. Al terminar, el splash sale de la
composición — su pulso se detiene en lugar de animarse detrás de dos horas de
vídeo — y los controles esperan 250 ms, así que el destape es solo el vídeo. Cada
duración vive junto a la pantalla y cada animación está condicionada por
`LocalMotionEnabled`.

Un conjunto pequeño de **opciones del motor** se expone en Ajustes (página
Reproducción, solo en escritorio) porque son las palancas que importan cuando un
título se ve mal: la **curva de tone-mapping** de HDR (`auto`, `bt.2390`, `hable`,
`mobius`), el **debanding** y la **decodificación por hardware** (un interruptor a
decodificación por software, lo primero que hay que probar cuando una fuente se ve
con colores raros o en negro). La lista es corta y consciente del renderer: las
curvas que solo existen en `vo=gpu-next` (`spline`, `bt.2446a`, las dinámicas de
HDR10+) no se ofrecen, porque la superficie de escritorio dibuja con la render API
de libmpv, que solo implementa el backend GPU legacy. Los valores viven en
`AppSettings` (`player.toneMapping`, `player.deband`, `player.hardwareDecoding` en
`settings.json`), se mapean a la grafía de mpv con un `MpvPlaybackOptions` puro
(`app:desktopApp`, con test unitario) y se aplican dos veces: como valores de
construcción por el hook `configureOptions` de mediamp, y otra vez antes de cada
apertura, así que un cambio llega al siguiente vídeo y no al siguiente arranque.
`auto` se envía en vez de omitirse: volver a él desde otra curva tiene que
desactivarla en un reproductor vivo. Android oculta el panel: su motor no tiene
un equivalente, y un interruptor que no hace nada es peor que ninguno.

Cerrar el reproductor es también cuando se calcula la pantalla que el usuario va a
ver. La posición final se escribe desde `AppServices.appScope`, no desde el scope
del composable (que se cancela al salir del reproductor, y eso hacía que la
escritura se perdiera), y solo cuando esa escritura ha llegado se marca
`PlaybackUpdates.closed`. Home observa esa marca y relee el progreso para repintar
"Continue watching", así que un título dejado a medias aparece al instante en
lugar de en la siguiente carga — con el mismo constructor que usa la carga,
porque repintarlo sin el enriquecimiento de arte devolvía el arte ancho a un
póster recortado. La marca es
una señal aparte a propósito: `ProgressRepository.changes` se incrementa en cada
guardado periódico (cada pocos segundos de reproducción), y una pantalla que
estuviera suscrita a eso releería el progreso durante toda la película. La fila
en sí tiene una tarjeta por título: el progreso se guarda por episodio, así que
el ViewModel se queda solo con el `lastWatched` más reciente de cada `contentId`
— y filtra los que están a medias después, así que un último episodio terminado
esconde la serie — mientras las filas por episodio se quedan en el repositorio
para el resume y el badge de visto. Una tarjeta es el arte ancho con el texto
dentro (`ContinueCard`): el título, `S1E3 · 33 min left` y la barra de progreso
sobre un scrim inferior suave, así el arte ocupa toda la tarjeta en vez de compartirla
con un bloque de caption. La fila es sobre lo que está en progreso, no sobre lo
que se guardó: un título que falta en la biblioteca se resuelve desde su metadata
(`unsavedItem`), así que terminar un episodio de algo que nunca se añadió a la
biblioteca igual lo pone en la fila. Un item de biblioteca guardado sin backdrop se dibujaría
como un póster vertical recortado, así que `enrichContinue` busca su arte ancho en
TMDB — solo para esos items, detrás del semáforo del billboard y de la regla de
enriquecer-antes-de-pintar de esta pantalla, y cada una de esas búsquedas va con
tope de tiempo (`ENRICH_TIMEOUT`) porque la fila se publica junto con el resto de
Home: un addon o TMDB que se atasca deja una tarjeta sin enriquecer en vez de
retener la pantalla. Pulsar una tarjeta reanuda la
reproducción: `LastStream` (`StreamCacheRepository`, escrito en
`last_streams.json` al elegir un stream) recuerda la URL con la que se reprodujo
el título por última vez, con la identidad del propio playback —
`WatchProgress.contentId` más el `videoId` del episodio, con un `videoId` en
blanco tratado como ausente porque así guarda el backend el de una película —,
porque el item de
biblioteca puede llevar un id que los addons no responden (`tmdb:123` en un
título abierto desde una recomendación de TMDB) mientras que el progreso guarda
el resuelto. Ese mismo registro lleva el **nombre del episodio**: la fila de
progreso no tiene columna para él (el esquema del backend no es nuestro), así que
la tarjeta lo lee de ahí (`S1E3 · Like a Boy · 33 min left`) en vez de volver a
pedir metadata a los addons. El autoplay del siguiente también escribe el
registro, porque resuelve un stream por su cuenta. La tarjeta la usa directamente mientras siga fresca
según `player.streamUrlCache` — los enlaces de debrid caducan y rotan, así que
esa preferencia es un techo a lo obsoleta que puede estar una reanudación. Cuando
caduca, o cuando no se recordaba nada, la tarjeta abre el selector de streams; el
reproductor borra la entrada en cuanto una URL falla al reproducir, así que un
enlace de debrid muerto cae al selector en el siguiente intento. Dónde vive el
episodio de un título nunca se lee solo del id: `StreamsRequest` y
`PlaybackTarget.fromContent` llevan la temporada y el episodio que el llamador ya
conoce — el objeto video del addon, o la fila de progreso al reanudar — porque un
addon de anime nombra un episodio `kitsu:43248:1`, cuya parte del medio es el id
de kitsu de la serie y si no se leería como una temporada 43248 bajo el content id
`kitsu`. Ese desajuste era lo que dejaba fuera de la fila a un anime a medias: su
progreso se escribía contra un id que ningún item de biblioteca ni addon responde.

El reproductor se adueña de la pantalla en un teléfono: `PlayerScreen` llama al
`PlayerWindowMode` de plataforma (`ui/platform`), que bloquea la activity
anfitriona a landscape de sensor y oculta las barras del sistema (inmersivo),
restaurando ambas al salir del reproductor; las barras vuelven con un swipe.
Android 16+ ignora el bloqueo de orientación en pantallas grandes. La activity
de Android declara el `configChanges` de orientación, así que rotar no la recrea
(ni reinicia el vídeo). El overlay oculta el botón de fullscreen donde la ventana
no puede hacerlo por sí misma (`WindowController.supportsFullScreen`), que es
Android; desktop lo mantiene.

En desktop, el fullscreen es un toggle del usuario que pasa por
`WindowController`, y salir del reproductor siempre lo desactiva para que vuelva
la navegación. En Windows el controlador cambia los estilos de la ventana con un
helper nativo (`WindowsWindowUtils.setUndecoratedFullscreen`): guarda los
estilos, el rect y el estado maximizado, quita `WS_CAPTION`/`WS_THICKFRAME`,
redimensiona al monitor y restaura todo al salir. Como el marco sigue siendo
nativo, no hay fullscreen exclusivo, ni baile de maximizado, ni empujón de
píxeles; la barra de título simplemente sale de la composición mientras dura el
fullscreen. Los demás targets de desktop todavía usan los workarounds de
`Floating`/exclusivo, que pueden eliminarse cuando adopten también el marco
custom.

La reproducción en segundo plano no está cableada todavía, así que
`MainActivity.onStop` pausa el reproductor salvo que el stop sea un cambio de
configuración; ese callback es la costura donde una media session y un servicio
en primer plano mantendrían la reproducción en su lugar. `PlayerService.pause()`
existe para esa llamada de ciclo de vida y nunca *inicia* la reproducción, a
diferencia de `playOrPause()`.

## Testing

`commonTest` cubre el dominio puro (playback target, reglas de visto, parsing de
año, resolver de badges con fakes) y los parsers/mappers defensivos. `jvmTest`
cubre los stores de fichero y los repositorios locales. El `commonTest` de
`:app:shared` cubre los ViewModels de sección con repositorios en memoria y
`Dispatchers.setMain`.

## Diferencias conocidas / limitaciones

- **El stream recordado es por dispositivo.** "Continue watching" reanuda directo
  al reproductor mientras la URL con la que se reprodujo siga fresca, pero ese
  registro (`last_streams.json`) es local: en un segundo dispositivo la fila se
  ve igual, solo que abre el selector de streams. Sincronizarlo necesita un
  backend propio (ver *Backlog de mejoras → Backends*).
- El reproductor de desktop usa el backend mpv de **mediamp**, que incluye un
  libmpv y FFmpeg a juego por plataforma: no hace falta instalar nada ni
  `PENGU_LIBMPV`. El runtime de Linux es GPL-3.0 (salida X11 de mpv); Windows y
  macOS son LGPL.
- Android usa el backend ExoPlayer (media3) de **mediamp**, que añade media3 y la
  UI de Compose al APK pero no necesita ningún runtime nativo empaquetado. media3
  solo acepta ficheros de subtítulos externos cuando el medio se prepara, así que
  elegir un subtítulo externo de addon recarga la fuente y vuelve a bufferizar
  brevemente en la misma posición; las pistas embebidas, la selección de audio y
  el resto no cambian.
- Las pantallas de metadata/edición de addons de Nuvio están disponibles en
  Ajustes → Addons (instalar, activar, reordenar y eliminar). El modo local
  mantiene su lista fija de `PENGU_LOCAL_ADDONS` en solo lectura, porque no hay
  backend donde escribir.

## Bugs conocidos

- **Dolby Vision profile 5 se ve con colores morados y verdes en desktop.**
  Solo el profile 5: es una capa única en ICtCp sin id de compatibilidad
  (`dv_bl_signal_compatibility_id: 0`) y no declara señalización de color
  utilizable, así que un renderer sin el reshaping de Dolby Vision no tiene a qué
  caer. Los profiles 7 y 8 van bien — llevan capa base HDR10
  (`compatibility_id: 1`) que el camino legacy tone-mapea —, y por eso "pone DV" y
  "es un Web-DL" son los dos criterios equivocados: el criterio es el perfil.
  Verificado con `ffprobe` en las muestras que fallan
  (`Hyper.Knife.S01E01…DV.H.265-ASiANLAD`,
  `Animals.2026.2160p.NF.WEB-DL…DV.H.265-FLUX`: `dv_profile: 5`) y en una que se ve
  bien (`dv_profile: 8`). El arreglo es el renderer, no el decodificador: mpv con
  `--vo=gpu-next` reproduce bien las dos y `--vo=gpu` rompe la de profile 5, y la
  render API de libmpv (la superficie de escritorio de aquí) solo implementa el
  backend GPU legacy. Nuvio lo esquiva dándole a mpv una ventana hija nativa
  (`vo=gpu-next` + `wid`) en lugar de una superficie de la render API. Como el
  perfil solo se puede leer del contenedor, la app no puede detectarlo desde el
  nombre del release, así que no hay mitigación barata.

## Backlog de mejoras

Elementos que necesitan una decisión de diseño, tocan varios módulos, o ambas
cosas, así que no son un cambio de un solo fichero. Están agrupados por el eje
que cambian, no por esfuerzo.

### Backends

- **Stremio es un backend completo, no solo un login.** Cambiar Nuvio por
  Stremio ya encaja en la costura `BackendKind` + `createBackend` y no necesita
  cambios en `ui/`, pero una cuenta usable también necesita `LibraryRepository`,
  `ProgressRepository` y `AddonRepository` contra el datastore de Stremio. El
  adaptador de auth es la parte pequeña; los repositorios de datos son el grueso.
- **Un backend propio, y lo que desbloquea.** La misma costura hace del backend un
  adaptador, así que reemplazar Nuvio es implementar esos cuatro repositorios
  contra una API nuestra. Lo primero que desbloquea es reanudar un título **en otro
  dispositivo**: hoy la URL vive en `last_streams.json`, que es local. Tres tablas,
  cada una por una razón que conviene escribir antes de congelar el esquema:
  `watch_progress` para identidad y posición, con `progress_key` único por perfil
  y la fórmula `contentId_s{s}e{e}` que la app ya usa; `progress_copy` para el
  texto que dibuja una tarjeta (título, póster, backdrop, título y thumbnail del
  episodio), así la fila no necesita ni el join con la biblioteca ni una petición
  de metadata por tarjeta; y `source_cache` para la URL de debrid y sus headers,
  **separada** del progreso porque el progreso se escribe cada pocos segundos y la
  URL casi nunca cambia. La URL debe llevar `resolved_at`/`expires_at` — un enlace
  de debrid es un token bearer firmado, así que una fila que lo sobreviva es una
  fuga — y el cliente sigue aplicando `player.streamUrlCache` al leer. Cuando
  llegue, `FileStreamCache` y `last_streams.json` se borran y la fila se sirve
  entera, lo que también elimina la petición de metadata de `unsavedItem`.

### Catálogos por defecto

- **Una fuente de catálogos de primera parte, activada por defecto.** Home
  incluiría seis filas construidas desde TMDB (tendencias, películas populares,
  series populares, mejor valoradas, próximos estrenos y una fila de género
  curada) para que la app sea útil en el primer arranque, sin un addon de
  catálogos instalado. Los addons se mantienen: siguen añadiendo filas y
  resolviendo la reproducción, no se eliminan. Esto necesita un servidor propio
  pequeño, porque la API key de TMDB tiene que vivir ahí y nunca en el cliente
  (una key embebida es extraíble). Las seis filas se componen y se cachean en
  una sola respuesta; la caché es TTL por fila (tendencias caduca en horas,
  populares y mejor valoradas en días), stale-while-revalidate para que nadie
  espere a una expiración, y single-flight para que una fila fría cueste una
  petición a TMDB en vez de una por cliente en espera, acotada y cacheando
  también las respuestas vacías. Las imágenes ya las cachea Coil (memoria +
  disco); el lever es el tamaño pedido, que `TmdbMapper` ya fija (`w500` /
  `w1280`).

  Hay dos cosas que diseñar. Los endpoints de lista de TMDB no devuelven
  `imdb_id`, así que pulsar un elemento del catálogo necesita el lookup a
  `/external_ids` antes de que los addons puedan resolver streams (la maquinaria
  de `TmdbIdResolver` ya existe para la pantalla de detalle); sin caché el Home
  pinta rápido pero cada pulsación paga un salto extra. Y hay que revisar los
  términos de uso de TMDB en cuanto a tiempo de caché, uso comercial y una key
  compartida única, con el servidor añadiendo su propio rate limiting por
  cliente. La costura en el repo es `BackendKind` + `createBackend`; `CatalogRef`
  hoy tiene forma de addon, así que las filas por defecto necesitan una ref
  agnóstica de la fuente o un decorador de `CatalogRepository` que una ambas
  fuentes y deduplique por `key`. Un módulo `:server` aparte deja al cliente
  hablando solo con sus propios DTOs, nunca con el JSON crudo de TMDB.

- **Dejar Nuvio por cuentas autoalojadas es una decisión aparte.** Poseer la
  creación de usuarios sustituye `NuvioAccountRepository` por hashing de
  contraseñas, manejo de sesión y refresh, y la autorización por filas que daba
  Supabase, además de migrar a los usuarios existentes. Comparte el módulo
  `:server` pero no el trabajo de catálogos, y merece la pena solo cuando el
  objetivo es la identidad en sí, no una forma de que funcionen los catálogos
  por defecto.

### Reproducción

- **La entrada al reproductor todavía corta.** La disolución del pre-roll está
  limpia, pero la entrada a él no: la ruta que abrió el reproductor se descarta
  (el reproductor se pinta fuera de la transición de ruta) y el backdrop se pide
  solo después de que el reproductor se compone, así que los primeros fotogramas
  pueden ser el splash negro esperando el artwork. Dos arreglos candidatos:
  llevar el backdrop y el logo que el detalle ya tiene por `StreamsRequest` →
  `PlaybackTarget` (o precargarlos al abrir el selector de streams), y hacer
  crossfade desde la pantalla saliente manteniéndola compuesta durante la
  disolución.
- **El runtime de Linux, cuando se aborde el target de Linux.** La raíz de
  composición de desktop declara solo los runtimes de Windows de libmpv/FFmpeg
  (`mediamp-mpv-runtime-windows-x64`, `mediamp-ffmpeg-runtime-windows-x64`); un
  build de Linux añade `mediamp-mpv-runtime-linux-x64` y
  `mediamp-ffmpeg-runtime-linux-x64` junto a ellos. El runtime de Linux es
  GPL-3.0, no LGPL, así que el licenciamiento de la distribución tiene que
  tenerlo en cuenta.
### Producto / UX

- **Pulido móvil y endurecimiento de release.** El layout compacto funciona,
  pero aún no está ajustado superficie por superficie: el espaciado y las
  proporciones todavía necesitan una pasada en teléfonos reales. El build de
  release de Android está minificado (`isMinifyEnabled` + `isShrinkResources`) e
  incluye los baseline profiles de librería (Compose y compañía) vía
  `profileinstaller`, pero todavía no tiene uno propio: generarlo requiere el
  plugin `androidx.baselineprofile` con una corrida de macrobenchmark en un
  dispositivo.
- **Navegación con teclado / D-pad.** `Interaction.kt` cubre estados hover,
  focus y pressed, y el reproductor tiene atajos, pero no hay recorrido de foco
  por los raíles de Home ni modelo de navegación espacial. Un target de TV lo
  hace obligatorio en vez de deseable.