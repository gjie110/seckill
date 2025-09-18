@echo off
title Ticket Seckill System
cd /d "%~dp0"

echo ========================================
echo   Ticket Seckill System
echo ========================================
echo.

if not exist "target\ticket-seckill-system-1.0.0.jar" (
    echo [FAIL] jar not found!
    echo Please build first: mvn clean package -DskipTests
    pause
    exit /b 1
)

echo [0/3] Checking MySQL ...
netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>&1
if %errorlevel% equ 0 (
    echo        [OK] MySQL already running
) else (
    echo        Starting MySQL service ...
    powershell -Command "Start-Process sc -ArgumentList 'start','MySQL80' -Verb RunAs -Wait" >nul 2>&1
    ping -n 4 127.0.0.1 >nul
    netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>&1
    if %errorlevel% neq 0 (
        echo        [WARN] MySQL failed to start - check services.msc
    ) else (
        echo        [OK] MySQL started
    )
)

echo [1/3] Waiting for RocketMQ ...
set RETRY=0
:mq_wait
netstat -ano | findstr ":9876" | findstr "LISTENING" >nul 2>&1
if %errorlevel% equ 0 goto mq_ready
set /a RETRY+=1
if %RETRY% gtr 30 (
    echo [FAIL] RocketMQ not running after 30s
    pause
    exit /b 1
)
ping -n 2 127.0.0.1 >nul
goto mq_wait
:mq_ready
echo        [OK] RocketMQ ready (waited %RETRY%s)

echo [2/3] Checking Redis ...
netstat -ano | findstr ":6380" | findstr "LISTENING" >nul 2>&1
if %errorlevel% neq 0 (
    echo        [WARN] Redis not running on :6380
    echo        Start Redis first then restart this script.
    pause
    exit /b 1
)
echo        [OK] Redis ready

echo [3/3] Starting application on port 8080 ...
start "" /b javaw -Xms256m -Xmx512m -XX:+UseG1GC --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-exports java.base/jdk.internal.misc=ALL-UNNAMED -jar target\ticket-seckill-system-1.0.0.jar

echo        Waiting for application ...
:wait_app
timeout /t 3 /nobreak >nul
curl -s http://localhost:8080/product/list >nul 2>&1
if %errorlevel% neq 0 goto wait_app

echo        [OK] Application ready
echo.
echo ========================================
echo   All services running!
echo   http://localhost:8080
echo ========================================
echo.
pause
