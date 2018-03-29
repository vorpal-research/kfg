package org.jetbrains.research.kfg

import org.apache.commons.cli.*
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class KfgConfig(args: Array<String>) {
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

        val targetOpt = Option("t", "target", true, "result target directory")
        targetOpt.isRequired = false
        options.addOption(targetOpt)
    }

    fun getStringValue(param: String) = cmd.getOptionValue(param)!!

    fun getOptionalValue(param: String) = cmd.getOptionValue(param)
    fun getStringValue(param: String, default: String) = getOptionalValue(param) ?: default

    private fun printHelp() {
        val helpFormatter = HelpFormatter()
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        helpFormatter.printHelp(pw, 80, "kfg", null, options, 1, 3, null)

        println("$sw")
    }
}