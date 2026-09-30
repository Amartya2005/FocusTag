package com.focustag.app.data.repository

import com.focustag.app.data.supabase.SupabaseModule
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AdminLocation(
    val id: String,
    val name: String
)

@Serializable
data class AdminClass(
    val id: String,
    val name: String,
    @SerialName("location_id") val locationId: String,
    @SerialName("institution_id") val institutionId: String
)

@Serializable
data class AdminProfile(
    val id: String,
    val name: String? = null,
    val role: String = "student"
)

@Serializable
data class AdminEnrollment(
    @SerialName("student_id") val studentId: String,
    @SerialName("class_id") val classId: String,
    val profiles: AdminProfile? = null
)

@Serializable
data class AdminTag(
    val uid: String,
    @SerialName("location_id") val locationId: String,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class AdminPolicy(
    val id: String,
    @SerialName("class_id") val classId: String,
    val version: String
)

@Serializable
data class AdminPolicyPackage(
    @SerialName("policy_id") val policyId: String,
    @SerialName("package_name") val packageName: String,
    val action: String
)

@Serializable
private data class NewClass(
    @SerialName("institution_id") val institutionId: String,
    @SerialName("location_id") val locationId: String,
    val name: String
)

@Serializable
private data class NewEnrollment(
    @SerialName("student_id") val studentId: String,
    @SerialName("class_id") val classId: String
)

@Serializable
private data class NewTag(
    val uid: String,
    @SerialName("location_id") val locationId: String,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
private data class NewPolicy(
    @SerialName("institution_id") val institutionId: String,
    @SerialName("class_id") val classId: String,
    val version: String
)

@Serializable
private data class NewPolicyPackage(
    @SerialName("policy_id") val policyId: String,
    @SerialName("package_name") val packageName: String,
    val action: String
)

class AdminRepository(private val institutionId: String) {
    suspend fun locations(): Result<List<AdminLocation>> = runCatching {
        SupabaseModule.client.from("locations").select { filter { eq("institution_id", institutionId) } }.decodeList()
    }

    suspend fun classes(): Result<List<AdminClass>> = runCatching {
        SupabaseModule.client.from("classes").select(columns = Columns.raw("id, name, location_id, institution_id")) {
            filter { eq("institution_id", institutionId) }
        }.decodeList()
    }

    suspend fun createClass(name: String, locationId: String): Result<Unit> = runCatching {
        SupabaseModule.client.from("classes").insert(NewClass(institutionId, locationId, name))
    }

    suspend fun students(): Result<List<AdminProfile>> = runCatching {
        SupabaseModule.client.from("profiles").select(columns = Columns.raw("id, name, role")) {
            filter { eq("institution_id", institutionId); eq("role", "student") }
        }.decodeList()
    }

    suspend fun enrollments(classId: String): Result<List<AdminEnrollment>> = runCatching {
        SupabaseModule.client.from("enrollments").select(columns = Columns.raw("student_id, class_id, profiles(id, name, role)")) {
            filter { eq("class_id", classId) }
        }.decodeList()
    }

    suspend fun enroll(studentId: String, classId: String): Result<Unit> = runCatching {
        SupabaseModule.client.from("enrollments").insert(NewEnrollment(studentId, classId))
    }

    suspend fun tags(): Result<List<AdminTag>> = runCatching {
        val locationIds = locations().getOrThrow().map { it.id }
        if (locationIds.isEmpty()) return@runCatching emptyList()
        SupabaseModule.client.from("nfc_tags").select(columns = Columns.raw("uid, location_id, is_active")) {
            filter { isIn("location_id", locationIds) }
        }.decodeList()
    }

    suspend fun registerTag(uid: String, locationId: String): Result<Unit> = runCatching {
        SupabaseModule.client.from("nfc_tags").insert(NewTag(uid.trim().uppercase(), locationId))
    }

    suspend fun latestPolicy(classId: String): Result<AdminPolicy?> = runCatching {
        SupabaseModule.client.from("class_app_policies").select {
            filter { eq("class_id", classId) }
            order("updated_at", Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull()
    }

    suspend fun policyPackages(policyId: String): Result<List<AdminPolicyPackage>> = runCatching {
        SupabaseModule.client.from("class_app_policy_packages").select { filter { eq("policy_id", policyId) } }.decodeList()
    }

    suspend fun savePolicy(classId: String, packages: List<Pair<String, String>>): Result<Unit> = runCatching {
        val version = System.currentTimeMillis().toString()
        val policy = SupabaseModule.client.from("class_app_policies").insert(NewPolicy(institutionId, classId, version)) { select() }.decodeSingle<AdminPolicy>()
        if (packages.isNotEmpty()) {
            SupabaseModule.client.from("class_app_policy_packages").insert(
                packages.map { (packageName, action) -> NewPolicyPackage(policy.id, packageName, action) }
            )
        }
    }
}
