package com.safari.mod.util;

public class ModScanner {
    public static String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder cleaned = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\u00A7' && i + 1 < text.length()) {
                i++;
                continue;
            }

            cleaned.append(current);
        }

        return cleaned.toString();
    }
}
