package org.vorpal.research.kfg.ir.value

import org.vorpal.research.kfg.ir.BasicBlock

interface User

interface ValueUser : User {
    fun replaceUsesOf(ctx: ValueUsageContext, from: UsableValue, to: UsableValue)
    fun clearUses(ctx: UsageContext)
}

interface BlockUser : User {
    fun replaceUsesOf(ctx: BlockUsageContext, from: UsableBlock, to: UsableBlock)
}

abstract class Usable<T> {
    abstract fun get(): T
}

abstract class UsableValue : Usable<Value>()
abstract class UsableBlock : Usable<BasicBlock>()
