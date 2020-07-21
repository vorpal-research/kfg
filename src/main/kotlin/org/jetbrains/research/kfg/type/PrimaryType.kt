package org.jetbrains.research.kfg.type

interface PrimaryType : Type {
    override val isConcrete get() = true
}