package org.jetbrains.research.kfg.value

class ValueFactory {
    private object Holder { val instance = ValueFactory() }

    companion object {
        val instance : ValueFactory by lazy { instance }
    }

    fun getNullConstant() = NullConstant.instance
    fun getIntConstant(value: Int) = IntConstant(value)
    fun getLongConstant(value: Long) = LongConstant(value)
    fun getFloatConstant(value: Float) = FloatConstant(value)
    fun getDoubleConstant(value: Double) = DoubleConstant(value)
}