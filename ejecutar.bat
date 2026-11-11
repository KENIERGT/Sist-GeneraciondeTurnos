@echo off
rem Compila y abre el sistema de turnos. Se ejecuta desde esta carpeta para que datos\ quede aqui.
cd /d "%~dp0"
call compilar.bat || exit /b 1
java -cp "bin;lib\*" Main
