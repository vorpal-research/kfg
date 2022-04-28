package org.vorpal.research.kfg

import org.apache.commons.cli.*
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kthelper.KtException
import org.vorpal.research.kthelper.assert.ktassert
import java.io.PrintWriter
import java.io.StringWriter

class InvalidKfgConfigException(msg: String) : KtException(msg) {
    constructor() : this("")
}

data class KfgConfig(
        val flags: Flags = Flags.readAll,
        val useCachingLoopManager: Boolean = false,
        val failOnError: Boolean = true,
        val verifyIR: Boolean = false,
        val checkClasses: Boolean = false
) {

    init {
        ktassert(flags < Flags.readSkipFrames, "Can't create config with 'skipFrames' option")
    }
}

class KfgConfigBuilder private constructor(private val current: KfgConfig) {
    constructor() : this(KfgConfig())

    fun flags(flags: Flags) = KfgConfigBuilder(current.copy(flags = flags))
    fun failOnError(value: Boolean) = KfgConfigBuilder(current.copy(failOnError = value))
    fun verifyIR(value: Boolean) = KfgConfigBuilder(current.copy(verifyIR = value))
    fun checkClasses(value: Boolean) = KfgConfigBuilder(current.copy(checkClasses = value))
    fun useCachingLoopManager(value: Boolean) = KfgConfigBuilder(current.copy(useCachingLoopManager = value))

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
            throw InvalidKfgConfigException(e.message ?: "")
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