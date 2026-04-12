# Локальный стенд: PostgreSQL (Docker) + Ktor API на :8080
# Запуск из PowerShell:
#   cd backend
#   .\scripts\start-local.ps1
#
# Опции:
#   -SkipDocker      # не поднимать Postgres через docker compose
#   -NoSeedAdmin     # не выставлять SEED_ADMIN_* по умолчанию
#   -WaitSeconds 60  # сколько ждать healthcheck postgres
#
# Требования: Docker Desktop запущен (если не указан -SkipDocker),
# gradlew.bat в backend/, JDK 21 (или 17).

param(
    [switch]$SkipDocker,
    [switch]$NoSeedAdmin,
    [ValidateRange(5, 300)]
    [int]$WaitSeconds = 60
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail([string]$Message, [int]$Code = 1) {
    Write-Host "ОШИБКА: $Message" -ForegroundColor Red
    exit $Code
}

function Ensure-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        Fail "Не найдена команда '$Name' в PATH."
    }
}

$Root = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $Root "docker-compose.yml"
$GradleWrapper = Join-Path $Root "gradlew.bat"
Set-Location $Root

if (-not (Test-Path -LiteralPath $ComposeFile)) {
    Fail "Не найден docker-compose.yml в $Root"
}
if (-not (Test-Path -LiteralPath $GradleWrapper)) {
    Fail "Не найден gradlew.bat в $Root"
}

if (-not $SkipDocker) {
    Ensure-Command "docker"

    try {
        docker info *> $null
    } catch {
        Fail "Docker недоступен. Запусти Docker Desktop и повтори."
    }

    Write-Host "==> Docker: поднимаю PostgreSQL (docker compose up -d postgres)..."
    docker compose up -d postgres
    if ($LASTEXITCODE -ne 0) {
        Fail "docker compose завершился с кодом $LASTEXITCODE" $LASTEXITCODE
    }

    Write-Host "==> Жду готовности Postgres (healthcheck, до $WaitSeconds с)..."
    $containerId = (docker compose ps -q postgres).Trim()
    if ([string]::IsNullOrWhiteSpace($containerId)) {
        Write-Host "Предупреждение: не удалось получить container id postgres. Продолжаю запуск backend." -ForegroundColor Yellow
    } else {
        $deadline = (Get-Date).AddSeconds($WaitSeconds)
        $ready = $false

        while ((Get-Date) -lt $deadline) {
            $health = (docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}" $containerId 2>$null).Trim()
            if ($health -eq "healthy") {
                $ready = $true
                break
            }
            if ($health -eq "unhealthy") {
                Write-Host "Предупреждение: postgres health=unhealthy, продолжаю ожидание..." -ForegroundColor Yellow
            }
            Start-Sleep -Seconds 2
        }

        if (-not $ready) {
            Write-Host "Предупреждение: Postgres не стал healthy за $WaitSeconds с, пробую запустить backend." -ForegroundColor Yellow
        }
    }
}

if (-not $env:JDBC_URL) {
    $env:JDBC_URL = "jdbc:postgresql://localhost:15432/apofeoz"
}

if (-not $NoSeedAdmin) {
    if (-not $env:SEED_ADMIN_EMAIL) {
        $env:SEED_ADMIN_EMAIL = "admin@local.test"
    }
    if (-not $env:SEED_ADMIN_PASSWORD) {
        $env:SEED_ADMIN_PASSWORD = "AdminPass123!"
    }
    Write-Host "==> SEED_ADMIN_EMAIL=$($env:SEED_ADMIN_EMAIL)"
}

Write-Host "==> JDBC_URL=$($env:JDBC_URL)"
Write-Host "==> Запуск Ktor (Ctrl+C остановит сервер)..."
& $GradleWrapper run --no-daemon
if ($LASTEXITCODE -ne 0) {
    Fail "gradlew run завершился с кодом $LASTEXITCODE" $LASTEXITCODE
}
