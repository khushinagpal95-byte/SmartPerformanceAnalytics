package com.analytics.web;

import java.util.Locale;

/** Two tiny helpers for writing JSON by hand (no external library needed). */
public final class JsonUtil {
    private JsonUtil() {
    }

    /** Puts text in double quotes and escapes special characters. */
    public static String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    /** Number with 2 decimals and a dot, whatever the computer's language settings are. */
    public static String num(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
