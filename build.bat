@echo off
REM Compiles the program into the "out" folder (needs JDK 17 or newer)
if not exist out mkdir out
javac -d out -sourcepath src src\com\analytics\Main.java && echo Build complete.
