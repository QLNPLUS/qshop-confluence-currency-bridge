# Downloads the dev-runtime prerequisites that are not on a Maven repository.
#
# Confluence 1.20.1 requires MesdagPortLib >= 1.2.5, which is published only on
# CurseForge and has no resolvable Maven coordinates, so it is fetched into libs/
# and referenced by build.gradle through a flatDir coordinate when the build runs
# with -Pwith_confluence_runtime=true.
#
# NOTE: this file is intentionally ASCII-only. Windows PowerShell 5.1 reads
# UTF-8 scripts without a BOM as ANSI and mangles non-ASCII text.
#
# Usage: powershell -File tools\fetch-dev-runtime.ps1
param(
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\libs\mesdagportlib-1.0.jar')
)

$ErrorActionPreference = 'Stop'

# CurseForge project mesdagportlib / file 8940829
# = MesdagPortLib+neoforge1.21.1 to forge1.20.1-1.2.5.jar
#
# Use the ForgeCDN URL directly: https://www.curseforge.com/.../download/<id>
# serves the HTML file page, not the archive.
$fileName = 'MesdagPortLib%2Bneoforge1.21.1%20to%20forge1.20.1-1.2.5.jar'
$downloadUrl = "https://mediafilez.forgecdn.net/files/8940/829/$fileName"

$output = [System.IO.Path]::GetFullPath($OutputPath)
$dir = Split-Path -Parent $output
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

Write-Host "Downloading MesdagPortLib 1.2.5 (forge 1.20.1) -> $output"
Invoke-WebRequest -Uri $downloadUrl -OutFile $output -UseBasicParsing -TimeoutSec 300

$item = Get-Item $output
if ($item.Length -lt 10000) {
    throw "Suspicious download size ($($item.Length) bytes); check that the CurseForge file id is still valid."
}
$magic = [System.IO.File]::ReadAllBytes($output)[0..3]
if (-not ($magic[0] -eq 0x50 -and $magic[1] -eq 0x4B)) {
    throw "Downloaded file is not a JAR/ZIP (expected PK magic). The CDN path may have changed."
}
Write-Host ("Done: {0} ({1:N0} bytes)" -f $item.FullName, $item.Length)
