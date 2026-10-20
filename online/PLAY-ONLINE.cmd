@echo off
cd /d "%~dp0"
if not exist logs mkdir logs
"client\NroYanii.exe" -logFile "%~dp0logs\client.log"
echo Neu gap loi, chay REPORT.cmd de gom log va mo mau bao loi.
pause
