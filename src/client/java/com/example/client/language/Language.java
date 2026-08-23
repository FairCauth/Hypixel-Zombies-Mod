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

    public static boolean isChinese() {
        String lang = selectedLanguage();
        return lang != null && lang.startsWith("zh");
    }

    public static boolean isEnglish() {
        String lang = selectedLanguage();
        return lang == null || lang.startsWith("en"); // 语言管理器未就绪时默认英文
    }

    /** 客户端早期初始化阶段 languageManager 可能为 null，做容错。 */
    private static String selectedLanguage() {
        if (mc.getLanguageManager() == null) {
            return null;
        }
        String selected = mc.getLanguageManager().getSelected();
        return selected == null ? null : selected.toLowerCase();
    }
}
