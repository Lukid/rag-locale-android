package it.netseven.raglocale.chat

/**
 * Guardrail difensivo per runtime nativi che continuano a streammare anche dopo errori
 * interni. Non giudica la qualita' della risposta: intercetta solo output palesemente
 * corrotto, cosi' la UI non si riempie di caratteri casuali.
 */
object OutputSanity {
    private const val MIN_SAMPLE_CHARS = 64
    private const val SAMPLE_CHARS = 240

    fun looksCorrupt(text: String): Boolean {
        val sample = text.takeLast(SAMPLE_CHARS)
        if (sample.length < MIN_SAMPLE_CHARS) return false

        val suspicious = sample.count { it.isSuspicious() }
        if (suspicious * 5 > sample.length) return true

        val lettersOrDigits = sample.count { it.isLetterOrDigit() }
        val whitespace = sample.count { it.isWhitespace() }
        val nonLatinLetters = sample.count { it.isLetter() && !it.isLatinLetter() }
        return (lettersOrDigits * 6 < sample.length && whitespace * 10 < sample.length) ||
            (nonLatinLetters * 3 > sample.length && whitespace * 12 < sample.length)
    }

    private fun Char.isSuspicious(): Boolean =
        this == '\uFFFD' ||
            this.isSurrogate() ||
            Character.getType(this) == Character.UNASSIGNED.toInt() ||
            Character.getType(this) == Character.PRIVATE_USE.toInt() ||
            (this.isISOControl() && this != '\n' && this != '\r' && this != '\t')

    private fun Char.isLatinLetter(): Boolean {
        val block = Character.UnicodeBlock.of(this)
        return block == Character.UnicodeBlock.BASIC_LATIN ||
            block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT ||
            block == Character.UnicodeBlock.LATIN_EXTENDED_A ||
            block == Character.UnicodeBlock.LATIN_EXTENDED_B ||
            block == Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL
    }
}
