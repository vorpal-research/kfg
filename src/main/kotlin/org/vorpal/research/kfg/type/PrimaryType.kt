package org.vorpal.research.kfg.type

interface PrimaryType : Type {
    override val isConcrete get() = true
}