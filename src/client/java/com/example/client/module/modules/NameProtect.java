package com.example.client.module.modules;

import com.example.client.config.ZombiesConfig;
import com.example.client.gui.NameProtectScreen;
import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 名字保护：把保护名单里的玩家名字显示成 Player1/Player2…
 * 本模块作为一次性入口——开启即打开 GUI 再自动关闭。
 * 名字渲染替换在别处用 {@link #display(String)} 接入。
 */
@ModuleInfo(name = "module.name_protect", enable = false)
public class NameProtect extends AbstractModule {

    @Override
    protected void onEnable() {
        if (mc.player == null || mc.level == null) return;
        toggle();
        mc.gui.setScreen(new NameProtectScreen(null));
        super.onEnable();
    }

    /** 这个名字（已去色码）是否被保护。 */
    public static boolean isProtected(String cleanName) {
        if (cleanName == null || cleanName.isEmpty()) return false;
        for (String n : ZombiesConfig.protectedNames) {
            if (n.equalsIgnoreCase(cleanName)) return true;
        }
        return false;
    }

    /** 被保护的名字返回别名 PlayerN（按名单顺序），否则原样返回。 */
    public static String display(String cleanName) {
        if (cleanName == null) return null;
        List<String> list = ZombiesConfig.protectedNames;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(cleanName)) {
                return "Player" + (i + 1);
            }
        }

        return cleanName;
    }

    /**
     * 中央替换：任意要渲染的字符串里，只要 contains 到保护名，就把该名字段替换成 PlayerN。
     * 渲染文字的入口（Font）统一调它即可。名单为空时原样返回（零开销）。
     */
    /** 管理界面本身要显示真名，打开它时跳过替换。 */
    private static boolean bypass() {
        return Minecraft.getInstance().gui.screen() instanceof NameProtectScreen;
    }

    public static String apply(String text) {
        if (text == null || text.isEmpty() || bypass()) return text;
        List<String> list = ZombiesConfig.protectedNames;
        if (list.isEmpty()) return text;

        String out = text;
        for (int i = 0; i < list.size(); i++) {
            String n = list.get(i);
            if (n == null || n.isEmpty()) continue;
            if (out.contains(n)) {
                out = out.replace(n, "Player" + (i + 1));
            }
        }
        return out;
    }

    // ====== 保留样式（颜色）的替换：Component / FormattedCharSequence ======

    private record Run(String text, Style style) {}

    /** Component 版：命中则按样式分段重建（别名套用被换名字的颜色），否则原样返回。 */
    public static Component protect(Component comp) {
        if (comp == null || ZombiesConfig.protectedNames.isEmpty() || bypass()) return comp;
        List<Run> runs = replaceStyled(comp.getVisualOrderText());
        if (runs == null) return comp;
        MutableComponent out = Component.empty();
        for (Run r : runs) {
            out.append(Component.literal(r.text()).setStyle(r.style()));
        }
        return out;
    }

    /** FormattedCharSequence 版（聊天/计分板/TAB 等）。 */
    public static FormattedCharSequence protect(FormattedCharSequence fcs) {
        if (fcs == null || ZombiesConfig.protectedNames.isEmpty() || bypass()) return fcs;
        List<Run> runs = replaceStyled(fcs);
        if (runs == null) return fcs;
        List<FormattedCharSequence> parts = new ArrayList<>(runs.size());
        for (Run r : runs) {
            parts.add(FormattedCharSequence.forward(r.text(), r.style()));
        }
        return FormattedCharSequence.composite(parts);
    }

    /**
     * 核心：把字形流拆成 (码点, 样式)，逐位匹配保护名；命中则插入别名（套用名字首字符的样式），
     * 其余字符保留各自样式。按样式合并成 Run。没命中返回 null（让调用方保留原对象+原样式）。
     */
    private static List<Run> replaceStyled(FormattedCharSequence fcs) {
        List<Integer> cps = new ArrayList<>();
        List<Style> sty = new ArrayList<>();
        fcs.accept((position, style, codePoint) -> {
            cps.add(codePoint);
            sty.add(style);
            return true;
        });

        List<String> names = ZombiesConfig.protectedNames;
        List<Run> runs = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        Style[] curStyle = {null};
        boolean changed = false;

        int i = 0;
        int len = cps.size();
        while (i < len) {
            int hit = -1;
            for (int k = 0; k < names.size(); k++) {
                if (matchesAt(cps, i, names.get(k))) { hit = k; break; }
            }
            if (hit >= 0) {
                String alias = "Player" + (hit + 1);
                Style nameStyle = sty.get(i); // 名字首字符的颜色/样式
                for (int c = 0; c < alias.length(); c++) {
                    pushChar(runs, cur, curStyle, alias.charAt(c), nameStyle);
                }
                i += names.get(hit).length();
                changed = true;
            } else {
                pushCp(runs, cur, curStyle, cps.get(i), sty.get(i));
                i++;
            }
        }
        if (!changed) return null;
        if (cur.length() > 0) runs.add(new Run(cur.toString(), curStyle[0]));
        return runs;
    }

    private static boolean matchesAt(List<Integer> cps, int i, String n) {
        if (n == null || n.isEmpty() || i + n.length() > cps.size()) return false;
        for (int j = 0; j < n.length(); j++) {
            if (cps.get(i + j) != n.charAt(j)) return false; // 名字为 ASCII，码点==char
        }
        return true;
    }

    private static void pushChar(List<Run> runs, StringBuilder cur, Style[] curStyle, char ch, Style s) {
        if (curStyle[0] != null && !Objects.equals(curStyle[0], s)) {
            runs.add(new Run(cur.toString(), curStyle[0]));
            cur.setLength(0);
        }
        curStyle[0] = s;
        cur.append(ch);
    }

    private static void pushCp(List<Run> runs, StringBuilder cur, Style[] curStyle, int cp, Style s) {
        if (curStyle[0] != null && !Objects.equals(curStyle[0], s)) {
            runs.add(new Run(cur.toString(), curStyle[0]));
            cur.setLength(0);
        }
        curStyle[0] = s;
        cur.appendCodePoint(cp);
    }
}
