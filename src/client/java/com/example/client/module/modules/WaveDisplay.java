package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.data.ZombiesWaves;
import com.example.client.events.SkiaEvent;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.skia.CanvasStack;
import com.example.client.skia.font.SkiaFont;
import com.example.client.skia.font.SkiaFonts;
import com.example.client.skia.render.LiquidGlassUi;
import com.example.client.skia.render.RenderUtils;
import com.example.client.tracker.ServerTracker;
import com.example.client.utils.PlayerUtils;
import com.example.client.utils.ZombiesMap;
import com.example.client.utils.ZombiesUtils;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

/**
 * 波数显示：右下角列出当前回合各波次，用箭头指示当前波、变暗已过波。
 * 回合号/回合起始时间来自 {@link ServerTracker}；波次时间表见 {@link ZombiesWaves}。
 */
@ModuleInfo(name = {
        @Text(label = "Wave Display", language = Language.English),
        @Text(label = "波数显示", language = Language.Chinese)
}, enable = false)
public class WaveDisplay extends AbstractModule {
    private static final float PANEL_WIDTH = 170F;
    private static final float PANEL_HEIGHT = 47F;
    private static final float PANEL_RADIUS = 13F;
    private static final float PADDING = 7F;

    private static final int TEXT_COLOR = 0xFFF1F5F8;
    private static final int MUTED_TEXT_COLOR = 0xFFB8C3CC;
    private static final int ACTIVE_COLOR = 0xFF67DDFC;
    private static final int COMPLETED_COLOR = 0x8059D9FF;
    private static final int FUTURE_COLOR = 0x26434B54;

    @SettingInfo(name = {
            @Text(label = "Only In Zombies", language = Language.English),
            @Text(label = "仅在僵尸末日里", language = Language.Chinese)
    })
    public static final BooleanSetting onlyGame = new BooleanSetting(true);

    @SettingInfo(name = {
            @Text(label = "Sound", language = Language.English),
            @Text(label = "音效", language = Language.Chinese)
    })
    public static final BooleanSetting sound = new BooleanSetting(true);

    @SettingInfo(name = {
            @Text(label = "X", language = Language.English),
            @Text(label = "X", language = Language.Chinese)
    })
    public static final NumberSetting posX = new NumberSetting(0.82, 0, 1, "#.00");

    @SettingInfo(name = {
            @Text(label = "Y", language = Language.English),
            @Text(label = "Y", language = Language.Chinese)
    })
    public static final NumberSetting posY = new NumberSetting(0.60, 0, 1, "#.00");

    private int soundRound = Integer.MIN_VALUE;
    private long soundRoundTime = Long.MIN_VALUE;
    private int soundWave = Integer.MIN_VALUE;
    private int lastCountdownSecond = -1;

    public WaveDisplay() {
        registerSetting(onlyGame, sound, posX, posY);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!sound.getValue()
                || mc.player == null
                || mc.level == null
                || (onlyGame.getValue() && !PlayerUtils.isInHypZombies())) {
            resetSoundState();
            return;
        }

        int round = ServerTracker.currentRound;
        ZombiesMap map = ZombiesUtils.getMap();
        int[] waves = ZombiesWaves.getWaves(map, round);
        if (round < 0 || map == null || map == ZombiesMap.NULL || waves == null || waves.length == 0) {
            resetSoundState();
            return;
        }

        double elapsed = Math.max(0D, (System.currentTimeMillis() - ServerTracker.roundTime) / 1000D);
        int currentWave = ZombiesWaves.currentWaveIndex(waves, elapsed);

        boolean roundNumberChanged = round != soundRound;
        boolean newRound = roundNumberChanged || ServerTracker.roundTime != soundRoundTime;
        if (newRound) {
            if (soundRound != Integer.MIN_VALUE && roundNumberChanged && round > soundRound) {
                playNextRoundSound();
            }
            soundRound = round;
            soundRoundTime = ServerTracker.roundTime;
            soundWave = currentWave;
            lastCountdownSecond = -1;
        } else if (currentWave != soundWave) {
            if (currentWave > soundWave) {
                playNextWaveSound();
            }
            soundWave = currentWave;
            lastCountdownSecond = -1;
        }

        double toNext = ZombiesWaves.secondsToNextWave(waves, elapsed);
        if (toNext > 0D && toNext <= 3D) {
            int second = Math.clamp((int) Math.ceil(toNext), 1, 3);
            if (second != lastCountdownSecond) {
                playCountdownSound();
                lastCountdownSecond = second;
            }
        } else if (toNext > 3D) {
            lastCountdownSecond = -1;
        }
    }

    private void playCountdownSound() {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 2
        ));
    }

    private void playNextWaveSound() {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_PLING.value(), 2, 2
        ));
    }

    private void playNextRoundSound() {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_PLING.value(), 2, 2
        ));
    }

    private void resetSoundState() {
        soundRound = Integer.MIN_VALUE;
        soundRoundTime = Long.MIN_VALUE;
        soundWave = Integer.MIN_VALUE;
        lastCountdownSecond = -1;
    }

    @EventTarget
    public void onRenderSkia(SkiaEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (onlyGame.getValue() && !PlayerUtils.isInHypZombies()) return;

        int round = ServerTracker.currentRound;
        if (round < 0) return;

        ZombiesMap map = ZombiesUtils.getMap();
        if (map == null || map == ZombiesMap.NULL) return;

        int[] waves = ZombiesWaves.getWaves(map, round);
        if (waves == null || waves.length == 0) return;

        boolean boss = ZombiesWaves.isBossRound(map, round);

        double elapsed = (System.currentTimeMillis() - ServerTracker.roundTime) / 1000.0;
        if (elapsed < 0) elapsed = 0;

        int current = ZombiesWaves.currentWaveIndex(waves, elapsed);

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float x = Math.clamp(
                sw * posX.getValue().floatValue(),
                2F,
                Math.max(2F, sw - PANEL_WIDTH - 2F)
        );
        float y = Math.clamp(
                sh * posY.getValue().floatValue(),
                2F,
                Math.max(2F, sh - PANEL_HEIGHT - 2F)
        );

        double toNext = ZombiesWaves.secondsToNextWave(waves, elapsed);
        drawPanel(event.getCanvasStack(), x, y, map, round, waves, current, elapsed, toNext, boss);
    }

    private void drawPanel(
            CanvasStack canvasStack,
            float x,
            float y,
            ZombiesMap map,
            int round,
            int[] waves,
            int current,
            double elapsed,
            double toNext,
            boolean bossRound
    ) {
        SkiaFont titleFont = SkiaFonts.getBoldFont(7);
        SkiaFont smallFont = SkiaFonts.getDefaultFont(5);

        int accentColor = bossRound ? 0xFFFF5A67 : ACTIVE_COLOR;
        LiquidGlassUi.drawPanel(canvasStack, x, y, PANEL_WIDTH, PANEL_HEIGHT, PANEL_RADIUS);
        LiquidGlassUi.drawStatusLight(canvasStack, x + PADDING, y + 5F, 3F, 7F, accentColor);

        String roundText = "R" + round;
        titleFont.drawString(canvasStack, roundText, x + PADDING + 7F, y + 3.8F, TEXT_COLOR);
        smallFont.drawString(
                canvasStack,
                mapName(map).toUpperCase(Locale.ROOT),
                x + PADDING + 9F + titleFont.getWidth(roundText),
                y + 6F,
                MUTED_TEXT_COLOR
        );

        if (bossRound) {
            String bossText = "BOSS";
            float bossWidth = smallFont.getWidth(bossText) + 12F;
            float bossX = x + PANEL_WIDTH - PADDING - bossWidth;
            LiquidGlassUi.drawPill(canvasStack, bossX, y + 3F, bossWidth, 10F, accentColor);
            LiquidGlassUi.drawStatusLight(canvasStack, bossX + 3.5F, y + 6.5F, 3F, 3F, accentColor);
            smallFont.drawString(canvasStack, bossText, bossX + 8F, y + 5F, 0xFFFF8B94);
        }

        String waveText = "WAVE " + Math.max(0, current + 1) + "/" + waves.length;
        String roundTimerText = "TIME " + formatTimer(elapsed);
        boolean overtime = current >= waves.length - 1;
        double overtimeSeconds = overtime
                ? Math.max(0D, elapsed - waves[waves.length - 1])
                : 0D;
        String timerText = overtime
                ? String.format(Locale.ROOT, "OVER +%.1fs", overtimeSeconds)
                : String.format(Locale.ROOT, "NEXT %.1fs", Math.max(0D, toNext));

        LiquidGlassUi.drawSurface(
                canvasStack,
                x + PADDING, y + 16F,
                PANEL_WIDTH - PADDING * 2F, 11F, 5.5F,
                overtime ? 0xFFFF6775 : accentColor,
                0x0A, 0x2C
        );

        smallFont.drawString(canvasStack, waveText, x + PADDING + 4F, y + 18F, TEXT_COLOR);
        smallFont.drawString(
                canvasStack,
                roundTimerText,
                x + PADDING + 4F + smallFont.getWidth(waveText) + 7F,
                y + 18F,
                MUTED_TEXT_COLOR
        );
        smallFont.drawString(
                canvasStack,
                timerText,
                x + PANEL_WIDTH - PADDING - 4F - smallFont.getWidth(timerText),
                y + 18F,
                overtime
                        ? 0xFFFF7A84
                        : (toNext >= 0D && toNext <= 3D ? 0xFFFFCC73 : ACTIVE_COLOR)
        );

        drawWaveRail(canvasStack, smallFont, x + PADDING, y + 31F, map, round, waves, current, elapsed);
    }

    private void drawWaveRail(
            CanvasStack canvasStack,
            SkiaFont font,
            float x,
            float y,
            ZombiesMap map,
            int round,
            int[] waves,
            int current,
            double elapsed
    ) {
        float railWidth = PANEL_WIDTH - PADDING * 2F;
        float gap = 2F;
        float segmentWidth = (railWidth - gap * (waves.length - 1)) / waves.length;
        float currentProgress = currentWaveProgress(waves, current, elapsed);

        for (int i = 0; i < waves.length; i++) {
            float segmentX = x + i * (segmentWidth + gap);
            ZombiesWaves.WaveBoss waveBoss = ZombiesWaves.aaWaveBoss(map, round, i + 1);
            int bossColor = bossColor(waveBoss);
            int segmentAccent = waveBoss == ZombiesWaves.WaveBoss.NONE ? ACTIVE_COLOR : bossColor;
            LiquidGlassUi.drawSurface(
                    canvasStack,
                    segmentX, y, segmentWidth, 9F, 4.5F,
                    segmentAccent,
                    waveBoss == ZombiesWaves.WaveBoss.NONE ? 0x06 : 0x12,
                    waveBoss == ZombiesWaves.WaveBoss.NONE ? 0x22 : 0x58
            );
            if (i < current) {
                RenderUtils.drawRect(
                        canvasStack,
                        segmentX + 1F, y + 1F,
                        segmentWidth - 2F, 7F, 3.5F,
                        waveBoss == ZombiesWaves.WaveBoss.NONE
                                ? COMPLETED_COLOR
                                : withAlpha(bossColor, 0x78)
                );
            } else if (i == current) {
                int active = waveBoss == ZombiesWaves.WaveBoss.NONE ? ACTIVE_COLOR : bossColor;
                RenderUtils.drawRect(
                        canvasStack,
                        segmentX + 1F, y + 1F,
                        Math.max(1.5F, (segmentWidth - 2F) * currentProgress), 7F, 3.5F,
                        withAlpha(active, 0xB5)
                );
            }

            String number = Integer.toString(i + 1);
            int numberColor = i == current ? 0xFFFFFFFF : 0xD0D8E0E6;
            font.drawString(
                    canvasStack,
                    number,
                    segmentX + (segmentWidth - font.getWidth(number)) * 0.5F,
                    y + 1.5F,
                    numberColor
            );
        }
    }

    private static float currentWaveProgress(int[] waves, int current, double elapsed) {
        if (current < 0) return 0F;
        if (current >= waves.length - 1) return 1F;
        double start = waves[current];
        double end = waves[current + 1];
        return (float) Math.clamp((elapsed - start) / Math.max(0.001D, end - start), 0D, 1D);
    }

    private static int bossColor(ZombiesWaves.WaveBoss wb) {
        return switch (wb) {
            case GIANT -> 0xFFB65CFF;
            case OLD_ONE -> 0xFF35D0C5;
            case BOTH -> 0xFFFF8A45;
            case NONE -> ACTIVE_COLOR;
        };
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.clamp(alpha, 0, 255) << 24);
    }

    private static String formatTimer(double totalSeconds) {
        totalSeconds = Math.max(0D, totalSeconds);
        int minutes = (int) (totalSeconds / 60D);
        double seconds = totalSeconds - minutes * 60D;
        return String.format(Locale.ROOT, "%d:%04.1f", minutes, seconds);
    }

    private static String mapName(ZombiesMap map) {
        return switch (map) {
            case DEAD_END -> "Dead End";
            case BAD_BLOOD -> "Bad Blood";
            case ALIEN_ARCADIUM -> "Alien Arcadium";
            case THE_LAB -> "The Lab";
            case PRISON -> "Prison";
            case NULL -> "?";
        };
    }

}
