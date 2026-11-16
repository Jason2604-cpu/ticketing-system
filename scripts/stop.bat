@echo off
REM Closes the two windows opened by dev.bat.
taskkill /FI "WINDOWTITLE eq Konoha HelpDesk - Backend*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Konoha HelpDesk - Frontend*" /T /F >nul 2>&1
echo Stopped (if they were running).
