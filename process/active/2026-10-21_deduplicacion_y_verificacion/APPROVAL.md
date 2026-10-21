# Aprobación: Deduplicación y Verificación de Archivos

Fecha: 2026-10-21
Estado: **Aprobado por Biglex** (decisiones tomadas antes de implementar)

## Decisiones

### 1. ¿Qué cuenta como "link duplicado" que se ignora?

**Duplicado en la cola + duplicado en el historial.**

Se ignoran los enlaces que ya están pendientes o descargándose en la cola, y
los que ya fueron descargados cuando su archivo sigue en pie.

### 2. ¿Cómo se determina que el archivo está dañado?

**Existe + tamaño mayor que cero.**

Si el archivo no existe, fue movido o eliminado: no está dañado y se descarga
de nuevo con normalidad. Si existe pero está vacío, está dañado: se rehace la
descarga y se reemplaza en el mismo sitio.

### 3. ¿Cómo se reemplaza el archivo dañado?

**Reemplazo en el mismo sitio con `--force-overwrites`.**

yt-dlp no sobrescribe archivos existentes por defecto, así que la opción se
añade solo cuando la descarga es una reparación.

## Alcance de la aprobación

Cubre la implementación de la regla de duplicados y de verificación de
archivos. Quedan fuera de este trabajo:

- La verificación de integridad más profunda (cabecera del contenedor y tamaño
  esperado por duración): se puede añadir más adelante sobre `LocalFileState`.
- La selección fina de elementos de una colección, ya listada en la hoja de ruta.
- El empaquetado del `.crx` de la extensión, que depende de Chrome.
