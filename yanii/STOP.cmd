@echo off
cd /d "%~dp0"
if not exist "local-data\run.lock" (
 echo Start SERVER.cmd first.
 pause
 exit /b 1
)
> "local-data\stop.request" echo stop
 echo Saving players and stopping the server. Wait for SERVER.cmd to close.
