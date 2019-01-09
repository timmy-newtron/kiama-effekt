/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2018 Anthony M Sloane, Macquarie University.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.bitbucket.inkytonik.kiama
package util

import org.eclipse.lsp4j.services.LanguageClient

case class Product(
    uri : String,
    name : String,
    language : String,
    content : String,
    rangeMap : Array[RangePair]
)

case class RangePair(
    sstart : Int, send : Int,
    tstart : Int, tend : Int
)

/**
 * Extend standard language client with Monto support.
 */
trait Client extends LanguageClient {

    import org.eclipse.lsp4j.jsonrpc.services._

    @JsonNotification("monto/publishProduct")
    def publishProduct(product : Product)

}
