package ru.lionzxy.forcealarm.model.api.firebase

import com.google.gson.annotations.SerializedName


data class FirebaseAnswer(
    @SerializedName("multicast_id")
    var multicastId: Long? = null,
    @SerializedName("success")
    var success: Int,
    @SerializedName("failure")
    var failure: Int,
    @SerializedName("canonical_ids")
    var canonicalIds: Int,
    @SerializedName("results")
    var results: List<FirebaseResult>? = null
)

data class FirebaseResult(
    @SerializedName("error")
    var error: String? = null,
    @SerializedName("message_id")
    var messageId: String? = null
)
