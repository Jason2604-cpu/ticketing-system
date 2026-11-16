package com.konohahelpdesk.service.kb;

import com.konohahelpdesk.dto.kb.KbSnippetPart;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KbSearchTest {

    @Test
    void prefixQueryKeepsOnlyWordsAndMakesThemPrefixes() {
        assertEquals("printer:* | offline:* | after:* | updating:*", KbSearch.prefixQuery("Printer offline after updating"));
        // Operators, quotes and punctuation can't reach to_tsquery
        assertEquals("vpn:* | token:* | rm:* | rf:*", KbSearch.prefixQuery("VPN's token!! & | ( ) :* rm -rf"));
        assertEquals("hp:* | m404:*", KbSearch.prefixQuery("HP-M404"));
        assertEquals("café:*", KbSearch.prefixQuery("Café"));
    }

    @Test
    void prefixQueryDropsOneLetterWordsAndDuplicates() {
        assertEquals("printer:*", KbSearch.prefixQuery("a printer, a PRINTER"));
        assertNull(KbSearch.prefixQuery("a b c"));
        assertNull(KbSearch.prefixQuery("  !!  "));
        assertNull(KbSearch.prefixQuery(null));
    }

    @Test
    void prefixQueryUsesAtMostEightWords() {
        String q = KbSearch.prefixQuery("one two three four five six seven eight nine ten");
        assertEquals(8, q.split(" \\| ").length);
        assertFalse(q.contains("nine"));
    }

    @Test
    void normaliseQuery() {
        assertNull(KbSearch.normaliseQuery("   "));
        assertNull(KbSearch.normaliseQuery(null));
        assertEquals("\"printer offline\" -scanner", KbSearch.normaliseQuery("  \"printer offline\" -scanner "));
        assertEquals(KbSearch.MAX_QUERY_LENGTH, KbSearch.normaliseQuery("x".repeat(500)).length());
    }

    @Test
    void snippetPartsSplitOnMarkers() {
        String headline = KbSearch.START + "Printer" + KbSearch.STOP + " shows\n  " + KbSearch.START + "offline"
            + KbSearch.STOP + " after the update";
        assertEquals(List.of(
            new KbSnippetPart("Printer", true),
            new KbSnippetPart(" shows ", false),
            new KbSnippetPart("offline", true),
            new KbSnippetPart(" after the update", false)
        ), KbSearch.snippetParts(headline));
    }

    @Test
    void snippetPartsWithoutMatchesIsOnePlainPiece() {
        assertEquals(List.of(new KbSnippetPart("First words of the body", false)),
            KbSearch.snippetParts("  First words of the body "));
        assertEquals(List.of(), KbSearch.snippetParts(""));
        assertEquals(List.of(), KbSearch.snippetParts(null));
    }

    @Test
    void htmlInAnArticleStaysText() {
        // The excerpt is never HTML: a <script> in the body comes back as a plain piece of text.
        List<KbSnippetPart> parts = KbSearch.snippetParts("<script>alert(1)</script> " + KbSearch.START + "vpn" + KbSearch.STOP);
        assertEquals(new KbSnippetPart("<script>alert(1)</script> ", false), parts.get(0));
    }
}
