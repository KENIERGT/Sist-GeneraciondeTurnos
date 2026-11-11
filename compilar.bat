@echo off
rem Compila todo el codigo de src\ hacia bin\
cd /d "%~dp0"
if not exist bin mkdir bin
javac -encoding UTF-8 -d bin src\*.java
if errorlevel 1 (
    echo Error al compilar.
    exit /b 1
)
echo Compilado en bin\
