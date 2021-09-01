/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2010-2021 Anthony M Sloane, Macquarie University.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.bitbucket.inkytonik.kiama
package util

/**
 * Trait to provide basic functionality for a compiler-like program
 * constructed from phases, including profiling and timing support.
 * `N` is the syntax tree node type used by this compiler. `T` is
 * the type of the syntax tree communicated from the parser
 * to the main processing of the compiler. `C` is the type of the
 * configuration.
 */
trait CompilerBase[N, T <: N, C <: Config] extends ServerWithConfig[N, T, C] {

    import org.bitbucket.inkytonik.kiama.output.PrettyPrinterTypes.{Document, emptyDocument}
    import org.bitbucket.inkytonik.kiama.output.PrettyPrinter.{any, pretty}
    import org.bitbucket.inkytonik.kiama.util.Messaging.Messages
    import org.eclipse.lsp4j.{DocumentSymbol, Command, Range => LSPRange, ExecuteCommandParams}
    import org.rogach.scallop.exceptions.ScallopException
    import scala.collection.mutable

    /**
     * The name of the language that this compiler processes. The best choice
     * is the extension used for files containing this language.
     */
    def name : String

    /**
     * The sources previously used by the semantic analysis phase of this
     * compiler, indexed by source name.
     */
    val sources = mutable.Map[String, Source]()

    /**
     * The position store used by this compiler.
     */
    val positions = new Positions

    /**
     * The messaging facilitiy used by this compiler.
     */
    val messaging = new Messaging(positions)

    /**
     * The entry point for this compiler.
     */
    def main(args : Array[String]) : Unit = {
        driver(args.toIndexedSeq)
    }

    /**
     * Create the configuration for a particular run of the compiler. Override
     * this if you have a custom configuration for your compiler.
     */
    def createConfig(args : Seq[String]) : C

    /**
     * Create and initialise the configuration for a particular run of the compiler.
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
     * Command-line driver for this compiler. First, use the argument list
     * to create a configuration for this execution. Then, use the
     * configuration to run the file compilation in the appropriate way.
     */
    def driver(args : Seq[String]) : Unit = {
        createAndInitConfig(args) match {
            case Left(message) =>
                System.err.println(message)
            case Right(config) =>
                run(config)
        }
    }

    /**
     * Run the compiler given a configuration.
     */
    def run(config : C) : Unit = {
        if (config.server())
            launch(config)
        else
            compileFiles(config)
    }

    /**
     * Compile the files one by one.
     */
    def compileFiles(config : C) : Unit = {
        for (filename <- config.filenames()) {
            compileFile(filename, config)
        }
    }

    /**
     * Compile input from a file. The character encoding of the
     * file is given by the `encoding` argument (default: UTF-8).
     */
    def compileFile(filename : String, config : C,
        encoding : String = "UTF-8") : Unit = {
        try {
            compileSource(FileSource(filename, encoding), config)
        } catch {
            case e : java.io.FileNotFoundException =>
                config.output().emitln(e.getMessage)
        }
    }

    /**
     * Compile input from a string.
     */
    def compileString(name : String, input : String, config : C) : Unit = {
        compileSource(StringSource(input, name), config)
    }

    /**
     * Compile the given source by using `makeast` to turn its contents into
     * an abstract syntax tree and then by `process` which conducts arbitrary
     * processing on the AST. If `makeast` produces messages, report them.
     */
    def compileSource(source : Source, config : C) : Unit = {
        sources(source.name) = source
        makeast(source, config) match {
            case Left(ast) =>
                if (config.server() || config.debug()) {
                    val astDocument = pretty(any(ast))
                    if (config.server()) {
                        publishSourceProduct(source, format(ast))
                        publishSourceTreeProduct(source, astDocument)
                    } else if (config.debug())
                        config.output().emitln(astDocument.layout)
                }
                process(source, ast, config)
            case Right(messages) =>
                clearSyntacticMessages(source, config)
                clearSemanticMessages(source, config)
                report(source, messages, config)
        }
    }

    def publishSourceProduct(source : Source, document : => Document = emptyDocument) : Unit = {
        if (settingBool("showSource"))
            publishProduct(source, "source", name, document)
    }

    def publishSourceTreeProduct(source : Source, document : => Document = emptyDocument) : Unit = {
        if (settingBool("showSourceTree"))
            publishProduct(source, "sourcetree", "scala", document)
    }

    /**
     * Make the contents of the given source, returning the AST wrapped in `Left`.
     * Return `Right` with messages if an AST cannot be made. `config` provides
     * access to all aspects of the configuration.
     */
    def makeast(source : Source, config : C) : Either[T, Messages]

    /**
     * Function to process the input that was parsed. `source` is the input
     * text processed by the compiler. `ast` is the abstract syntax tree
     * produced by the parser from that text. `config` provides access to all
     * aspects of the configuration.
     */
    def process(source : Source, ast : T, config : C) : Unit

    /**
     * Format an abstract syntax tree for printing. Default: return an empty document.
     */
    def format(ast : T) : Document

    /**
     * Output the messages in order of position to the configuration's output.
     */
    def report(source : Source, messages : Messages, config : C) : Unit = {
        if (config.server())
            publishMessages(messages)
        else
            messaging.report(source, messages, config.output())
    }

    /**
     * Clear any previously reported semantic messages. By default,
     * clear the servers's source and sourcetree products.
     */
    def clearSyntacticMessages(source : Source, config : C) : Unit = {
        if (config.server()) {
            publishSourceProduct(source)
            publishSourceTreeProduct(source)
        }
    }

    /**
     * Clear any previously reported semantic messages. By default,
     * do nothing.
     */
    def clearSemanticMessages(source : Source, config : C) : Unit = {
        // Do nothing
    }

    /**
     * Arbitry command execution.
     * This implements the LSP workspace/executeCommand request as defined in
     * https://microsoft.github.io/language-server-protocol/specification#workspace_executeCommand
     */
    def executeCommand(executeCommandParams : ExecuteCommandParams) : Any =
        None

    /**
     * A representation of a simple named code action that replaces
     * a tree node with other text.
     */
    // FIXME: can the "to" be a node too? But server can't access correct PP...
    case class TreeAction(name : String, uri : String, from : N, to : String)

    /**
     * Return applicable code actions for the given position (if any).
     * Each action is in terms of an old tree node and a new node that
     * replaces it. Default is to return no actions.
     */
    def getCodeActions(position : Position) : Option[Vector[TreeAction]] =
        None

    /**
     * A representation of a simple named code lens.
     * From lsp4j / CodeLens.java: "A code lens represents a command
     * that should be shown along with source text, like the number of
     * references, a way to run tests, etc."
     * This is a wrapper to lsp4j/CodeLens
     */
    case class TreeLens(name : String, command : Command, range : LSPRange)

    /**
     * Return showable code lenses for the given position (if any).
     * Each lens is in terms of a lens description and a code action
     * that is triggered by this lens. Default is to return no lenses.
     */
    def getCodeLenses(uri : String) : Option[Vector[TreeLens]] =
        None

    /**
     * Return the corresponding definition node for the given position
     * (if any). Default is to never return anything.
     */
    def getDefinition(position : Position) : Option[N] =
        None

    /**
     * Return a formatted version of the whole of the given source.
     * By default, return `None` meaning there is no formatter.
     */
    def getFormatted(source : Source) : Option[String] =
        None

    /**
     * Return markdown hover markup for the given position (if any).
     * Default is to never return anything.
     */
    def getHover(position : Position) : Option[String] =
        None

    /**
     * Return the corresponding reference nodes (uses) of the symbol
     * at the given position (if any). If `includeDecl` is true, also
     * include the declaration of the symbol. Default is to never return
     * anything.
     */
    def getReferences(position : Position, includeDecl : Boolean) : Option[Vector[N]] =
        None

    /**
     * Return the symbols frmo a compilation unit. Default is to return
     * no symbols.
     */
    def getSymbols(source : Source) : Option[Vector[DocumentSymbol]] =
        None
}

/**
 * A compiler that uses Parsers to produce positioned ASTs. `C` is the type of the
 * compiler configuration.
 */
trait CompilerWithConfig[N, T <: N, C <: Config] extends CompilerBase[N, T, C] {

    import org.bitbucket.inkytonik.kiama.parsing.{NoSuccess, ParseResult, Success}
    import org.bitbucket.inkytonik.kiama.util.Messaging.{message, Messages}

    /**
     * Make an AST by running the parser on the given source, returning messages
     * instead if the parse fails.
     */
    def makeast(source : Source, config : C) : Either[T, Messages] = {
        try {
            parse(source) match {
                case Success(ast, _) =>
                    Left(ast)
                case res : NoSuccess =>
                    val input = res.next
                    positions.setStart(res, input.position)
                    positions.setFinish(res, input.nextPosition)
                    val messages = message(res, res.message)
                    Right(messages)
            }
        } catch {
            case e : java.io.FileNotFoundException =>
                Right(message(e, e.getMessage))
        }
    }

    /**
     * Parse a source, returning a parse result.
     */
    def parse(source : Source) : ParseResult[T]

}

/**
 * Specialisation of `CompilerWithConfig` that uses the default configuration
 * type.
 */
trait Compiler[N, T <: N] extends CompilerWithConfig[N, T, Config] {

    def createConfig(args : Seq[String]) : Config =
        new Config(args)

}
