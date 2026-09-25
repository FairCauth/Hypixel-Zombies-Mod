package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.data.PowerupPredictor.Type;          // 直接导入嵌套枚举，避开与本模块同名的冲突
import com.example.client.events.SkiaEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.skia.CanvasStack;
import com.example.client.skia.font.SkiaFont;
import com.example.client.skia.font.SkiaFonts;
import com.example.client.skia.render.LiquidGlassUi;
import com.example.client.skia.render.RenderUtils;
import com.example.client.tracker.ServerTracker;
import com.example.client.utils.PlayerUtils;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = {
        @Text(label = "AA Powerup Predictor", language = Language.English),
        @Text(label = "AA 道具预测", language = Language.Chinese)
}, enable = true)
public class PowerupPredictor extends AbstractModule {
    private static final float PANEL_WIDTH = 176F;
    private static final float PANEL_HEIGHT = 61F;
    private static final float PANEL_RADIUS = 13F;
    private static final float PADDING = 7F;
    private static final float ROW_HEIGHT = 12F;
    private static final float ROW_GAP = 2F;

    private static final int TEXT_COLOR = 0xFFF1F5F8;
    private static final int MUTED_TEXT_COLOR = 0xFFB8C3CC;


    @SettingInfo(name = {@Text(label = "X", language = Language.English)})
    public static final NumberSetting posX = new NumberSetting(0.01, 0, 1, "#.00");

    @SettingInfo(name = {@Text(label = "Y", language = Language.English)})
    public static final NumberSetting posY = new NumberSetting(0.45, 0, 1, "#.00");

    /** 每个道具往后列几个掉落回合 */
    @SettingInfo(name = {@Text(label = "Count", language = Language.English)})
    public static final NumberSetting count = new NumberSetting(3, 1, 8, "#");

    public PowerupPredictor() {
        registerSetting(posX, posY, count);
    }

    @EventTarget
    public void onRenderSkia(SkiaEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!PlayerUtils.isInHypZombies()) return;

        var pred = ServerTracker.powerup.getPredictor();
        int cur = ServerTracker.currentRound;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float x = Math.clamp(
                screenWidth * posX.getValue().floatValue(),
                2F,
                Math.max(2F, screenWidth - PANEL_WIDTH - 2F)
        );
        float y = Math.clamp(
                screenHeight * posY.getValue().floatValue(),
                2F,
                Math.max(2F, screenHeight - PANEL_HEIGHT - 2F)
        );
        int show = count.getValue().intValue();

        drawPanel(event.getCanvasStack(), x, y, pred, cur, show);
    }

    private void drawPanel(
            CanvasStack canvasStack,
            float x,
            float y,
            com.example.client.data.PowerupPredictor predictor,
            int currentRound,
            int showCount
    ) {
        SkiaFont titleFont = SkiaFonts.getBoldFont(7);
        SkiaFont rowFont = SkiaFonts.getDefaultFont(5);
        int lockedCount = 0;
        for (Type type : Type.values()) {
            if (predictor.isLocked(type)) lockedCount++;
        }

        int summaryColor = lockedCount == Type.values().length ? 0xFF72F5A5 : 0xFFC38BFF;
        LiquidGlassUi.drawPanel(canvasStack, x, y, PANEL_WIDTH, PANEL_HEIGHT, PANEL_RADIUS);
        LiquidGlassUi.drawStatusLight(canvasStack, x + PADDING, y + 5F, 3F, 7F, 0xFFC38BFF);
        titleFont.drawString(canvasStack, "POWERUP FORECAST", x + PADDING + 7F, y + 3.8F, TEXT_COLOR);

        String lockText = lockedCount + "/" + Type.values().length + " LOCKED";
        float pillWidth = rowFont.getWidth(lockText) + 12F;
        float pillX = x + PANEL_WIDTH - PADDING - pillWidth;
        LiquidGlassUi.drawPill(canvasStack, pillX, y + 3F, pillWidth, 10F, summaryColor);
        LiquidGlassUi.drawStatusLight(canvasStack, pillX + 3.5F, y + 6.5F, 3F, 3F, summaryColor);
        rowFont.drawString(
                canvasStack,
                lockText,
                pillX + 8F,
                y + 5F,
                summaryColor
        );

        float rowY = y + 17F;
        for (Type type : Type.values()) {
            drawPowerupRow(canvasStack, rowFont, x + PADDING, rowY, type, predictor, currentRound, showCount);
            rowY += ROW_HEIGHT + ROW_GAP;
        }
    }

    private void drawPowerupRow(
            CanvasStack canvasStack,
            SkiaFont font,
            float x,
            float y,
            Type type,
            com.example.client.data.PowerupPredictor predictor,
            int currentRound,
            int showCount
    ) {
        float rowWidth = PANEL_WIDTH - PADDING * 2F;
        int typeColor = color(type);
        boolean locked = predictor.isLocked(type);

        LiquidGlassUi.drawSurface(
                canvasStack,
                x, y, rowWidth, ROW_HEIGHT, 5.5F,
                typeColor,
                locked ? 0x14 : 0x07,
                locked ? 0x5C : 0x28
        );
        LiquidGlassUi.drawStatusLight(
                canvasStack,
                x + 4F, y + 4.5F,
                2.5F, 2.5F,
                locked ? typeColor : withAlpha(typeColor, 0x80)
        );
        font.drawString(
                canvasStack,
                label(type),
                x + 9F,
                y + 2.5F,
                locked ? typeColor : MUTED_TEXT_COLOR
        );

        float timelineX = x + 43F;
        float timelineWidth = rowWidth - 47F;
        float timelineY = y + 9F;
        RenderUtils.drawRect(canvasStack, timelineX, timelineY, timelineWidth, 0.75F, 0.375F, 0x52FFFFFF);

        if (!locked) {
            font.drawString(canvasStack, "SCANNING", timelineX, y + 2.5F, MUTED_TEXT_COLOR);
            for (int i = 0; i < 4; i++) {
                float dotX = timelineX + timelineWidth - 20F + i * 6F;
                RenderUtils.drawRect(canvasStack, dotX, timelineY - 1F, 2.5F, 2.5F, 1.25F, 0x705D6974);
            }
            return;
        }

        List<String> entries = predictionEntries(type, predictor, currentRound, showCount);
        if (entries.isEmpty()) entries = List.of("-");
        float spacing = entries.size() <= 1 ? 0F : timelineWidth / (entries.size() - 1F);

        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            float nodeX = entries.size() == 1 ? timelineX + timelineWidth * 0.5F : timelineX + spacing * i;
            boolean now = "NOW".equals(entry);
            float labelX = Math.clamp(
                    nodeX - font.getWidth(entry) * 0.5F,
                    timelineX,
                    timelineX + timelineWidth - font.getWidth(entry)
            );

            RenderUtils.drawRect(
                    canvasStack,
                    nodeX - (now ? 1.8F : 1.2F),
                    timelineY - (now ? 1.8F : 1.2F),
                    now ? 3.6F : 2.4F,
                    now ? 3.6F : 2.4F,
                    now ? 1.8F : 1.2F,
                    now ? 0xFFFFC857 : typeColor
            );
            font.drawString(
                    canvasStack,
                    entry,
                    labelX,
                    y + 2F,
                    now ? 0xFFFFD56A : TEXT_COLOR
            );
        }
    }

    private static List<String> predictionEntries(
            Type type,
            com.example.client.data.PowerupPredictor predictor,
            int currentRound,
            int showCount
    ) {
        List<String> entries = new ArrayList<>();
        if (predictor.isPowerupRound(type, currentRound)) {
            entries.add("NOW");
        }

        int round = currentRound;
        for (int i = 0; i < showCount; i++) {
            int next = predictor.nextRound(type, round);
            if (next < 0) break;
            entries.add("R" + next);
            round = next;
        }
        return entries;
    }

    private static String label(Type t) {
        return switch (t) {
            case INSTA -> "INSTA";
            case MAX -> "MAX";
            case SS -> "SPREE";
        };
    }

    private static int color(Type t) {
        return switch (t) {
            case INSTA -> 0xFFFF5A67;
            case MAX -> 0xFF59A8FF;
            case SS -> 0xFFB65CFF;
        };
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.clamp(alpha, 0, 255) << 24);
    }
}
