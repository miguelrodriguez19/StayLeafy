package com.miguelrodriguez19.stayleafy.data.settings

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node

class LocalizationParityTest {

    @Test
    fun englishAndSpanishHaveTheSameTranslatableStringResources() {
        val english = loadResources("values")
        val spanish = loadResources("values-es")

        assertEquals(
            "Translatable resource keys/types differ",
            english.keys.sorted(),
            spanish.keys.sorted(),
        )
    }

    @Test
    fun translatedStringsAndPluralQuantitiesKeepPlaceholderSignatures() {
        val english = loadResources("values")
        val spanish = loadResources("values-es")

        assertEquals("Resource sets must have the same keys", english.keys, spanish.keys)
        english.keys.sorted().forEach { key ->
            val source = english.getValue(key)
            val translation = spanish.getValue(key)
            when (source) {
                is StringResource -> assertEquals(
                    "Placeholder signature differs for ${key.name}",
                    placeholderSignature(source.value),
                    placeholderSignature((translation as StringResource).value),
                )

                is PluralsResource -> {
                    val translated = translation as PluralsResource
                    assertEquals(
                        "Plural quantities differ for ${key.name}",
                        source.items.keys.sorted(),
                        translated.items.keys.sorted(),
                    )
                    source.items.keys.forEach { quantity ->
                        assertEquals(
                            "Placeholder signature differs for ${key.name}[$quantity]",
                            placeholderSignature(source.items.getValue(quantity)),
                            placeholderSignature(translated.items.getValue(quantity)),
                        )
                    }
                }
            }
        }
    }

    private fun loadResources(valuesDirectory: String): LinkedHashMap<ResourceKey, Resource> {
        val directory = resourceRoot().resolve(valuesDirectory)
        assertTrue("Missing Android resource directory: $directory", Files.isDirectory(directory))

        val resources = LinkedHashMap<ResourceKey, Resource>()
        xmlFiles(directory).forEach { file ->
            parse(file).documentElement.childNodes.let { children ->
                for (index in 0 until children.length) {
                    val node = children.item(index)
                    if (node.nodeType != Node.ELEMENT_NODE) continue
                    val element = node as Element
                    val resource = element.toResource(file)
                    if (resource == null || !resource.translatable) continue

                    val previous = resources.put(resource.key, resource)
                    check(previous == null) {
                        "Duplicate translatable resource ${resource.key} in $file"
                    }
                }
            }
        }
        return resources
    }

    private fun Element.toResource(file: Path): Resource? {
        val name = getAttribute("name").takeIf { it.isNotEmpty() } ?: return null
        val translatable = !getAttribute("translatable").equals("false", ignoreCase = true)
        val key = when (tagName) {
            "string" -> ResourceKey(ResourceKind.STRING, name)
            "plurals" -> ResourceKey(ResourceKind.PLURALS, name)
            "item" -> if (getAttribute("type") == "string") {
                ResourceKey(ResourceKind.STRING, name)
            } else {
                return null
            }

            else -> return null
        }

        return when (key.kind) {
            ResourceKind.STRING -> StringResource(
                key = key,
                translatable = translatable,
                value = textContent,
            )

            ResourceKind.PLURALS -> {
                val items = LinkedHashMap<String, String>()
                val children = childNodes
                for (index in 0 until children.length) {
                    val child = children.item(index)
                    if (child.nodeType != Node.ELEMENT_NODE || (child as Element).tagName != "item") {
                        continue
                    }
                    val quantity = child.getAttribute("quantity").takeIf { it.isNotEmpty() }
                        ?: error("Plural $name has an item without quantity in $file")
                    check(items.put(quantity, child.textContent) == null) {
                        "Duplicate plural quantity $quantity in $file"
                    }
                }
                PluralsResource(key, translatable, items)
            }
        }
    }

    private fun xmlFiles(directory: Path): List<Path> = Files.walk(directory).use { paths ->
        paths
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".xml") }
            .sorted()
            .toList()
    }

    private fun parse(file: Path) = secureDocumentBuilderFactory().newDocumentBuilder().run {
        Files.newInputStream(file).use { input -> parse(input) }
    }

    private fun resourceRoot(): Path {
        val candidates = listOf(
            Paths.get("src", "main", "res"),
            Paths.get("app", "src", "main", "res"),
        )
        return candidates.firstOrNull { Files.isDirectory(it) }?.toAbsolutePath()?.normalize()
            ?: error("Could not locate app/src/main/res from ${Paths.get("").toAbsolutePath()}")
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }

    private fun placeholderSignature(value: String): List<Placeholder> {
        val placeholders = mutableListOf<Placeholder>()
        var nextImplicitIndex = 1
        var previousIndex: Int? = null
        var cursor = 0

        while (cursor < value.length) {
            if (value[cursor] != '%') {
                cursor++
                continue
            }
            if (cursor + 1 >= value.length) {
                error("Unterminated format placeholder in '$value'")
            }
            if (value[cursor + 1] == '%') {
                cursor += 2
                continue
            }

            var tokenCursor = cursor + 1
            val explicitStart = tokenCursor
            while (tokenCursor < value.length && value[tokenCursor].isDigit()) tokenCursor++
            val explicitIndex = if (
                tokenCursor > explicitStart &&
                tokenCursor < value.length &&
                value[tokenCursor] == '$'
            ) {
                value.substring(explicitStart, tokenCursor).toIntOrNull()
                    ?: error("Invalid format argument index in '$value'")
            } else {
                tokenCursor = explicitStart
                null
            }
            if (explicitIndex != null) tokenCursor++

            var reusesPrevious = false
            while (tokenCursor < value.length && value[tokenCursor] in "-#+ 0,(<,") {
                reusesPrevious = reusesPrevious || value[tokenCursor] == '<'
                tokenCursor++
            }
            while (tokenCursor < value.length && value[tokenCursor].isDigit()) tokenCursor++
            if (tokenCursor < value.length && value[tokenCursor] == '.') {
                tokenCursor++
                while (tokenCursor < value.length && value[tokenCursor].isDigit()) tokenCursor++
            }

            var datePrefix: Char? = null
            if (tokenCursor < value.length && value[tokenCursor] in "tT") {
                datePrefix = value[tokenCursor]
                tokenCursor++
            }
            val conversion = value.getOrNull(tokenCursor)
            val validConversion = if (datePrefix == null) {
                conversion != null && conversion in FORMAT_CONVERSIONS
            } else {
                conversion != null && conversion in DATE_TIME_CONVERSIONS
            }
            if (!validConversion) {
                error("Malformed format placeholder in '$value'")
            }

            val index = when {
                explicitIndex != null -> explicitIndex
                reusesPrevious -> previousIndex ?: error("Format reuse has no previous argument in '$value'")
                else -> nextImplicitIndex++
            }
            check(index > 0) { "Format argument indexes must be positive in '$value'" }
            previousIndex = index
            placeholders += Placeholder(index, buildString {
                datePrefix?.let(::append)
                append(checkNotNull(conversion))
            })
            cursor = tokenCursor + 1
        }

        return placeholders.sortedWith(compareBy<Placeholder> { it.index }.thenBy { it.conversion })
    }

    private data class ResourceKey(val kind: ResourceKind, val name: String) : Comparable<ResourceKey> {
        override fun compareTo(other: ResourceKey): Int =
            compareValuesBy(this, other, ResourceKey::kind, ResourceKey::name)
    }

    private enum class ResourceKind {
        STRING,
        PLURALS,
    }

    private sealed interface Resource {
        val key: ResourceKey
        val translatable: Boolean
    }

    private data class StringResource(
        override val key: ResourceKey,
        override val translatable: Boolean,
        val value: String,
    ) : Resource

    private data class PluralsResource(
        override val key: ResourceKey,
        override val translatable: Boolean,
        val items: LinkedHashMap<String, String>,
    ) : Resource

    private data class Placeholder(val index: Int, val conversion: String)

    private companion object {
        const val FORMAT_CONVERSIONS = "bBhHsScCdoxXeEfgGaAn"
        const val DATE_TIME_CONVERSIONS = "HIklMSLNpzZsQBbhAaCYyjmdeRTrDFc"
    }
}
