param(
    [Parameter(Position = 0)]
    [string]$Version,
    [string]$ReleaseNotesFile = "RELEASE_MESSAGE.md",
    [switch]$LocalOnly,
    [switch]$SkipWindows,
    [switch]$SkipAndroid,
    [switch]$SkipTests,
    [switch]$SkipBuild,
    [switch]$SkipSigning
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
. (Join-Path $root "scripts\release\ReleaseTools.ps1")

$repository = "biglexj/Luna---Fetch"
$propertiesPath = Join-Path $root "gradle.properties"
$releaseNotesPath = Join-Path $root $ReleaseNotesFile
$properties = Get-Content -LiteralPath $propertiesPath -Raw -Encoding UTF8
$versionMatch = [regex]::Match($properties, '(?m)^lunafetch\.versionName=(\d+\.\d+\.\d+)$')
$codeMatch = [regex]::Match($properties, '(?m)^lunafetch\.versionCode=(\d+)$')
if (-not $versionMatch.Success -or -not $codeMatch.Success) { throw "No se pudo leer la versión de Luna Fetch." }

$currentVersion = $versionMatch.Groups[1].Value
$newCode = [int]$codeMatch.Groups[1].Value

if ($Version -match '^(?i)local|-local|-localonly$') {
    $Version = $currentVersion
    $LocalOnly = $true
}

if (-not $Version) { $Version = $currentVersion }
Assert-SemanticVersion $Version
if (-not (Test-Path -LiteralPath $releaseNotesPath)) { throw "No se encontró $releaseNotesPath" }
if (-not $LocalOnly -and $SkipSigning) { throw "Una publicación oficial no puede usar -SkipSigning." }

function Get-MsiProductCode {
    param([Parameter(Mandatory)] [string]$Path)

    $installer = New-Object -ComObject WindowsInstaller.Installer
    $database = $installer.OpenDatabase($Path, 0)
    $view = $database.OpenView("SELECT `Value` FROM `Property` WHERE `Property`='ProductCode'")
    $view.Execute()
    $record = $view.Fetch()
    if (-not $record) { throw "No se encontró ProductCode en $Path." }
    return $record.StringData(1).Trim()
}

function Update-WingetManifest {
    param(
        [Parameter(Mandatory)] [string]$Root,
        [Parameter(Mandatory)] [string]$Version,
        [Parameter(Mandatory)] [string]$MsiPath
    )

    $manifestDirectory = Join-Path $Root "packaging\winget\manifests\b\biglexj\LunaFetch\$Version"
    New-Item -ItemType Directory -Path $manifestDirectory -Force | Out-Null
    $productCode = Get-MsiProductCode $MsiPath
    $sha256 = (Get-FileHash -LiteralPath $MsiPath -Algorithm SHA256).Hash.ToUpperInvariant()
    $releaseDate = (Get-Date).ToString("yyyy-MM-dd")

    @"
# yaml-language-server: `$schema=https://aka.ms/winget-manifest.version.1.10.0.schema.json

PackageIdentifier: biglexj.LunaFetch
PackageVersion: $Version
DefaultLocale: es-PE
ManifestType: version
ManifestVersion: 1.10.0
"@ | Set-Content -LiteralPath (Join-Path $manifestDirectory "biglexj.LunaFetch.yaml") -Encoding UTF8 -NoNewline

    @"
# yaml-language-server: `$schema=https://aka.ms/winget-manifest.installer.1.10.0.schema.json

PackageIdentifier: biglexj.LunaFetch
PackageVersion: $Version
InstallerType: wix
Scope: user
InstallModes:
  - interactive
  - silent
  - silentWithProgress
UpgradeBehavior: install
ProductCode: '$productCode'
ReleaseDate: $releaseDate
Installers:
  - Architecture: x64
    InstallerUrl: https://github.com/biglexj/Luna---Fetch/releases/download/v$Version/LunaFetch-Windows-$Version.msi
    InstallerSha256: $sha256
ManifestType: installer
ManifestVersion: 1.10.0
"@ | Set-Content -LiteralPath (Join-Path $manifestDirectory "biglexj.LunaFetch.installer.yaml") -Encoding UTF8 -NoNewline

    @"
# yaml-language-server: `$schema=https://aka.ms/winget-manifest.defaultLocale.1.10.0.schema.json

PackageIdentifier: biglexj.LunaFetch
PackageVersion: $Version
PackageLocale: es-PE
Publisher: biglexj
PublisherUrl: https://github.com/biglexj
PublisherSupportUrl: https://github.com/biglexj/Luna---Fetch/issues
Author: Biglex J
PackageName: Luna Fetch
PackageUrl: https://github.com/biglexj/Luna---Fetch
License: MIT
LicenseUrl: https://github.com/biglexj/Luna---Fetch/blob/main/LICENSE
Copyright: Copyright (c) 2026 Biglex J
ShortDescription: Descarga videos y audio en alta calidad desde una interfaz multiplataforma.
Description: |-
  Luna Fetch analiza enlaces multimedia y descarga video MP4/WebM o audio MP3/M4A.
  Incluye selección de calidad, progreso, cancelación, registro técnico y apertura del resultado.
Moniker: lunafetch
Tags:
  - audio
  - downloader
  - media
  - video
  - youtube
ReleaseNotes: |-
  Luna Fetch $Version publica APK Android separados para ARM64, ARM32 y x86_64,
  y simplifica el selector de tema a un único control visual.
ReleaseNotesUrl: https://github.com/biglexj/Luna---Fetch/releases/tag/v$Version
ManifestType: defaultLocale
ManifestVersion: 1.10.0
"@ | Set-Content -LiteralPath (Join-Path $manifestDirectory "biglexj.LunaFetch.locale.es-PE.yaml") -Encoding UTF8 -NoNewline

    return $manifestDirectory
}

$tag = "v$Version"
$output = Join-Path $root "release"
if (-not $LocalOnly) { Assert-PublishPreflight -Root $root -Repository $repository -Tag $tag }

if ($Version -ne $currentVersion) {
    $newCode++
    $properties = $properties `
        -replace '(?m)^(lunafetch\.versionName=)\d+\.\d+\.\d+$', "`${1}$Version" `
        -replace '(?m)^(lunafetch\.versionCode=)\d+$', "`${1}$newCode"
    [System.IO.File]::WriteAllText($propertiesPath, $properties, (New-Object System.Text.UTF8Encoding($false)))
}

$appConfigPath = Join-Path $root "composeApp\src\commonMain\kotlin\com\biglexj\lunafetch\domain\AppConfig.kt"
if (Test-Path -LiteralPath $appConfigPath) {
    $appConfigContent = Get-Content -LiteralPath $appConfigPath -Raw -Encoding UTF8
    $appConfigContent = $appConfigContent -replace 'const val APP_VERSION = "[^"]+"', "const val APP_VERSION = ""$Version"""
    Set-Content -LiteralPath $appConfigPath -Value $appConfigContent -Encoding UTF8 -NoNewline
}

$jdk = Get-FullJdk
$signTool = if ($SkipSigning) { $null } else { Get-WindowsSdkTool "signtool.exe" }
$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }

if (-not $LocalOnly -and -not $SkipAndroid) {
    $hasSigningFile = Test-Path -LiteralPath (Join-Path $root "keystore.properties")
    $hasSigningEnvironment = @(
        "LUNAFETCH_ANDROID_KEYSTORE",
        "LUNAFETCH_ANDROID_STORE_PASSWORD",
        "LUNAFETCH_ANDROID_KEY_ALIAS",
        "LUNAFETCH_ANDROID_KEY_PASSWORD"
    ) | ForEach-Object { [bool][Environment]::GetEnvironmentVariable($_) }
    if (-not $hasSigningFile -and $hasSigningEnvironment.Contains($false)) {
        throw "Falta la firma Android permanente. Configura keystore.properties o las variables LUNAFETCH_ANDROID_* completas."
    }
}

if (-not $SkipBuild) {
    $tasks = @()
    if (-not $SkipWindows) {
        $tasks += @(
            ":composeApp:createDistributable",
            ":composeApp:packageExe"
        )
    }
    if (-not $SkipAndroid) {
        if ($LocalOnly) {
            $tasks += ":composeApp:assembleDebug"
        } else {
            $tasks += ":composeApp:assembleRelease"
        }
    }
    if (-not $SkipTests) { $tasks = @(":composeApp:desktopTest") + $tasks }
    if ($tasks.Count -gt 0) {
        Invoke-Checked (Join-Path $root "gradlew.bat") (@("-Dorg.gradle.java.home=$jdk") + $tasks)
    }
}

New-Item -ItemType Directory -Path $output -Force | Out-Null
$resolvedRoot = [IO.Path]::GetFullPath($root).TrimEnd('\') + '\'
$resolvedOutput = [IO.Path]::GetFullPath($output).TrimEnd('\') + '\'
if (-not $resolvedOutput.StartsWith($resolvedRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "La salida resuelta quedó fuera del proyecto."
}
try {
    Get-ChildItem -LiteralPath $output -File -ErrorAction SilentlyContinue | Remove-Item -Force
} catch [System.UnauthorizedAccessException] {
    $lockedPath = if ($_.Exception.PSObject.Properties['ItemName']) { $_.Exception.ItemName } else { $_.Exception.Message }
    $lockingProcesses = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object { $_.ExecutablePath -and $_.ExecutablePath.Equals($lockedPath, [StringComparison]::OrdinalIgnoreCase) } |
        ForEach-Object { "$($_.Name) (PID $($_.ProcessId))" }
    $lockDescription = if ($lockingProcesses) { $lockingProcesses -join ", " } else { "un proceso externo" }
    throw "No se puede limpiar '$lockedPath': está en uso por $lockDescription. Cierra esa instancia y vuelve a ejecutar."
}

$publishedArtifacts = @()

if (-not $SkipWindows) {
    $exe = Join-Path $output "LunaFetch-Windows-$Version.exe"
    Copy-Item -LiteralPath (Join-Path $root "composeApp\build\compose\binaries\main\exe\LunaFetch-$Version.exe") -Destination $exe
    (Get-Item -LiteralPath $exe).IsReadOnly = $false

    if (-not $SkipSigning) {
        $certificate = Join-Path $root "LunaFetch_Dev_Certificate.pfx"
        if (-not (Test-Path -LiteralPath $certificate)) { throw "Falta LunaFetch_Dev_Certificate.pfx." }
        Invoke-Checked $signTool @("sign", "/fd", "SHA256", "/f", $certificate, $exe)
        Assert-SignedArtifact -Path $exe -Publisher "CN=biglexj"
    }
    $publishedArtifacts += $exe
}

$androidArtifacts = @()
if (-not $SkipAndroid) {
    $androidVariant = if ($LocalOnly) { "debug" } else { "release" }
    $apkOutput = Join-Path $root "composeApp\build\outputs\apk\$androidVariant"
    $metadataPath = Join-Path $apkOutput "output-metadata.json"
    if (-not (Test-Path -LiteralPath $metadataPath)) { throw "No se generó el metadato de APK: $metadataPath" }
    $metadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8 | ConvertFrom-Json

    $buildTools = Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME "build-tools") -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        Select-Object -First 1
    if (-not $buildTools) { throw "No se encontraron Android build-tools para validar los APK." }
    $apkSigner = Join-Path $buildTools.FullName "apksigner.bat"
    $aapt = Join-Path $buildTools.FullName "aapt.exe"

    $element = @($metadata.elements)[0]
    if (-not $element) { throw "No se encontró el elemento APK en $metadataPath" }
    $apkSource = Join-Path $apkOutput $element.outputFile
    if (-not (Test-Path -LiteralPath $apkSource)) { throw "No se generó el APK: $apkSource" }

    if (-not $LocalOnly) {
        Invoke-Checked $apkSigner @("verify", "--verbose", "--print-certs", $apkSource)
    }
    $badging = (& $aapt dump badging $apkSource) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo inspeccionar el APK." }
    if ($badging -notmatch "name='com\.biglexj\.lunafetch'") {
        throw "El applicationId del APK no corresponde a Luna Fetch."
    }
    if ($badging -notmatch "versionCode='$newCode'" -or $badging -notmatch "versionName='$([regex]::Escape($Version))'") {
        throw "La versión interna del APK no coincide con $Version ($newCode)."
    }

    $debugSuffix = if ($LocalOnly) { "-debug" } else { "" }
    $apk = Join-Path $output "LunaFetch-Android-$Version$debugSuffix.apk"
    Copy-Item -LiteralPath $apkSource -Destination $apk
    $androidArtifacts = @($apk)
    $publishedArtifacts += $apk
}

if ($publishedArtifacts.Count -eq 0) {
    throw "No se seleccionó ninguna plataforma para compilar (ambas omitidas)."
}

foreach ($artifact in $publishedArtifacts) {
    if (-not (Test-Path -LiteralPath $artifact) -or (Get-Item -LiteralPath $artifact).Length -eq 0) {
        throw "El artefacto final falta o está vacío: $artifact"
    }
}

$hashPath = Join-Path $output "SHA256SUMS.txt"
$publishedArtifacts |
    ForEach-Object { Get-FileHash -LiteralPath $_ -Algorithm SHA256 } |
    ForEach-Object { "{0}  {1}" -f $_.Hash.ToLowerInvariant(), (Split-Path $_.Path -Leaf) } |
    Set-Content -LiteralPath $hashPath -Encoding UTF8

if ($LocalOnly) { Write-Host "Build local terminado en $output" -ForegroundColor Green; exit 0 }

Push-Location $root
try {
    Invoke-Checked git @("add", "-A")
    Invoke-Checked git @("diff", "--cached", "--check")
    & git diff --cached --quiet
    if ($LASTEXITCODE -ne 0) { Invoke-Checked git @("commit", "-m", "release: Luna Fetch $tag") }
    Invoke-Checked git @("tag", "-a", $tag, "-m", "Luna Fetch $tag")
    Invoke-Checked git @("push", "--atomic", "origin", "HEAD:main", "refs/tags/$tag")
} finally { Pop-Location }

$assets = $publishedArtifacts + @($hashPath)
Invoke-Checked gh (@("release", "create", $tag) + $assets + @(
    "--repo", $repository,
    "--verify-tag",
    "--title", "Luna Fetch $tag",
    "--notes-file", $releaseNotesPath
))
