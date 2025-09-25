package org.jellyfin.androidtv.preference.constant

/**
 * Represents the available subtitle languages.
 * The values correspond to the language codes used for subtitle tracks.
 */
enum class SubtitleLanguage(
    /**
     * The language code used for subtitle tracks
     */
    val code: String,
    /**
     * The display name of the language
     */
    val displayName: String
) {
    // Default option (use video's default or server-side subtitle track)
    DEFAULT("default", "Default"),
    
    // None option (no subtitles)
    NONE("", "None"),
    
    // Supported subtitle languages with format: English (Native)
    ARABIC("ara", "Arabic (العربية)"),
    CHINESE("zho", "Chinese (中文)"),
    CZECH("ces", "Czech (Čeština)"),
    DANISH("dan", "Danish (Dansk)"),
    DUTCH("nld", "Dutch (Nederlands)"),
    ENGLISH("eng", "English (English)"),
    FINNISH("fin", "Finnish (Suomi)"),
    FRENCH("fra", "French (Français)"),
    GERMAN("deu", "German (Deutsch)"),
    GREEK("ell", "Greek (Ελληνικά)"),
    HEBREW("heb", "Hebrew (עברית)"),
    HINDI("hin", "Hindi (हिन्दी)"),
    HUNGARIAN("hun", "Hungarian (Magyar)"),
    INDONESIAN("ind", "Indonesian (Bahasa Indonesia)"),
    ITALIAN("ita", "Italian (Italiano)"),
    JAPANESE("jpn", "Japanese (日本語)"),
    KOREAN("kor", "Korean (한국어)"),
    MALAY("msa", "Malay (Bahasa Melayu)"),
    NORWEGIAN("nor", "Norwegian (Norsk)"),
    POLISH("pol", "Polish (Polski)"),
    PORTUGUESE("por", "Portuguese (Português)"),
    ROMANIAN("ron", "Romanian (Română)"),
    RUSSIAN("rus", "Russian (Русский)"),
    SLOVAK("slk", "Slovak (Slovenčina)"),
    SPANISH("spa", "Spanish (Español)"),
    SWEDISH("swe", "Swedish (Svenska)"),
    THAI("tha", "Thai (ไทย)"),
    TURKISH("tur", "Turkish (Türkçe)"),
    UKRAINIAN("ukr", "Ukrainian (Українська)"),
    VIETNAMESE("vie", "Vietnamese (Tiếng Việt)");

    companion object {
        /**
         * Get a SubtitleLanguage enum from its code or name
         * @param code The language code or enum name to look up
         * @return The matching SubtitleLanguage or NONE if not found
         */
        fun fromCode(code: String?): SubtitleLanguage? {
            if (code.isNullOrEmpty()) return NONE
            return values().find { it.code == code || it.name == code } ?: NONE
        }
    }
}
