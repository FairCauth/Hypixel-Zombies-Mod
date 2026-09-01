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
    private static final float PANEL_HEIGHT = 43F;
    private static final float PANEL_RADIUS = 6F;
    private static final float PADDING = 7F;

    private static final int PANEL_COLOR = 0xA8181D23;
    private static final int PANEL_HIGHLIGHT = 0x60343C46;
    private static final int TEXT_COLOR = 0xFFF1F5F8;
    private static final int MUTED_TEXT_COLOR = 0xFF99A6B2;
    private static final int ACTIVE_COLOR = 0xFF59D9FF;
    private static final int COMPLETED_COLOR = 0xA059D9FF;
    private static final int FUTURE_COLOR = 0x70434B54;

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
        RenderUtils.drawShadow(
                canvasStack, x, y, PANEL_WIDTH, PANEL_HEIGHT, PANEL_RADIUS,
                0x60000000, 6F, 0F, 2F
        );
        RenderUtils.drawBlur(canvasStack, x, y, PANEL_WIDTH, PANEL_HEIGHT, PANEL_RADIUS, 10F);
        RenderUtils.drawRect(canvasStack, x, y, PANEL_WIDTH, PANEL_HEIGHT, PANEL_RADIUS, PANEL_COLOR);
        RenderUtils.drawRect(canvasStack, x + 1F, y + 1F, PANEL_WIDTH - 2F, 1F, 1F, PANEL_HIGHLIGHT);
        RenderUtils.drawRect(canvasStack, x + PADDING, y + 6F, 2F, 8F, 1F, accentColor);

        String roundText = "R" + round;
        titleFont.drawShadowString(canvasStack, roundText, x + 12F, y + 4F, TEXT_COLOR, true);
        smallFont.drawShadowString(
                canvasStack,
                mapName(map).toUpperCase(Locale.ROOT),
                x + 14F + titleFont.getWidth(roundText),
                y + 6F,
                MUTED_TEXT_COLOR,
                true
        );

        if (bossRound) {
            String bossText = "BOSS";
            float bossWidth = smallFont.getWidth(bossText) + 7F;
            float bossX = x + PANEL_WIDTH - PADDING - bossWidth;
            RenderUtils.drawRect(canvasStack, bossX, y + 4F, bossWidth, 10F, 5F, 0x703D161B);
            smallFont.drawShadowString(canvasStack, bossText, bossX + 3.5F, y + 6F, 0xFFFF7A84, true);
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

        smallFont.drawShadowString(canvasStack, waveText, x + PADDING, y + 17F, TEXT_COLOR, true);
        smallFont.drawShadowString(
                canvasStack,
                roundTimerText,
                x + PADDING + smallFont.getWidth(waveText) + 7F,
                y + 17F,
                MUTED_TEXT_COLOR,
                true
        );
        smallFont.drawShadowString(
                canvasStack,
                timerText,
                x + PANEL_WIDTH - PADDING - smallFont.getWidth(timerText),
                y + 17F,
                overtime
                        ? 0xFFFF7A84
                        : (toNext >= 0D && toNext <= 3D ? 0xFFFFC857 : ACTIVE_COLOR),
                true
        );

        drawWaveRail(canvasStack, smallFont, x + PADDING, y + 29F, map, round, waves, current, elapsed);
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
            int trackColor = waveBoss == ZombiesWaves.WaveBoss.NONE ? FUTURE_COLOR : withAlpha(bossColor, 0x70);

            RenderUtils.drawRect(canvasStack, segmentX, y, segmentWidth, 8F, 2.5F, trackColor);
            if (i < current) {
                RenderUtils.drawRect(canvasStack, segmentX, y, segmentWidth, 8F, 2.5F, COMPLETED_COLOR);
            } else if (i == current) {
                int active = waveBoss == ZombiesWaves.WaveBoss.NONE ? ACTIVE_COLOR : bossColor;
                RenderUtils.drawRect(
                        canvasStack, segmentX, y,
                        Math.max(2F, segmentWidth * currentProgress), 8F, 2.5F, active
                );
            }

            String number = Integer.toString(i + 1);
            int numberColor = i == current ? 0xFFFFFFFF : 0xC8D8E0E6;
            font.drawShadowString(
                    canvasStack,
                    number,
                    segmentX + (segmentWidth - font.getWidth(number)) * 0.5F,
                    y + 1F,
                    numberColor,
                    true
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
