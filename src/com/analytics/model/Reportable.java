package com.analytics.model;

/** Anything that can be written as a line/block in an exported report. */
public interface Reportable {
    String toReportLine();
}
