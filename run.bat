@echo off
REM Builds and starts the web dashboard.  Use "run.bat console" for the console menu.
call build.bat
java -cp "out;lib\*" com.analytics.Main %*
