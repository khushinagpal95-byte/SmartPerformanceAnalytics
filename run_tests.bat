@echo off
REM Builds and runs the unit tests and the full integration test
call build.bat
if not exist out-tests mkdir out-tests
javac -cp out -d out-tests tests\AnalyticsTests.java tests\IntegrationTest.java
java -cp out;out-tests AnalyticsTests
java -cp out;out-tests IntegrationTest
