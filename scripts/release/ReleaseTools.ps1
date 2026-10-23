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

function Get-JdkMajorVersion {
    param([Parameter(Mandatory)] [string]$JdkPath)

    $releaseFile = Join-Path $JdkPath "release"
    if (Test-Path -LiteralPath $releaseFile) {
        $releaseContent = Get-Content -LiteralPath $releaseFile -Raw -ErrorAction SilentlyContinue
        if ($releaseContent) {
            $match = [regex]::Match($releaseContent, '(?m)^JAVA_VERSION="?(\d+)')
            if ($match.Success) { return [int]$match.Groups[1].Value }
        }
    }

    $java = Join-Path $JdkPath "bin\java.exe"
    if (-not (Test-Path -LiteralPath $java)) { return $null }
    try {
        $startInfo = New-Object System.Diagnostics.ProcessStartInfo
        $startInfo.FileName = $java
        $startInfo.Arguments = "-version"
        $startInfo.RedirectStandardError = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.UseShellExecute = $false
        $process = [System.Diagnostics.Process]::Start($startInfo)
        $output = $process.StandardError.ReadToEnd() + $process.StandardOutput.ReadToEnd()
        $process.WaitForExit()
        $match = [regex]::Match($output, 'version "(\d+)')
        if ($match.Success) { return [int]$match.Groups[1].Value }
    } catch {
        return $null
    }
    return $null
}

function Get-FullJdk {
    # Se busca cualquier JDK 17 completo instalado (Microsoft, Adoptium, Corretto,
    # Zulu o el JBR de Android Studio) y se valida la version mayor antes de usarlo:
    # un JDK 25 rompe el compilador de Kotlin en este proyecto.
    $requiredMajor = 17
    $searchRoots = @(
        "C:\Program Files\Microsoft",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java",
        "C:\Program Files\Zulu",
        "C:\Program Files\Amazon Corretto",
        "C:\Program Files\Android\Android Studio\jbr"
    )

    $candidates = New-Object System.Collections.Generic.List[string]
    foreach ($searchRoot in $searchRoots) {
        if (-not (Test-Path -LiteralPath $searchRoot)) { continue }
        $directories = Get-ChildItem -LiteralPath $searchRoot -Directory -ErrorAction SilentlyContinue
        foreach ($directory in $directories) {
            if ($directory.Name -match '^(?:jdk|jre|jbr|temurin|corretto)-?(\d+)' -or $directory.Name -eq "jbr") {
                $candidates.Add($directory.FullName)
            }
        }
    }
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }

    foreach ($candidate in $candidates) {
        if (-not (Test-Path -LiteralPath (Join-Path $candidate "bin\jpackage.exe"))) { continue }
        if (-not (Test-Path -LiteralPath (Join-Path $candidate "bin\javac.exe"))) { continue }
        $major = Get-JdkMajorVersion -JdkPath $candidate
        if ($major -eq $requiredMajor) { return $candidate }
    }

    throw "No se encontro un JDK $requiredMajor completo (con javac.exe y jpackage.exe). Instala un JDK $requiredMajor o configura JAVA_HOME con uno."
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
