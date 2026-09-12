package com.AirDrop.Spherical.AI.L1;

public class TextNormalizer {

    public static String normalize(String input) {
        if (input == null) return "";

        String clean = input.toLowerCase();

        // 1. Convert common Leet-speak replacements
        clean = clean.replace("@", "a")
                .replace("1", "i")
                .replace("!", "i")
                .replace("$", "s")
                .replace("0", "o")
                .replace("3", "e")
                .replace("5", "s");

        // 2. Remove all symbols, punctuation, underscores, and dots inserted inside words
        clean = clean.replaceAll("[_\\.\\*\\-\\s\\+\\#\\@\\~\\!]", "");

        // 3. Collapse repeated characters (e.g., "fuuuuuck" -> "fuck")
        clean = clean.replaceAll("(.)\\1+", "$1");

        return clean;
    }
}