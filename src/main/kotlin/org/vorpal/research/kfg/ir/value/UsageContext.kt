package org.vorpal.research.kfg.ir.value

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.CatchBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.MethodBody
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.InstructionBuilder
import org.vorpal.research.kfg.ir.value.instruction.inst
import java.io.Closeable
import java.util.*

interface ValueUsageContext {
    val UsableValue.users: Set<ValueUser>

    fun UsableValue.addUser(user: ValueUser)
    fun UsableValue.removeUser(user: ValueUser)
    fun UsableValue.replaceAllUsesWith(to: UsableValue)
}

interface BlockUsageContext {
    val UsableBlock.users: Set<BlockUser>

    fun UsableBlock.addUser(user: BlockUser)
    fun UsableBlock.removeUser(user: BlockUser)
    fun UsableBlock.replaceAllUsesWith(to: UsableBlock)
}

interface UsageContext : ValueUsageContext, BlockUsageContext {
    /**
     * user wrappers
     */
    fun ValueUser.clearValueUses() {
        this.clearValueUses(this@UsageContext)
    }

    fun BlockUser.clearBlockUses() {
        this.clearBlockUses(this@UsageContext)
    }

    fun User.clearAllUses() {
        if (this is ValueUser) this.clearValueUses(this@UsageContext)
        if (this is BlockUser) this.clearBlockUses(this@UsageContext)
    }

    fun ValueUser.replaceUsesOf(from: UsableValue, to: UsableValue) = this.replaceUsesOf(this@UsageContext, from, to)
    fun BlockUser.replaceUsesOf(from: UsableBlock, to: UsableBlock) = this.replaceUsesOf(this@UsageContext, from, to)

    /**
     * method wrappers
     */
    fun MethodBody.add(block: BasicBlock) = this.add(this@UsageContext, block)
    fun MethodBody.remove(block: BasicBlock) = this.remove(this@UsageContext, block)
    fun MethodBody.addBefore(before: BasicBlock, bb: BasicBlock) = this.addBefore(this@UsageContext, before, bb)
    fun MethodBody.addAfter(after: BasicBlock, bb: BasicBlock) = this.addAfter(this@UsageContext, after, bb)

    /**
     * basic block wrappers
     */
    fun BasicBlock.addSuccessor(block: BasicBlock) = this.addSuccessor(this@UsageContext, block)
    fun BasicBlock.addSuccessors(vararg blocks: BasicBlock) = this.addSuccessors(this@UsageContext, *blocks)
    fun BasicBlock.addSuccessors(blocks: List<BasicBlock>) = this.addSuccessors(this@UsageContext, blocks)
    fun BasicBlock.removeSuccessor(block: BasicBlock) = this.removeSuccessor(this@UsageContext, block)

    fun BasicBlock.addPredecessor(block: BasicBlock) = this.addPredecessor(this@UsageContext, block)
    fun BasicBlock.addPredecessors(vararg blocks: BasicBlock) = this.addPredecessors(this@UsageContext, *blocks)
    fun BasicBlock.addPredecessors(blocks: List<BasicBlock>) = this.addPredecessors(this@UsageContext, blocks)
    fun BasicBlock.removePredecessor(block: BasicBlock) = this.removePredecessor(this@UsageContext, block)

    fun BasicBlock.addHandler(handle: CatchBlock) = this.addHandler(this@UsageContext, handle)
    fun BasicBlock.removeHandler(handle: CatchBlock) = this.removeHandler(this@UsageContext, handle)
    fun CatchBlock.addThrower(thrower: BasicBlock) = this.addThrowers(listOf(thrower))
    fun CatchBlock.addThrowers(throwers: List<BasicBlock>) = this.addThrowers(this@UsageContext, throwers)
    fun CatchBlock.removeThrower(thrower: BasicBlock) = this.removeThrower(this@UsageContext, thrower)

    fun BasicBlock.linkForward(block: BasicBlock) = this.linkForward(this@UsageContext, block)
    fun BasicBlock.linkBackward(block: BasicBlock) = this.linkBackward(this@UsageContext, block)
    fun BasicBlock.linkThrowing(block: CatchBlock) = this.linkThrowing(this@UsageContext, block)
    fun CatchBlock.linkCatching(thrower: BasicBlock) = this.linkCatching(this@UsageContext, thrower)
    fun BasicBlock.unlinkForward(block: BasicBlock): Unit = this.unlinkForward(this@UsageContext, block)
    fun BasicBlock.unlinkBackward(block: BasicBlock): Unit = this.unlinkBackward(this@UsageContext, block)
    fun BasicBlock.unlinkThrowing(block: CatchBlock): Unit = this.unlinkThrowing(this@UsageContext, block)
    fun CatchBlock.unlinkCatching(thrower: BasicBlock) = this.unlinkCatching(this@UsageContext, thrower)

    fun BasicBlock.replaceSuccessorUsesOf(from: UsableBlock, to: UsableBlock) =
        this.replaceSuccessorUsesOf(this@UsageContext, from, to)

    /**
     * instruction factory wrappers
     */
    fun inst(cm: ClassManager, body: InstructionBuilder.() -> Instruction): Instruction = inst(cm, this, body)
}

@Suppress("MemberVisibilityCanBePrivate")
abstract class AbstractUsageContext : UsageContext {
    protected var privateValueUsers = IdentityHashMap<UsableValue, MutableSet<ValueUser>>()
        private set
    protected var privateBlockUsers = IdentityHashMap<UsableBlock, MutableSet<BlockUser>>()
        private set

    override val UsableValue.users: Set<ValueUser>
        get() = privateValueUsers[this] ?: emptySet()

    override fun UsableValue.addUser(user: ValueUser) {
        if (this.get() is Constant) return
        privateValueUsers.getOrPut(this, ::mutableSetOf).add(user)
    }

    override fun UsableValue.removeUser(user: ValueUser) {
        privateValueUsers[this]?.remove(user)
    }

    override fun UsableValue.replaceAllUsesWith(to: UsableValue) {
        users.toSet().forEach { it.replaceUsesOf(this@AbstractUsageContext, this@replaceAllUsesWith, to) }
    }

    override val UsableBlock.users: Set<BlockUser>
        get() = privateBlockUsers[this] ?: emptySet()

    override fun UsableBlock.addUser(user: BlockUser) {
        privateBlockUsers.getOrPut(this, ::mutableSetOf).add(user)
    }

    override fun UsableBlock.removeUser(user: BlockUser) {
        privateBlockUsers[this]?.remove(user)
    }

    override fun UsableBlock.replaceAllUsesWith(to: UsableBlock) {
        users.toSet().forEach { it.replaceUsesOf(this@AbstractUsageContext, this@replaceAllUsesWith, to) }
    }

    protected open fun clear() {
        privateValueUsers.values.forEach { it.clear() }
        privateValueUsers.clear()
        privateValueUsers = IdentityHashMap()
        privateBlockUsers.values.forEach { it.clear() }
        privateBlockUsers.clear()
        privateBlockUsers = IdentityHashMap()
    }
}

object EmptyUsageContext : UsageContext {
    override val UsableValue.users: Set<ValueUser>
        get() = emptySet()

    override fun UsableValue.addUser(user: ValueUser) {
        // nothing
    }

    override fun UsableValue.removeUser(user: ValueUser) {
        // nothing
    }

    override fun UsableValue.replaceAllUsesWith(to: UsableValue) {
        // nothing
    }

    override val UsableBlock.users: Set<BlockUser>
        get() = emptySet()

    override fun UsableBlock.addUser(user: BlockUser) {
        // nothing
    }

    override fun UsableBlock.removeUser(user: BlockUser) {
        // nothing
    }

    override fun UsableBlock.replaceAllUsesWith(to: UsableBlock) {
        // nothing
    }
}

class MethodUsageContext(val method: Method) : AbstractUsageContext(), Closeable {
    init {
        for (block in method.body) {
            block.addUser(method.body)
            for (successor in block.successors) {
                block.addUser(successor)
            }
            for (predecessor in block.predecessors) {
                block.addUser(predecessor)
            }
        }
        for (inst in method.body.flatten()) {
            for (value in inst.operands) {
                value.addUser(inst)
            }
        }
    }

    override fun close() = clear()
}

@Suppress("MemberVisibilityCanBePrivate", "unused")
open class ExtendableUsageContext(vararg method: Method) : AbstractUsageContext(), Closeable {
    protected val methods = method.toMutableSet()

    private fun Method.computeUsages() {
        for (block in body) {
            block.addUser(body)
            for (successor in block.successors) {
                block.addUser(successor)
            }
            for (predecessor in block.predecessors) {
                block.addUser(predecessor)
            }
        }
        for (inst in body.flatten()) {
            for (value in inst.operands) {
                value.addUser(inst)
            }
        }
    }

    operator fun plusAssign(method: Method) {
        if (method !in methods) {
            methods += method
            method.computeUsages()
        }
    }

    override fun close() {
        methods.clear()
        super.clear()
    }
}

val Method.usageContext: MethodUsageContext get() = MethodUsageContext(this)
