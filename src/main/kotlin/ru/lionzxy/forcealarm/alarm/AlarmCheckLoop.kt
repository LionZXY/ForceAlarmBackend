package ru.lionzxy.forcealarm.alarm

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.defaultSerializer
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.config.ApplicationConfig
import io.ktor.http.ContentType
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import ru.lionzxy.forcealarm.model.api.firebase.FirebaseAnswer
import ru.lionzxy.forcealarm.model.api.firebase.FirebaseRequest
import ru.lionzxy.forcealarm.model.db.*
import ru.lionzxy.forcealarm.telegram.TelegramBot

private val client = HttpClient(Apache) {
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
    install(Logging) {
        logger = Logger.Companion.DEFAULT
        level = LogLevel.HEADERS
    }
}

class AlarmCheckLoop(
    private val bot: TelegramBot,
    private val config: ApplicationConfig
) : Thread() {
    private val logger = LoggerFactory.getLogger(AlarmCheckLoop::class.java)
    private val lastSendMessage = HashMap<Int, String>()

    override fun run() {
        while (!isInterrupted) {
            try {
                Thread.sleep(10000)
                val alarms = transaction {
                    Alarm.find { Alarms.isActive eq true }.toList()
                }
                alarms.forEach {
                    try {
                        processAlarm(it)
                    } catch (ex: Exception) {
                        logger.error("Error while invalidate alarm ${it.id}", ex)
                    }
                }
            } catch (ex: Exception) {
                logger.error("Error while tick alarm check loop", ex)
                ex.printStackTrace()
            }
        }
    }

    public fun onAlarmDisactive(alarm: Alarm) {
        lastSendMessage.remove(alarm.id.value)
    }

    private fun processAlarm(alarm: Alarm) {
        try {
            processPolling(alarm)
        } catch (ex: Exception) {
            transaction {
                alarm.pollingStatus = AlarmMethodStatus.ERROR
                alarm.pollingDescription = "Ошибка"
                alarm.addLog("Error on polling")
            }
        }

        try {
            GlobalScope.launch { processPush(alarm) }
        } catch (ex: Exception) {
            transaction {
                alarm.pushStatus = AlarmMethodStatus.ERROR
                alarm.pushDescription = "Ошибка"
                alarm.addLog("Error on push")
            }
        }

        try {
            GlobalScope.launch { processSMS(alarm) }
        } catch (ex: Exception) {
            transaction {
                alarm.smsStatus = AlarmMethodStatus.ERROR
                alarm.smsDescription = "Ошибка"
                alarm.addLog("Error on sms")
            }
        }

        invalidateAlarm(alarm)
    }

    private fun processPolling(alarm: Alarm) {
        if (alarm.pollingStatus == AlarmMethodStatus.OK) {
            return
        }

        if (alarm.pollingStatus == AlarmMethodStatus.PROCESSING) {
            transaction {
                alarm.pollingStatus = AlarmMethodStatus.WAITING
                alarm.pollingDescription = "Ждем пока приложение придет на сервер"
                alarm.addLog("Polling switch to waiting")
            }
            return
        }
    }

    private suspend fun processPush(alarm: Alarm) = coroutineScope {
        if (alarm.pushStatus == AlarmMethodStatus.OK ||
            alarm.pushStatus == AlarmMethodStatus.ERROR ||
            alarm.pushStatus == AlarmMethodStatus.WAITING
        ) {
            return@coroutineScope
        }
        val instance = transaction { Instance.find { Instances.assignFor eq alarm.to.id }.firstOrNull() }
        if (instance?.pushToken == null) {
            transaction {
                alarm.pushStatus = AlarmMethodStatus.ERROR
                alarm.pushDescription = "Отсутствует pushToken"
                alarm.addLog("Push switch to error")
            }
            return@coroutineScope
        }
        val request = transaction {
            return@transaction FirebaseRequest(
                instance.pushToken!!,
                alarm.toAlarmApi()
            )
        }
        val answer = client.post<FirebaseAnswer>("https://fcm.googleapis.com/fcm/send") {
            header(
                "Authorization",
                "key=${config.property("forcealarm.deliver.firebase_server_token_legacy").getString()}"
            )
            body = defaultSerializer().write(request, ContentType.Application.Json)
        }
        if (answer.success < 1) {
            transaction {
                alarm.pushStatus = AlarmMethodStatus.ERROR
                alarm.pushDescription = answer.results?.firstOrNull()?.error ?: "Error send gcm"
                alarm.addLog("Error answer firebase")
            }
            logger.error("Error $alarm on $instance with $answer")
            return@coroutineScope
        }

        transaction {
            alarm.pushStatus = AlarmMethodStatus.WAITING
            alarm.pushDescription = "gcm-пуш отправлен"
            alarm.addLog("GCM push send")
        }
    }

    private suspend fun processSMS(alarm: Alarm) = coroutineScope {
        if (alarm.smsStatus == AlarmMethodStatus.OK ||
            alarm.smsStatus == AlarmMethodStatus.ERROR ||
            alarm.smsStatus == AlarmMethodStatus.WAITING
        ) {
            return@coroutineScope
        }
    }

    private fun invalidateAlarm(alarm: Alarm) {
        val builder = StringBuilder("⏰ Будильник *№${alarm.id}*. Состояние: ")
        if (alarm.isActive) {
            builder.append("*активен*")
        } else {
            builder.append("*неактивен*")
        }

        builder.append("\n\n")

        builder.append(alarm.pollingStatus.emoji).append(" Polling: ").append(alarm.pollingDescription.decapitalize())
            .append('\n')
        builder.append(alarm.pushStatus.emoji).append(" Firebase уведомление: ")
            .append(alarm.pushDescription.decapitalize()).append('\n')
        builder.append(alarm.smsStatus.emoji).append(" SMS отправка: ").append(alarm.smsDescription.decapitalize())
            .append('\n')

        builder.append('\n').append("Причина: ").append(alarm.reason).append("\n\n")

        builder.append("Создан: ").append(alarm.createdAt.toString()).append('\n')
        if (alarm.answerAt != null) {
            builder.append("Ответ: ").append(alarm.answerAt.toString())
        } else {
            builder.append("Ответа еще нет")
        }

        builder.append("\n\nЛоги:\n").append(alarm.logs)
        val text = builder.toString().take(4000)
        if (lastSendMessage[alarm.id.value] == text) {
            logger.info("Message for alarm ${alarm.id} already invalidate")
            return
        }
        val msg = EditMessageText()
        msg.chatId = alarm.telegramChatId.toString()
        msg.messageId = alarm.telegramMessageId
        msg.text = text
        msg.enableMarkdown(true)
        bot.execute(msg)
        lastSendMessage[alarm.id.value] = text
    }

}
