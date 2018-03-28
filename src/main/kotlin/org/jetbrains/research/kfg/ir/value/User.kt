package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.ir.BasicBlock

interface User

interface Usable<T> {
    val users: MutableSet<User>

    fun get(): T
    fun addUser(user: User) { users.add(user) }
    fun removeUser(user: User) { users.remove(user) }
}

interface UsableValue : Usable<Value> {
    fun users() = users.map { it as ValueUser }.toSet()
    override fun addUser(user: User) {
        assert(user is ValueUser, { "Trying to add non-value user to value"})
        super.addUser(user)
    }
    fun replaceAllUsesWith(to: Value) {
        users().forEach { it.replaceUsesOf(this, to) }
    }
}

interface UsableBlock : Usable<BasicBlock> {
    fun users() = users.map { it as BlockUser }.toSet()
    override fun addUser(user: User) {
        assert(user is BlockUser, { "Trying to add non-block user to usable block"})
        super.addUser(user)
    }
    fun replaceAllUsesWith(to: BasicBlock) {
        users().forEach { it.replaceUsesOf(this, to) }
    }
}

interface ValueUser : User {
    fun replaceUsesOf(from: UsableValue, to: UsableValue)
}

interface BlockUser : User {
    fun replaceUsesOf(from: UsableBlock, to: UsableBlock)
}