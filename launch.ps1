# ==============================================================================
# FOREX INTELLIGENCE SUITE — ENTERPRISE INSTITUTIONAL COMMAND CENTER v4.0
# AUTONOMOUS BUILD & LAUNCH ORCHESTRATOR (launch.ps1)
# ==============================================================================
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host "  FOREX INTELLIGENCE SUITE - INSTITUTIONAL QUANT TERMINAL v4.0" -ForegroundColor Yellow
Write-Host "  Anton Kreil 6-Pillar Macro Framework | 336 Indicators | 28-Pair Risk Shield" -ForegroundColor White
Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host ""

$root = $PSScriptRoot
if (-not $root) { $root = (Get-Location).Path }
Set-Location -Path $root

# 1. Verifying directories
Write-Host "[*] Step 1/7: Verifying institutional storage directories..." -ForegroundColor Cyan
$excelDir = Join-Path $root "data\Excel_Logs"
$h2Dir = Join-Path $root "data\h2db"

if (-not (Test-Path $excelDir)) {
    New-Item -ItemType Directory -Path $excelDir -Force | Out-Null
}
if (-not (Test-Path $h2Dir)) {
    New-Item -ItemType Directory -Path $h2Dir -Force | Out-Null
}
Write-Host "    [OK] Storage directories ready." -ForegroundColor Green

# 2. Check Java
Write-Host ""
Write-Host "[*] Step 2/7: Verifying Java 21+ Runtime Environment..." -ForegroundColor Cyan
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-Host "    [ERROR] Java not found in PATH. Please install Java 21+." -ForegroundColor Red
    Read-Host "Press Enter to exit..."
    exit 1
}
Write-Host "    [OK] Java Runtime verified ($($javaCmd.Source))." -ForegroundColor Green

# 3. Check Port 8080 and 8085 collision and kill stale PIDs
Write-Host ""
Write-Host "[*] Step 3/7: Releasing any stale processes on Ports 8080 & 8085..." -ForegroundColor Cyan
try {
    # 3.1 Terminate by open ports
    foreach ($p in @(8080, 8085)) {
        $netstatLines = netstat -ano | Select-String ":$p\s+"
        foreach ($line in $netstatLines) {
            $parts = ($line.ToString().Trim() -split '\s+')
            $targetPid = $parts[-1]
            if ($targetPid -match '^\d+$' -and [int]$targetPid -gt 0 -and [int]$targetPid -ne $PID) {
                Write-Host "    [!] Terminating prior process on port $p (PID: $targetPid)..." -ForegroundColor Yellow
                Stop-Process -Id $targetPid -Force -ErrorAction SilentlyContinue
            }
        }
    }

    # 3.2 Terminate prior Java instances running this suite
    Get-Process -Name java -ErrorAction SilentlyContinue | ForEach-Object {
        try {
            $procInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue
            if ($procInfo -and $procInfo.CommandLine -match "forex-intelligence-suite") {
                Write-Host "    [!] Terminating prior Java backend instance (PID: $($_.Id))..." -ForegroundColor Yellow
                Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
            }
        } catch {}
    }

    # 3.3 Terminate prior Python MT5 daemons
    Get-Process -Name python -ErrorAction SilentlyContinue | ForEach-Object {
        try {
            $procInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue
            if ($procInfo -and $procInfo.CommandLine -match "mt5_cockpit_daemon") {
                Write-Host "    [!] Terminating prior MT5 Python daemon (PID: $($_.Id))..." -ForegroundColor Yellow
                Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
            }
        } catch {}
    }

    Start-Sleep -Milliseconds 600
    Write-Host "    [OK] Ports 8080 and 8085 and stale processes released." -ForegroundColor Green
} catch {
    Write-Host "    [NOTE] Port checks completed." -ForegroundColor Gray
}

# 4. Check JAR package & Auto-Rebuild if source files are newer
Write-Host ""
Write-Host "[*] Step 4/7: Verifying v4.0 JAR package & source synchronization..." -ForegroundColor Cyan
$jarPath = Join-Path $root "backend-java\target\forex-intelligence-suite-1.0.0-SNAPSHOT.jar"
$srcDir = Join-Path $root "backend-java\src"
$needsBuild = -not (Test-Path $jarPath)

if (-not $needsBuild) {
    $jarTime = (Get-Item $jarPath).LastWriteTimeUtc
    $newerFiles = Get-ChildItem -Path $srcDir -Recurse -File | Where-Object { $_.LastWriteTimeUtc -gt $jarTime }
    if ($newerFiles) {
        $needsBuild = $true
    }
}

if ($needsBuild) {
    Write-Host "    [*] Compiling and packaging Institutional Suite v4.0 via Maven Wrapper..." -ForegroundColor Yellow
    Set-Location -Path (Join-Path $root "backend-java")
    try {
        & .\mvnw.cmd package -DskipTests
    } catch {
        Write-Host "    [WARN] Maven build notice: $($_.Exception.Message)" -ForegroundColor Yellow
    }
    Set-Location -Path $root
    if (-not (Test-Path $jarPath)) {
        Write-Host "    [ERROR] Maven build failed and no prior executable JAR exists." -ForegroundColor Red
        Read-Host "Press Enter to exit..."
        exit 1
    }
}
Write-Host "    [OK] Executable JAR verified: $jarPath" -ForegroundColor Green

# 5. Launch Backend in dedicated window
Write-Host ""
Write-Host "[*] Step 5/7: Launching Spring Boot Quantitative Engine..." -ForegroundColor Cyan
$staticLoc = "file:./backend-java/src/main/resources/static/,classpath:/static/"

$serverProc = Start-Process -FilePath "java" `
    -ArgumentList "-Dfile.encoding=UTF-8", "-jar", "`"$jarPath`"", "--spring.web.resources.static-locations=`"$staticLoc`"" `
    -WorkingDirectory $root `
    -PassThru

Write-Host "    [OK] Backend server started (PID: $($serverProc.Id))." -ForegroundColor Green

# 6. Await Health Check
Write-Host ""
Write-Host "[*] Step 6/7: Awaiting Bootstrap Telemetry Readiness..." -ForegroundColor Cyan
$ready = $false
$maxWaitSec = 60
for ($i = 1; $i -le $maxWaitSec; $i++) {
    Start-Sleep -Seconds 1
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/bootstrap" -UseBasicParsing -TimeoutSec 2
        if ($r.StatusCode -eq 200) {
            Write-Host ""
            Write-Host "    [READY] Quantitative Engine online in $i seconds!" -ForegroundColor Green
            $ready = $true
            break
        }
    } catch {
        Write-Host -NoNewline "." -ForegroundColor Yellow
    }
}

if ($ready) {
    try {
        Invoke-RestMethod -Uri "http://localhost:8080/api/v1/scraper/sync-all" -Method Post -TimeoutSec 2 | Out-Null
    } catch {}
} else {
    Write-Host ""
    Write-Host "    [WARNING] Quantitative Engine did not report ready within $maxWaitSec seconds." -ForegroundColor Yellow
}

# 7. Launch MetaTrader 5 Python REST Bridge (Port 8085)
Write-Host ""
Write-Host "[*] Step 7/7: Launching MT5 Live Cockpit REST Daemon (Port 8085)..." -ForegroundColor Cyan
$pyCandidates = @(
    "C:\Users\Zbook Power\AppData\Local\Programs\Python\Python312\python.exe",
    (Join-Path $env:LOCALAPPDATA "Programs\Python\Python312\python.exe")
)
$pyExe = $null
foreach ($cand in $pyCandidates) {
    if ($cand -and (Test-Path $cand)) {
        $pyExe = $cand
        break
    }
}
if (-not $pyExe) {
    $pyCmd = Get-Command python -ErrorAction SilentlyContinue
    if ($pyCmd) { $pyExe = $pyCmd.Source }
}

$daemonScript = Join-Path $root "mt5_cockpit_daemon.py"
if ($pyExe -and (Test-Path $pyExe) -and (Test-Path $daemonScript)) {
    $pyProc = Start-Process -FilePath $pyExe `
        -ArgumentList "`"$daemonScript`" --headless" `
        -WorkingDirectory $root `
        -PassThru
    Write-Host "    [OK] MT5 Headless Cloud Daemon started (PID: $($pyProc.Id)) on Port 8085." -ForegroundColor Green
} else {
    Write-Host "    [WARNING] Python 3.12 or mt5_cockpit_daemon.py not found. MT5 bridge will remain offline." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "    [+] Opening Institutional Command Center in browser..." -ForegroundColor Cyan
Start-Process "http://localhost:8080/?v=7.0"

Write-Host ""
Write-Host "===============================================================================" -ForegroundColor Green
Write-Host "  [ONLINE] FOREX INTELLIGENCE SUITE v7.0 ENTERPRISE EDITION" -ForegroundColor Green
Write-Host "  Dashboard URL:    http://localhost:8080/?v=7.0" -ForegroundColor Yellow
Write-Host "  Backtest Lab:     http://localhost:8080/#multiyear-backtest" -ForegroundColor Cyan
Write-Host "  Anti-Hunt Engine: http://localhost:8080/#antihunt-engine" -ForegroundColor Cyan
Write-Host "  Telegram Cockpit: http://localhost:8080/#telegram-cockpit" -ForegroundColor Cyan
Write-Host "  Gemini AI Pool:   http://localhost:8080/#gemini-cockpit" -ForegroundColor Cyan
Write-Host "  MT5 Python API:   http://127.0.0.1:8085/api/mt5/status" -ForegroundColor White
Write-Host "  Excel & CSV Logs: $root\data\Excel_Logs" -ForegroundColor Gray
Write-Host "===============================================================================" -ForegroundColor Green
Write-Host ""
if ([Environment]::UserInteractive -and -not [Console]::IsInputRedirected) {
    Write-Host "Services are running. Press Enter to close this launcher console..." -ForegroundColor Cyan
    try { Read-Host } catch {}
} else {
    Write-Host "Services running in background." -ForegroundColor Green
}
exit 0
