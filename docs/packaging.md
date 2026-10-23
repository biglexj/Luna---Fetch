# Empaquetado de Luna Fetch

La versión se centraliza en `gradle.properties`. En Windows el instalador es un EXE de **Inno Setup 6** con la plantilla compartida **Biglex Modern 2.0** (`packaging/windows/`); Compose Desktop aporta la distribución de la app (app-image de jpackage) y el empaquetador sigue generando también EXE/MSI de Bootstrap WiX, DEB y RPM como empaquetado histórico de Linux/Windows; Android Gradle Plugin genera APK separados para ARM64, ARM32 y x86_64.

El instalador de Windows se compila por su cuenta:

```powershell
.\scripts\packaging\Build-Installer.ps1
```

El script regenera `packaging/windows/version.iss` con la versión real, prepara en staging el app-image de jpackage, invoca `ISCC.exe` y deja `release/LunaFetch-Windows-<versión>.exe`. La transición desde el bootstrapper WiX Burn y el MSI está documentada en [packaging/windows/MIGRATION.md](../packaging/windows/MIGRATION.md).

## Build local
 
### Windows y Android
```powershell
.\scripts\build\build-release.ps1 -LocalOnly -SkipSigning
```

### Linux (Arch Linux, AppImage, DEB)
```bash
./scripts/build/build-linux.sh
```

El flujo ejecuta pruebas de escritorio, compila Android, prepara el instalador EXE de Windows (Inno Setup) y el bootstrapper WiX histórico, copia tres APK de depuración por arquitectura y calcula `SHA256SUMS.txt`. En Linux genera `.pkg.tar.zst`, `.AppImage` y `.deb`. Los artefactos quedan en `release/`.

Para firmar Windows localmente, coloca `LunaFetch_Dev_Certificate.pfx` en la raíz y omite `-SkipSigning`. El publicador esperado es `CN=biglexj`. El certificado y las claves Android están ignorados por Git.

## Publicación oficial

Antes de publicar Android crea `keystore.properties` en la raíz:

```properties
storeFile=signing/lunafetch-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Como alternativa, configura:

- `LUNAFETCH_ANDROID_KEYSTORE`
- `LUNAFETCH_ANDROID_STORE_PASSWORD`
- `LUNAFETCH_ANDROID_KEY_ALIAS`
- `LUNAFETCH_ANDROID_KEY_PASSWORD`

Después ejecuta:

```powershell
.\scripts\build\build-release.ps1
```

La publicación exige `main` sincronizada, GitHub CLI autenticado, certificado Windows y firma Android permanente. Valida la firma, identidad y ABI de cada APK, además de los paquetes Windows; después crea commit, tag, push atómico y GitHub Release con el instalador EXE de Windows, los APK ARM64/ARM32/x86_64 y hashes.

MSIX y Microsoft Store están descartados. Winget usa un manifiesto separado que apunta al instalador EXE publicado en GitHub, con tipo de instalador `inno`; aparecer en el catálogo depende de la aceptación del manifiesto en `microsoft/winget-pkgs`.

Para Linux se utiliza el empaquetador `./scripts/build/build-linux.sh` y la receta [packaging/arch/PKGBUILD](../packaging/arch/PKGBUILD).

