package com.silica.assistant.core.llm

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Test

class TextUtilsTest {

    // --- limitSentence ---

    @Test
    fun `limitSentence returns text as-is when under maxChars`() {
        val text = "Halo, apa kabar?"
        assertEquals(text, limitSentence(text, 160))
    }

    @Test
    fun `limitSentence truncates at sentence boundary`() {
        val text = "Ini kalimat pertama. Ini kalimat kedua. Ini kalimat ketiga."
        assertEquals("Ini kalimat pertama.", limitSentence(text, 30))
    }

    @Test
    fun `limitSentence falls back to char cut when no boundary found`() {
        val text = "abcdefghijklmnopqrstuvwxyz"
        assertEquals("abcdefghijklmno", limitSentence(text, 15))
    }

    @Test
    fun `limitSentence handles newline as boundary`() {
        val text = "Baris pertama.\nBaris kedua.\nBaris ketiga."
        assertEquals("Baris pertama.", limitSentence(text, 20))
    }

    @Test
    fun `limitSentence uses exclamation as boundary`() {
        val text = "Wah bagus! Gimana caranya?"
        assertEquals("Wah bagus!", limitSentence(text, 15))
    }

    @Test
    fun `limitSentence uses question mark as boundary`() {
        val text = "Siapa kamu? Siapa dia?"
        assertEquals("Siapa kamu?", limitSentence(text, 14))
    }

    // --- safeContent ---

    @Test
    fun `safeContent detects safety phrases and returns fallback`() {
        val text = "I cannot comment on that, it is inappropriate and harmful."
        assertEquals("Hmm, nggak bisa komentar soal itu~", safeContent(text, 300))
    }

    @Test
    fun `safeContent returns normal text when no safety phrases`() {
        val text = "Hari ini cuacanya cerah."
        assertEquals(text, safeContent(text, 300))
    }

    @Test
    fun `safeContent truncates long text`() {
        val text = "A".repeat(400)
        val result = safeContent(text, 50)
        assertTrue("panjang ${result.length} > 50", result.length <= 50)
    }

    @Test
    fun `safeContent does not trigger on single safety word`() {
        val text = "Ini konten tidak pantas."
        assertEquals(text, safeContent(text, 300))
    }

    @Test
    fun `safeContent does not trigger when text is long enough`() {
        val text = "I cannot comment on that specific issue because it is inappropriate to discuss without more context and harmful assumptions could be made if we do not have all the relevant information to consider properly as part of this discussion about the topic at hand today."
        val result = safeContent(text, 300)
        assertEquals(text, result)
    }

    // --- codepointAwareTake ---

    @Test
    fun `codepointAwareTake returns as-is when under max`() {
        val text = "hello"
        assertEquals(text, codepointAwareTake(text, 10))
    }

    @Test
    fun `codepointAwareTake truncates at max`() {
        val text = "hello world"
        assertEquals("hello wo", codepointAwareTake(text, 8))
    }

    @Test
    fun `codepointAwareTake handles surrogate pairs`() {
        val text = "a\uD83D\uDE00b\uD83D\uDE01c" // a😀b😁c
        val result = codepointAwareTake(text, 3)
        assertEquals("a\uD83D\uDE00", result) // a😀 (3 chars: 'a', high surrogate, low surrogate)
    }

    // --- markdownToAnnotated ---

    @Test
    fun `markdown plain text has no styles`() {
        val result = markdownToAnnotated("Halo dunia")
        assertEquals("Halo dunia", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `markdown bold renders correctly`() {
        val result = markdownToAnnotated("Ini **bold** kan?")
        assertEquals("Ini bold kan?", result.text)
        val bold = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, bold.size)
        assertEquals(4, bold[0].start)
        assertEquals(8, bold[0].end)
    }

    @Test
    fun `markdown italic renders correctly`() {
        val result = markdownToAnnotated("Ini *italic* kan?")
        assertEquals("Ini italic kan?", result.text)
        val italic = result.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertEquals(1, italic.size)
        assertEquals(4, italic[0].start)
        assertEquals(10, italic[0].end)
    }

    @Test
    fun `markdown bold and italic together`() {
        val result = markdownToAnnotated("**Bold** dan *italic*")
        assertEquals("Bold dan italic", result.text)
        assertEquals(2, result.spanStyles.size)
    }

    @Test
    fun `markdown single asterisk between spaces is not italic`() {
        val result = markdownToAnnotated("2 * 3 = 6")
        assertEquals("2 * 3 = 6", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `markdown unclosed bold shows asterisks as text`() {
        val result = markdownToAnnotated("Ini **gak ditutup")
        assertEquals("Ini **gak ditutup", result.text)
    }
}
