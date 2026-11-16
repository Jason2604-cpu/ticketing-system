@echo off
REM Starts the backend (Spring Boot) and frontend (Vite) dev servers together,
REM each in its own window, so you don't have to run two terminals by hand.
setlocal
set ROOT=%~dp0..

echo Starting backend (Spring Boot) on http://localhost:8080 ...
start "Konoha HelpDesk - Backend" /D "%ROOT%\backend" cmd /k mvnw.cmd spring-boot:run

echo Starting frontend (Vite) on http://localhost:5173 ...
start "Konoha HelpDesk - Frontend" /D "%ROOT%\frontend" cmd /k npm run dev

echo.
echo Both are launching in separate windows. Close those windows (or Ctrl+C inside them) to stop.
