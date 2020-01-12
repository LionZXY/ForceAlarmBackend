package ru.lionzxy.forcealarm.model.api

import com.google.gson.annotations.SerializedName

data class InstanceApiRequest(
    @SerializedName("push_token")
    var pushToken: String? = null,
    @SerializedName("phone_number")
    var phoneNumber: String? = null
)
