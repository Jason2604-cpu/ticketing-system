package com.konohahelpdesk.service.kb;

import com.konohahelpdesk.dto.kb.KbSnippetPart;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turning what people type into Postgres full-text queries, and search excerpts back into text.
 * Pure functions, tested in {@code KbSearchTest}. Articles are English, so every query uses the
 * {@code english} configuration (stemming, stop-words) that the V5 search_vector was built with.
 */
public final class KbSearch {

    /** Longest search text we pass on; anything longer is cut, not refused. */
    public static final int MAX_QUERY_LENGTH = 200;
    /** Words used for a suggestion — the first few of a ticket title say what it's about. */
    public static final int MAX_SUGGEST_TERMS = 8;
    public static final int SUGGEST_LIMIT = 5;

    /** Match markers in ts_headline output: control characters can't come from a normal article. */
    public static final char START = '\u0002';
    public static final char STOP = '\u0003';

    /** ts_headline options: up to two short fragments around the matches. */
    public static final String HEADLINE_OPTIONS = "StartSel=" + START + ", StopSel=" + STOP
        + ", MaxWords=35, MinWords=15, ShortWord=3, MaxFragments=2, FragmentDelimiter=\" … \"";

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private KbSearch() {}

    /** The search box text, trimmed and capped; null when there is nothing to search for. */
    public static String normaliseQuery(String q) {
        if (q == null) return null;
        String t = q.strip();
        if (t.isEmpty()) return null;
        return t.length() > MAX_QUERY_LENGTH ? t.substring(0, MAX_QUERY_LENGTH) : t;
    }

    /**
     * A prefix OR query for type-ahead: "Printer offline after updating" →
     * {@code printer:* | offline:* | after:* | updating:*}. Only letters and digits survive, so the
     * result is always valid {@code to_tsquery} syntax; Postgres then stems each term and drops
     * stop-words ("after"). Words of one character are dropped (they'd prefix-match everything).
     * Null when nothing usable is left.
     */
    public static String prefixQuery(String text) {
        if (text == null) return null;
        String t = text.length() > MAX_QUERY_LENGTH ? text.substring(0, MAX_QUERY_LENGTH) : text;
        Matcher m = WORD.matcher(t.toLowerCase(Locale.ROOT));
        Set<String> words = new LinkedHashSet<>();
        while (m.find() && words.size() < MAX_SUGGEST_TERMS) {
            if (m.group().length() >= 2) words.add(m.group());
        }
        if (words.isEmpty()) return null;
        return String.join(" | ", words.stream().map(w -> w + ":*").toList());
    }

    /** ts_headline output → plain-text pieces, matches flagged. Whitespace collapsed. */
    public static List<KbSnippetPart> snippetParts(String headline) {
        if (headline == null || headline.isBlank()) return List.of();
        String text = headline.replaceAll("\\s+", " ").strip();
        List<KbSnippetPart> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean match = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == START || ch == STOP) {
                if (!current.isEmpty()) parts.add(new KbSnippetPart(current.toString(), match));
                current.setLength(0);
                match = ch == START;
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) parts.add(new KbSnippetPart(current.toString(), match));
        return parts;
    }
}
