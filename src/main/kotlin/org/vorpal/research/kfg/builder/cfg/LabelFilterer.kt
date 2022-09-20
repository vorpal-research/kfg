package org.vorpal.research.kfg.builder.cfg

import org.objectweb.asm.Label
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode

internal class LabelFilterer(private val mn: MethodNode) {

    fun build(): MethodNode {
        val insts = mn.instructions
        val replacement = mutableMapOf<LabelNode, LabelNode>()

        val new = MethodNode(mn.access, mn.name, mn.desc, mn.signature, mn.exceptions.toTypedArray())
        var prev: LabelNode? = null
        for (inst in insts) {
            if (prev != null && inst is LabelNode) {
                replacement[inst] = replacement.getOrDefault(prev, prev)
            }
            prev = inst as? LabelNode
        }

        val clonedLabels = insts
            .filterIsInstance<LabelNode>()
            .associateWith { LabelNode(Label()) }
        val newReplacement = clonedLabels.mapValues { (key, value) ->
            when (key) {
                in replacement -> clonedLabels.getValue(replacement.getValue(key))
                else -> value
            }
        }
        val newInsts = insts.mapNotNull {
            when (it) {
                is LabelNode -> when (it) {
                    in replacement -> null
                    in clonedLabels -> clonedLabels[it]
                    else -> it.clone(newReplacement)
                }

                else -> it.clone(newReplacement)
            }
        }
        val tryCatches = mn.tryCatchBlocks.map {
            val tcb = TryCatchBlockNode(
                newReplacement.getValue(it.start), newReplacement.getValue(it.end),
                newReplacement.getValue(it.handler), it.type
            )
            tcb.visibleTypeAnnotations = it.visibleTypeAnnotations?.toList()
            tcb.invisibleTypeAnnotations = it.invisibleTypeAnnotations?.toList()
            tcb
        }
        for (it in newInsts) {
            new.instructions.add(it)
        }
        for (it in tryCatches) {
            new.tryCatchBlocks.add(it)
        }

        new.visibleParameterAnnotations = mn.visibleParameterAnnotations?.clone()
        new.invisibleParameterAnnotations = mn.invisibleParameterAnnotations?.clone()

        new.visibleAnnotableParameterCount = mn.visibleAnnotableParameterCount
        new.invisibleAnnotableParameterCount = mn.invisibleAnnotableParameterCount

        new.parameters = mn.parameters?.toList().orEmpty()

        new.maxStack = mn.maxStack
        new.maxLocals = mn.maxLocals

        return new
    }
}