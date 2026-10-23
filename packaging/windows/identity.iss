; ---------------------------------------------------------------------------
; identity.iss - identidad de instalacion de Luna Fetch (Biglex Modern 2.0)
; ---------------------------------------------------------------------------
; MyAppId es la identidad de actualizacion de ESTE instalador (Inno Setup).
; Deliberadamente es un GUID propio y no el UpgradeCode del MSI
; (ea999172-1299-4930-93c9-90ea30707c17): Windows Installer no reconoce
; actualizaciones entre un MSI y un EXE de Inno Setup, y reutilizar ese GUID
; haria que ambos se competingieran por la misma clave de desinstalacion.
; Ver packaging\windows\MIGRATION.md.
; ---------------------------------------------------------------------------
#ifndef MyAppName
  #define MyAppName "Luna Fetch"
#endif
#ifndef MyAppId
  #define MyAppId "{BE666925-88CF-4246-AA76-C1D171EA74E3}"
#endif
#ifndef MyAppDirectory
  #define MyAppDirectory "LunaFetch"
#endif
#ifndef MyAppExe
  #define MyAppExe "LunaFetch.exe"
#endif
#ifndef MyAppIcon
  #define MyAppIcon "LunaFetch.ico"
#endif
#ifndef MyAppTheme
  #define MyAppTheme "dynamic"
#endif
; Carpeta que contiene la distribucion de la app (app-image de jpackage).
; Por defecto "app", relativa al directorio de este script. El script de
; build la sobreescribe con la ruta del staging: /DMyAppPayloadDir=<ruta>
#ifndef MyAppPayloadDir
  #define MyAppPayloadDir "app"
#endif

#ifndef MyAppSubtitle
  #define MyAppSubtitle "Tus vídeos y música, siempre a mano."
#endif
