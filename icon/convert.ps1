$PSScriptRoot = Split-Path -Parent -Path $MyInvocation.MyCommand.Definition
$pngPath = Join-Path $PSScriptRoot "300x300.png"
$icoPath = Join-Path $PSScriptRoot "icon.ico"

if (-not (Test-Path $pngPath)) {
    Write-Error "PNG file not found at $pngPath"
    exit 1
}

$pngBytes = [System.IO.File]::ReadAllBytes($pngPath)
$pngSize = $pngBytes.Length
$stream = [System.IO.File]::Open($icoPath, [System.IO.FileMode]::Create)
$writer = New-Object System.IO.BinaryWriter($stream)

# Write ICO Header
$writer.Write([UInt16]0) # Reserved
$writer.Write([UInt16]1) # Type: Icon
$writer.Write([UInt16]1) # Count

# Write Directory Entry
$writer.Write([Byte]0)   # Width
$writer.Write([Byte]0)   # Height
$writer.Write([Byte]0)   # Color Count
$writer.Write([Byte]0)   # Reserved
$writer.Write([UInt16]1) # Planes
$writer.Write([UInt16]32)# Bits per pixel
$writer.Write([UInt32]$pngSize) # Size
$writer.Write([UInt32]22)       # Offset

# Write PNG Bytes
$writer.Write($pngBytes)

$writer.Close()
$stream.Close()
