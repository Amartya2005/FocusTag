package com.focustag.app.data.remote

import android.util.Log
import com.focustag.app.data.model.AcsHealth
import com.focustag.app.data.model.EntrySource
import com.focustag.app.data.model.TapFocusResponse
import com.focustag.app.data.supabase.SupabaseModule
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Pack 2/4: server owns OPEN/CLOSED. Client arms/releases only after accepted=true.
 */
open class TapFocusRepository {

    open suspend fun tapFocus(
        tagUid: String,
        installUuid: String,
        acsHealth: AcsHealth,
        entrySource: EntrySource? = null,
        idempotencyKey: UUID = UUID.randomUUID(),
        force: Boolean = false,
        targetStudentId: String? = null
    ): Result<TapFocusResponse> {
        return try {
            val payload = buildJsonObject {
                put("p_tag_uid", tagUid)
                put("p_install_uuid", installUuid)
                put("p_idempotency_key", idempotencyKey.toString())
                put("p_acs_health", acsHealth.name)
                put("p_force", force)
                if (entrySource != null) put("p_entry_source", entrySource.name.lowercase())
                if (targetStudentId != null) put("p_target_student_id", targetStudentId)
            }
            val response = SupabaseModule.client.postgrest.rpc("tap_focus", payload).decodeAs<TapFocusResponse>()
            Result.success(response)
        } catch (e: Exception) {
            Log.e("TapFocusRepository", "tap_focus failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    open suspend fun blockedPackagesForClass(classId: String?): Set<String> {
        if (classId.isNullOrBlank()) return emptySet()
        return try {
            val payload = buildJsonObject { put("p_class_id", classId) }
            val raw = SupabaseModule.client.postgrest.rpc("get_class_policy", payload).data
            val root = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
            root["packages"]?.jsonArray.orEmpty().mapNotNull { item ->
                val obj = item.jsonObject
                val action = obj["action"]?.jsonPrimitive?.content
                val pkg = obj["package"]?.jsonPrimitive?.content
                if (action == "BLOCK" && !pkg.isNullOrBlank()) pkg else null
            }.toSet()
        } catch (e: Exception) {
            Log.e("TapFocusRepository", "class policy failed: ${e.message}")
            emptySet()
        }
    }
}
