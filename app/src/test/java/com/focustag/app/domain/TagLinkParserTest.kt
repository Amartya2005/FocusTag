package com.focustag.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagLinkParserTest {

    private val pilot = "1D:1D:70:1C:1A:10:80"

    @Test
    fun parsesFocusTagScheme() {
        assertEquals(pilot, TagLinkParser.extractUid("focustag://tag/1D:1D:70:1C:1A:10:80"))
    }

    @Test
    fun parsesSchemeWithQueryAndLowercase() {
        assertEquals(pilot, TagLinkParser.extractUid("FOCUSTAG://tag/1d1d701c1a1080?src=door"))
    }

    @Test
    fun parsesUrlEncodedColons() {
        assertEquals(pilot, TagLinkParser.extractUid("focustag://tag/1D%3A1D%3A70%3A1C%3A1A%3A10%3A80"))
    }

    @Test
    fun parsesRawColonUid() {
        assertEquals(pilot, TagLinkParser.extractUid("1D:1D:70:1C:1A:10:80"))
    }

    @Test
    fun parsesCompactHex() {
        assertEquals(pilot, TagLinkParser.extractUid("1d1d701c1a1080"))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(TagLinkParser.extractUid("https://example.com"))
        assertNull(TagLinkParser.extractUid("focustag://tag/not-a-uid"))
        assertNull(TagLinkParser.extractUid(""))
        assertNull(TagLinkParser.extractUid(null))
    }
}
