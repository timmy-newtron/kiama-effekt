/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2015-2016 Anthony M Sloane, Macquarie University.
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
package util

/**
 * Tests of source utility routiines.
 */
class SourceTests extends Tests {

    import Source.{dropCurrentPath, dropPrefix}
    import java.io.File.separator
    import java.lang.System.getProperty

    val currentPathFile = getProperty("user.dir") + separator + "foo.txt"
    val currentPathBase = "foo.txt"
    val notCurrentPathFileWithSep = separator + "x" + currentPathFile
    val notCurrentPathFileWithoutSep = "x" + currentPathFile

    test("dropCurrentPath drops current path correctly") {
        dropCurrentPath(currentPathFile) shouldBe currentPathBase
    }

    test("dropCurrentPath doesn't drop non-current path (with sep)") {
        dropCurrentPath(notCurrentPathFileWithSep) shouldBe notCurrentPathFileWithSep
    }

    test("dropCurrentPath doesn't drop non-current path (without sep)") {
        dropCurrentPath(notCurrentPathFileWithoutSep) shouldBe notCurrentPathFileWithoutSep
    }

    def makePath(p : String*) = p mkString separator
    def makePrefixPath(p : String*) = separator + makePath(p : _*)

    val testPath = makePrefixPath("foo", "bar", "ble.txt")
    val prefixOfTestPath = makePrefixPath("foo", "bar")
    val otherPath = makePath("bob", "harry")

    test("dropPrefix copes with empty filename") {
        dropPrefix("", testPath) shouldBe ""
    }

    test("dropPrefix correctly drops nothing if prefix is empty") {
        dropPrefix(testPath, "") shouldBe testPath
    }

    test("dropPrefix correctly drops prefix that is there") {
        dropPrefix(testPath, prefixOfTestPath) shouldBe "ble.txt"
    }

    test("dropPrefix correctly drops prefix that is whole filename") {
        dropPrefix(testPath, testPath) shouldBe ""
    }

    test("dropPrefix correctly ignores path that isn't there") {
        dropPrefix(testPath, otherPath) shouldBe testPath
    }

    test("dropPrefix correctly deals with filename that is prefix of prefix") {
        dropPrefix(prefixOfTestPath, testPath) shouldBe ""
    }

}