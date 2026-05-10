@echo off
setlocal

set "DIRNAME=%~dp0"
if "%DIRNAME%"=="" set "DIRNAME=."
set "APP_HOME=%DIRNAME%"

if defined JAVA_HOME (
  set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_CMD=java.exe"
)

set "CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"

"%JAVA_CMD%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

endlocal