package ru.lionzxy.forcealarm.routes

import io.ktor.application.call
import io.ktor.request.receive
import io.ktor.routing.Route
import io.ktor.routing.post
import ru.lionzxy.forcealarm.model.api.ReportStatus

fun Route.statusRoutes() {
    post("report/{instance_id}") {
        val instanceId = call.parameters["instance_id"]
        val status = call.receive<ReportStatus>()
    }

    post("alarm/done/{id}") {
        val alarmId = call.parameters["id"]
    }
}
