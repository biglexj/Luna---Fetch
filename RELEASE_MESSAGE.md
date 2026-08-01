# 🌙 Luna Fetch 1.1.2

Unificación de Ajustes y Acerca de, rediseño del icono de engranaje, controladores del motor, temporizador de estado y guía de la extensión de navegador.

- ⚙️ **Modal de Ajustes Unificado**: Se unificaron las opciones del sistema, comprobador de controladores y sección "Acerca de Luna Fetch" en un solo modal responsivo (con icono de engranaje mecánico de 6 dientes).
- 🚗 **Controladores del Motor**: Panel de estado e instalador/actualizador en tiempo real para los binarios de extracción y conversión (`yt-dlp`), desinfectando salidas crudas de consola a mensajes limpios de UI.
- 📱 **Diseño Adaptativo Móvil**: Ajuste automático del modal al 80% de ancho en pantallas móviles con espaciado vertical cómodo para interacción táctil.
- ⏱️ **Temporizador de Estado (3s)**: Ocultación automática a los 3 segundos del mensaje de comprobación de la app con animaciones suaves de entrada y salida (`AnimatedVisibility`), libre de banners flotantes duplicados.
- 🧩 **Guía de Extensión de Navegador**: Registro automático del Native Messaging Host en Windows y creación de la guía `browser-extension/README.md` paso a paso para instalación en Chrome, Edge, Brave y Opera.
