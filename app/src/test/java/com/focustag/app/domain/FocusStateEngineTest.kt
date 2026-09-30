package com.focustag.app.domain

import com.focustag.app.data.model.FocusSessionState
import com.focustag.app.data.model.FocusState
import com.focustag.app.data.model.FocusTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FocusStateEngineTest {
    private val libTag = "1D:FF:7C:1C:1A:10:80"
    private val class1Tag = "1D:5B:70:1C:1A:10:80"
    private val class3Tag = "1D:C5:7C:1C:1A:10:80"
    private val unknownTag = "AA:BB:CC:DD:EE:FF:00"
    @Before fun setup() { NfcProtocol.setRegisteredTags(setOf(libTag, class1Tag, "1D:3D:70:1C:1A:10:80")) }
    @Test fun `registry recognizes real prototype tags`() { assertTrue(NfcProtocol.isRegistered(libTag)); assertTrue(NfcProtocol.isRegistered(class1Tag)); assertTrue(NfcProtocol.isRegistered("1D:3D:70:1C:1A:10:80")) }
    @Test fun `registry rejects unknown tags`() { assertTrue(!NfcProtocol.isRegistered(unknownTag)) }
    @Test fun `simulated tag is always registered`() { assertTrue(NfcProtocol.isRegistered("simulated_tag_01")) }
    @Test fun `registry recognizes newly added tags after update`() { assertTrue(!NfcProtocol.isRegistered(class3Tag)); NfcProtocol.setRegisteredTags(setOf(libTag, class1Tag, class3Tag)); assertTrue(NfcProtocol.isRegistered(class3Tag)) }
    @Test fun `registry rejects deactivated tags after update`() { NfcProtocol.setRegisteredTags(setOf(libTag, class1Tag)); assertTrue(NfcProtocol.isRegistered(libTag)); NfcProtocol.setRegisteredTags(setOf(class1Tag)); assertTrue(!NfcProtocol.isRegistered(libTag)) }
    @Test fun `normalization handles canonical format`() { assertEquals(libTag, NfcProtocol.normalize(libTag)) }
    @Test fun `normalization handles lowercase and no colons`() { assertEquals(libTag, NfcProtocol.normalize("1dff7c1c1a1080")); assertEquals(libTag, NfcProtocol.normalize("1DFF7C1C1A1080")) }
    @Test fun `physical prototype tags are correctly identified from raw hex`() { val rawLib = "1DFF7C1C1A1080"; val rawClass1 = "1D5B701C1A1080"; val rawClass2 = "1D3D701C1A1080"; assertEquals(libTag, NfcProtocol.normalize(rawLib)); assertEquals(class1Tag, NfcProtocol.normalize(rawClass1)); assertEquals("1D:3D:70:1C:1A:10:80", NfcProtocol.normalize(rawClass2)); assertTrue(NfcProtocol.isRegistered(NfcProtocol.normalize(rawLib)!!)); assertTrue(NfcProtocol.isRegistered(NfcProtocol.normalize(rawClass1)!!)); assertTrue(NfcProtocol.isRegistered(NfcProtocol.normalize(rawClass2)!!)) }
    @Test fun `normalization rejects malformed input`() { assertNull(NfcProtocol.normalize("NOT_HEX")); assertNull(NfcProtocol.normalize("1DFF7C1C1A10801")) }
    @Test fun `normalization preserves leading zeros`() { assertEquals("00:11:22:33:44:55:66", NfcProtocol.normalize("00112233445566")) }
    @Test fun `NORMAL state + registered tag results in START`() { assertTrue(FocusStateEngine.determineTransition(FocusSessionState(FocusState.NORMAL), libTag) is FocusTransition.Start); assertTrue(FocusStateEngine.determineTransition(FocusSessionState(FocusState.NORMAL), class1Tag) is FocusTransition.Start); assertTrue(FocusStateEngine.determineTransition(FocusSessionState(FocusState.NORMAL), "1D:3D:70:1C:1A:10:80") is FocusTransition.Start) }
    @Test fun `NORMAL state + unknown tag results in IGNORE`() { assertEquals(FocusTransition.Ignore, FocusStateEngine.determineTransition(FocusSessionState(FocusState.NORMAL), unknownTag)) }
    @Test fun `ACTIVE state + same tag results in STOP`() { assertEquals(FocusTransition.Stop, FocusStateEngine.determineTransition(FocusSessionState(FocusState.FOCUS_ACTIVE, libTag), libTag)); assertEquals(FocusTransition.Stop, FocusStateEngine.determineTransition(FocusSessionState(FocusState.FOCUS_ACTIVE, class1Tag), class1Tag)) }
    @Test fun `ACTIVE state + different registered tag results in IGNORE`() { assertEquals(FocusTransition.Ignore, FocusStateEngine.determineTransition(FocusSessionState(FocusState.FOCUS_ACTIVE, libTag), class1Tag)); assertEquals(FocusTransition.Ignore, FocusStateEngine.determineTransition(FocusSessionState(FocusState.FOCUS_ACTIVE, class1Tag), libTag)) }
    @Test fun `ACTIVE state + unknown tag results in IGNORE`() { assertEquals(FocusTransition.Ignore, FocusStateEngine.determineTransition(FocusSessionState(FocusState.FOCUS_ACTIVE, libTag), unknownTag)) }
    @Test fun `simulated tag toggles correctly`() { val simTag = "simulated_tag_01"; assertTrue(FocusStateEngine.determineTransition(FocusSessionState(FocusState.NORMAL), simTag) is FocusTransition.Start); assertEquals(FocusTransition.Stop, FocusStateEngine.determineTransition(FocusSessionState(FocusState.FOCUS_ACTIVE, simTag), simTag)); assertEquals(FocusTransition.Ignore, FocusStateEngine.determineTransition(FocusSessionState(FocusState.FOCUS_ACTIVE, libTag), simTag)) }
}
