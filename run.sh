#!/bin/sh
# Builds and starts the web dashboard.  Use "./run.sh console" for the console menu.
./build.sh && java -cp "out:lib/*" com.analytics.Main "$@"
