@echo off
echo ========================================
echo   Bayport Veterinary Clinic - Windows Builder
echo ========================================
echo.

REM Check if Node.js is installed
where node >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Node.js is not installed or not in PATH
    echo Please install Node.js from https://nodejs.org/
    pause
    exit /b 1
)

echo [1/2] Setting up execution policy...
powershell -ExecutionPolicy Bypass -Command "Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope Process -Force" >nul 2>&1

echo [2/2] Building Windows installer...
echo.
echo This may take a few minutes...
echo.

call npm run build:win

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo   BUILD SUCCESSFUL!
    echo ========================================
    echo.
    echo Your installer is located at:
    echo   dist\Bayport Veterinary Clinic Setup 0.1.0.exe
    echo.
    echo You can now distribute this file to users.
    echo They can double-click it to install - no terminal needed!
    echo.
) else (
    echo.
    echo ========================================
    echo   BUILD FAILED
    echo ========================================
    echo.
    echo Please check the error messages above.
    echo.
)

pause

