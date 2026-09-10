#!/usr/bin/env bash
set -e

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RELEASE_DIR="${ROOT_DIR}/release"
mkdir -p "${RELEASE_DIR}"

VERSION=$(grep -m1 '^lunafetch.versionName=' "${ROOT_DIR}/gradle.properties" | cut -d'=' -f2 | tr -d '\r\n')
if [ -z "${VERSION}" ]; then
    echo "[!] Error: No se pudo leer la versión de gradle.properties." >&2
    exit 1
fi

echo "========================================================"
echo "   🌙 Luna Fetch - Empaquetador para Linux v${VERSION}"
echo "========================================================"

# Sincronizar versión en PKGBUILD
sed -i "s/^pkgver=.*/pkgver=${VERSION}/" "${ROOT_DIR}/packaging/arch/PKGBUILD"

# 1. Compilar Distributable y paquete .deb con Compose Desktop
echo "[1/4] Compilando con Gradle (Distributable + DEB)..."
"${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :composeApp:createDistributable :composeApp:packageDeb

# Copiar .deb a la carpeta release
DEB_SRC=$(find "${ROOT_DIR}/composeApp/build/compose/binaries/main/deb" -name "*.deb" 2>/dev/null | head -n1 || true)
if [ -n "${DEB_SRC}" ] && [ -f "${DEB_SRC}" ]; then
    DEB_DEST="${RELEASE_DIR}/LunaFetch-Linux-${VERSION}.deb"
    cp "${DEB_SRC}" "${DEB_DEST}"
    echo "[✓] Paquete Debian creado: ${DEB_DEST}"
fi

# 2. Empaquetar para Arch Linux (.pkg.tar.zst)
echo "[2/4] Generando paquete para Arch Linux..."
if command -v makepkg &> /dev/null; then
    (
        cd "${ROOT_DIR}/packaging/arch"
        makepkg -f --nodeps
        ARCH_PKG=$(ls -t lunafetch-bin-*.pkg.tar.zst 2>/dev/null | head -n1 || true)
        if [ -n "${ARCH_PKG}" ]; then
            cp "${ARCH_PKG}" "${RELEASE_DIR}/LunaFetch-Arch-${VERSION}-x86_64.pkg.tar.zst"
            echo "[✓] Paquete Arch Linux creado: ${RELEASE_DIR}/LunaFetch-Arch-${VERSION}-x86_64.pkg.tar.zst"
        fi
    )
else
    echo "[-] makepkg no está disponible en este entorno; se omite generación directa de .pkg.tar.zst (el PKGBUILD está listo en packaging/arch/)."
fi

# 3. Generar AppImage portable
echo "[3/4] Generando AppImage portable..."
if [ -f "${ROOT_DIR}/packaging/appimage/package-appimage.sh" ]; then
    bash "${ROOT_DIR}/packaging/appimage/package-appimage.sh"
fi

# 4. Generar Hashes SHA256
echo "[4/4] Calculando firmas SHA-256..."
(
    cd "${RELEASE_DIR}"
    if command -v sha256sum &> /dev/null; then
        sha256sum LunaFetch-Linux-* LunaFetch-Arch-* 2>/dev/null > "SHA256SUMS-Linux.txt" || true
    fi
)

echo "========================================================"
echo "   ✨ Artefactos generados en: ${RELEASE_DIR}"
ls -lh "${RELEASE_DIR}"
echo "========================================================"
