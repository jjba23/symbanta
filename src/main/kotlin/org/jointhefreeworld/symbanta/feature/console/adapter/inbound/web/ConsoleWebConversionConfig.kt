/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.console.adapter.inbound.web

import org.jointhefreeworld.symbanta.openapi.models.ConsoleLogLevelDto
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.format.FormatterRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * The OpenAPI console levels use lowercase wire values (`log`, `warn`, ...) while Spring's
 * default enum binding expects constant names. This registers the wire format explicitly.
 */
@Configuration
class ConsoleWebConversionConfig : WebMvcConfigurer {
    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverter(
            String::class.java,
            ConsoleLogLevelDto::class.java,
            Converter { source ->
                ConsoleLogLevelDto.fromValue(source.trim().lowercase())
                    ?: throw IllegalArgumentException("Unknown console log level '$source'")
            },
        )
    }
}
