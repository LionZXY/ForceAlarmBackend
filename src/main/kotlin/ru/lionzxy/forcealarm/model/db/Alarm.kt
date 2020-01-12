package ru.lionzxy.forcealarm.model.db

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import ru.lionzxy.forcealarm.model.api.AlarmApi

object Alarms : IntIdTable("alarms") {
    val from = reference("from", Users)
    val to = reference("to", Users)
    val telegramMessageId = integer("telegram_message_id")
    val telegramChatId = long("telegram_chat_id")
    val reason = text("reason")
    // with defaults
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").clientDefault { DateTime.now() }
    val answerAt = datetime("answer_at").nullable()
    val logs = text("logs").default("")
    val pollingStatus = enumeration("polling_status", AlarmMethodStatus::class).default(AlarmMethodStatus.PROCESSING)
    val pollingDescription = text("polling_description").default("starting...")
    val pushStatus = enumeration("push_status", AlarmMethodStatus::class).default(AlarmMethodStatus.PROCESSING)
    val pushDescription = text("push_description").default("starting...")
    val smsStatus = enumeration("sms_status", AlarmMethodStatus::class).default(AlarmMethodStatus.PROCESSING)
    val smsDescription = text("sms_description").default("starting...")
}

enum class AlarmMethodStatus(val emoji: String) {
    PROCESSING("\uD83D\uDD04"),
    WAITING("\uD83D\uDD50"),
    OK("✅"),
    ERROR("‼️")
}

class Alarm(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Alarm>(Alarms)

    var from by User referencedOn Alarms.from
    var to by User referencedOn Alarms.to
    var telegramMessageId by Alarms.telegramMessageId
    var telegramChatId by Alarms.telegramChatId
    var reason by Alarms.reason

    var isActive by Alarms.isActive
    var createdAt by Alarms.createdAt
    var answerAt by Alarms.answerAt
    var logs by Alarms.logs
    var pollingStatus by Alarms.pollingStatus
    var pollingDescription by Alarms.pollingDescription
    var pushStatus by Alarms.pushStatus
    var pushDescription by Alarms.pushDescription
    var smsStatus by Alarms.smsStatus
    var smsDescription by Alarms.smsDescription

    fun addLog(log: String) {
        if (TransactionManager.currentOrNull() == null) {
            transaction { logs = "$logs\n$log" }
        }
        logs = "$logs\n$log"
    }

    fun toAlarmApi(): AlarmApi {
        return AlarmApi(
            id.value,
            from.fullName(),
            reason,
            isActive,
            createdAt
        )
    }
}
