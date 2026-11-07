@echo off
title AI Study Planner - Backend (Spring Boot)
cd /d "%~dp0..\backend"
echo ========================================================
echo   AI Study Planner - Backend Spring Boot
echo   Running at: http://localhost:8080/
echo ========================================================
if "%DB_PASSWORD%"=="" (
    set DB_PASSWORD=changeit
)
mvn spring-boot:run
pause
