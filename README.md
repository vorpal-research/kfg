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

# Download

The latest release of the KFG is available through [Vorpal GitHub Packages repository](https://github.com/orgs/vorpal-research/packages).
Add the link to the repository to your `pom.xml`:
```xml
<repository>
    <id>github-vorpal-research-kotlin-maven</id>
    <url>https://maven.vorpal-research.science</url>
</repository>
```

Include:
```xml
<dependency>
	<groupId>org.jetbrains.research</groupId>
	<artifactId>kfg</artifactId>
	<version>${kfg.version}</version>
</dependency>
``` 

# Usage example

Simple example of how to scan Jar file
```kotlin
/**
 * @path -- path to the jar file to analyze
 * @package -- package to scan in the jar
 */
fun example(path: Path, `package`: Package) {
    // create Jar file instance
    val jar = Jar(path, `package`)
    // create ClassManager and initialize it with the jar
    val cm = ClassManager(KfgConfig(Flags.readAll, failOnError = true))
    cm.initialize(jar)
    // iterate over all found classes
    for (klass in cm.concreteClasses) {
        for (method in klass.allMethods) {
            // view each method as graph
            method.view("/usr/bin/dot", "/usr/bin/browser")
        }
    }
    // save all changes to methods back to jar
    jar.update(cm)
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
