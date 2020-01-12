package ru.lionzxy.forcealarm.telegram

import io.ktor.config.ApplicationConfig
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.lionzxy.forcealarm.model.db.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

typealias TelegramUser = org.telegram.telegrambots.meta.api.objects.User

class TelegramBot(
    private val config: ApplicationConfig
) : TelegramLongPollingBot() {
    private val logger = LoggerFactory.getLogger("TelegramBot")
    private val uuidRegex = config.property("forcealarm.telegram.user.uuid_regexp").getString().toRegex()
    private val genCommandRegex = "\\/gen\\d+".toRegex()
    private val adminId = config.property("forcealarm.telegram.user.admin_id").getString()

    private val tmpReasonStorage = HashMap<Int, String>()

    override fun getBotUsername() = config.property("forcealarm.telegram.bot.name").getString()
    override fun getBotToken() = config.property("forcealarm.telegram.bot.token").getString()

    override fun onUpdateReceived(update: Update?) {
        val msg = update?.message
        if (msg != null) {
            processMessage(msg)
        }
        val callbackQuery = update?.callbackQuery
        if (callbackQuery != null) {
            processCallback(callbackQuery)
        }
    }

    private fun processMessage(msg: Message) {
        try {
            if (adminId == msg.from.id.toString()) {
                processAdminCommand(msg)
                return
            }

            processGuestCommand(msg)
        } catch (ex: Exception) {
            logger.error("Error while process message", ex)
            sendTextMessage(msg.from.id, config.property("forcealarm.telegram.text.general_error").getString())
        }
    }

    private fun processAdminCommand(msg: Message) {
        val text = msg.text ?: return

        if (text.startsWith("/start")) {
            sendTextMessage(
                msg.from.id,
                config.property("forcealarm.telegram.text.welcome_message_admin").getString()
            )
        }

        val uuidGroups = uuidRegex.find(text)
        if (uuidGroups != null) {
            val uuid = UUID.fromString(uuidGroups.value)
            val instance = transaction { Instance.findById(uuid) }
            if (instance == null) {
                sendTextMessage(
                    msg.from.id,
                    config.property("forcealarm.telegram.text.instance_not_found").getString()
                )
                return
            }
            transaction {
                val user = User.findById(msg.from.id.toLong()) ?: User.new(msg.from.id.toLong()) {
                    firstName = msg.from.firstName
                    lastName = msg.from.lastName
                    username = msg.from.userName
                }

                Instances.update({ Instances.assignFor eq user.id }) {
                    it[assignFor] = null
                }
                instance.assignFor = user
            }
            val answer =
                String.format(config.property("forcealarm.telegram.text.instance_done").getString(), uuid.toString())
            sendTextMessage(msg.from.id, answer)
            return
        }

        val instance = transaction {
            Instance.find { Instances.assignFor eq EntityID(msg.from.id.toLong(), Users) }.firstOrNull()
        }

        if (instance == null) {
            sendTextMessage(msg.from.id, config.property("forcealarm.telegram.text.instance_need").getString())
            return
        }

        if (text.startsWith("/gen")) {
            val number = genCommandRegex.find(text)
            val uuids = genUUIDs(number?.value?.toIntOrNull() ?: 1).joinToString("\n") {
                "`$it`"
            }
            val answer = String.format(
                config.property("forcealarm.telegram.text.gen_uuids").getString(),
                uuids
            )
            sendTextMessage(msg.from.id, answer)
            return
        }
    }

    private fun processGuestCommand(msg: Message) {
        val text = msg.text ?: return
        val user = transaction { User.findById(msg.from.id.toLong()) }
        val uuidGroups = uuidRegex.find(text)

        if (uuidGroups != null) {
            val uuidText = uuidGroups.value
            val uuid = UUID.fromString(uuidText)
            transaction { processUUID(uuid, msg.from) }
            return
        }

        if (user == null) {
            val answer = String.format(
                config.property("forcealarm.telegram.text.welcome_message").getString(),
                msg.from.id.toString()
            )
            sendTextMessage(msg.from.id, answer)
            return
        }

        if (!user.checkContainsToken()) {
            sendTextMessage(msg.from.id, config.property("forcealarm.telegram.text.invalid_permission").getString())
            return
        }

        if (text.startsWith("/help")) {
            sendTextMessage(msg.from.id, config.property("forcealarm.telegram.text.help").getString())
            return
        }

        if (text.startsWith("!alarm")) {
            val deliveryMessage =
                if (text.length > "!alarm".length) text.substring("!alarm".length).trim(' ', '\n') else null
            if (deliveryMessage.isNullOrEmpty()) {
                sendTextMessage(
                    msg.from.id,
                    config.property("forcealarm.telegram.text.alarm_error_empty_description").getString()
                )
                return
            }

            sendTextMessage(msg.from.id, config.property("forcealarm.telegram.text.alarm_warning").getString())

            val adminUser = transaction { User.findById(adminId.toLong())!! }
            val confirmDialog = SendMessage()
            val keyboard = InlineKeyboardMarkup()
            val okButton =
                InlineKeyboardButton(config.property("forcealarm.telegram.text.alarm_confirm_ok").getString())
            okButton.callbackData = "ok"
            val cancelButton =
                InlineKeyboardButton(config.property("forcealarm.telegram.text.alarm_confirm_cancel").getString())
            cancelButton.callbackData = "cancel"
            keyboard.keyboard = listOf(listOf(okButton), listOf(cancelButton))
            confirmDialog.text = String.format(
                config.property("forcealarm.telegram.text.alarm_confirm").getString(),
                adminUser.fullName(),
                deliveryMessage
            )
            confirmDialog.enableMarkdown(true)
            confirmDialog.replyMarkup = keyboard
            confirmDialog.chatId = msg.from.id.toString()
            val dialogMsg = execute(confirmDialog)
            tmpReasonStorage[dialogMsg.messageId] = deliveryMessage
            return
        }

        sendTextMessage(msg.from.id, config.property("forcealarm.telegram.text.unknown_message").getString())
    }

    private fun processCallback(callback: CallbackQuery) {
        execute(DeleteMessage(callback.from.id.toString(), callback.message.messageId))

        if (callback.data == "cancel") {
            sendTextMessage(
                callback.from.id,
                config.property("forcealarm.telegram.text.alarm_confirm_cancel").getString()
            )
            return
        }

        if (callback.data != "ok") {
            return
        }
        val msg =
            sendTextMessage(callback.from.id, config.property("forcealarm.telegram.text.alarm_confirm_ok").getString())

        transaction {
            val from = User.findById(callback.from.id.toLong())!!
            val to = User.findById(adminId.toLong())!!

            Alarm.new {
                this.from = from
                this.to = to
                this.telegramMessageId = msg.messageId
                this.telegramChatId = msg.chatId
                this.reason = tmpReasonStorage[callback.message.messageId]!!
            }
            tmpReasonStorage.remove(callback.message.messageId)
        }
    }

    private fun genUUIDs(num: Int): List<UUID> {
        val uuids = ArrayList<UUID>()
        transaction {
            repeat(num) {
                var uuid = UUID.randomUUID()

                while (Token.findById(uuid) != null) {
                    uuid = UUID.randomUUID()
                }
                Token.new(uuid) {}
                uuids.add(uuid)
            }
        }
        return uuids
    }

    private fun Transaction.processUUID(uuid: UUID, tgUser: TelegramUser) {
        val token = Token.findById(uuid)

        if (token == null) {
            val answer = String.format(
                config.property("forcealarm.telegram.text.uuid_not_found").getString(),
                uuid
            )
            sendTextMessage(tgUser.id, answer)
            return
        }

        if (token.assignFor != null) {
            val answer = config.property("forcealarm.telegram.text.uuid_already_use").getString()
            sendTextMessage(tgUser.id, answer)
            return
        }

        var user = User.findById(tgUser.id.toLong())

        if (user == null) {
            user = User.new(tgUser.id.toLong()) {
                firstName = tgUser.firstName
                lastName = tgUser.lastName
                username = tgUser.userName
            }
        }
        token.assignFor = user

        sendTextMessage(
            tgUser.id, config.property("forcealarm.telegram.text.success").getString() + "\n" +
                    config.property("forcealarm.telegram.text.alarm_warning").getString()
        )
    }

    private fun sendTextMessage(id: Number, text: String): Message {
        val msg = SendMessage(id.toLong(), text.take(4000))
        msg.enableMarkdown(true)
        msg.disableWebPagePreview()
        return sendApiMethod(msg)
    }
}
