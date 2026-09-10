<#
.SYNOPSIS
    Wrapper raíz de conveniencia para invocar el pipeline de release de Luna Fetch.
.DESCRIPTION
    Redirige automáticamente a scripts/build/build-release.ps1 soportando sintaxis intuitiva:
    - .\build-release.ps1 local
    - .\build-release.ps1 -LocalOnly
    - .\build-release.ps1 1.2.1
    - .\build-release.ps1 -SkipAndroid -LocalOnly
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Version,
    [switch]$LocalOnly,
    [switch]$SkipWindows,
    [switch]$SkipAndroid,
    [switch]$SkipTests,
    [switch]$SkipBuild,
    [switch]$SkipSigning,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$RemainingArgs
)

# Detectar si se pasó 'local' como primer parámetro posicional
if ($Version -match '^(?i)local|-local|-localonly$') {
    $Version = $null
    $LocalOnly = $true
}

$scriptPath = Join-Path $PSScriptRoot "scripts\build\build-release.ps1"
if (-not (Test-Path -LiteralPath $scriptPath)) {
    throw "No se encontró el script de release en $scriptPath"
}

$splat = @{}
if ($Version)      { $splat['Version']      = $Version }
if ($LocalOnly)    { $splat['LocalOnly']    = $true }
if ($SkipWindows)  { $splat['SkipWindows']  = $true }
if ($SkipAndroid)  { $splat['SkipAndroid']  = $true }
if ($SkipTests)    { $splat['SkipTests']    = $true }
if ($SkipBuild)    { $splat['SkipBuild']    = $true }
if ($SkipSigning)  { $splat['SkipSigning']  = $true }

& $scriptPath @splat @RemainingArgs
exit $LASTEXITCODE
