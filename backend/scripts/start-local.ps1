# Локальный стенд: PostgreSQL (Docker) + Ktor API на :8080
# Запуск из PowerShell:
#   cd backend
#   .\scripts\start-local.ps1
#
# Требования: Docker Desktop запущен, JDK 21 (или 17), порты 8080 и 15432 свободны (Postgres с хоста).

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host "==> Docker: поднимаю PostgreSQL (docker compose)..."
try {
    docker compose up -d
}
catch {
    Write-Host "ОШИБКА: Docker недоступен. Запусти Docker Desktop и повтори." -ForegroundColor Red
    exit 1
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "ОШИБКА: docker compose завершился с кодом $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "==> Жду готовности Postgres (до 30 с)..."
$deadline = (Get-Date).AddSeconds(30)
$ready = $false
while ((Get-Date) -lt $deadline) {
    docker compose exec -T postgres pg_isready -U apofeoz -d apofeoz 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $ready = $true
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $ready) {
    Write-Host "Предупреждение: pg_isready не подтвердился за 30 с, пробую запустить backend." -ForegroundColor Yellow
}

if (-not $env:JDBC_URL) {
    $env:JDBC_URL = "jdbc:postgresql://localhost:15432/apofeoz"
}

if (-not $env:SEED_ADMIN_EMAIL) {
    $env:SEED_ADMIN_EMAIL = "admin@local.test"
}
if (-not $env:SEED_ADMIN_PASSWORD) {
    $env:SEED_ADMIN_PASSWORD = "AdminPass123!"
}

Write-Host "==> SEED_ADMIN_EMAIL=$($env:SEED_ADMIN_EMAIL)"
Write-Host "==> Запуск Ktor (Ctrl+C остановит сервер)..."
& "$Root\gradlew.bat" run --no-daemon
