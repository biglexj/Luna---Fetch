Set-StrictMode -Version Latest

function Invoke-Checked {
    param(
        [Parameter(Mandatory)] [string]$Executable,
        [Parameter(Mandatory)] [AllowEmptyString()] [string[]]$ArgumentList
    )

    & $Executable @ArgumentList
    if ($LASTEXITCODE -ne 0) {
        throw "El comando '$Executable' terminó con código $LASTEXITCODE."
    }
}

function Get-FullJdk {
    $candidates = @(
        "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot",
        $env:JAVA_HOME,
        "C:\Program Files\Android\Android Studio\jbr"
    ) | Where-Object {
        $_ -and (Test-Path -LiteralPath (Join-Path $_ "bin\jpackage.exe"))
    }

    $jdk = $candidates | Select-Object -First 1
    if (-not $jdk) { throw "Configura JAVA_HOME con un JDK completo que incluya jpackage.exe." }
    return $jdk
}

function Get-WindowsSdkTool {
    param([Parameter(Mandatory)] [string]$Name)

    $sdkRoot = "C:\Program Files (x86)\Windows Kits\10\bin"
    $sdk = Get-ChildItem -LiteralPath $sdkRoot -Directory |
        Where-Object Name -Match '^10\.' |
        Sort-Object { [version]$_.Name } -Descending |
        Select-Object -First 1
    if (-not $sdk) { throw "No se encontró Windows SDK." }

    $tool = Join-Path $sdk.FullName "x64\$Name"
    if (-not (Test-Path -LiteralPath $tool)) { throw "No se encontró $Name en Windows SDK." }
    return $tool
}

function Assert-SemanticVersion {
    param([Parameter(Mandatory)] [string]$Version)
    if ($Version -notmatch '^(\d+)\.(\d+)\.(\d+)$') {
        throw "La versión '$Version' no cumple el formato mayor.menor.parche."
    }
}

function Assert-PublishPreflight {
    param(
        [Parameter(Mandatory)] [string]$Root,
        [Parameter(Mandatory)] [string]$Repository,
        [Parameter(Mandatory)] [string]$Tag
    )
    Push-Location $Root
    try {
        $branch = (& git branch --show-current).Trim()
        if ($LASTEXITCODE -ne 0 -or $branch -ne "main") { throw "La publicación exige la rama main." }
        $origin = (& git remote get-url origin).Trim()
        if ($LASTEXITCODE -ne 0 -or $origin -notmatch "github\.com[/:]$([regex]::Escape($Repository))(\.git)?$") {
            throw "El remoto origin no corresponde a ${Repository}: '$origin'."
        }
        Invoke-Checked git @("fetch", "origin", "main", "--tags")
        if ((& git rev-parse HEAD).Trim() -ne (& git rev-parse origin/main).Trim()) {
            throw "main debe estar sincronizada con origin/main antes de publicar."
        }
        Invoke-Checked gh @("auth", "status")
        
        $oldPreference = $ErrorActionPreference
        $ErrorActionPreference = "SilentlyContinue"
        
        & git rev-parse --quiet --verify "refs/tags/$Tag" *> $null
        $tagExists = ($LASTEXITCODE -eq 0)
        
        & gh release view $Tag --repo $Repository *> $null
        $releaseExists = ($LASTEXITCODE -eq 0)
        
        $ErrorActionPreference = $oldPreference
        
        if ($tagExists) { throw "El tag $Tag ya existe." }
        if ($releaseExists) { throw "El GitHub Release $Tag ya existe." }
    } finally { Pop-Location }
}

function Assert-SignedArtifact {
    param(
        [Parameter(Mandatory)] [string]$Path,
        [Parameter(Mandatory)] [string]$Publisher
    )
    $signature = Get-AuthenticodeSignature -LiteralPath $Path
    if (-not $signature.SignerCertificate -or $signature.SignerCertificate.Subject -ne $Publisher) {
        throw "La firma de '$Path' no corresponde a $Publisher."
    }
}
