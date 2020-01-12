package ru.lionzxy.forcealarm.model.api

import com.google.gson.annotations.SerializedName

data class RegisterResponse(
    @SerializedName("instance_id")
    val instanceId: String,
    @SerializedName("bot_username")
    val botUsername: String? = null
)
