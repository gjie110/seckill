@echo off
title RocketMQ Launcher
color 0A

echo.
echo   ==============================================
echo         RocketMQ 5.1.4  Launcher
echo   ==============================================
echo.

:: Check if already running
netstat -ano | findstr ":9876" | findstr "LISTENING" >nul 2>&1
if %errorlevel% equ 0 (
    echo   [ INFO ] RocketMQ already running!
    echo           NameServer : localhost:9876
    echo           Broker     : localhost:10911
    echo.
    timeout /t 3 >nul
    exit /b 0
)

:: Step 1: NameServer (new window)
echo   [ 1/2 ] Starting NameServer ...
start "RocketMQ-NameServer" "%~dp0start-namesrv.cmd"

:wait_ns
ping -n 3 127.0.0.1 >nul
netstat -ano | findstr ":9876" | findstr "LISTENING" >nul 2>&1
if %errorlevel% neq 0 goto wait_ns
echo           [OK] NameServer on :9876

:: Step 2: Broker (new window)
echo   [ 2/2 ] Starting Broker ...
start "RocketMQ-Broker" "%~dp0start-broker.cmd"

:wait_bk
ping -n 5 127.0.0.1 >nul
netstat -ano | findstr ":10911" | findstr "LISTENING" >nul 2>&1
if %errorlevel% neq 0 goto wait_bk
echo           [OK] Broker on :10911

echo.
echo   ==============================================
echo          RocketMQ is RUNNING !
echo       NameServer : localhost:9876  [OK]
echo       Broker     : localhost:10911 [OK]
echo   ==============================================
echo.
echo   Close this window.
echo   To stop MQ, close the NameServer and Broker
echo   windows or run stop-all.bat
echo   ==============================================
pause
