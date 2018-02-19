package org.jetbrains.research.kfg.ir

class ClassManager private constructor() {
    val classes = mutableMapOf<String, Class>()

    private object Holder {
        val instance = ClassManager()
    }

    companion object {
        val instance: ClassManager by lazy { Holder.instance }
    }

    fun createOrGet(name: String) = classes.getOrPut(name, { Class(name) })
    fun getClassByName(name: String) = classes[name]
    fun saveClass(name: String, klass: Class) { classes[name] = klass }
}