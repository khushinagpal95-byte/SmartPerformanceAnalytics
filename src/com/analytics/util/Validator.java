package com.analytics.util;

import com.analytics.exception.InvalidMarksException;

/** Input validation helpers (strings, regex, wrapper-class parsing). */
public final class Validator {
    private Validator() {
    }

    public static boolean isValidRollNo(String s) {
        return s != null && s.matches("[A-Za-z0-9]{3,15}");
    }

    /** Letters, spaces, dots and hyphens only; no commas so the CSV file stays safe. */
    public static boolean isValidName(String s) {
        return s != null && s.trim().matches("[A-Za-z][A-Za-z .'-]{1,39}");
    }

    public static boolean isValidSubject(String s) {
        return s != null && s.trim().matches("[A-Za-z0-9][A-Za-z0-9 &+-]{1,29}");
    }

    public static String normalizeSubject(String s) {
        // "java programming" -> "Java Programming"
        StringBuilder sb = new StringBuilder();
        for (String word : s.trim().split("\\s+")) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /** Parses text into a number and checks the range; throws a checked exception on failure. */
    public static double parseInRange(String text, String field, double min, double max)
            throws InvalidMarksException {
        Double value;
        try {
            value = Double.valueOf(text.trim());
        } catch (NumberFormatException e) {
            throw new InvalidMarksException(field + " must be a number (got \"" + text + "\")");
        }
        if (value.isNaN() || value < min || value > max) {
            throw new InvalidMarksException(field + " must be between " + show(min) + " and " + show(max));
        }
        return value;
    }

    /** 70.0 is shown as 70, 0.5 stays 0.5 (nicer error messages). */
    private static String show(double number) {
        return number == Math.rint(number) ? String.valueOf((long) number) : String.valueOf(number);
    }
}
