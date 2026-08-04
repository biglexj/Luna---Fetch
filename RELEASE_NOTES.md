# 🌌 Release Notes — Luna Fetch

> [!IMPORTANT]
> **Protocolo de Verificación de Versión en GitHub ("Lanzar actualización") [CRÍTICO]:**
> - Al recibir la orden de *"Lanzar actualización"*, es **OBLIGATORIO Y DE LEY** consultar primero la última versión publicada en GitHub / remoto (`gh release list` o `git ls-remote --tags`).
> - Si la versión local ya fue subida (así haya sido lanzada hace minutos), NUNCA se debe sobrescribir ni re-etiquetar. Se DEBE incrementar obligatoriamente a la siguiente versión de parche (e.g. `1.1.3` → `1.1.4`).
>
> **Sanitización de Notas (CRÍTICO):**
> - Los mensajes de las notas de lanzamiento DEBEN estar limpios de rutas de archivos del sistema local (ej. `d:\Proyectos\...`), nombres de variables internas, fragmentos de prompts o logs técnicos de depuración. Deben redactarse con lenguaje limpio, profesional y enfocado al usuario final.
>
> **Regla del .9 para Versionado:**
> - Nunca se debe pasar de una versión de parche `.9` (ej. de `1.0.9` no se pasa a `1.0.10`). Al alcanzar el límite del parche `.9`, se incrementa el número menor/secundario (ej. pasando a `1.1.0`).
> - De igual manera, al alcanzar el límite de la versión menor `1.9.9` (o ante hitos de arquitectura significativos), se debe saltar obligatoriamente al siguiente número mayor completo (`2.0.0`).
> - **Extensión proporcional en Release Notes:** La cantidad de párrafos depende del alcance: 1 para un hito pequeño, 2 cuando hay dos cambios relevantes, 3 como extensión habitual, 4 para hitos relativamente grandes y hasta 5 para lanzamientos de gran alcance. Cada párrafo debe concentrarse en un cambio principal y evitar descripciones excesivamente largas o listas detalladas de archivos.
> - **No duplicar versiones**: Si una versión ya está registrada localmente pero aún no se ha hecho push a Git, añadir los nuevos cambios bajo la misma versión activa en lugar de crear una nueva versión de parche. Simplemente añade los nuevos cambios dentro de la misma versión activa.

Registro histórico de cambios y versiones del proyecto.

## [1.1.7] — Auto-pegado Inteligente, Placeholder Reactivo, Layout Responsivo e Instalador de 1 solo Clic — 2026-08-04

Luna Fetch 1.1.7 introduce el auto-pegado inteligente de enlaces de YouTube (o plataformas soportadas) al abrir la aplicación si el portapapeles contiene una URL válida. El cuadro de entrada de enlace ahora oculta su texto de ayuda (*"Pega acá la URL..."*) tan pronto como recibe el foco o cursor del usuario.

Se añade un layout responsivo adaptativo que se divide en 2 filas en pantallas compactas o con escalado de fuente elevado, incorporando el nuevo botón dedicado `📋 Pegar` junto a `Analizar`. Además, en Android las notificaciones flotantes emergentes respetan dinámicamente el área del *status bar* y recorte de la cámara (*camera punch hole*).

En Windows, el instalador ejecutable (`.exe` y `.msi`) se optimiza para un flujo de instalación rápida de **1 solo clic** (`dirChooser = false`), instalándose directamente por usuario (`perUserInstall = true`) sin requerir permisos de administrador ni cuadros de elevación de UAC.

## [1.1.6] — Actualización In-App de Fricción Cero con Cierre y Auto-Reinicio — 2026-08-02

Luna Fetch 1.1.6 implementa el flujo completo de Actualizaciones In-App de Fricción Cero definido en el estándar de escritorio (`desktop_app_standards.md`). Al pulsar "Instalar y Reiniciar", la aplicación libera el bloqueo de instancia única (`SingleInstanceLock`), ejecuta el instalador silencioso en modo pasivo (`/passive`) en un proceso secundario desasociado y finaliza inmediatamente la instancia actual (`exitProcess(0)`).

Una vez que el instalador de Windows (.exe o .msi) reemplaza los binarios en segundo plano, la nueva versión de Luna Fetch se abre automáticamente sin requerir interacción manual del usuario.

## [1.1.5] — Sincronización Dinámica de Versión y Corrección de UI de Ajustes — 2026-08-02

Luna Fetch 1.1.5 introduce la centralización del número de versión mediante `AppConfig.APP_VERSION` en todo el código fuente Kotlin. Se corrige el bug donde la cabecera de la modal de Ajustes y el verificador de actualizaciones mantenían un string estático hardcodeado ("1.1.3"), lo cual provocaba avisos de actualización falsos positivos y una etiqueta de versión desfasada en la interfaz gráfica.

A partir de esta versión, la actualización del número de versión en `gradle.properties` actualiza automáticamente de forma síncrona `AppConfig.kt`, la interfaz gráfica y los verificadores de GitHub Releases mediante `build-release.ps1`.

## [1.1.4] — Adopción de Estándares de Escritorio, Single-Instance Lock y Memoria de Ventana — 2026-08-02

Luna Fetch 1.1.4 adopta los estándares de arquitectura de escritorio del ecosistema. Se implementa la Garantía de Instancia Única (Single-Instance Lock) mediante un socket de bucle local (`127.0.0.1:51235`), previniendo procesos duplicados e iconos repetidos en la bandeja de entrada (*System Tray*). Si la aplicación vuelve a lanzarse en producción, la instancia activa trae su ventana al frente y la nueva finaliza de inmediato; incluye bypass automático para el entorno de desarrollo (`isDev`).

Se incorpora la persistencia automática de las dimensiones, posición y estado de maximizado de la ventana (`isMaximized`) entre sesiones mediante `Preferences` de Java. Asimismo, se ajusta la duración de la notificación flotante emergente de actualizaciones a 4 segundos centrada en la parte superior y se estandariza el canal de soporte y retroalimentación mediante GitHub Issues.

## [1.1.3] — Corrección del Lanzador Nativo en Windows y Estabilización In-App — 2026-08-01

Luna Fetch 1.1.3 subsana la invocación del ejecutable de actualización en Windows al presionar el botón "Instalar". Se refactoriza el invocador de la plataforma de escritorio utilizando la ejecución directa de proceso nativo (`ProcessBuilder`), garantizando la apertura e instalación limpia del archivo ejecutable o MSI descargado sin depender de la API de AWT ShellExecute.

Se consolida la unificación del menú de Ajustes y Acerca de en un modal responsivo adaptado al 80% de ancho en dispositivos móviles. Asimismo, se formaliza la arquitectura modular por subsistemas de dominio (`header`, `download`, `history`, `logs`, `update` y `components`) e integra la guía paso a paso para la instalación de la extensión de navegador en Chrome, Edge, Brave y Opera.

## [1.1.2] — Unificación de Ajustes y Acerca de, Rediseño Modular de Carpetas y Temporizador de Estado — 2026-08-01

Luna Fetch 1.1.2 unifica el panel de "Acerca de Luna Fetch" dentro del modal de "Ajustes", eliminando la acumulación de botones independientes en la barra superior. Se rediseña la iconografía de Ajustes con un trazado de engranaje mecánico de 6 dientes trapezoidales y se añade un panel de actualización en tiempo real para los controladores del motor de extracción (`yt-dlp`), sanitizando las salidas de consola a mensajes limpios de interfaz.

Se refactoriza la arquitectura de la capa UI reorganizando orgánicamente los componentes en subsistemas por dominio funcional (`header`, `download`, `history`, `logs`, `update` y `components`). El modal de Ajustes se adapta de forma responsiva al 80% del ancho de pantalla en teléfonos móviles con márgenes verticales optimizados para interacción táctil.

Se incorpora un temporizador de 3 segundos con animaciones suaves de entrada y salida (`AnimatedVisibility`) para la comprobación manual de actualizaciones, garantizando que el estado aparezca centrado en el modal y se disuelva automáticamente sin mostrar avisos flotantes emergentes en la barra superior. Asimismo, se añade soporte para el registro automático del Native Messaging Host en Windows y la guía paso a paso `browser-extension/README.md` para la instalación de la extensión en Chrome, Edge, Brave y Opera.

## [1.1.1] — Optimización Visual de Modales en Móvil, Transición de Diálogos y Modo Oscuro — 2026-07-31

Luna Fetch 1.1.1 optimiza el diseño del Modal Central de Actualización fijando su contenedor al 80% del ancho de pantalla en dispositivos móviles (`usePlatformDefaultWidth = false`). Se simplifican los botones de acción a "Descargar" e "Instalar" con tipografía adaptativa de 14sp, eliminando cualquier desbordamiento de texto.

Se refactoriza la transición entre ventanas modales: al pulsar "Buscar actualizaciones" desde el panel "Acerca de", este se cierra de inmediato y despliega el modal de actualización en el mismo fotograma sin parpadeos ni destellos intermedios. Asimismo, el banner superior de notificación se oculta automáticamente mientras el modal central esté visible para evitar duplicidad de elementos.

Se incorpora un parseador y renderizador limpio de notas de versión en Markdown que convierte encabezados y negritas en texto estilizado con viñetas. Finalmente, se corrige la detección del tema oscuro del sistema en Android, eliminando el parpadeo y la regresión a modo claro que ocurría tras un segundo.

## [1.1.0] — Modal Central de Actualización e Instalación Nativa In-App — 2026-07-31

Luna Fetch 1.1.0 renueva por completo el sistema de actualizaciones. Se elimina la descarga incontrolada en segundo plano y se introduce un Modal Central Interactivo de Actualización en Compose que muestra en tiempo real el porcentaje de descarga (0-100%) y las notas de la versión.

Se implementa una descarga resiliente por flujo de red que sigue hasta 5 redirecciones HTTP (302/307) de GitHub Releases y valida la integridad del archivo ejecutable o APK (`PK\x03\x04`). Al completar la descarga, la aplicación permite ejecutar la instalación limpia e inmediata del paquete mediante `FileProvider` e intenciones nativas de Android sin errores de análisis de paquete.

## [1.0.9] — Estabilidad de TikTok, Historial Persistente y Tema Dinámico — 2026-07-27

Luna Fetch 1.0.9 resuelve las interrupciones en descargas de TikTok actualizando dinámicamente el motor a las compilaciones nocturnas de `yt-dlp` (`_NIGHTLY`) ante errores de extracción, eliminando el User-Agent de escritorio desfasado y preservando los parámetros esenciales en enlaces cortos (`vt.tiktok.com`).

Se garantiza el guardado del historial de descargas en `SharedPreferences` y `Preferences` mediante una serialización JSON tolerante a cambios, añadiendo refresco automático en tiempo real sin requerir reiniciar la aplicación. El cuadro de diálogo de Historial se ajusta al 88% del ancho de pantalla en móviles para prevenir recortes de texto.

Se aplica la arquitectura nativa JNA para consultar directamente el Registro de Windows (`AppsUseLightTheme`), permitiendo que el modo de tema "Sistema" reaccione instantáneamente en 1 segundo al alternar entre Modo Claro y Oscuro en Windows. Además, el panel de registro técnico se expande de forma continua al detectar errores y muestra un banner de advertencia claro al final de la pantalla.

## [1.0.8] — Experiencia Híbrida del Diálogo Acerca de y Optimización Móvil — 2026-07-25

Luna Fetch 1.0.8 optimiza la interfaz del panel "Acerca de Luna Fetch" mediante un diseño híbrido adaptativo para PC y teléfonos móviles. En PC, se mantiene el diseño de escritorio estilo LyraFlow con la comprobación de actualizaciones ubicada de forma accesible en la barra de acciones inferior.

En teléfonos móviles, la ventana se ajusta dinámicamente al 85% del ancho de pantalla con píldoras apiladas para donaciones (Yape, Plin y Web), Buy Me a Coffee, perfil de GitHub y comprobación de actualizaciones, garantizando una lectura limpia y sin recortes de texto.

## [1.0.7] — Auto-actualizador Nativo para Windows y Optimización de Publicaciones — 2026-07-25

Luna Fetch 1.0.7 amplía la experiencia de actualización silenciosa a Windows. Al detectar una nueva versión, la aplicación descarga automáticamente el instalador oficial a la carpeta de descargas del usuario y ejecuta la instalación sin requerir navegación manual externa.

Se incorporan reglas estrictas de sanitización de notas de versión en el sistema de plantillas y empaquetado, asegurando que todos los registros e historiales de cambios sean limpios, profesionales y orientados al usuario final.

## [1.0.6] — TikTok sin Marca de Agua, Material Expressive & Auto-Updater — 2026-07-25

Luna Fetch 1.0.6 introduce compatibilidad completa para la descarga de videos de TikTok sin marca de agua, desinfección de enlaces con parámetros de seguimiento e inclusión de cabeceras HTTP de navegador.

Se adopta el sistema de diseño Material 3 Expressive para Windows y Android con colores tonales vibrantes, botones en forma de píldora, tarjetas elevadas y menús emergentes sin tintes desalineados.

Se integra la comprobación y descarga directa de actualizaciones desde GitHub Releases, notificaciones del sistema de progreso en Android y la sección oficial de "Acerca de" con accesos a donaciones (Yape, Plin y Buy Me a Coffee).

## [1.0.5] — Refactorización y Estandarización Modular — 2026-07-24

Estandarización de la estructura del repositorio bajo las reglas del agente y refactorización modular de la interfaz de usuario en Compose. Se eliminó la deuda técnica de `LunaFetchApp.kt` dividiéndolo en 9 sub-componentes atomizados, mejorando la mantenibilidad y organización del proyecto.

## [1.0.4] — Extensión Web e Historial Unificado — 2026-07-24

Integración de la extensión oficial para navegadores Chromium (Chrome / Edge) con botón directo de descarga y sincronización silenciosa en segundo plano. Se unifica el historial de descargas entre cliente de escritorio y móvil, optimizando además las miniaturas a relación de aspecto 16:9 y mejorando la resiliencia contra verificaciones anti-bot.

## [1.0.3] — Audio con contexto — 2026-07-18

MP3 y M4A conservan ahora todos los metadatos y portada que entregue la fuente, sin asumir que cada audio es una canción. Las playlists y álbumes se detectan como colecciones, pueden descargarse completas y numeran sus pistas; cuando existen, su título e índice se incorporan como álbum y pista.

## [1.0.2] — APK por arquitectura — 2026-07-18

Android se distribuye ahora en APK firmados y separados para ARM64, ARM32 y x86_64: se eliminan el APK universal, x86 y AAB para reducir drásticamente las descargas. El selector de tema se simplifica a un único icono que rota entre Sistema, Claro y Oscuro.

## [1.0.1] — Migración Kotlin Multiplatform — 2026-07-16

Luna YT-DLP Downloader adopta el nombre **Luna Fetch** y migra de WPF/.NET a Kotlin Multiplatform con una interfaz Compose compartida para Windows, Linux y Android. La versión conserva análisis, formatos, calidades, miniaturas, progreso y logs, añade cancelación real y permite abrir el archivo descargado pulsando su tarjeta.

Android incorpora Material 3, color dinámico, almacenamiento mediante el selector del sistema y un motor local con Python, `yt-dlp` y FFmpeg. La distribución de escritorio adopta una cadena reproducible para EXE, MSI, DEB/RPM, firma y hashes.

## [1.0.0] — Lollipop — 2026-07-14

Primera versión WPF para Windows con análisis y descarga mediante `yt-dlp`, conversión con FFmpeg, selección de formato/calidad, tema claro/oscuro, progreso y consola técnica.
