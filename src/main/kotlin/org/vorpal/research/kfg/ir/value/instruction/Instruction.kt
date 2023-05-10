package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Location
import org.vorpal.research.kfg.ir.value.BlockUsageContext
import org.vorpal.research.kfg.ir.value.BlockUser
import org.vorpal.research.kfg.ir.value.Name
import org.vorpal.research.kfg.ir.value.UsableBlock
import org.vorpal.research.kfg.ir.value.UsableValue
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.ValueUsageContext
import org.vorpal.research.kfg.ir.value.ValueUser
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kthelper.assert.asserted

abstract class Instruction internal constructor(
    name: Name,
    type: Type,
    protected val ops: MutableList<Value>,
    ctx: UsageContext
) : Value(name, type), ValueUser, Iterable<Value> {

    internal var parentUnsafe: BasicBlock? = null
    var location = Location()
        internal set

    val parent get() = asserted(hasParent) { parentUnsafe!! }
    val hasParent get() = parentUnsafe != null

    open val isTerminate = false

    val operands: List<Value>
        get() = ops

    init {
        with(ctx) {
            ops.forEach { it.addUser(this@Instruction) }
        }
    }


    abstract fun print(): String
    override fun iterator(): Iterator<Value> = ops.iterator()

    override fun replaceUsesOf(ctx: ValueUsageContext, from: UsableValue, to: UsableValue) = with(ctx) {
        for (index in ops.indices) {
            if (ops[index] == from) {
                ops[index].removeUser(this@Instruction)
                ops[index] = to.get()
                to.addUser(this@Instruction)
            }
        }
    }

    abstract fun clone(ctx: UsageContext): Instruction
    open fun update(ctx: UsageContext, remapping: Map<Value, Value> = mapOf(), loc: Location = location): Instruction {
        val new = clone(ctx)
        remapping.forEach { (from, to) -> new.replaceUsesOf(ctx, from, to) }
        new.location = loc
        return new
    }

    override fun clearValueUses(ctx: ValueUsageContext) = with(ctx) {
        ops.forEach {
            it.removeUser(this@Instruction)
        }
    }
}

abstract class TerminateInst(
    name: Name,
    type: Type,
    operands: MutableList<Value>,
    protected val internalSuccessors: MutableList<BasicBlock>,
    ctx: UsageContext
) : Instruction(name, type, operands, ctx), BlockUser {

    val successors: List<BasicBlock>
        get() = internalSuccessors

    override val isTerminate = true

    init {
        with(ctx) {
            internalSuccessors.forEach { it.addUser(this@TerminateInst) }
        }
    }

    override fun replaceUsesOf(ctx: BlockUsageContext, from: UsableBlock, to: UsableBlock) = with(ctx) {
        for (index in internalSuccessors.indices) {
            if (internalSuccessors[index] == from) {
                internalSuccessors[index].removeUser(this@TerminateInst)
                internalSuccessors[index] = to.get()
                to.addUser(this@TerminateInst)
            }
        }
    }

    override fun clearBlockUses(ctx: BlockUsageContext) = with(ctx) {
        internalSuccessors.forEach {
            it.removeUser(this@TerminateInst)
        }
    }
}
