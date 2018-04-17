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
    protected val users = mutableSetOf<User>()

    abstract fun get(): T
    open fun addUser(user: User) {
        users.add(user)
    }

    open fun removeUser(user: User) {
        users.remove(user)
    }
}

abstract class UsableValue : Usable<Value>() {
    fun users() = users.map { it as ValueUser }.toSet()
    override fun addUser(user: User) {
        assert(user is ValueUser, { "Trying to add non-value user to value" })
        super.addUser(user)
    }

    fun replaceAllUsesWith(to: UsableValue) {
        users().forEach { it.replaceUsesOf(this, to) }
    }
}

abstract class UsableBlock : Usable<BasicBlock>() {
    fun users() = users.map { it as BlockUser }.toSet()
    override fun addUser(user: User) {
        assert(user is BlockUser, { "Trying to add non-block user to block" })
        super.addUser(user)
    }

    fun replaceAllUsesWith(to: UsableBlock) {
        users().forEach { it.replaceUsesOf(this, to) }
    }
}