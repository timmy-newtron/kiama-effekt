/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2011-2017 Anthony M Sloane, Macquarie University.
 *
 * Kiama is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Kiama is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Kiama.  (See files COPYING and COPYING.LESSER.)  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.bitbucket.inkytonik.kiama
package example.imperative

import ImperativeTree._
import org.bitbucket.inkytonik.kiama.util.GeneratingREPL

/**
 * ScalaCheck generators for programs in the imperative language.
 */
trait Generator {

    import org.scalacheck._
    import ImperativeTree._

    val genInteger = for (i <- Gen.choose(1, 100)) yield Num(i)
    val genDouble = for (i <- Gen.choose(1.0, 1000000.0)) yield Num(i)
    val genNum = Gen.frequency((3, genInteger), (1, genDouble))

    implicit def arbNum : Arbitrary[Num] =
        Arbitrary(genNum)

    val genIdn : Gen[String] = for (s <- Gen.identifier) yield (s.take(5))
    val genVar = for (v <- genIdn) yield Var(v)

    val genLeafExp = Gen.oneOf(genNum, genVar)

    def genNeg(sz : Int) : Gen[Neg] =
        for { e <- genExp(sz / 2) } yield Neg(e)

    def genAdd(sz : Int) : Gen[Add] =
        for { l <- genExp(sz / 2); r <- genExp(sz / 2) } yield Add(l, r)

    def genSub(sz : Int) : Gen[Sub] =
        for { l <- genExp(sz / 2); r <- genExp(sz / 2) } yield Sub(l, r)

    def genMul(sz : Int) : Gen[Mul] =
        for { l <- genExp(sz / 2); r <- genExp(sz / 2) } yield Mul(l, r)

    def genDiv(sz : Int) : Gen[Div] =
        for { l <- genExp(sz / 2); r <- genExp(sz / 2) } yield Div(l, r)

    def genInternalExp(sz : Int) : Gen[Exp] =
        Gen.oneOf(genAdd(sz), genSub(sz), genMul(sz), genDiv(sz))

    def genExp(sz : Int) : Gen[Exp] =
        if (sz <= 0)
            genLeafExp
        else
            Gen.frequency((1, genLeafExp), (3, genInternalExp(sz)))

    implicit def arbExp : Arbitrary[Exp] =
        Arbitrary { Gen.sized(sz => genExp(sz)) }

    val genLeafStmt = Gen.const(Null())

    def genSeqn(sz : Int) : Gen[Seqn] =
        for {
            len <- Gen.choose(1, sz)
            ss <- Gen.containerOfN[Vector, Stmt](len, genStmt(sz / len))
        } yield Seqn(ss)

    implicit def arbSeqn : Arbitrary[Seqn] =
        Arbitrary { Gen.sized(sz => genSeqn(sz)) }

    def genAsgn(sz : Int) : Gen[Asgn] =
        for { v <- genVar; e <- genExp(sz - 1) } yield Asgn(v, e)

    implicit def arbAsgn : Arbitrary[Asgn] =
        Arbitrary { Gen.sized(sz => genAsgn(sz)) }

    def genWhile(sz : Int) : Gen[While] =
        for { e <- genExp(sz / 3); b <- genStmt(sz - 1) } yield While(e, b)

    implicit def arbWhile : Arbitrary[While] =
        Arbitrary { Gen.sized(sz => genWhile(sz)) }

    def genInternalStmt(sz : Int) : Gen[Stmt] =
        Gen.frequency((1, genSeqn(sz)), (5, genAsgn(sz)), (3, genWhile(sz)))

    def genStmt(sz : Int) : Gen[Stmt] =
        if (sz <= 0)
            genLeafStmt
        else
            Gen.frequency((1, genLeafStmt), (9, genInternalStmt(sz)))

    implicit def arbStmt : Arbitrary[Stmt] =
        Arbitrary { Gen.sized(sz => genStmt(sz)) }

}

/**
 * A read-eval-print loop for generating random imperative statements.
 */
object ImperativeGen extends GeneratingREPL[Stmt] with Generator {

    import org.bitbucket.inkytonik.kiama.util.{REPLConfig, Source}
    import org.scalacheck.Arbitrary
    import PrettyPrinter.format

    def generator : Arbitrary[Stmt] =
        arbStmt

    override def process(source : Source, s : Stmt, config : REPLConfig) {
        super.process(source, s, config)
        config.output().emitln(format(s).layout)
    }

}