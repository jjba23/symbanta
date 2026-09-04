package org.jointhefreeworld.symbanta.controllers

import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.io.InputStream

@RestController
class StaticFileController {
    @GetMapping("/symbanta.js")
    fun serveRootFile(): ResponseEntity<Resource> {
        val fileStream: InputStream =
            this::class.java.getResourceAsStream("/js/symbanta.js") ?: return ResponseEntity.notFound().build()

        val inputStreamResource = InputStreamResource(fileStream)
        val httpHeaders = HttpHeaders()
        httpHeaders.setContentLength(inputStreamResource.contentLength())

        return ResponseEntity(inputStreamResource, httpHeaders, HttpStatus.OK)
    }
}
