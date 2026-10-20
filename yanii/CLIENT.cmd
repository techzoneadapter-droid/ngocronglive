@echo off
cd /d "%~dp0client"
start "" "NroYanii.exe" -logFile "%~dp0logs\client.log"
