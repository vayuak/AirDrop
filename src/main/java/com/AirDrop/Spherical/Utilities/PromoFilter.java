package com.AirDrop.Spherical.Utilities;

import java.util.regex.Pattern;

/**
 * Cheap, deterministic anti-promotion / anti-spam check (WarnPack rule: no hotel, cafe, agency or product promotions).
 * Blocks links, contact numbers and typical advert phrases. Returns null when the text is fine, otherwise a user-facing reason.
 */
public final class PromoFilter {
    private PromoFilter() {}

    private static final Pattern LINK = Pattern.compile(
            "(?i)(https?://|www\\.|\\b[a-z0-9][a-z0-9-]{1,}\\.(com|net|org|io|co|in|id|th|vn|travel|shop|store|app|xyz|me|ly|info|biz|tours?|agency)\\b|wa\\.me|t\\.me|bit\\.ly|linktr\\.ee)");
    private static final Pattern ADVERT = Pattern.compile(
            "(?i)\\b(promo ?code|discount ?code|coupon|use my code|referral|book now|dm me|call us|contact us|whats ?app me|best price guaranteed|cheap (tours?|rooms?|rides?)|tour packages?)\\b");
    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");

    public static String check(String text) {
        if (text == null || text.isBlank()) return null;
        if (LINK.matcher(text).find() || EMAIL.matcher(text).find())
            return "Links and email addresses are not allowed. Describe what happened instead.";
        if (ADVERT.matcher(text).find())
            return "Promotions and advertising are not allowed on WarnPack.";
        if (hasPhoneNumber(text))
            return "Phone numbers are not allowed.";
        return null;
    }

    /** 9+ digits in one run (spaces, dots, dashes, brackets and + allowed inside the run). Prices such as 1.500.000 pass. */
    private static boolean hasPhoneNumber(String t) {
        int digits = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') {
                if (++digits >= 9) return true;
            } else if (c == ' ' || c == '-' || c == '.' || c == '(' || c == ')' || c == '+') {
                // separator: keep counting
            } else {
                digits = 0;
            }
        }
        return false;
    }
}
