package com.example.client.language;


import com.example.client.utils.IMinecraft;

public enum Language implements IMinecraft {
    English,
    Chinese;

    public static Language getLanguage() {
        return isChinese() ? Chinese : English;
    }

    public static Language getDefaultLanguage() {
        return English;
    }

    public static String getLabel(Text[] texts, Language language) {
        for (Text text : texts) {
            if (text.language().equals(language))
                return text.label();
        }
        for (Text text : texts) {
            if (text.language().equals(English))
                return text.label();
        }
        return "";
    }
    public static boolean isChinese() {
        String lang = mc
                .getLanguageManager()
                .getSelected()
                .toLowerCase();

        return lang.startsWith("zh");
    }

    public static boolean isEnglish() {
        String lang = mc
                .getLanguageManager()
                .getSelected()
                .toLowerCase();

        return lang.startsWith("en");
    }
}
