package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.allValues
import java.io.Closeable
import java.util.*

private abstract class ValueUsageContext {
    private var usersMap: MutableMap<Value, MutableSet<ValueUser>>? = null

    operator fun get(value: Value): MutableSet<ValueUser> {
        if (usersMap == null) usersMap = IdentityHashMap()
        return usersMap!!.getOrPut(value, ::mutableSetOf)
    }

    fun clearValueInfo(value: Value) {
        usersMap?.remove(value)?.clear()
    }

    fun clear() {
        usersMap?.forEach {
            it.value.clear()
        }
        usersMap?.clear()
        usersMap = null
    }
}

private object UsageContextState : ValueUsageContext()

interface User

interface ValueUser : User {
    fun replaceUsesOf(from: UsableValue, to: UsableValue)
    fun clearUses()
}

interface BlockUser : User {
    fun replaceUsesOf(from: UsableBlock, to: UsableBlock)
}

abstract class Usable<T> {
    protected abstract val abstractUsers: MutableSet<User>// = hashSetOf()

    abstract fun get(): T
    open fun addUser(user: User) {
        abstractUsers.add(user)
    }

    open fun removeUser(user: User) {
        abstractUsers.remove(user)
    }
}

abstract class UsableValue : Usable<Value>() {
    @Suppress("UNCHECKED_CAST")
    override val abstractUsers: MutableSet<User>
        get() = UsageContextState[this.get()] as MutableSet<User>

    @Suppress("UNCHECKED_CAST")
    val users: Set<ValueUser>
        get() = abstractUsers as Set<ValueUser>

    override fun addUser(user: User) {
        require(user is ValueUser) { "Trying to register non-value user to value" }
        super.addUser(user)
    }

    open fun replaceAllUsesWith(to: UsableValue) {
        users.toSet().forEach { it.replaceUsesOf(this, to) }
    }
}

abstract class UsableBlock : Usable<BasicBlock>() {
    @Suppress("UNCHECKED_CAST")
    override val abstractUsers: MutableSet<User> = mutableSetOf()

    @Suppress("UNCHECKED_CAST")
    val users: Set<BlockUser>
        get() = abstractUsers as Set<BlockUser>

    override fun addUser(user: User) {
        require(user is BlockUser) { "Trying to register non-block user to block" }
        super.addUser(user)
    }

    fun replaceAllUsesWith(to: UsableBlock) {
        users.toSet().forEach { it.replaceUsesOf(this, to) }
    }
}


class UsageContext(vararg val methods: Method) : Closeable {
    init {
        for (method in methods) {
            for (inst in method.flatten()) {
                for (operand in inst.operands) {
                    operand.addUser(inst)
                }
            }
        }
    }

    override fun close() {
        for (method in methods) {
            for (value in method.allValues) {
                UsageContextState.clearValueInfo(value)
            }
        }
    }
}

val Method.usageContext get() = UsageContext(this)

fun <T : Any> withUsageContextOf(vararg methods: Method, body: UsageContext.() -> T) =
    UsageContext(*methods).use(body)

fun <T : Any> withUsageContext(ctx: UsageContext, body: UsageContext.() -> T) = ctx.body()