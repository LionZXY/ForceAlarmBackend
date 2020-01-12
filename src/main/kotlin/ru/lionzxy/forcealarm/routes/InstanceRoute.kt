package ru.lionzxy.forcealarm.routes

import io.ktor.application.call
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.joda.time.DateTime
import ru.lionzxy.forcealarm.model.api.ApiResponse
import ru.lionzxy.forcealarm.model.api.InstanceApiRequest
import ru.lionzxy.forcealarm.model.api.RegisterResponse
import ru.lionzxy.forcealarm.model.api.respondWithCode
import ru.lionzxy.forcealarm.model.db.Alarm
import ru.lionzxy.forcealarm.model.db.AlarmMethodStatus
import ru.lionzxy.forcealarm.model.db.Alarms
import ru.lionzxy.forcealarm.model.db.Instance
import java.util.*

fun Route.instanceRoutes() {
    post("register") {
        val request = call.receive<InstanceApiRequest>()
        val instance = transaction {
            Instance.new {
                pushToken = request.pushToken
                phoneNumber = request.phoneNumber
            }
        }
        val botUsername = call.application.environment.config
            .property("forcealarm.telegram.bot.name").getString()
        call.respondWithCode(ApiResponse(RegisterResponse(instance.id.toString(), botUsername)))
    }

    post("update/{instance_id}") {
        val instanceId = call.parameters["instance_id"]
        val request = call.receive<InstanceApiRequest>()
        transaction {
            val instance = Instance.findById(UUID.fromString(instanceId))!!
            instance.lastActive = DateTime.now()
            request.phoneNumber?.let { instance.phoneNumber = request.phoneNumber }
            request.pushToken?.let { instance.pushToken = it }
        }
    }

    get("events/{instance_id}") {
        val instanceId = call.parameters["instance_id"]
        val alarms = transaction {
            val instance = Instance.findById(UUID.fromString(instanceId))!!
            val user = instance.assignFor!!.id
            Alarms.update({ Alarms.to eq user }) {
                it[pollingStatus] = AlarmMethodStatus.OK
                it[pollingDescription] = "Доставлено"
            }
            return@transaction Alarm.find { Alarms.to eq user and (Alarms.isActive eq true) }.map { it.toAlarmApi() }
                .toList()
        }
        call.respond(ApiResponse(alarms))
    }
}
