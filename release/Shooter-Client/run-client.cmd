@echo off
set DIR=%~dp0
set JFX_MODS=
for %%f in ("%DIR%javafx-sdk\*.jar") do set JFX_MODS=!JFX_MODS!;%%f
set CLASSPATH=
for %%f in ("%DIR%lib\*.jar") do set CLASSPATH=!CLASSPATH!;%%f
java --module-path "%JFX_MODS:~1%" --add-modules javafx.controls,javafx.graphics,javafx.media -cp "%CLASSPATH:~1%" com.aa.client.Main %*
