package com.focustag.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AdminLocation(
    val id: String,
    val name: String,
    @SerialName("institution_id") val institutionId: String? = null
)

@Serializable
data class AdminClass(
    val id: String,
    val name: String,
    @SerialName("location_id") val locationId: String,
    @SerialName("institution_id") val institutionId: String
)

@Serializable
data class AdminProfileLookup(
    val id: String,
    val name: String? = null,
    val role: String = "student",
    @SerialName("institution_id") val institutionId: String? = null
)

@Serializable
data class AdminEnrollment(
    @SerialName("student_id") val studentId: String,
    @SerialName("class_id") val classId: String,
    val profiles: AdminProfileLookup? = null
)

@Serializable
data class AdminTag(
    val id: String? = null,
    val uid: String,
    @SerialName("location_id") val locationId: String,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class AdminPolicy(
    val id: String,
    @SerialName("institution_id") val institutionId: String? = null,
    @SerialName("class_id") val classId: String,
    val version: String,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class AdminPolicyPackage(
    @SerialName("policy_id") val policyId: String? = null,
    @SerialName("package_name") val packageName: String,
    val action: String
)

@Serializable
data class AdminIotDevice(
    val id: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("location_id") val locationId: String? = null,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class ClassPolicySnapshot(
    val version: String? = null,
    @SerialName("institution_id") val institutionId: String? = null,
    @SerialName("class_id") val classId: String? = null,
    val packages: List<ClassPolicyPackageEntry> = emptyList()
)

@Serializable
data class ClassPolicyPackageEntry(
    @SerialName("package") val packageName: String,
    val action: String
)
