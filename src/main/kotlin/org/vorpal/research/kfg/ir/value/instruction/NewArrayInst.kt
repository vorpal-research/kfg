package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.Name
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kthelper.assert.ktassert

class NewArrayInst internal constructor(
    name: Name,
    type: Type,
    dimensions: List<Value>,
    ctx: UsageContext
) : Instruction(name, type, dimensions.toMutableList(), ctx) {
    val component: Type

    val dimensions: List<Value>
        get() = ops

    val numDimensions: Int
        get() = ops.size

    init {
        var current = type
        repeat(numDimensions) {
            ktassert(current is ArrayType)
            current = (current as ArrayType).component
        }
        this.component = current
    }

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$name = new ${component.name}")
        dimensions.forEach {
            sb.append("[$it]")
        }
        return sb.toString()
    }

    override fun clone(ctx: UsageContext): Instruction = NewArrayInst(name.clone(), type, ops, ctx)
}
