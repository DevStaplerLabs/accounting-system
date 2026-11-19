@echo off
REM Compiles and starts the StaplerLabs Accounting System.
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > out\sources.txt
javac -encoding UTF-8 -d out -cp "lib/*" @out\sources.txt
if errorlevel 1 (
  echo.
  echo Compilation failed. Make sure JDK 17 or newer is installed.
  pause
  exit /b 1
)
java -cp "out;lib/*" com.staplerlabs.accounting.Main
pause
