package org.jetbrains.research.kfg

import com.abdullin.kthelper.assert.ktassert
import com.abdullin.kthelper.logging.log
import org.apache.commons.cli.*
import org.jetbrains.research.kfg.util.Flags
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

data class KfgConfig(
        val flags: Flags = Flags.readAll,
        val failOnError: Boolean = true,
        val ignoreNotFoundClasses: Boolean = true
) {

    init {
        ktassert(flags < Flags.readSkipFrames) { log.error("Can't create config with 'skipFrames' option") }
    }
}

class KfgConfigBuilder private constructor(private val current: KfgConfig) {
    constructor() : this(KfgConfig())

    fun flags(flags: Flags) = KfgConfigBuilder(current.copy(flags = flags))
    fun failOnError(value: Boolean) = KfgConfigBuilder(current.copy(failOnError = value))

    fun build() = current
}

class KfgConfigParser(args: Array<String>) {
    private val options = Options()
    private val cmd: CommandLine

    init {
        setupOptions()

        val parser = DefaultParser()
        cmd = try {
            parser.parse(options, args)
        } catch (e: ParseException) {
            printHelp()
            exitProcess(1)
        }
    }

    private fun setupOptions() {
        val jarOpt = Option("j", "jar", true, "input jar file path")
        jarOpt.isRequired = true
        options.addOption(jarOpt)

        val packageOpt = Option("p", "package", true, "analyzed package")
        packageOpt.isRequired = false
        options.addOption(packageOpt)
    }

    fun getStringValue(param: String) = cmd.getOptionValue(param)!!

    fun getOptionalValue(param: String): String? = cmd.getOptionValue(param)
    fun getStringValue(param: String, default: String) = getOptionalValue(param) ?: default

    private fun printHelp() {
        val helpFormatter = HelpFormatter()
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        helpFormatter.printHelp(pw, 80, "kfg", null, options, 1, 3, null)

        println("$sw")
    }
}