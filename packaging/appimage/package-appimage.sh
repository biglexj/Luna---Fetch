#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

VERSION=$(grep -m1 '^lunafetch.versionName=' "${ROOT_DIR}/gradle.properties" | cut -d'=' -f2 | tr -d '\r\n')
if [ -z "${VERSION}" ]; then
    VERSION="1.0.0"
fi

OUTPUT_DIR="${ROOT_DIR}/release"
mkdir -p "${OUTPUT_DIR}"

APP_DIST="${ROOT_DIR}/composeApp/build/compose/binaries/main/app"
if [ ! -d "${APP_DIST}" ]; then
    APP_DIST="${ROOT_DIR}/composeApp/build/compose/binaries/main-release/app"
fi

if [ ! -d "${APP_DIST}" ]; then
    echo "[!] No se encontró el bundle de Compose Desktop. Ejecutando build..."
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :composeApp:createDistributable
fi

BUILD_DIR="${ROOT_DIR}/temp/appimage-build"
rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}/AppDir"
APPDIR="${BUILD_DIR}/AppDir"

echo "[*] Preparando estructura AppDir para Luna Fetch v${VERSION}..."
cp -r "${APP_DIST}/"* "${APPDIR}/"

# AppRun
cat << 'EOF' > "${APPDIR}/AppRun"
#!/usr/bin/env bash
HERE="$(dirname "$(readlink -f "${0}")")"
export PATH="${HERE}/bin:${PATH}"
export LD_LIBRARY_PATH="${HERE}/lib:${LD_LIBRARY_PATH}"
exec "${HERE}/bin/LunaFetch" "$@"
EOF
chmod +x "${APPDIR}/AppRun"

# Desktop file & Icons
cp "${SCRIPT_DIR}/lunafetch.desktop" "${APPDIR}/lunafetch.desktop"
cp "${ROOT_DIR}/icon/300x300.png" "${APPDIR}/lunafetch.png"
cp "${ROOT_DIR}/icon/300x300.png" "${APPDIR}/.DirIcon"

# AppImageTool detection/download
APPIMAGETOOL="${BUILD_DIR}/appimagetool"
if ! command -v appimagetool &> /dev/null; then
    if [ ! -f "${APPIMAGETOOL}" ]; then
        echo "[*] Descargando appimagetool..."
        curl -sL -o "${APPIMAGETOOL}" "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage" || \
        curl -sL -o "${APPIMAGETOOL}" "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
        chmod +x "${APPIMAGETOOL}"
    fi
    TOOL_CMD="${APPIMAGETOOL}"
else
    TOOL_CMD="appimagetool"
fi

APPIMAGE_OUTPUT="${OUTPUT_DIR}/LunaFetch-Linux-${VERSION}-x86_64.AppImage"
echo "[*] Generando AppImage: ${APPIMAGE_OUTPUT}..."
ARCH=x86_64 "${TOOL_CMD}" --no-appstream "${APPDIR}" "${APPIMAGE_OUTPUT}"

echo "[✓] AppImage generado exitosamente en: ${APPIMAGE_OUTPUT}"
