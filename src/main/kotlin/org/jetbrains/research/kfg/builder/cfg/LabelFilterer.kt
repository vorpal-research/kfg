package org.jetbrains.research.kfg.builder.cfg

import org.objectweb.asm.Label
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.jetbrains.research.kfg.util.instructions

internal class LabelFilterer(val mn: MethodNode) {

    fun build(): MethodNode {
        val insts = mn.instructions()
        val replacement = mutableMapOf<LabelNode, LabelNode>()

        val new = MethodNode(mn.access, mn.name, mn.desc, mn.signature,
                mn.exceptions?.mapNotNull { it as? String }?.toTypedArray())
        var prev: LabelNode? = null
        for (inst in insts) {
            if (prev != null && inst is LabelNode) {
                replacement[inst] = replacement.getOrDefault(prev, prev)
            }
            prev = inst as? LabelNode
        }

        val clonedLabels = insts.mapNotNull { it as? LabelNode }.map {
            it to LabelNode(Label())
        }.toMap()
        val newReplacement = clonedLabels.map { (key, value) ->
            key to when (key) {
                in replacement -> clonedLabels.getValue(replacement.getValue(key))
                else -> value
            }
        }.toMap()
        val newInsts = insts.mapNotNull {
            when (it) {
                in replacement -> null
                in clonedLabels -> clonedLabels[it]
                else -> {
                    val clone = it.clone(newReplacement)
                    clone
                }
            }
        }
        val tryCatches = mn.tryCatchBlocks().map {
            val tcb = TryCatchBlockNode(
                    newReplacement.getValue(it.start), newReplacement.getValue(it.end),
                    newReplacement.getValue(it.handler), it.type
            )
            tcb.visibleTypeAnnotations = it.visibleTypeAnnotations?.toList()
            tcb.invisibleTypeAnnotations = it.invisibleTypeAnnotations?.toList()
            tcb
        }
        newInsts.forEach {
            new.instructions.add(it)
        }
        tryCatches.forEach {
            new.tryCatchBlocks.add(it)
        }

        return new
    }
}