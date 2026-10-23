# Migración MSI → EXE (Inno Setup) — Luna Fetch

Documento de transición del empaquetado de Windows. Describe cómo conviven el
instalador anterior (MSI de WiX, lanzado por un bootstrapper Burn) y el nuevo
instalador EXE de Inno Setup con la plantilla Biglex Modern 2.0.

**Nada de esto borra instalaciones previas ni datos del usuario.** El cambio de
empaquetado no modifica la lógica de desinstalación del MSI ni la ubicación de
los datos de la aplicación.

## 1. Qué cambia

| | Antes | Ahora |
| --- | --- | --- |
| Artefacto público | `release\LunaFetch-Windows-<v>.exe` = bootstrapper **WiX Burn** que extrae el MSI y ejecuta `msiexec` | `release\LunaFetch-Windows-<v>.exe` = instalador **Inno Setup 6.7.3** (Biglex Modern 2.0) |
| Motor | Windows Installer (MSI) | Inno Setup |
| Instala en | `%LOCALAPPDATA%\LunaFetch` | `%LOCALAPPDATA%\Programs\LunaFetch` |
| Privilegios | Por usuario (`InstallScope="perUser"`) | Por usuario, sin elevación (`PrivilegesRequired=lowest`) |
| Flujo | Asistente WiX de 3 pasos (carpeta, instalación) | Entrada → progreso → éxito, todo por usuario |
| Código fuente del empaquetado | `package\windows\main.wxs`, `banner.bmp`, `dialog.bmp` | `packaging\windows\setup.iss` + plantilla local |

El MSI y sus recursos (`package\windows\*`) se conservan en el repositorio: son
el respaldo del empaquetado anterior y permiten reconstruir la línea histórica.
El flujo de build deja de publicar el bootstrapper Burn y publica el EXE de
Inno Setup en su lugar, con el mismo nombre de archivo y la misma URL de
GitHub Releases.

## 2. Identidades (son distintas y no se deben unificar)

| | MSI (anterior) | EXE Inno Setup (nuevo) |
| --- | --- | --- |
| Identificador | `UpgradeCode` `{EA999172-1299-4930-93C9-90EA30707C17}` (estable) y `ProductCode` propio de cada compilación (por ejemplo `{7756E96D-3A23-3DF7-B54A-A3C2C23BB658}` en 1.3.1) | `AppId` `{BE666925-88CF-4246-AA76-C1D171EA74E3}` (estable) |
| Clave de desinstalación | `HKLM\Software\Microsoft\Windows\CurrentVersion\Uninstall\{7756E96D-…}` (el MSI por usuario se registra en HKLM en esta máquina) | `HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\{BE666925-…}_is1` |
| Carpeta | `%LOCALAPPDATA%\LunaFetch` | `%LOCALAPPDATA%\Programs\LunaFetch` |
| Ejecutable | `%LOCALAPPDATA%\LunaFetch\LunaFetch.exe` | `%LOCALAPPDATA%\Programs\LunaFetch\LunaFetch.exe` |
| Registro propio | `HKCU\Software\biglexj\LunaFetch` (`installed`, `desktop_shortcut`) | no lo crea: esa clave pertenece al MSI |

**Por qué el `AppId` es propio y no el `UpgradeCode` del MSI.** Windows
Installer solo reconoce actualizaciones entre dos MSI que comparten
`UpgradeCode`. Un `AppId` de Inno Setup no participa de esa mecánica: aunque se
reutilizara el GUID del `UpgradeCode`, Windows Installer no vería la instalación
de Inno Setup, el EXE no quedaría registrado como una instalación de Windows
Installer y no habría una actualización recognized entre ambos formatos. El
GUID propio evita además que los dos instaladores compitan por la misma clave
de desinstalación y que uno se borre al desinstalar el otro.

## 3. Coexistencia

Los dos instaladores **pueden estar instalados al mismo tiempo**:

- Registran entradas de desinstalación distintas (ver la tabla anterior).
- Instalan en carpetas distintas.
- No comparten clave de registro de producto (`HKCU\Software\biglexj\LunaFetch`
  solo la crea el MSI).

Lo que **sí** comparten es el nombre de los accesos directos:

| Acceso directo | MSI | EXE Inno Setup |
| --- | --- | --- |
| Menú Inicio | `%APPDATA%\Microsoft\Windows\Start Menu\Programs\Luna Fetch\Luna Fetch.lnk` | `{autoprograms}\Luna Fetch.lnk` (misma ruta) |
| Escritorio | `%USERPROFILE%\Desktop\Luna Fetch.lnk` | `{autodesktop}\Luna Fetch.lnk` (misma ruta) |

Ambos escriben el mismo archivo de acceso directo, así que **el último que se
instala deja el acceso directo apuntando a su carpeta**. Al desinstalar el MSI,
este borra los accesos directos con su nombre: el EXE queda instalado pero sin
accesos directos. Se recuperan creando de nuevo el acceso directo desde
`%LOCALAPPDATA%\Programs\LunaFetch\LunaFetch.exe`.

### Datos del usuario (no se tocan)

La aplicación guarda sus datos **fuera** de la carpeta de instalación:

- Manifiesto del host nativo de la extensión del navegador:
  `%APPDATA%\LunaFetch\com.biglexj.lunafetch.json` y las claves
  `HKCU\Software\Google\Chrome\NativeMessagingHosts\com.biglexj.lunafetch` y
  `HKCU\Software\Microsoft\Edge\NativeMessagingHosts\com.biglexj.lunafetch`.
- Preferencias e historial: preferencias de Java en
  `HKCU\Software\JavaSoft\Prefs\…`.

Ninguno de esos paths está dentro de `%LOCALAPPDATA%\LunaFetch` ni de
`%LOCALAPPDATA%\Programs\LunaFetch`, por lo que **desinstalar cualquiera de los
dos instaladores no borra los datos de la aplicación**. El desinstalador de
Inno Setup solo elimina los archivos que él mismo instaló.

## 4. Cómo desinstalar el MSI antes de instalar el EXE

**Opción recomendada (gráfica):** **Configuración → Aplicaciones →
Aplicaciones instaladas** → entrada **LunaFetch** → **Desinstalar**. Windows
Installer ejecuta `MsiExec.exe /X{…}` con el `ProductCode` correcto de la
versión instalada, que cambia en cada compilación.

**Opción por consola**, con el `ProductCode` de la versión instalada (en esta
máquina, 1.3.1):

```powershell
msiexec /x {7756E96D-3A23-3DF7-B54A-A3C2C23BB658}
```

El `UpgradeCode` `{EA999172-1299-4930-93C9-90EA30707C17}` **no sirve** para
desinstalar: `msiexec /x` exige un `ProductCode` o la ruta del `.msi`, no un
`UpgradeCode` (ese solo sirve para agrupar upgrades). Por eso el ejemplo
anterior usa el `ProductCode`.

**Diferenciar las dos entradas en "Agregar o quitar programas":** la del MSI
publica el editor `biglexj` y el desinstalador `MsiExec.exe`; la del EXE de
Inno Setup publica `Biglex` y el desinstalador `unins000.exe` dentro de
`%LOCALAPPDATA%\Programs\LunaFetch`.

Comprobación rápida de qué hay instalado:

```powershell
Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
                 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*' |
  Where-Object DisplayName -like '*Luna*' |
  Select-Object DisplayName, DisplayVersion, InstallLocation, UninstallString
```

## 5. Winget

Los manifiestos de winget **no están versionados** en el repositorio: los
genera `scripts\build\build-release.ps1` (función `Update-WingetManifest`) en
el momento del release y se publican como artefacto. Como el artefacto público
deja de ser un bootstrapper Burn y pasa a ser un instalador de Inno Setup, el
manifiesto se actualizó:

- `InstallerType: burn` → `InstallerType: inno`
- `Silent: /quiet` → `Silent: /VERYSILENT /SUPPRESSMSGBOXES /NORESTART`
- `SilentWithProgress: /passive` → `SilentWithProgress: /SILENT /SUPPRESSMSGBOXES /NORESTART`

`UpgradeBehavior: install` se conserva: Inno Setup actualiza sobre la
instalación anterior del mismo `AppId` sin crear una instalación paralela.

## 6. El actualizador automático de la app (ajuste aplicado)

La app descarga el instalador publicado y lo ejecuta con sus propios
parámetros. Con el bootstrapper WiX Burn usaba `/passive`; con el instalador
de Inno Setup se cambió a los parámetros propios de Inno:

```kotlin
// composeApp/src/desktopMain/kotlin/com/biglexj/lunafetch/platform/DesktopPlatformBindings.kt
"timeout /t 2 /nobreak > nul & start /wait \"\" \"$absPath\" /SILENT /SUPPRESSMSGBOXES /NORESTART & start \"\" \"$currentExePath\""
```

`/SILENT` muestra el progreso pero no hace preguntas: es el equivalente de
`/passive`. `/SUPPRESSMSGBOXES` evita que una confirmación interrumpa la
actualización y `/NORESTART` impide reiniciar el equipo.

El cambio es compatible con ambas líneas: WiX Burn ignora los parámetros que no
conoce, de modo que el instalador descargado todavía puede ser el EXE de una
versión anterior mientras se completa la transición. No se añadió `/NOSTYLE`:
los usuarios con contraste alto necesitan el estilo por defecto.

Alternativa de packaging descartada: el instalador podría traducir `/passive`
a modo silencioso relanzándose a sí mismo, pero Inno Setup no expone al script
la ruta del EXE original (`{cmd}` devuelve la línea de órdenes del proceso
host y `ParamStr(0)` devuelve la copia temporal interna del setup), por lo que
el relanzamiento no es fiable.

## 7. Referencia rápida

```powershell
# Compilar el instalador (regenera version.iss, prepara staging, invoca ISCC)
.\scripts\packaging\Build-Installer.ps1

# Solo compilar, reutilizando el app-image de jpackage existente
.\scripts\packaging\Build-Installer.ps1 -SkipAppBuild -SkipSigning

# Instalar en silencio (para pruebas)
& ".\release\LunaFetch-Windows-1.3.1.exe" /VERYSILENT /SUPPRESSMSGBOXES /NORESTART

# Desinstalar la copia de Inno Setup
& "$env:LOCALAPPDATA\Programs\LunaFetch\unins000.exe" /VERYSILENT
```
