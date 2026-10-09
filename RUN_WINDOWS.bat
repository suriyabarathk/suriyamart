@echo off
cd /d "%~dp0"
echo Starting SuriyaMart on port 8082...
mvn org.springframework.boot:spring-boot-maven-plugin:3.5.6:run
pause
