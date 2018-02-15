package org.jetbrains.research.kfg.ir

class ClassManager {
    val classes = mutableMapOf<String, Class>()

    fun getClassByName(name: String) = classes[name]
}