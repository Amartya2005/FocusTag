package com.focustag.app.util

import android.app.Activity
import android.nfc.NfcAdapter
import android.util.Log

/**
 * Pack 2/4 NFC reader.
 * HID dedupe only (250–500ms). Start/release debounce lives in FocusViewModel
 * and is stamped only after server `tap_focus` accept.
 */
class NfcController(
    private val activity: Activity,
    /** Unused for debounce; kept for call-site compatibility. */
    private val isFocusActive: () -> Boolean = { false }
) {

    private companion object {
        const val TAG = "NfcController"
        const val HID_DEDUPE_MS = 350L
    }

    private val nfcAdapter: NfcAdapter? by lazy {
        NfcAdapter.getDefaultAdapter(activity)
    }

    private var lastTagId: String? = null
    private var lastTagTimestamp: Long = 0L

    fun isNfcEnabled(): Boolean {
        return nfcAdapter?.isEnabled == true
    }

    fun isNfcAvailable(): Boolean {
        return nfcAdapter != null
    }

    fun enableReaderMode(onTagDetected: (String) -> Unit) {
        nfcAdapter?.enableReaderMode(
            activity,
            { tag ->
                val tagId = bytesToHex(tag.id)
                val currentTime = System.currentTimeMillis()

                synchronized(this) {
                    if (tagId == lastTagId && (currentTime - lastTagTimestamp) < HID_DEDUPE_MS) {
                        Log.d(TAG, "NFC HID dedupe: ignoring chatter $tagId")
                        return@enableReaderMode
                    }
                    lastTagId = tagId
                    lastTagTimestamp = currentTime
                }

                Log.d(TAG, "NFC tag detected: $tagId")
                onTagDetected(tagId)
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    fun disableReaderMode() {
        nfcAdapter?.disableReaderMode(activity)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
}
