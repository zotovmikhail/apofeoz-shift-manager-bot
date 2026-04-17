param(
    [ValidateSet("dev", "prod")]
    [string]$Mode = "prod",
    [string]$ApiBaseUrl = "http://localhost:8080",
    [int]$Port = 3000
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$webRoot = Split-Path -Parent $scriptDir

Push-Location $webRoot
try {
    $env:NEXT_PUBLIC_API_BASE_URL = $ApiBaseUrl
    $env:PORT = "$Port"

    if ($Mode -eq "prod") {
        Write-Host "==> Building web app for stable local run on port $Port"
        npm.cmd run build
        Write-Host "==> Starting Next.js in production mode"
        npm.cmd run start
    } else {
        Write-Host "==> Starting Next.js in dev mode on port $Port"
        npm.cmd run dev
    }
}
finally {
    Pop-Location
}
