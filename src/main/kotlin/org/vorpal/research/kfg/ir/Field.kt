package org.vorpal.research.kfg.ir

import org.objectweb.asm.tree.FieldNode
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.parseDescOrNull

class Field : Node {
    val klass: Class
    internal val fn: FieldNode
    val type: Type
    var defaultValue: Value?
    override val innerAnnotations = mutableSetOf<Annotation>()
    override val innerTypeAnnotations = mutableSetOf<TypeAnnotation>()

    constructor(cm: ClassManager, klass: Class, fn: FieldNode) : super(cm, fn.name, Modifiers(fn.access)) {
        this.fn = fn
        this.klass = klass
        this.type = parseDescOrNull(cm.type, fn.desc)!!
        this.defaultValue = cm.value.getConstant(fn.value)
        fn.visibleAnnotations.orEmpty().filterNotNull().forEach {
            innerAnnotations += AnnotationBase.parseAnnotation(cm, it, visible = true)
        }
        fn.invisibleAnnotations.orEmpty().filterNotNull().forEach {
            innerAnnotations += AnnotationBase.parseAnnotation(cm, it, visible = false)
        }
        fn.visibleTypeAnnotations.orEmpty().filterNotNull().forEach {
            innerTypeAnnotations += AnnotationBase.parseTypeAnnotation(cm, it, visible = true)
        }
        fn.invisibleTypeAnnotations.orEmpty().filterNotNull().forEach {
            innerTypeAnnotations += AnnotationBase.parseTypeAnnotation(cm, it, visible = false)
        }
    }

    constructor(cm: ClassManager, klass: Class, name: String, type: Type, modifiers: Modifiers = Modifiers(0)) :
            super(cm, name, modifiers) {
        this.fn = FieldNode(modifiers.value, name, type.asmDesc, null, null)
        this.klass = klass
        this.type = type
        this.defaultValue = null
    }

    override val asmDesc
        get() = type.asmDesc

    public override fun addAnnotation(annotation: Annotation) {
        super.addAnnotation(annotation)
    }

    public override fun addTypeAnnotation(annotation: TypeAnnotation) {
        super.addTypeAnnotation(annotation)
    }

    public override fun removeAnnotation(annotation: Annotation) {
        super.removeAnnotation(annotation)
    }

    public override fun removeTypeAnnotation(annotation: TypeAnnotation) {
        super.removeTypeAnnotation(annotation)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Field

        if (klass != other.klass) return false
        return type == other.type
    }

    override fun hashCode(): Int {
        var result = fn.hashCode()
        result = 31 * result + klass.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    override fun toString() = "${klass.fullName}.$name: $type"
}
