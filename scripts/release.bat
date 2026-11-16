@echo off
rem Build the backend + frontend images and push them to Docker Hub (Windows / Docker Desktop).
rem
rem   scripts\release.bat <dockerhub-repo> <version>
rem   scripts\release.bat youruser/konoha-helpdesk 1.0.0
rem
rem Run "docker login" once first. Same result as scripts/release.sh.
setlocal
set "REPO=%~1"
set "VERSION=%~2"
if "%REPO%"=="" goto usage
if "%VERSION%"=="" goto usage
if /i "%VERSION%"=="latest" goto usage

cd /d "%~dp0.."

echo ==^> Building backend  %REPO%:backend-%VERSION%
docker build --pull -t "%REPO%:backend-%VERSION%" -t "%REPO%:backend-latest" backend || goto failed

echo ==^> Building frontend %REPO%:frontend-%VERSION%
docker build --pull --build-arg VITE_API_BASE_URL=/api -t "%REPO%:frontend-%VERSION%" -t "%REPO%:frontend-latest" frontend || goto failed

docker push "%REPO%:backend-%VERSION%" || goto failed
docker push "%REPO%:backend-latest" || goto failed
docker push "%REPO%:frontend-%VERSION%" || goto failed
docker push "%REPO%:frontend-latest" || goto failed

echo.
echo Done. On the server set APP_VERSION=%VERSION% in .env, then:
echo   docker compose -f docker-compose.prod.yml pull
echo   docker compose -f docker-compose.prod.yml up -d
exit /b 0

:usage
echo Usage: scripts\release.bat ^<dockerhub-repo^> ^<version^>    e.g. youruser/konoha-helpdesk 1.0.0
echo The version must be a real one such as 1.0.0 ("latest" is pushed automatically as well).
exit /b 1

:failed
echo.
echo FAILED - see the error above. Is Docker Desktop running, and have you run "docker login"?
exit /b 1
