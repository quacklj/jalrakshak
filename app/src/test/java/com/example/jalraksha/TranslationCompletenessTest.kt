package com.example.jalraksha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Guards the promise that every screen reads fully in every language the picker offers.
 *
 * Android's own `MissingTranslation` lint check only fires on the default variant of a resource,
 * and it says nothing about format specifiers — a translator who drops a `%1$s` ships a string
 * that crashes `String.format` the first time that screen opens, in one language, on someone
 * else's phone. These run on the JVM, so they fail in CI rather than in a village.
 */
class TranslationCompletenessTest {

    private val resDir = File("src/main/res")

    private val languages = listOf("hi", "mr", "bn", "te", "ta", "gu", "kn")

    @Test
    fun `every language ships the same keys as English`() {
        val english = keysIn(stringsFor(null))
        assertTrue("English strings.xml should not be empty", english.isNotEmpty())

        languages.forEach { language ->
            val translated = keysIn(stringsFor(language))
            assertEquals(
                "values-$language/strings.xml does not match the English key set",
                english,
                translated,
            )
        }
    }

    @Test
    fun `every translation keeps the format specifiers English declares`() {
        val english = specifiersIn(stringsFor(null))

        languages.forEach { language ->
            val translated = specifiersIn(stringsFor(language))
            english.forEach { (key, expected) ->
                assertEquals(
                    "values-$language/strings.xml: \"$key\" must use exactly the arguments " +
                        "English uses, or String.format will throw at runtime",
                    expected,
                    translated[key],
                )
            }
        }
    }

    @Test
    fun `no translation was left in English`() {
        // A copy-pasted file is the usual way a language ends up half-done. Identity is fine for
        // genuinely invariant strings — unit symbols, the em dash placeholder — so only flag a
        // language where nearly everything matches English.
        val english = valuesIn(stringsFor(null))

        languages.forEach { language ->
            val translated = valuesIn(stringsFor(language))
            val identical = english.count { (key, value) -> translated[key] == value }
            val ratio = identical.toDouble() / english.size
            assertTrue(
                "values-$language/strings.xml is ${(ratio * 100).toInt()}% identical to English — " +
                    "it looks like an untranslated copy",
                ratio < 0.5,
            )
        }
    }

    private fun stringsFor(language: String?): File {
        val dir = if (language == null) "values" else "values-$language"
        val file = File(resDir, "$dir/strings.xml")
        assertTrue("Missing $dir/strings.xml", file.exists())
        return file
    }

    private fun elements(file: File): List<Element> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = document.documentElement.childNodes
        return (0 until root.length)
            .mapNotNull { root.item(it) as? Element }
            .filter { it.tagName == "string" || it.tagName == "plurals" }
    }

    private fun keysIn(file: File): Set<String> =
        elements(file).map { it.getAttribute("name") }.toSet()

    /** Plain strings only — a plural's text varies by quantity, so identity there means little. */
    private fun valuesIn(file: File): Map<String, String> =
        elements(file)
            .filter { it.tagName == "string" }
            .associate { it.getAttribute("name") to it.textContent.trim() }

    /**
     * The set of positional arguments each resource uses, e.g. `{"%1$s"}`. Compared as a set so a
     * translator may reorder them — which is exactly why they are positional.
     */
    private fun specifiersIn(file: File): Map<String, Set<String>> =
        elements(file).associate { element ->
            element.getAttribute("name") to SPECIFIER.findAll(element.textContent).map { it.value }.toSet()
        }

    private companion object {
        val SPECIFIER = Regex("""%\d+\$[a-zA-Z]""")
    }
}
