# 🧩 Extensión de Navegador — Luna Fetch

Esta extensión conecta tu navegador (**Google Chrome**, **Microsoft Edge**, **Brave**, **Opera**) con la aplicación de escritorio **Luna Fetch** para enviar descargas de YouTube en alta calidad de forma instantánea.

---

## ⚡ Guía de Instalación Rápida (Paso a Paso)

### 1️⃣ Activar la integración en Luna Fetch
1. Abre **Luna Fetch** en tu equipo.
2. Haz clic en **Ajustes (⚙️)** y activa el interruptor **"Extensión de navegador"**.
   > 💡 *Esto registra automáticamente el controlador seguro (Native Messaging Host) en el registro de Windows.*

---

### 2️⃣ Cargar la Extensión en tu Navegador

1. Abre tu navegador e ingresa a la página de extensiones:
   - **Google Chrome**: `chrome://extensions`
   - **Microsoft Edge**: `edge://extensions`
   - **Brave**: `brave://extensions`
   - **Opera**: `opera://extensions`

2. En la esquina superior derecha, activa el **Modo de desarrollador** *(Developer mode)*.

3. Haz clic en el botón **"Cargar descomprimida"** *(Load unpacked)*.

4. Selecciona la carpeta **`browser-extension`** descargada de Luna Fetch.

---

## 🚀 ¿Cómo se usa?

Una vez instalada la extensión:
- Aparecerá el botón o icono de **Luna Fetch** en la barra de herramientas del navegador.
- Al navegar en **YouTube**, podrás pulsar el icono para enviar la URL del video directamente a Luna Fetch y descargarla sin copiar ni pegar enlaces manualmente.

### 📥 Envío de varios enlaces

- El popup **no se cierra** al enviar: puedes pegar el siguiente enlace y enviarlo
  sin volver a abrirlo.
- Cada enlace se **encola** en Luna Fetch y se descarga **uno tras otro**, en el
  orden en que se envió. La tarjeta "Cola de descargas" muestra cuántos enlaces
  faltan y el estado de cada uno.

---

## ☀️ Suno: importar la lista abierta de una sola vez

El importador publica en lote las canciones de la lista que tengas abierta en
Suno, captura el enlace público de cada una **sin copiarlo a mano** y las envía
a Luna Fetch. No hay que iniciar sesión otra vez: todo ocurre dentro de la
pestaña de Suno que ya tienes autenticada.

### Cómo se usa

1. Abre Suno y deja visible la lista de canciones (el espacio de trabajo).
2. Pulsa el icono de **Luna Fetch** en el navegador.
3. En la sección **☀️ Suno**, elige el formato y pulsa **«Importar lista abierta»**.
4. El importador se abre en la pestaña de Suno: publica lo que esté en privado,
   copia cada enlace y envía el lote.

En el panel del importador puedes cambiar de modo:

| Modo | Qué hace |
|---|---|
| **Publicar y enviar** | Publica lo que esté en privado, captura los enlaces y los envía. |
| **Publicar por API (acelerado)** | Aprende la llamada real de publicación de Suno y la repite sin clics. Si falla, vuelve al método de interfaz. |
| **Simulación (no publica)** | Solo informa: no publica ni envía nada. |
| **Solo enlaces (sin publicar)** | Captura los enlaces ya públicos, sin publicar nada. |

### Envío automático

- **«Autoenviar enlaces públicos de Suno»**: cualquier enlace `suno.com/s/…` o
  `suno.com/song/…` que copies dentro de Suno se encola solo.
- **«Enviar enlace copiado»**: lee el portapapeles del sistema y encola el
  enlace de Suno que contenga, sin abrir la pestaña.

### Sesión y cookies

La extensión envía las cookies de la sesión a la aplicación de escritorio
(siempre por `127.0.0.1`, a un fichero temporal local). Con ellas, Luna Fetch
lee la **ficha real** —título, autor, estilo, letra y portada— también en
canciones privadas, y las descargas de YouTube siguendiskupando las cookies
anti-bot.

### Modo diagnóstico

Si Suno cambia sus menús, activa **«Diagnóstico (volcar menús)»**: el panel
muestra el HTML del menú y del diálogo con el elemento exacto que se está
pulsando. Con esa información se calibran los selectores en una sola iteración.
