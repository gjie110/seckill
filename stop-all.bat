@echo off
echo Stopping RocketMQ ...

:: Find and kill NameServer
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":9876" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
    echo [OK] NameServer stopped (PID %%a)
)

:: Find and kill Broker
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":10911" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
    echo [OK] Broker stopped (PID %%a)
)

echo [OK] Stopping backend app ...

:: Find and kill javaw (backend app)
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
    echo [OK] App stopped (PID %%a)
)

echo.
echo All services stopped.
pause
