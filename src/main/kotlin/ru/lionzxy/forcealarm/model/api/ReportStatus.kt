package ru.lionzxy.forcealarm.model.api

import com.google.gson.annotations.SerializedName

data class ReportStatus(
    @SerializedName("delivery_method")
    var method: DeliveryMethod,
    @SerializedName("alarm_id")
    var alarmId: Int
)

enum class DeliveryMethod() {
    @SerializedName("sms")
    SMS,
    @SerializedName("push")
    PUSH
}
