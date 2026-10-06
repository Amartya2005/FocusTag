package com.focustag.app.util

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Build
import android.util.Log
import com.focustag.app.domain.NfcProtocol
import com.focustag.app.domain.TagLinkParser

class NfcController(
    private val activity: Activity,
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

    fun isNfcEnabled(): Boolean = nfcAdapter?.isEnabled == true
    fun isNfcAvailable(): Boolean = nfcAdapter != null

    fun enableReaderMode(onTagDetected: (String) -> Unit) {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }
        adapter.enableReaderMode(
            activity,
            { tag ->
                val tagId = NfcProtocol.normalize(bytesToHex(tag.id)) ?: return@enableReaderMode
                if (dedupe(tagId)) return@enableReaderMode
                Log.d(TAG, "NFC tag detected while app is open: $tagId")
                activity.runOnUiThread { onTagDetected(tagId) }
            },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V,
            extras
        )
    }

    fun disableReaderMode() {
        nfcAdapter?.disableReaderMode(activity)
    }

    fun uidFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val action = intent.action ?: return TagLinkParser.extractUid(intent.data?.toString())
        if (action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != Intent.ACTION_VIEW
        ) {
            return TagLinkParser.extractUid(intent.data?.toString())
        }
        TagLinkParser.extractUid(intent.data?.toString())?.let { return it }
        extractNdefUid(intent)?.let { return it }
        val tag = parcelTag(intent) ?: return null
        return NfcProtocol.normalize(bytesToHex(tag.id))
    }

    private fun extractNdefUid(intent: Intent): String? {
        val raw = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        } ?: return null
        raw.filterIsInstance<NdefMessage>().forEach { msg ->
            msg.records.forEach { record ->
                val payload = runCatching { String(record.payload) }.getOrNull() ?: return@forEach
                TagLinkParser.extractUid(payload)?.let { return it }
            }
        }
        return null
    }

    private fun parcelTag(intent: Intent): Tag? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
    }

    private fun dedupe(tagId: String): Boolean {
        val currentTime = System.currentTimeMillis()
        synchronized(this) {
            if (tagId == lastTagId && (currentTime - lastTagTimestamp) < HID_DEDUPE_MS) {
                Log.d(TAG, "NFC HID dedupe: ignoring chatter $tagId")
                return true
            }
            lastTagId = tagId
            lastTagTimestamp = currentTime
        }
        return false
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }
}
