package com.analytics.model;

/** Letter grades with their minimum percentage and grade points. */
public enum Grade {
    O("O", 90.0, 10),
    A_PLUS("A+", 80.0, 9),
    A("A", 70.0, 8),
    B_PLUS("B+", 60.0, 7),
    B("B", 50.0, 6),
    C("C", 40.0, 5),
    F("F", 0.0, 0);

    private final String label;
    private final double minPercentage;
    private final int points;

    Grade(String label, double minPercentage, int points) {
        this.label = label;
        this.minPercentage = minPercentage;
        this.points = points;
    }

    public String getLabel() {
        return label;
    }

    public double getMinPercentage() {
        return minPercentage;
    }

    public int getPoints() {
        return points;
    }

    public boolean isPass() {
        return this != F;
    }

    /** Enum constants are declared from highest to lowest, so the first match wins. */
    public static Grade fromPercentage(double percentage) {
        for (Grade g : values()) {
            if (percentage >= g.minPercentage) {
                return g;
            }
        }
        return F;
    }

    @Override
    public String toString() {
        return label;
    }
}
