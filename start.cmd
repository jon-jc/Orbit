@echo off
setlocal
cd /d "%~dp0"
if not exist "target\orbit-1.0.0.jar" (
  call mvnw.cmd -B -ntp verify
  if errorlevel 1 exit /b 1
)
if defined JAVA_HOME (
  set "ORBIT_JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "ORBIT_JAVA=java"
)
"%ORBIT_JAVA%" -jar target\orbit-1.0.0.jar --spring.profiles.active=local --server.address=127.0.0.1
