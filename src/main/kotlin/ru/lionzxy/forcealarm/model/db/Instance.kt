package ru.lionzxy.forcealarm.model.db

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.UUIDTable
import org.joda.time.DateTime
import java.util.*

object Instances : UUIDTable("instances") {
    val pushToken = text("gcm_pushtoken").nullable()
    val lastActive = datetime("last_active").clientDefault { DateTime.now() }
    val assignFor = optReference("user_assign", Users)
    val phoneNumber = text("phone").nullable()
}

class Instance(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Instance>(Instances)

    var pushToken by Instances.pushToken
    var phoneNumber by Instances.phoneNumber
    var lastActive by Instances.lastActive
    var assignFor by User optionalReferencedOn Instances.assignFor
}
