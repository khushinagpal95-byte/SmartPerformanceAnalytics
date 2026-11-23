-- Smart Performance Analytics Tool - MySQL schema
-- The application creates these tables automatically on first start, so running this file is OPTIONAL.
-- Run it yourself if you want to create/inspect the database manually:
--     mysql -u root -p < database/schema.sql

CREATE DATABASE IF NOT EXISTS smart_analytics;
USE smart_analytics;

CREATE TABLE IF NOT EXISTS students (
    roll_no VARCHAR(15) PRIMARY KEY,
    name    VARCHAR(40) NOT NULL
);

CREATE TABLE IF NOT EXISTS subjects (
    subject_name VARCHAR(30) PRIMARY KEY
);

-- one row = one student's marks in one subject
CREATE TABLE IF NOT EXISTS marks (
    roll_no      VARCHAR(15) NOT NULL,
    subject_name VARCHAR(30) NOT NULL,
    assessment   DOUBLE NOT NULL,   -- out of 70
    assignment   DOUBLE NOT NULL,   -- out of 20
    attendance   DOUBLE NOT NULL,   -- percentage, 0-100 (worth 10 marks)
    PRIMARY KEY (roll_no, subject_name),
    FOREIGN KEY (roll_no)      REFERENCES students(roll_no)      ON DELETE CASCADE,
    FOREIGN KEY (subject_name) REFERENCES subjects(subject_name) ON DELETE CASCADE
);
