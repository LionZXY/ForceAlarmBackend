package ru.lionzxy.forcealarm

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.config.ApplicationConfig
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.gson.gson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import ru.lionzxy.forcealarm.alarm.AlarmCheckLoop
import ru.lionzxy.forcealarm.model.db.Alarms
import ru.lionzxy.forcealarm.model.db.Instances
import ru.lionzxy.forcealarm.model.db.Tokens
import ru.lionzxy.forcealarm.model.db.Users
import ru.lionzxy.forcealarm.routes.instanceRoutes
import ru.lionzxy.forcealarm.routes.statusRoutes
import ru.lionzxy.forcealarm.telegram.TelegramBot
import java.sql.Connection
import java.util.*

fun Application.main() {
    TimeZone.setDefault(TimeZone.getTimeZone("MSK"))

    initDatabase()
    val bot = initTelegramBot(environment.config)
    val loop = AlarmCheckLoop(bot, environment.config)
    loop.start()

    install(DefaultHeaders)
    install(ContentNegotiation) {
        gson {}
    }

    routing {
        route("api") {
            route("status") {
                statusRoutes()
            }
            route("instance") {
                instanceRoutes()
            }
        }
        get("/") {
            call.respond("Test")
        }
    }
}

private fun initDatabase() {
    Database.connect("jdbc:sqlite:local.db", "org.sqlite.JDBC")

    TransactionManager.manager.defaultIsolationLevel =
        Connection.TRANSACTION_SERIALIZABLE // Or Connection.TRANSACTION_READ_UNCOMMITTED
    transaction {
        SchemaUtils.create(Users, Tokens, Instances, Alarms)
    }
}

private fun initTelegramBot(config: ApplicationConfig): TelegramBot {
    ApiContextInitializer.init();
    val telegramBotsApi = TelegramBotsApi()
    val telegramBot = TelegramBot(config)
    try {
        telegramBotsApi.registerBot(telegramBot)
    } catch (e: TelegramApiException) {
        e.printStackTrace()
    }
    return telegramBot
}
