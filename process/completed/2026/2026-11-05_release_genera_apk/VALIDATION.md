# Validación

## Causa

La release se caía en dos sitios, siempre antes de copiar el APK a `release/`:

1. **La fecha del ejecutable.** Tras el build, el script refresca la fecha de
   `composeApp\build\compose\binaries\main\app\LunaFetch\LunaFetch.exe` para
   que la comprobación de frescura del instalador pase. jpackage deja ese
   fichero con el atributo de solo lectura y en Windows no se puede cambiar la
   fecha de un fichero de solo lectura: `Excepción al establecer
   "LastWriteTime": "Acceso denegado"`. La excepción abortaba el script.
2. **Los módulos de PowerShell.** `Get-AuthenticodeSignature` (Microsoft.
   PowerShell.Security) y `Get-FileHash` (Microsoft.PowerShell.Utility) no
   estaban disponibles: «el módulo no pudo cargarse». La causa es que el
   proceso hereda el `PSModulePath` de PowerShell 7 —la carpeta WindowsApps entra
   al abrir la terminal—, de modo que Windows PowerShell 5.1 cargaba los
   módulos de PS7, donde esos cmdlets no existen o fallan con choques de
   TypeData.

## Correcciones

- La fecha del ejecutable se refresca quitando antes el atributo de solo
  lectura, y si aun así falla se avisa sin detener la release.
- `ReleaseTools.ps1` fija un `PSModulePath` propio (los módulos de Windows
  PowerShell más los del usuario) antes de usar ningún cmdlet de módulo.
- `Assert-SignedArtifact` comprueba la firma con `Get-AuthenticodeSignature` y,
  si el módulo no carga, lee el certificado del fichero firmado directamente por
  .NET.
- Cada plataforma se compila en su propio bloque `try/catch`: un fallo en
  Windows no deja el APK sin generar, y los errores se informan juntos al
  final.

## Comprobaciones

- `mise release` (local): termina con código 0 y deja en `release/`:
  - `LunaFetch-Windows-1.2.1.exe` (58 MB, firmado con el certificado de
    desarrollo).
  - `LunaFetch-Android-1.2.1-debug.apk` (206 MB, variante debug por ser
    compilación local).
  - `SHA256SUMS.txt` con la suma de ambos.
- El APK se validó con `aapt dump badging`:
  `name='com.biglexj.lunafetch' versionCode='25' versionName='1.2.1'`.
- No se ha publicado nada en GitHub: la compilación local se detiene antes del
  commit y de la etiqueta.
