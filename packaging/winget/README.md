# Winget

Los manifiestos de esta carpeta apuntan al instalador EXE publicado en GitHub Releases (Inno Setup, plantilla Biglex Modern 2.0). El tipo de instalador declarado es `inno`, con `/VERYSILENT /SUPPRESSMSGBOXES /NORESTART` para la instalación silenciosa y `/SILENT /SUPPRESSMSGBOXES /NORESTART` para la silenciosa con progreso. Se validan localmente con:

```powershell
winget validate --manifest .\packaging\winget\manifests\b\biglexj\LunaFetch\1.0.2
```

El manifiesto no está versionado: lo genera `scripts\build\build-release.ps1` (función `Update-WingetManifest`) en el momento del release. La transición desde el bootstrapper WiX Burn (tipo `burn`) al instalador de Inno Setup (tipo `inno`) está documentada en [packaging/windows/MIGRATION.md](../windows/MIGRATION.md).

La presencia de estos archivos no implica que Luna Fetch ya esté disponible en el catálogo. La publicación requiere enviar y aprobar el manifiesto en `microsoft/winget-pkgs`.
