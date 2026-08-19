@REM Licensed to the Apache Software Foundation (ASF) under one or more
@REM contributor license agreements. See the NOTICE file distributed with
@REM this work for additional information regarding copyright ownership.
@REM The ASF licenses this file to You under the Apache License, Version 2.0.
@echo off
setlocal

set "BASE_DIR=%~dp0"
set "PROPERTIES_FILE=%BASE_DIR%.mvn\wrapper\maven-wrapper.properties"

for /f "tokens=1,* delims==" %%A in ('findstr /b "distributionUrl=" "%PROPERTIES_FILE%"') do set "DISTRIBUTION_URL=%%B"
if "%DISTRIBUTION_URL%"=="" (
  echo distributionUrl is required in %PROPERTIES_FILE% 1>&2
  exit /b 1
)

if "%MAVEN_USER_HOME%"=="" set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
for %%F in ("%DISTRIBUTION_URL%") do set "ARCHIVE_NAME=%%~nxF"
set "DIST_NAME=%ARCHIVE_NAME:.zip=%"
set "MAVEN_DIR_NAME=%DIST_NAME:-bin=%"
set "DIST_DIR=%MAVEN_USER_HOME%\wrapper\dists\%DIST_NAME%"
set "MAVEN_HOME=%DIST_DIR%\%MAVEN_DIR_NAME%"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
  set "ARCHIVE=%DIST_DIR%\%ARCHIVE_NAME%"
  if not exist "%ARCHIVE%" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing '%DISTRIBUTION_URL%' -OutFile '%ARCHIVE%'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%ARCHIVE%' -DestinationPath '%DIST_DIR%' -Force"
)

call "%MAVEN_HOME%\bin\mvn.cmd" %*
endlocal
