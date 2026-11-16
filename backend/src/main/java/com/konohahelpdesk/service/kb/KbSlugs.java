package com.konohahelpdesk.service.kb;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** URL slugs for articles: "Printer offline after Windows update!" → "printer-offline-after-windows-update". */
public final class KbSlugs {

    static final int MAX_BASE = 150;   // column is 160; leaves room for "-9999"

    private KbSlugs() {}

    public static String slugify(String title) {
        String s = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "")          // é → e
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        if (s.length() > MAX_BASE) s = s.substring(0, MAX_BASE).replaceAll("-+$", "");
        return s.isEmpty() ? "article" : s;
    }

    /** {@code base}, or {@code base-2}, {@code base-3}… — the first one {@code taken} says is free. */
    public static String unique(String base, Predicate<String> taken) {
        if (!taken.test(base)) return base;
        for (int n = 2; ; n++) {
            String candidate = base + "-" + n;
            if (!taken.test(candidate)) return candidate;
        }
    }
}
