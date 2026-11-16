package com.konohahelpdesk.service.kb;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KbSlugsTest {

    @Test
    void slugifiesTitles() {
        assertEquals("printer-offline-after-windows-update", KbSlugs.slugify("Printer offline after Windows update!"));
        assertEquals("reset-the-vpn-token-hp-m404", KbSlugs.slugify("  Reset the VPN token (HP-M404)  "));
        assertEquals("cafe-resume", KbSlugs.slugify("Café résumé"));
        assertEquals("article", KbSlugs.slugify("!!!"));
        assertEquals("article", KbSlugs.slugify(null));
    }

    @Test
    void capsLength() {
        String slug = KbSlugs.slugify("word ".repeat(100));
        assertTrue(slug.length() <= KbSlugs.MAX_BASE);
        assertTrue(!slug.endsWith("-"));
    }

    @Test
    void deduplicates() {
        Set<String> taken = Set.of("vpn", "vpn-2");
        assertEquals("vpn-3", KbSlugs.unique("vpn", taken::contains));
        assertEquals("printer", KbSlugs.unique("printer", taken::contains));
    }

    @Test
    void escapesLikeWildcards() {
        assertEquals("100\\% done\\_ok\\\\", KbService.escapeLike("100% done_ok\\"));
    }
}
