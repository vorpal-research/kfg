package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.ir.BasicBlock

interface User

interface ValueUser : User {
    fun replaceUsesOf(from: UsableValue, to: UsableValue)
}

interface BlockUser : User {
    fun replaceUsesOf(from: UsableBlock, to: UsableBlock)
}

abstract class Usable<T> {
    protected val abstractUsers = hashSetOf<User>()

    abstract fun get(): T
    open fun addUser(user: User) {
        abstractUsers.add(user)
    }

    open fun removeUser(user: User) {
        abstractUsers.remove(user)
    }
}

abstract class UsableValue : Usable<Value>() {
    val users: Set<ValueUser>
        get() = abstractUsers.asSequence().map { it as ValueUser }.toSet()

    override fun addUser(user: User) {
        require(user is ValueUser) { "Trying to register non-value user to value" }
        super.addUser(user)
    }

    open fun replaceAllUsesWith(to: UsableValue) {
        users.forEach { it.replaceUsesOf(this, to) }
    }
}

abstract class UsableBlock : Usable<BasicBlock>() {
    val users: Set<BlockUser>
        get() = abstractUsers.asSequence().map { it as BlockUser }.toSet()

    override fun addUser(user: User) {
        require(user is BlockUser) { "Trying to register non-block user to block" }
        super.addUser(user)
    }

    fun replaceAllUsesWith(to: UsableBlock) {
        users.forEach { it.replaceUsesOf(this, to) }
    }
}