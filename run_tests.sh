#!/bin/sh
# Builds and runs the unit tests and the full integration test
./build.sh || exit 1
mkdir -p out-tests
javac -cp out -d out-tests tests/*.java || exit 1
java -cp out:out-tests AnalyticsTests || exit 1
java -cp out:out-tests IntegrationTest
