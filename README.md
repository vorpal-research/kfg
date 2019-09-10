# KFG

Library for building control flow graph from Java bytecode.

# Build

```
mvn clean package
```

# Run integration tests

```
mvn clean verify
```

# Usage example

Simple example of how to scan Jar file
```kotlin
/**
 * @jar -- jar file to scan
 * @package -- package to scan in the jar
 */
fun example(jar: JarFile, `package`: Package) {
    // create ClassManager and scan the jar file
    val cm = ClassManager(jar, `package`, Flags.readAll)
    // iterate over all found classes
    for (klass in cm.concreteClasses) {
        for ((_, method) in klass.methods) {
            // view each method as graph
            method.viewCfg("/usr/bin/dot", "/usr/bin/browser")
        }
    }
    // save all changes to methods back to jar
    updateJar(cm, jar, `package`)
}
```

Pipeline example
```kotlin
class MethodPrinter(override val cm: ClassManager) : MethodVisitor {
    /**
     * should override this method and cleanup all the temporary info between visitor invocations
     */
    override fun cleanup() {}

    override fun visit(method: Method) {
        println("$method")
        super.visit(method)
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        println("${bb.name}:")
        super.visitBasicBlock(bb)
    }

    override fun visitInstruction(inst: Instruction) {
        println("  $inst")
        super.visitInstruction(inst)
    }
}

fun pipelineExample(cm: ClassManager, `package`: Package) {
    executePipeline(cm, `package`) {
        +MethodPrinter(cm)
        +LoopAnalysis(cm)
        +LoopSimplifier(cm)
        +MethodPrinter(cm)
    }
}
```