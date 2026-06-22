@echo off
REM Lanzador del cliente Shooter 2D para Windows
REM Requiere: Java 25+, JavaFX SDK 25
REM
REM Descargar JavaFX SDK desde: https://gluonhq.com/products/javafx/
REM Descomprimir y configurar JAVAFX_HOME
REM
REM Uso:
REM   run-client.bat                       # default
REM   run-client.bat --mcp                 # con MCP TCP
REM   run-client.bat --host 10.0.0.5 --port 8080 --ssl

set SCRIPT_DIR=%~dp0
set CLIENT_JAR=%SCRIPT_DIR%client\target\client.jar

if not exist "%CLIENT_JAR%" (
    echo [!] No se encuentra %CLIENT_JAR%
    echo [!] Ejecuta primero: mvn package -pl client -am -DskipTests
    exit /b 1
)

if "%JAVAFX_HOME%"=="" set JAVAFX_HOME=C:\javafx-sdk-25\lib

if not exist "%JAVAFX_HOME%" (
    echo [!] JAVAFX_HOME no encontrado en %JAVAFX_HOME%
    echo [!] Descargar de: https://gluonhq.com/products/javafx/
    echo     set JAVAFX_HOME=C:\ruta\javafx-sdk-25\lib
    exit /b 1
)

java ^
    --module-path "%JAVAFX_HOME%" ^
    --add-modules javafx.controls,javafx.fxml,javafx.media,javafx.swing ^
    -jar "%CLIENT_JAR%" %*
