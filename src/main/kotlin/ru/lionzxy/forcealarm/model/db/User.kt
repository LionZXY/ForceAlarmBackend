package ru.lionzxy.forcealarm.model.db

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction


// Primary key - telegram user id
object Users : LongIdTable("users") {
    val firstName = text("first_name").nullable()
    val lastName = text("last_name").nullable()
    val username = text("username").nullable()
}

class User(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<User>(Users)

    var firstName by Users.firstName
    var lastName by Users.lastName
    var username by Users.username

    fun checkContainsToken(): Boolean {
        if (TransactionManager.currentOrNull() == null) {
            return transaction { !Token.find { Tokens.assignFor eq id }.empty() }
        }
        return !Token.find { Tokens.assignFor eq id }.empty()
    }

    fun fullName(): String {
        val nameBuilder = StringBuilder(firstName)
        if (!lastName.isNullOrEmpty()) {
            nameBuilder.append(' ').append(lastName)
        }
        if (!username.isNullOrEmpty()) {
            nameBuilder.append(" (").append(username).append(')')
        }
        return nameBuilder.toString()
    }
}
