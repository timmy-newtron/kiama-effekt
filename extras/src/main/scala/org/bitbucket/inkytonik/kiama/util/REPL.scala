/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2008-2020 Anthony M Sloane, Macquarie University.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.bitbucket.inkytonik.kiama
package util

/**
 * General support for applications that implement read-eval-print loops (REPLs).
 */
trait REPLBase[C <: REPLConfig] {

    import scala.annotation.tailrec
    import org.bitbucket.inkytonik.kiama.util.{Source, StringSource}
    import org.rogach.scallop.exceptions.ScallopException

    /**
     * Banner message that is printed before the REPL starts.
     */
    def banner : String

    /**
     * The position store used by this REPL.
     */
    val positions = new Positions

    /**
     * The messaging facilitiy used by this REPL.
     */
    val messaging = new Messaging(positions)

    /**
     * Profiler for this compiler.
     */
    val profiler = new Profiler

    /**
     * The entry point for this REPL.
     */
    def main(args : Array[String]) : Unit = {
        driver(args.toIndexedSeq)
    }

    /**
     * Create the configuration for a particular run of the REPL. If supplied, use
     * `emitter` instead of a standard output emitter.
     */
    def createConfig(args : Seq[String]) : C

    /**
     * Create and initialise the configuration for a particular run of the REPL.
     * Default: call `createConfig` and then initialise the resulting configuration.
     * Returns either the created configuration or an error message describing
     * why the configuration couldn't be created.
     */
    def createAndInitConfig(args : Seq[String]) : Either[String, C] = {
        try {
            val config = createConfig(args)
            config.verify()
            Right(config)
        } catch {
            case e : ScallopException =>
                Left(e.getMessage())
        }
    }

    /**
     * Driver for this REPL. First, use the argument list to create a
     * configuration for this execution. If the arguments parse ok, then
     * print the REPL banner. Read lines from the console and pass non-null ones
     * to `processline`. If `ignoreWhitespaceLines` is true, do not pass lines that
     * contain just whitespace, otherwise do. Continue until `processline`
     * returns false. Call `prompt` each time input is about to be read.
     */
    def driver(args : Seq[String]) : Unit = {
        // Set up the configuration
        createAndInitConfig(args) match {
            case Left(message) =>
                println(message)
            case Right(config) =>
                // Process any filename arguments
                processfiles(config)

                // Enter interactive phase
                config.output().emitln(banner)
                if (config.profile.isDefined) {
                    val dimensions = profiler.parseProfileOption(config.profile())
                    profiler.profile(processlines(config), dimensions, config.logging())
                } else if (config.time())
                    profiler.time(processlines(config))
                else
                    processlines(config)

                config.output().emitln()
        }
    }

    /**
     * Define the prompt (default: `"> "`).
     */
    def prompt : String =
        "> "

    /**
     * Process interactively entered lines, one by one, until end of file.
     * Prompt with the given prompt.
     */
    def processlines(config : C) : Unit = {
        processconsole(config.console(), prompt, config)
    }

    /**
     * Process the files one by one, allowing config to be updated each time
     * and updated config to be used by the next file.
     */
    final def processfiles(config : C) : C = {

        @tailrec
        def loop(filenames : List[String], config : C) : C =
            filenames match {
                case filename +: rest =>
                    loop(rest, processfile(filename, config))
                case _ =>
                    config
            }

        loop(config.filenames(), config)

    }

    /**
     * Process a file argument by passing its contents line-by-line to
     * `processline`.
     */
    def processfile(filename : String, config : C) : C =
        processconsole(new FileConsole(filename), "", config)

    /**
     * Process interactively entered lines, one by one, until end of file.
     */
    @tailrec
    final def processconsole(console : Console, prompt : String, config : C) : C = {
        val line = console.readLine(prompt)
        if (line == null)
            config
        else {
            val source = new StringSource(line)
            processline(source, console, config) match {
                case Some(newConfig) =>
                    processconsole(console, prompt, newConfig)
                case _ =>
                    config
            }
        }
    }

    /**
     * Process user input from the given source. The return value allows the
     * processing to optionally return a new configuration that will be used
     * in subsequent processing. A return value of `None` indicates that no
     * more lines from the current console should be processed.
     */
    def processline(source : Source, console : Console, config : C) : Option[C]

}

/**
 * General support for applications that implement read-eval-print loops (REPLs).
 */
trait REPL extends REPLBase[REPLConfig] {

    def createConfig(args : Seq[String]) : REPLConfig =
        new REPLConfig(args)

}

/**
 * A REPL that parses its input lines into a value (such as an abstract syntax
 * tree), then processes them. Output is emitted using a configurable emitter.
 */
trait ParsingREPLBase[T, C <: REPLConfig] extends REPLBase[C] {

    import org.bitbucket.inkytonik.kiama.parsing.{NoSuccess, ParseResult, Success}
    import org.bitbucket.inkytonik.kiama.util.Messaging.{message, Messages}
    import org.bitbucket.inkytonik.kiama.util.Source

    /**
     * Parse a source, returning a parse result.
     */
    def parse(source : Source) : ParseResult[T]

    /**
     * Process a user input line by parsing it to get a value of type `T`,
     * then passing it to the `process` method. Returns the configuration
     * unchanged.
     */
    def processline(source : Source, console : Console, config : C) : Option[C] = {
        if (config.processWhitespaceLines() || (source.content.trim.length != 0)) {
            parse(source) match {
                case Success(e, _) =>
                    process(source, e, config)
                case res : NoSuccess =>
                    val pos = res.next.position
                    positions.setStart(res, pos)
                    positions.setFinish(res, pos)
                    val messages = message(res, res.message)
                    report(source, messages, config)
            }
        }
        Some(config)
    }

    /**
     * Process a user input value in the given configuration.
     */
    def process(source : Source, t : T, config : C) : Unit

    /**
     * Output the messages in order of position using the given configuration,
     * which defaults to that configuration's output.
     */
    def report(source : Source, messages : Messages, config : C) : Unit = {
        config.output().emit(messaging.formatMessages(messages))
    }

}

/**
 * A REPL that parses its input lines into a value (such as an abstract syntax
 * tree), then processes them. `C` is the type of the configuration.
 */
trait ParsingREPLWithConfig[T, C <: REPLConfig] extends ParsingREPLBase[T, C]

/**
 * A REPL that parses its input lines into a value (such as an abstract syntax
 * tree), then processes them. Output is emitted to standard output.
 */
trait ParsingREPL[T] extends ParsingREPLWithConfig[T, REPLConfig] {

    def createConfig(args : Seq[String]) : REPLConfig =
        new REPLConfig(args)

}
