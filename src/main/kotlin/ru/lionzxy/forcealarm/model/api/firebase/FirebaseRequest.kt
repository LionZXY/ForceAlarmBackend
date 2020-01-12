package ru.lionzxy.forcealarm.model.api.firebase

import com.google.gson.annotations.SerializedName

data class FirebaseRequest<T>(
    @SerializedName("to")
    var to: String,
    @SerializedName("data")
    var data: T
)
