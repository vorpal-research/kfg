package org.jetbrains.research.kfg.ir

class InstFactory private constructor() {
    private object Holder { val instance = InstFactory() }

    companion object {
        val instance : InstFactory by lazy { instance }
    }
}