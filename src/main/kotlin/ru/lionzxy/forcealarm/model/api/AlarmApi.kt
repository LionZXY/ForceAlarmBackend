package ru.lionzxy.forcealarm.model.api

import com.google.gson.annotations.SerializedName
import org.joda.time.DateTime

data class AlarmApi(
    @SerializedName("id")
    private val id: Int,
    @SerializedName("source_name")
    private val from: String,
    @SerializedName("reason")
    private val reason: String,
    @SerializedName("is_active")
    private val isActive: Boolean,
    @SerializedName("created_at")
    private val createdAt: DateTime
)
