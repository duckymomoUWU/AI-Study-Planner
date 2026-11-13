@echo off
title AI Study Planner - Backend (Spring Boot)
cd /d "%~dp0..\backend"
echo ========================================================
echo   AI Study Planner - Backend Spring Boot
echo   Configuration: project root .env - see PORT for the HTTP port
echo ========================================================
mvn spring-boot:run
pause
