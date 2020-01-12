package ru.lionzxy.forcealarm.model.db

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.UUIDTable
import java.util.*

object Tokens : UUIDTable() {
    val assignFor = optReference("assign_for", Users)
}

class Token(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Token>(Tokens)

    var assignFor by User optionalReferencedOn Tokens.assignFor
}
