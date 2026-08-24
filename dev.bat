@echo off
REM ============================================================================
REM  Luna Fetch - Modo Desarrollo (Desktop)
REM  Ejecuta la aplicacion de escritorio desde el codigo fuente.
REM  El task :composeApp:run inyecta -Dlunafetch.dev=true, segun el modulo
REM  SingleInstanceLock (bypass de instancia unica en desarrollo).
REM ============================================================================
setlocal

set "ROOT=%~dp0"
cd /d "%ROOT%"

echo [LunaFetch] Iniciando modo desarrollo (composeApp:run)...
call "%ROOT%gradlew.bat" :composeApp:run

if errorlevel 1 (
    echo [LunaFetch] El build termino con errores. Codigo: %errorlevel%
    pause
)

endlocal
