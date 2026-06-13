@echo off
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
java -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
