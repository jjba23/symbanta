/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.telemetry.adapter.inbound.web

import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

@RestController
class TelemetryScriptController {
    @GetMapping("/symbanta.js")
    fun serveTelemetryScript(): ResponseEntity<ByteArray> {
        val script =
            this::class.java.getResourceAsStream("/js/symbanta.js")
                ?: return ResponseEntity.notFound().build()

        return script.use {
            ResponseEntity
                .ok()
                .header("Content-Type", "application/javascript")
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                .body(it.readBytes())
        }
    }
}
