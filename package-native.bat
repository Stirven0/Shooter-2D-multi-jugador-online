@echo off
REM =====================================================
REM  Empaquetador nativo para Windows
REM  Crea Shooter-Server.exe y Shooter-Client.exe
REM =====================================================
REM Requisitos:
REM   1. JDK 25+ con jpackage (Liberica JDK Full recomendado)
REM   2. JavaFX SDK 25 (se descarga automaticamente)
REM   3. WiX Toolset (https://wixtoolset.org/) para .exe
REM =====================================================

setlocal enabledelayedexpansion

echo.
echo ========================================
echo  Shooter 2D - Empaquetado nativo
echo ========================================
echo.

REM ---- 1. Verificar jpackage ----
where jpackage >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] jpackage no encontrado. Asegurate de tener JDK 17+.
    echo     Descargar: https://bell-sw.com/pages/downloads/
    pause
    exit /b 1
)
echo [OK] jpackage encontrado

REM ---- 2. Compilar fat JARs ----
echo.
echo [ ] Compilando fat JARs...
call mvn package -DskipTests -q
if %errorlevel% neq 0 (
    echo [!] Error compilando
    pause
    exit /b 1
)
echo [OK] Fat JARs compilados

REM ---- 3. Descargar JavaFX SDK si no existe ----
set JAVAFX_DIR=%USERPROFILE%\.javafx-sdk\25
set JAVAFX_LIB=%JAVAFX_DIR%\lib

if not exist "%JAVAFX_LIB%" (
    echo.
    echo [ ] Descargando JavaFX SDK 25...
    mkdir "%USERPROFILE%\.javafx-sdk" 2>nul
    set URL=https://download2.gluonhq.com/openjfx/25/openjfx-25_windows-x64_bin.zip
    set ZIP=%TEMP%\javafx-sdk-25.zip
    echo     URL: !URL!
    powershell -c "Invoke-WebRequest -Uri '!URL!' -OutFile '!ZIP!'"
    echo     Extrayendo...
    powershell -c "Expand-Archive -Path '!ZIP!' -DestinationPath '%USERPROFILE%\.javafx-sdk\temp' -Force"
    move "%USERPROFILE%\.javafx-sdk\temp\javafx-sdk-25" "%JAVAFX_DIR%" >nul
    rmdir "%USERPROFILE%\.javafx-sdk\temp" 2>nul
    echo [OK] JavaFX SDK instalado
) else (
    echo [OK] JavaFX SDK encontrado
)

REM ---- 4. Crear directorio de salida ----
set DISTDIR=%CD%\dist
mkdir "%DISTDIR%" 2>nul

REM ---- 5. Empaquetar servidor ----
echo.
echo [ ] Empaquetando Shooter-Server...
jpackage ^
    --name "Shooter-Server" ^
    --app-version "1.0" ^
    --vendor "Stirven" ^
    --input server\target\ ^
    --main-jar server.jar ^
    --main-class com.aa.server.Main ^
    --type exe ^
    --dest "%DISTDIR%" ^
    --java-options "-Dprism.order=sw"
if %errorlevel% equ 0 (
    echo [OK] Shooter-Server empaquetado
) else (
    echo [!] Error empaquetando servidor
)

REM ---- 6. Empaquetar cliente ----
echo.
echo [ ] Empaquetando Shooter-Client...
jpackage ^
    --name "Shooter-Client" ^
    --app-version "1.0" ^
    --vendor "Stirven" ^
    --input client\target\ ^
    --main-jar client.jar ^
    --main-class com.aa.client.Main ^
    --module-path "%JAVAFX_LIB%" ^
    --add-modules javafx.controls,javafx.fxml,javafx.media,javafx.swing ^
    --type exe ^
    --dest "%DISTDIR%" ^
    --java-options "-Dprism.order=sw"
if %errorlevel% equ 0 (
    echo [OK] Shooter-Client empaquetado
) else (
    echo [!] Error empaquetando cliente
)

REM ---- 7. Resumen ----
echo.
echo ========================================
echo  Empaquetado completado
echo ========================================
dir "%DISTDIR%"
echo.
echo Los instaladores estan en: %DISTDIR%
pause
