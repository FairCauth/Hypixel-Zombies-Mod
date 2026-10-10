package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.config.ZombiesConfig;
import com.example.client.ZombiesModClient;
import com.example.client.integration.QQMusicBridge;
import com.example.client.skia.render.dynamicisland.IslandLiveController;
import com.example.client.events.KeyInputEvent;
import com.example.client.events.MouseInputEvent;
import com.example.client.events.SkiaEvent;
import com.example.client.gui.DynamicIslandDemoScreen;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.ButtonSetting;
import com.example.client.setting.settings.KeySetting;
import com.example.client.setting.settings.ModeSetting;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.skia.render.dynamicisland.DynamicIsland;
import com.example.client.skia.render.dynamicisland.DynamicIslandHudRenderer;
import com.example.client.skia.Skia;
import com.example.client.skia.render.LiquidGlass;
import com.example.client.utils.InputBindingUtils;
import io.github.humbleui.skija.Image;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import java.util.List;

@ModuleInfo(name = {
        @Text(label = "Dynamic Island", language = Language.English),
        @Text(label = "灵动岛", language = Language.Chinese)
}, enable = true)
public final class DynamicIslandHud extends AbstractModule {
    @SettingInfo(name = {
            @Text(label = "Source", language = Language.English),
            @Text(label = "内容来源", language = Language.Chinese)
    })
    private final ModeSetting source = new ModeSetting("Live", List.of("Live", "Demo"));

    @SettingInfo(name = {
            @Text(label = "QQ Music", language = Language.English),
            @Text(label = "QQ 音乐", language = Language.Chinese)
    })
    private final BooleanSetting qqMusic = new BooleanSetting(true);

    @SettingInfo(name = {
            @Text(label = "Show Lyrics", language = Language.English),
            @Text(label = "显示歌词", language = Language.Chinese)
    })
    private final BooleanSetting showLyrics = new BooleanSetting(false);

    @SettingInfo(name = {
            @Text(label = "Highlight Advance (ms)", language = Language.English),
            @Text(label = "高光提前 (ms)", language = Language.Chinese)
    })
    private final NumberSetting lyricHighlightAdvance = new NumberSetting(250, -2000, 2000, "0");

    @SettingInfo(name = {
            @Text(label = "Wave HUD In Island", language = Language.English),
            @Text(label = "波次 HUD 接入岛", language = Language.Chinese)
    })
    private final BooleanSetting waveHud = new BooleanSetting(true);

    @SettingInfo(name = {
            @Text(label = "Revive HUD In Island", language = Language.English),
            @Text(label = "复活 HUD 接入岛", language = Language.Chinese)
    })
    private final BooleanSetting reviveHud = new BooleanSetting(true);

    @SettingInfo(name = {
            @Text(label = "Automatic Glances", language = Language.English),
            @Text(label = "自动短暂展开", language = Language.Chinese)
    })
    private final BooleanSetting glances = new BooleanSetting(true);

    @SettingInfo(name = {
            @Text(label = "Hold To Expand", language = Language.English),
            @Text(label = "按住展开按键", language = Language.Chinese)
    })
    private final KeySetting holdToExpand = new KeySetting(GLFW.GLFW_KEY_TAB);

    @SettingInfo(name = {
            @Text(label = "Toggle Expansion", language = Language.English),
            @Text(label = "切换展开按键", language = Language.Chinese)
    })
    private final KeySetting toggleExpansion = new KeySetting(GLFW.GLFW_KEY_F8);
    @SettingInfo(name = {
            @Text(label = "Scale", language = Language.English),
            @Text(label = "大小", language = Language.Chinese)
    })
    private final NumberSetting scale = new NumberSetting(0.65, 0.35, 1.25, "0.00");

    @SettingInfo(name = {
            @Text(label = "Top Margin", language = Language.English),
            @Text(label = "顶部距离", language = Language.Chinese)
    })
    private final NumberSetting topMargin = new NumberSetting(8, 2, 60, "0");

    @SettingInfo(name = {
            @Text(label = "Activity", language = Language.English),
            @Text(label = "示例内容", language = Language.Chinese)
    })
    private final ModeSetting activity = new ModeSetting("Music", List.of("Music", "Timer", "Call"));

    @SettingInfo(name = {
            @Text(label = "Second Activity", language = Language.English),
            @Text(label = "第二个任务", language = Language.Chinese)
    })
    private final ModeSetting secondActivity = new ModeSetting("None", List.of("None", "Timer", "Music", "Call"));

    @SettingInfo(name = {
            @Text(label = "Expanded Task", language = Language.English),
            @Text(label = "展开任务", language = Language.Chinese)
    })
    private final ModeSetting expandedTask = new ModeSetting("Main", List.of("Main", "Second"));

    @SettingInfo(name = {
            @Text(label = "Expanded", language = Language.English),
            @Text(label = "展开", language = Language.Chinese)
    })
    private final BooleanSetting expanded = new BooleanSetting(false);

    @SettingInfo(name = {
            @Text(label = "Auto Demo", language = Language.English),
            @Text(label = "自动演示", language = Language.Chinese)
    })
    private final BooleanSetting autoplay = new BooleanSetting(false);

    @SettingInfo(name = {
            @Text(label = "Open Preview", language = Language.English),
            @Text(label = "独立预览", language = Language.Chinese)
    })
    private final ButtonSetting preview = new ButtonSetting() {
        @Override
        public void onClickedButton() {
            mc.setScreen(new DynamicIslandDemoScreen(mc.screen));
        }
    };

    private DynamicIslandHudRenderer renderer;
    private IslandLiveController liveController;
    private QQMusicBridge musicBridge;
    private String renderingSource;
    private long lastFrame;
    private boolean peekHeld;

    public DynamicIslandHud() {
        registerSetting(source, qqMusic, showLyrics, lyricHighlightAdvance, waveHud, reviveHud, glances, holdToExpand, toggleExpansion, scale, topMargin,
                activity, secondActivity, expandedTask, expanded, autoplay, preview);
        // Also clean up when GUI rendering is skipped (F1, title screen, or shutdown).
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isEnable() || client.player == null || client.level == null) releaseAll();
            else if (client.screen != null || client.options.hideGui || !client.getWindow().isFocused()) updatePeek(false, false);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> cleanup());
    }

    @EventTarget
    public void onRenderSkia(SkiaEvent event) {
        // Screens get their own pass after vanilla has drawn the entire UI.
        if (mc.screen != null) return;
        renderIsland(event);
    }

    public static void renderAboveScreen() {
        if (mc.screen == null || mc.screen instanceof DynamicIslandDemoScreen
                || mc.player == null || mc.level == null || mc.options.hideGui) return;
        var manager = ZombiesModClient.moduleManager;
        DynamicIslandHud hud = manager == null ? null : manager.getModule(DynamicIslandHud.class);
        if (hud != null && hud.isEnable()) {
            Skia.tick((canvasStack, layer) -> hud.renderIsland(new SkiaEvent(canvasStack, layer)));
        }
    }

    private void renderIsland(SkiaEvent event) {
        if (mc.player == null || mc.level == null) {
            releaseAll();
            return;
        }
        if (!source.getValue().equals(renderingSource)) {
            releaseRenderer();
            renderingSource = source.getValue();
        }
        if (source.is("Live") && qqMusic.getValue()) {
            if (musicBridge == null) musicBridge = new QQMusicBridge(showLyrics.getValue());
            musicBridge.setLyricsEnabled(showLyrics.getValue());
        } else releaseMusic();
        if (mc.options.hideGui) {
            updatePeek(false, false);
            lastFrame = 0;
            return;
        }
        // Allocate native Skia resources only on the render thread, after initialization.
        if (renderer == null) renderer = new DynamicIslandHudRenderer();
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1_000_000_000.0, 0.1);
        lastFrame = now;
        if (source.is("Live")) {
            if (liveController == null) {
                liveController = new IslandLiveController(renderer.island());
                renderer.island().setMusicControl(action -> { if (musicBridge != null) musicBridge.command(action); });
            }
            updatePeek(isExpandHeld(), isShiftDown());
            renderer.island().setMusicVisuals(musicBridge == null ? null : musicBridge.audioSnapshot(),
                    musicBridge == null ? null : musicBridge.paletteSnapshot());
            var music = musicBridge == null ? null : musicBridge.snapshot();
            if (music != null) music = music.atTime(now);
            renderer.island().setMusicLyrics(showLyrics.getValue(), musicBridge == null ? null : musicBridge.lyricSnapshot(music),
                    lyricHighlightAdvance.getValue().doubleValue() / 1000.0);
            var manager = ZombiesModClient.moduleManager;
            WaveDisplay wave = manager == null ? null : manager.getModule(WaveDisplay.class);
            ReviveAura revive = manager == null ? null : manager.getModule(ReviveAura.class);
            liveController.update(music,
                    waveHud.getValue() && wave != null ? wave.islandSnapshot() : null,
                    reviveHud.getValue() && revive != null ? revive.islandSnapshot() : null, dt, glances.getValue());
        } else {
            DynamicIsland.Activity selected = parseActivity(activity.getValue());
            DynamicIsland.Activity second = secondActivity.is("None") ? null : parseActivity(secondActivity.getValue());
            renderer.configure(selected, second, expandedTask.is("Second"), expanded.getValue(), autoplay.getValue());
            updatePeek(isExpandHeld(), isShiftDown());
        }
        renderer.advance(dt);
        // Demo autoplay can change focus during advance; a held key keeps the requested task open.
        if (source.is("Demo") && peekHeld) updatePeek(true, isShiftDown());
        if (renderer.island().visibleTaskIds().isEmpty()) return;
        Image snapshot = Skia.getGameImage();
        float guiScale = (float) mc.getWindow().getGuiScale();
        Image blurred = snapshot == null ? null : LiquidGlass.getBlurredBackground(snapshot, 4 * guiScale);
        renderer.draw(event.getCanvasStack().canvas(), mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(), scale.getValue().floatValue(), topMargin.getValue().floatValue(),
                snapshot, blurred, guiScale, guiScale);
    }

    private static DynamicIsland.Activity parseActivity(String value) {
        return switch (value) {
            case "Timer" -> DynamicIsland.Activity.TIMER;
            case "Call" -> DynamicIsland.Activity.CALL;
            default -> DynamicIsland.Activity.MUSIC;
        };
    }

    private boolean isExpandHeld() {
        // Do not consume chat typing, screen navigation, or a key being rebound in settings.
        if (mc.screen != null || !mc.getWindow().isFocused()) return false;
        int binding = holdToExpand.getValue();
        if (binding == InputBindingUtils.NONE) return false;
        long window = mc.getWindow().handle();
        return InputBindingUtils.isMouseBinding(binding)
                ? GLFW.glfwGetMouseButton(window, InputBindingUtils.decodeMouseButton(binding)) == GLFW.GLFW_PRESS
                : GLFW.glfwGetKey(window, binding) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        return mc.getWindow().isFocused()
                && (GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
    }

    private void updatePeek(boolean held, boolean second) {
        if (liveController != null) liveController.setPeekHeld(held, second);
        else if (renderer != null) {
            var visible = renderer.island().visibleTaskIds();
            if (held && !visible.isEmpty()) renderer.island().expandTask(visible.get(second && visible.size() > 1 ? 1 : 0));
            else if (peekHeld && !held) renderer.island().setExpanded(false);
        }
        peekHeld = held;
    }

    @EventTarget
    public void onKey(KeyInputEvent event) {
        if (mc.player == null || mc.level == null || mc.screen != null
                || mc.options.hideGui || event.getAction() != GLFW.GLFW_PRESS) return;
        boolean shift = (event.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (InputBindingUtils.matchesKeyboard(toggleExpansion.getValue(), event.getKey())) {
            toggleExpanded(shift);
            return;
        }
        if (source.is("Live")) {
            if (event.getKey() == GLFW.GLFW_KEY_F9 && musicBridge != null) musicBridge.command("toggle");
            else if (event.getKey() == GLFW.GLFW_KEY_F10 && musicBridge != null) musicBridge.command(shift ? "previous" : "next");
            return;
        }
        if (event.getKey() == GLFW.GLFW_KEY_F9) {
            autoplay.setValue(false);
            activity.next();
            ZombiesConfig.save();
        } else if (event.getKey() == GLFW.GLFW_KEY_F10) {
            autoplay.setValue(false);
            secondActivity.setMode(secondActivity.is("None") ? activity.is("Timer") ? "Music" : "Timer" : "None");
            expanded.setValue(false);
            ZombiesConfig.save();
        }
    }

    @EventTarget
    public void onMouse(MouseInputEvent event) {
        if (mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui
                || event.getAction() != GLFW.GLFW_PRESS) return;
        if (InputBindingUtils.matchesMouse(toggleExpansion.getValue(), event.getButton())) {
            toggleExpanded((event.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
        }
    }

    private void toggleExpanded(boolean second) {
        if (source.is("Live")) {
            if (liveController != null) liveController.toggle(second);
            return;
        }
        autoplay.setValue(false);
        String target = second && !secondActivity.is("None") ? "Second" : "Main";
        expanded.setValue(!expanded.getValue() || !expandedTask.is(target));
        expandedTask.setMode(target);
        ZombiesConfig.save();
    }

    @Override
    protected void onDisable() {
        releaseAll();
    }

    private void releaseRenderer() {
        if (renderer != null) { renderer.close(); renderer = null; }
        liveController = null;
        peekHeld = false;
        lastFrame = 0;
    }

    private void releaseMusic() {
        if (musicBridge != null) { musicBridge.close(); musicBridge = null; }
    }

    private void releaseAll() { releaseRenderer(); releaseMusic(); renderingSource = null; }

    private static DynamicIslandHud routedHud() {
        var manager = ZombiesModClient.moduleManager;
        DynamicIslandHud hud = manager == null ? null : manager.getModule(DynamicIslandHud.class);
        return hud != null && hud.isEnable() && hud.source.is("Live") && hud.liveController != null
                && !mc.options.hideGui && !(mc.screen instanceof DynamicIslandDemoScreen) ? hud : null;
    }

    public static boolean routesRevive() { DynamicIslandHud hud = routedHud(); return hud != null && hud.reviveHud.getValue(); }

    @Override
    public void cleanup() {
        releaseAll();
    }

    @Override
    public String getDescription() {
        return "真实 QQ 音乐、波次与复活 HUD；打开其他 UI 时仍在顶部显示。按住 "
                + InputBindingUtils.displayName(holdToExpand.getValue()) + " 展开主岛，配合 Shift 展开副岛，松开收起；"
                + InputBindingUtils.displayName(toggleExpansion.getValue())
                + " 切换常驻展开。两种按键均可自定义，Esc 可取消绑定。F9 播放/暂停，F10 下一首，Shift+F10 上一首。";
    }

    @Override public String getTag() { return source.is("Live") && musicBridge != null ? musicBridge.status() : source.getValue(); }
}
