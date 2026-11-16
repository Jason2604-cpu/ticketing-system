package com.konohahelpdesk.service.media;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MediaStorageServiceTest {

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void detectsImageTypesByMagicBytes() {
        assertEquals("image/jpeg", MediaStorageService.detectImageType(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertEquals("image/png", MediaStorageService.detectImageType(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}));
        assertEquals("image/gif", MediaStorageService.detectImageType(ascii("GIF89a")));
        assertEquals("image/webp", MediaStorageService.detectImageType(ascii("RIFF....WEBPVP8 ")));
    }

    @Test
    void rejectsNonImages() {
        assertNull(MediaStorageService.detectImageType(ascii("<html><script>alert(1)</script>")));
        assertNull(MediaStorageService.detectImageType(ascii("%PDF-1.4")));
        assertNull(MediaStorageService.detectImageType(ascii("MZ......")));
        assertNull(MediaStorageService.detectImageType(new byte[0]));
    }
}
