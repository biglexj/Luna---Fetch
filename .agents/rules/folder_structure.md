---
trigger: always_on
---

# 📁 Regla de Estructura de Carpetas — Luna Fetch

> [!CAUTION]
> Esta regla es **CRÍTICA y no negociable**. Todo nuevo archivo, carpeta o módulo creado por el agente DEBE seguir esta convención. Violar esta estructura es inaceptable y debe ser corregido inmediatamente.

## Estructura Raíz del Proyecto

```
Luna---Fetch/                           # Raíz del repositorio
├── .agents/rules/                      # Reglas del agente (folder_structure.md, design_system.md, ...)
├── composeApp/                         # Código fuente Kotlin Multiplatform / Compose UI
│   └── src/commonMain/kotlin/com/biglexj/lunafetch/
│       ├── core/                       # Tokens de diseño, temas y utilidades core
│       │   └── theme/                  # Temas Material 3 (Color, Type, Theme)
│       ├── domain/                     # Lógica de dominio, modelos e interfaces de motor
│       ├── feature/                    # Lógica de negocio y pantallas organizadas por dominio
│       │   └── components/             # Sub-componentes UI atomizados y modulares
│       └── pages/                      # Puntos de entrada de UI por plataforma si aplica
├── docs/                               # Documentación técnica y guías del proyecto
│   └── es/guides/                      # Guías de referencia en español
├── process/                            # Planificación, tareas, validación y aprobación
│   ├── active/                         # Procesos actualmente en ejecución
│   ├── completed/                      # Procesos validados y aprobados, por año
│   ├── archive/                        # Procesos cancelados o cerrados incompletos
│   └── templates/                      # Moldes locales para crear procesos
├── temp/                               # Archivos temporales de trabajo (ignorado en .gitignore)
├── scratch/                            # Scripts utilitarios de mantenimiento (solo raíz)
├── test/                               # Scripts de prueba temporales (ignorado en .gitignore)
├── .agents/rules/base.md               # Entrada única de instrucciones del agente
├── ROADMAP.md                          # Pendientes, prioridades e historial del producto
├── RELEASE_NOTES.md                    # Historial de cambios por versión
├── RELEASE_MESSAGE.md                  # Mensaje de anuncio del último lanzamiento
└── README.md                           # Documentación pública del proyecto
```

> [!NOTE]
> Adaptar el árbol anterior al stack tecnológico del proyecto (Kotlin Multiplatform / Compose). Mantener la misma **semántica de carpetas**: `feature/` para UI y lógica de negocio, `domain/` para modelos/interfaces, `core/` para diseño y utilidades compartidas, `docs/` para documentación. Las plantillas oficiales del ecosistema residen en `D:\Proyectos\biglexj\Docs\templates`.

## Reglas de Nomenclatura [CRÍTICO]

| Elemento | Convención | Ejemplo |
|---|---|---|
| Carpetas de feature | `PascalCase` o `camelCase` según stack | `components/`, `download/` |
| Archivos de componente | `PascalCase` + sufijo de tipo | `HeaderSection.kt`, `ProgressCard.kt` |
| Archivos de ruta/pantalla | `PascalCase` | `LunaFetchApp.kt` |
| Modelos / Data classes | `PascalCase` | `DownloadItem`, `YtdlpFormat` |
| Constantes | `SCREAMING_SNAKE_CASE` | `MAX_RETRY_COUNT` |
| Variables / funciones | `camelCase` | `startDownload()`, `isDownloading` |

## Reglas Estructurales Obligatorias

### ✅ PERMITIDO
- Crear sub-componentes dentro de `feature/components/` o carpetas específicas de cada feature.
- Crear componentes compartidos en `core/` o `feature/components/` si son usados por 2 o más pantallas.
- Usar `temp/` en la raíz para borradores o trabajos temporales del desarrollo activo.
- Usar `test/` en la raíz para scripts temporales de prueba.
- Usar `scratch/` en la raíz para scripts de mantenimiento, organizados en subcategorías.

### ❌ PROHIBIDO — VIOLACIONES COMUNES A EVITAR
- **Nunca** crear carpetas `scratch/` o `temp/` dentro de `composeApp/` o carpetas de código fuente.
- **Nunca** duplicar componentes UI: si ya existe en `components/`, importarlo; no copiarlo.
- **Nunca** añadir archivos de modelo/tipo directamente dentro de carpetas de UI sin separarlos en `domain/`.
- **Nunca** crear carpetas con nombres genéricos (`utils/`, `helpers/`, `misc/`) en la raíz del proyecto sin una categoría clara.

## Regla de Crecimiento de Archivos

Como buena práctica, se debe **evitar normalmente que un archivo supere las 800 - 900 líneas**. El límite máximo permitido es de **1000 a 1200 líneas** (pudiendo llegar excepcionalmente hasta **1220 líneas**). Los archivos que superen las **1200 - 1220 líneas** son **deuda técnica activa** y el agente DEBE proponer su división en sub-componentes y registrarlo en el ROADMAP como tarea de refactorización pendiente.

- **Límite Preferido**: Evitar exceder de 800 a 900 líneas por archivo.
- **Límite Máximo Absoluto**: 1000 a 1200 líneas (máximo 1220 líneas excepcionales).
- **Componentes y Screens**: Si un archivo supera las 1200 líneas, extraer sub-componentes en `feature/components/` o su feature respectiva.
