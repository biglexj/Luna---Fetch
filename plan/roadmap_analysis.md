# 📌 Análisis de Roadmap y Arquitectura de Planes — Luna Fetch

Este documento registra las decisiones tomadas tras la evaluación de prioridades y feedback del usuario.

## 🎯 Evaluaciones de Funcionalidades

### 1. Corrección del Modal "Acerca de"
- **Estado**: ✅ Completado e Implementado en `AppHeader.kt`.
- **Comportamiento**: Al presionar *"Buscar actualizaciones"*, el diálogo "Acerca de" NO se cierra si la aplicación ya está en la última versión. Mantiene el modal abierto y despliega la notificación `"¡Tienes la versión más reciente (v1.1.1)!"`. Si existe una nueva versión en GitHub Releases, cierra el modal "Acerca de" y despliega la ventana central de actualización.

### 2. Selección Fina de Colecciones (Playlists)
- **Estado**: 🔴 Urgente / Pendiente Activo.
- **Descripción**: Permitir que el usuario pegue un enlace de playlist de YouTube o colección de TikTok y pueda desmarcar/marcar items específicos en una lista previa a iniciar la descarga en lote.

### 3. Gestión Guiada de `yt-dlp` y FFmpeg
- **Estado**: 🔴 Urgente / Pendiente Activo.
- **Descripción**: Interfaz gráfica en el panel de Ajustes (Escritorio y Android) para consultar la versión actual instalada de `yt-dlp` / FFmpeg, forzar actualización manual y alternar entre canales Stable y Nightly.

### 4. Cola de Descargas Simultáneas
- **Estado**: 🔴 Urgente / Pendiente Activo.
- **Descripción**: Permitir descargas en paralelo limitadas por un valor configurable (ej. 1, 2, 4 descargas en simultáneo), encolando automáticamente los siguientes enlaces.

### 5. Funcionalidades Descartadas / En Pausa
- ⏸️ **Filtros e Historial Avanzado**: Búsqueda por nombre y ordenamiento descartados por el usuario por baja utilidad práctica.
- ⏸️ **Sincronización LAN**: Puesta en pausa por complejidad y poca frecuencia de uso.
- ⏸️ **Licenciamiento GPL-3.0**: Retirado del roadmap ya que el proyecto es de código abierto libre y no requiere trámites de licenciamiento para tiendas cerradas.

---

## 📈 Estrategia de Versionado y Calidad

- **Control de Calidad Obligatorio (Pruebas antes de Release)**: Toda modificación o lanzamiento de versión debe ser rigurosamente comprobada y probada antes de generar una nueva versión o etiquetar un parche, evitando publicaciones precipitadas con fallos de regresión.
- **Acumulación de Cambios**: Las correcciones menores se validan y prueban exhaustivamente antes de consolidarse en un hito de versión limpia (ej. `1.2.0` para Selección Fina de Playlists y Cola de Descargas).
