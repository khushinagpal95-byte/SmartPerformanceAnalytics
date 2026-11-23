Put the MySQL JDBC driver here (only needed if you want to use MySQL):

  1. Download "Connector/J" from  https://dev.mysql.com/downloads/connector/j/
     (choose "Platform Independent", download the ZIP).
  2. Extract the ZIP and copy the file  mysql-connector-j-<version>.jar  into this lib folder.

run.sh / run.bat already add this folder to the classpath.
Without the jar the program still works - it saves to CSV files in the data folder instead.
