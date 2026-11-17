package com.jobsfinnished.zipgpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MediaDocumentIdParserTest {
    @Test public void parsesImageId() {
        assertEquals(Long.valueOf(12345L), MediaDocumentIdParser.parseImageId("image:12345"));
    }

    @Test public void rejectsNonImageAndMalformedIds() {
        assertNull(MediaDocumentIdParser.parseImageId("video:12345"));
        assertNull(MediaDocumentIdParser.parseImageId("image:abc"));
        assertNull(MediaDocumentIdParser.parseImageId("image:"));
        assertNull(MediaDocumentIdParser.parseImageId(null));
    }
}
