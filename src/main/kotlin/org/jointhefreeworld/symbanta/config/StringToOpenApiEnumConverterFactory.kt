package org.jointhefreeworld.symbanta.config

import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.converter.ConverterFactory
import org.springframework.stereotype.Component

@Component
class StringToOpenApiEnumConverterFactory : ConverterFactory<String, Enum<*>> {

    override fun <T : Enum<*>> getConverter(targetType: Class<T>): Converter<String, T> {
        return StringToEnumConverter(targetType)
    }

    private class StringToEnumConverter<T : Enum<*>>(
        private val enumType: Class<T>,
    ) : Converter<String, T> {

        override fun convert(source: String): T? {
            println("\nSOURCE: $source")
            if (source.isBlank()) return null

            // Try invoking openapi-generated `fromValue` companion function
            try {
                val companionField = enumType.declaredFields.firstOrNull { it.name == "Companion" }
                val companion = companionField?.get(null)
                val method = companion?.javaClass?.methods?.firstOrNull { it.name == "fromValue" }
                if (method != null) {
                    @Suppress("UNCHECKED_CAST")
                    return method.invoke(companion, source) as? T
                }
            } catch (_: Exception) {
                // Fall back to matching standard enum constants
            }

            // Fall back to standard valueOf check
            return enumType.enumConstants.firstOrNull {
                it.name.equals(source, ignoreCase = true)
            }
        }
    }
}
