#!/bin/sh
# Compiles the program into the "out" folder (needs JDK 17 or newer)
mkdir -p out
javac -d out -sourcepath src src/com/analytics/Main.java && echo "Build complete."
