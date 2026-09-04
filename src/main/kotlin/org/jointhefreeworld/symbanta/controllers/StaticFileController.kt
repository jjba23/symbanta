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
