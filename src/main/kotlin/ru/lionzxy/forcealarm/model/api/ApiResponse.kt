package ru.lionzxy.forcealarm.model.api

import com.google.gson.annotations.SerializedName
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond

data class ApiResponse<T>(
    @SerializedName("message")
    var message: T?,
    @SerializedName("code")
    var code: Int = HttpStatusCode.OK.value,
    @SerializedName("status")
    var status: String = HttpStatusCode.OK.description
)

suspend inline fun ApplicationCall.respondWithCode(
    response: ApiResponse<*>,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    response.code = status.value
    response.status = status.description
    this.respond(status, response)
}


suspend inline fun ApplicationCall.respondError(response: ErrorResponse, status: HttpStatusCode = HttpStatusCode.OK) {
    this.respond(status, ApiResponse(response.reason, status.value, response.status.id))
}
