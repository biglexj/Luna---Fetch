<#
.SYNOPSIS
    Compila el instalador de Windows de Luna Fetch (Inno Setup, Biglex Modern 2.0).
.DESCRIPTION
    Regenera packaging\windows\version.iss con la versión real, prepara en staging
    la distribución de la app (app-image de jpackage) y compila el instalador con
    ISCC.exe. El instalador queda en la carpeta de release del proyecto.

    Es idempotente: se puede volver a ejecutar sin tocar el flujo de release.
    Lo invoca scripts\build\build-release.ps1, pero también se puede usar suelto.
.PARAMETER Version
    Versión a empaquetar. Por defecto se lee de gradle.properties.
.PARAMETER SkipAppBuild
    No ejecuta la tarea Gradle :composeApp:createDistributable (usa el app-image
    que ya exista en la carpeta de build).
.PARAMETER SkipSigning
    No firma el instalador resultante.
.PARAMETER OutputDir
    Carpeta donde se deja el instalador. Por defecto, <proyecto>\release.
.PARAMETER IsccPath
    Ruta a ISCC.exe. Por defecto se busca subiendo desde este script hasta
    <workspace>\Scripts\tools\InnoSetup\ISCC.exe y, si no, en el PATH.
.PARAMETER ForceStale
    Empaqueta aunque los artefactos sean mas viejos que el codigo fuente.
    Solo para depurar: publicar asi reintroduce el build obsoleto.
.EXAMPLE
    .\scripts\packaging\Build-Installer.ps1
#>
[CmdletBinding()]
param(
    [string]$Version,
    [switch]$SkipAppBuild,
    [switch]$SkipSigning,
    [string]$OutputDir,
    [string]$IsccPath = '',

    # Empaqueta aunque los artefactos sean mas viejos que el codigo fuente.
    # Solo para depurar: publicar asi reintroduce el build obsoleto.
    [switch]$ForceStale
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
. (Join-Path $root "scripts\release\ReleaseTools.ps1")

$packagingDir = Join-Path $root "packaging\windows"
$setupScript = Join-Path $packagingDir "setup.iss"
$versionScript = Join-Path $packagingDir "version.iss"

# --- Localizador de ISCC -----------------------------------------------------
# Localiza ISCC.exe sin rutas absolutas: primero la copia del workspace,
# subiendo desde este script; despues el PATH del sistema.
function Resolve-Iscc {
    $dir = $PSScriptRoot
    for ($i = 0; $i -lt 8; $i++) {
        $candidate = Join-Path $dir 'Scripts\tools\InnoSetup\ISCC.exe'
        if (Test-Path -LiteralPath $candidate) { return $candidate }
        $parent = Split-Path -Parent $dir
        if (-not $parent -or $parent -eq $dir) { break }
        $dir = $parent
    }
    $onPath = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    return $null
}

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Fail {
    param([string]$Message)
    Write-Host "ERROR: $Message" -ForegroundColor Red
    throw $Message
}

# --- Invariantes de empaquetado (validador compartido) ---------------------
# La plantilla visual y el arranque post-instalacion son fuente unica del
# workspace. Si un .iss editado a mano o copiado de otra app los rompe, el
# instalador sale mal, asi que el validador compartido aborta el build.
function Resolve-SharedInnoTool {
    param([string]$Relative)
    $dir = $PSScriptRoot
    for ($i = 0; $i -lt 8; $i++) {
        $candidate = Join-Path $dir $Relative
        if (Test-Path -LiteralPath $candidate) { return $candidate }
        $parent = Split-Path -Parent $dir
        if (-not $parent -or $parent -eq $dir) { break }
        $dir = $parent
    }
    return $null
}

function Assert-PackagingInvariants {
    param([string]$SetupIss, [string]$PackagingDir)

    $sharedDir = Resolve-SharedInnoTool 'Scripts\tools\inno'
    if (-not $sharedDir) {
        Write-Host "AVISO: no se localizo Scripts\tools\inno en este workspace: se omite la verificacion de empaquetado." -ForegroundColor Yellow
        return
    }

    # Plantilla visual con fuente unica: el repo guarda lo que se compilo.
    $sharedUi = Join-Path $sharedDir 'BiglexModernUI.iss'
    $localUi = Join-Path $PackagingDir 'BiglexModernUI.iss'
    if ((Test-Path -LiteralPath $sharedUi) -and (Test-Path -LiteralPath $localUi)) {
        Copy-Item -LiteralPath $sharedUi -Destination $localUi -Force
        Write-Host "Plantilla visual sincronizada con la copia compartida del workspace." -ForegroundColor Gray
    }

    $checker = Join-Path $sharedDir 'Check-Packaging.ps1'
    if (-not (Test-Path -LiteralPath $checker)) {
        Write-Host "AVISO: no se localizo Check-Packaging.ps1: se omite la verificacion de empaquetado." -ForegroundColor Yellow
        return
    }
    $shell = Get-Command pwsh.exe -ErrorAction SilentlyContinue
    if (-not $shell) { $shell = Get-Command powershell.exe -ErrorAction SilentlyContinue }
    if (-not $shell) {
        Write-Host "AVISO: no hay pwsh.exe ni powershell.exe: se omite la verificacion de empaquetado." -ForegroundColor Yellow
        return
    }

    Write-Host "Verificando las invariantes de empaquetado (Check-Packaging.ps1)..." -ForegroundColor Cyan
    & $shell.Source -NoProfile -ExecutionPolicy Bypass -File $checker -SetupIss $SetupIss -Quiet
    if ($LASTEXITCODE -ne 0) {
        Write-Fail ("Las invariantes de empaquetado no se cumplen (Check-Packaging.ps1 devolvio $LASTEXITCODE). " +
                    "Revisa el mensaje del validador de arriba y corrige el setup.iss antes de compilar.")
    }
    Write-Host "Invariantes de empaquetado correctas." -ForegroundColor Green
}

# --- Candado anti-build-obsoleto -------------------------------------------
# Si el artefacto que se empaqueta es mas viejo que el codigo fuente, el
# instalador queda "recien compilado" pero lleva dentro una app vieja.
# Regla de Core: Docs/global/architecture/command-scripts-standard.md,
# principio 2.
# SHA-256 con la API de .NET en lugar de Get-FileHash: ese cmdlet no esta
# disponible en el modulo Microsoft.PowerShell.Utility cuando `powershell` se
# invoca con -NoProfile, que es como lo llama el pipeline de release.
function Get-Sha256Hex {
    param([Parameter(Mandatory)][string]$LiteralPath)
    $algo = [System.Security.Cryptography.SHA256]::Create()
    $stream = $null
    try {
        $stream = [System.IO.File]::OpenRead($LiteralPath)
        return ([BitConverter]::ToString($algo.ComputeHash($stream))).Replace('-', '')
    }
    finally {
        if ($stream) { $stream.Dispose() }
        $algo.Dispose()
    }
}

function Get-NewestWriteTime {
    param([string[]]$Paths)
    $best = $null
    $bestPath = $null
    foreach ($path in $Paths) {
        if (-not (Test-Path -LiteralPath $path)) { continue }
        $item = if (Test-Path -LiteralPath $path -PathType Container) {
            Get-ChildItem -LiteralPath $path -Recurse -File -Force -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending | Select-Object -First 1
        }
        else {
            Get-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
        }
        if ($item -and (-not $best -or $item.LastWriteTime -gt $best)) {
            $best = $item.LastWriteTime
            $bestPath = $item.FullName
        }
    }
    return [pscustomobject]@{ Time = $best; Path = $bestPath }
}

function Assert-BuildFresh {
    param([string[]]$Artifacts, [string]$AppRoot, [string[]]$SourceRoots, [switch]$Force)
    $exeItem = $null
    foreach ($a in $Artifacts) {
        if (Test-Path -LiteralPath $a) {
            $i = Get-Item -LiteralPath $a
            if (-not $exeItem -or $i.LastWriteTime -gt $exeItem.LastWriteTime) { $exeItem = $i }
        }
    }
    if (-not $exeItem) {
        Write-Fail "No se encontro ningun artefacto compilado de: $($Artifacts -join ', ')`n  Compila la app sin el flag de omision."
    }
    $watch = @($SourceRoots)
    $watch += @(
        (Join-Path $AppRoot 'gradle.properties'),
        (Join-Path $AppRoot 'build.gradle.kts'),
        (Join-Path $AppRoot 'settings.gradle.kts')
    )
    $newest = Get-NewestWriteTime -Paths $watch
    if (-not $newest.Time) { return }
    if ($exeItem.LastWriteTime -ge $newest.Time) {
        Write-Step "Artefacto al dia: $($exeItem.LastWriteTime.ToString('dd/MM HH:mm:ss'))  $($exeItem.Name)"
        return
    }
    $rel = if ($newest.Path) { $newest.Path.Substring($AppRoot.Length).TrimStart('\') } else { '(desconocido)' }
    if ($Force) {
        Write-Host "AVISO: se empaqueta un build OBSOLETO por -ForceStale." -ForegroundColor Yellow
        Write-Host "       artefacto $($exeItem.LastWriteTime.ToString('dd/MM HH:mm:ss')) < fuente $($newest.Time.ToString('dd/MM HH:mm:ss')) $rel" -ForegroundColor Yellow
        return
    }
    Write-Fail ("El artefacto a empaquetar es MAS VIEJO que el codigo fuente:`n" +
                "  artefacto : $($exeItem.LastWriteTime.ToString('dd/MM HH:mm:ss'))  $($exeItem.FullName)`n" +
                "  fuente    : $($newest.Time.ToString('dd/MM HH:mm:ss'))  $rel`n`n" +
                "Vuelve a compilar la app (sin el flag de omision), o usa -ForceStale solo para depurar.")
}

if (-not (Test-Path -LiteralPath $setupScript)) { throw "No se encontró $setupScript" }
if (-not $IsccPath) { $IsccPath = Resolve-Iscc }
if (-not $IsccPath) { Write-Fail 'No se localizo ISCC.exe en Scripts\tools\InnoSetup ni en el PATH.' }
$iscc = $IsccPath

# --- Versión real ----------------------------------------------------------
$propertiesPath = Join-Path $root "gradle.properties"
$properties = Get-Content -LiteralPath $propertiesPath -Raw -Encoding UTF8
$versionMatch = [regex]::Match($properties, '(?m)^lunafetch\.versionName=(\d+\.\d+\.\d+)\r?$')
if (-not $versionMatch.Success) { throw "No se pudo leer la versión de Luna Fetch." }
if (-not $Version) { $Version = $versionMatch.Groups[1].Value }
Assert-SemanticVersion $Version

# --- version.iss se regenera siempre con la versión real -------------------
$versionContent = @"
; ---------------------------------------------------------------------------
; version.iss - generated por el script de build con la version real del app.
; El script de build debe reescribir este archivo en cada release:
;   #define MyAppVersion "$Version"
; ---------------------------------------------------------------------------
#define MyAppVersion "$Version"
"@
[System.IO.File]::WriteAllText($versionScript, $versionContent, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "version.iss regenerado con la versión $Version" -ForegroundColor Cyan

# --- Distribución de la app (app-image de jpackage) ------------------------
if (-not $SkipAppBuild) {
    $jdk = Get-FullJdk
    Invoke-Checked (Join-Path $root "gradlew.bat") @("-Dorg.gradle.java.home=$jdk", ":composeApp:createDistributable")
}

$appImage = Join-Path $root "composeApp\build\compose\binaries\main\app\LunaFetch"
$sourceRoots = @(Join-Path $root "composeApp\src")
Assert-BuildFresh -Artifacts @((Join-Path $appImage "LunaFetch.exe")) -AppRoot $root -SourceRoots $sourceRoots -Force:$ForceStale

# --- Staging: copia limpia que Inno Setup empaqueta ------------------------
# Cada ejecución usa una carpeta propia: así el contenido empaquetado es
# siempre el app-image recien generado, sin arrastrar archivos de una
# compilación anterior. La carpeta vive bajo build/ (ignorado por Git).
$stagingRoot = Join-Path $root "build\packaging\windows"
$stagingApp = Join-Path $stagingRoot ("app-{0}-{1}" -f $Version, (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Path $stagingApp -Force | Out-Null
Copy-Item -Path (Join-Path $appImage "*") -Destination $stagingApp -Recurse -Force
if (-not (Test-Path -LiteralPath (Join-Path $stagingApp "LunaFetch.exe"))) {
    throw "El staging no contiene LunaFetch.exe: $stagingApp"
}
Write-Host "Staging preparado en $stagingApp" -ForegroundColor Cyan

# --- Compilación con Inno Setup --------------------------------------------
if (-not $OutputDir) { $OutputDir = Join-Path $root "release" }
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

# Rutas con barras normales: el preprocesador de Inno trata la barra invertida
# como caracter de escape y romperia la definicion. ISCC no admite comillas en
# el valor de /D, asi que la ruta no puede contener espacios.
$payloadForDefine = $stagingApp.Replace('\', '/')
if ($payloadForDefine -match '\s') {
    throw "La ruta del staging no puede contener espacios para /DMyAppPayloadDir: $stagingApp"
}
Assert-PackagingInvariants -SetupIss $setupScript -PackagingDir $packagingDir
Invoke-Checked $iscc @(
    "/O$OutputDir",
    "/DMyAppPayloadDir=$payloadForDefine",
    $setupScript
)

$installer = Join-Path $OutputDir "LunaFetch-Windows-$Version.exe"
if (-not (Test-Path -LiteralPath $installer)) {
    throw "ISCC no dejó el instalador esperado en $installer"
}
(Get-Item -LiteralPath $installer).IsReadOnly = $false

# --- Firma (opcional, mismo certificado que el flujo de release) -----------
if (-not $SkipSigning) {
    $certificate = Join-Path $root "LunaFetch_Dev_Certificate.pfx"
    if (-not (Test-Path -LiteralPath $certificate)) { throw "Falta LunaFetch_Dev_Certificate.pfx." }
    $signTool = Get-WindowsSdkTool "signtool.exe"
    Invoke-Checked $signTool @("sign", "/fd", "SHA256", "/f", $certificate, $installer)
    Assert-SignedArtifact -Path $installer -Publisher "CN=biglexj"
}

$hash = (Get-Sha256Hex -LiteralPath $installer).ToLowerInvariant()
Write-Host "Instalador listo: $installer" -ForegroundColor Green
Write-Host "SHA256: $hash" -ForegroundColor Green

# Sincronizar copia en '12. Releases'
$releasesGlobalDir = 'D:\Proyectos\biglexj\12. Releases'
if (Test-Path -LiteralPath $releasesGlobalDir) {
    $installerLeaf = Split-Path $installer -Leaf
    Get-ChildItem -LiteralPath $releasesGlobalDir -Filter "LunaFetch-Windows-*.exe" -File |
        Where-Object { $_.Name -ne $installerLeaf } |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force -ErrorAction SilentlyContinue }
    Copy-Item -LiteralPath $installer -Destination (Join-Path $releasesGlobalDir $installerLeaf) -Force
    Write-Host "Copia sincronizada en 12. Releases\$installerLeaf" -ForegroundColor Green
}
exit 0
