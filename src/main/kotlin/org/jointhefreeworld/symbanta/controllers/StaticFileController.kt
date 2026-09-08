/* symbanta
*
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*
* symbanta is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* symbanta is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Affero General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with symbanta.  If not, see <https://www.gnu.org/licenses/>.
*/

package org.jointhefreeworld.symbanta.controllers

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class StaticFileController {
    @GetMapping("/symbanta.js")
    fun serveRootFile(): ResponseEntity<ByteArray> {
        val f =
            this::class.java.getResource("/js/symbanta.js") ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok().body(f.readBytes())
    }
}
