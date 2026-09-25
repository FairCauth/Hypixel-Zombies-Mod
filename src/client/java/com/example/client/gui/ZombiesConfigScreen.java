package com.example.client.gui;

import com.example.client.config.ZombiesConfig;
import com.example.client.ZombiesModClient;
import com.example.client.module.AbstractModule;
import com.example.client.setting.Setting;
import com.example.client.setting.SettingManager;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.ButtonSetting;
import com.example.client.setting.settings.ModeSetting;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.utils.render.DoubleSliderButton;
import com.example.client.utils.InputBindingUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * 主配置界面：顶部 Tab(Features / Guns Config)+ 左模块列表(可搜索) + 右设置面板(选中模块的设置)。
 * Guns Config 复用 {@link AutoSwitchWeaponScreen}（点标签切过去）。
 */
public class ZombiesConfigScreen extends Screen {
    public static ZombiesConfigScreen instance = null;

    private Screen parent;
    public void setParent(Screen parent) { this.parent = parent; }

    private EditBox searchBox;
    private ScrollPanelWidget listPanel;     // 左：模块列表
    private ScrollPanelWidget settingsPanel; // 右：选中模块的设置

    private AbstractModule selected = null;
    /** 与搜索框显示内容共用同一份状态，Screen 重新 init 后也要恢复。 */
    private String searchQuery = "";

    private boolean listeningForKey = false;       // 绑定"打开GUI"的键
    private AbstractModule listeningModule = null; // 绑定某模块的开关键

    private static final int SIDE = 20;
    private static final int TABS_Y = 26;
    private static final int SEARCH_Y = 50;
    private static final int PANEL_TOP = 76;
    private static final int BOTTOM_SPACE = 40;
    private static final int LIST_W = 160;
    private static final int GAP = 10;
    private static final int ROW_H = 22;
    private static final int ITEM_H = 25;

    private int rightW;

    public ZombiesConfigScreen(Screen parent) {
        super(Component.literal("Zombies Mod Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        instance = this;
        this.listeningForKey = false;
        this.listeningModule = null;

        // ---- 搜索框 ----
        this.searchBox = new EditBox(this.font, SIDE, SEARCH_Y, LIST_W, 18, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search…"));
        // 先恢复文本再安装 responder，避免面板组件尚未创建时提前触发列表重建。
        this.searchBox.setValue(this.searchQuery);
        this.searchBox.setResponder(s -> {
            searchQuery = s == null ? "" : s;
            buildModuleList();
        });
        this.addRenderableWidget(this.searchBox);

        // ---- 两个面板 ----
        int panelH = this.height - PANEL_TOP - BOTTOM_SPACE;
        this.listPanel = new ScrollPanelWidget(SIDE, PANEL_TOP, LIST_W, panelH);
        int rightX = SIDE + LIST_W + GAP;
        this.rightW = this.width - rightX - SIDE;
        this.settingsPanel = new ScrollPanelWidget(rightX, PANEL_TOP, rightW, panelH);

        buildModuleList();
        buildSettings();

        this.addRenderableWidget(this.listPanel);
        this.addRenderableWidget(this.settingsPanel);

        // ---- 底部：Gui Bind + Done ----
        this.addRenderableWidget(Button.builder(bindText(), b -> {
            this.listeningForKey = true;
            b.setMessage(Component.literal("Gui Bind: ").append(Component.literal("<…>").withStyle(ChatFormatting.YELLOW)));
        }).bounds(SIDE, this.height - 26, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"),
                b -> { ZombiesConfig.save(); onClose(); })
                .bounds(this.width / 2 - 80, this.height - 26, 160, 20).build());
    }

    @Override
    public boolean mouseClicked(@NotNull net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (InputBindingUtils.isBindableMouseButton(event.button())) {
            int binding = InputBindingUtils.encodeMouseButton(event.button());
            if (this.listeningForKey) {
                ZombiesModClient.guiKey = binding;
                this.listeningForKey = false;
                ZombiesConfig.save();
                rebuild();
                return true;
            }
            if (this.listeningModule != null) {
                this.listeningModule.setKey(binding);
                this.listeningModule = null;
                ZombiesConfig.save();
                buildSettings();
                return true;
            }
        }
        int tab = NavTabs.hit(this.width, event.x(), event.y());
        if (tab >= 0 && tab != 0) { NavTabs.open(tab, this.parent); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    /** 左：模块列表（按搜索过滤，点选 → 右边显示设置）。 */
    private void buildModuleList() {
        int off = listPanel.getScrollOffset();
        listPanel.clearContent();
        String normalizedQuery = searchQuery.toLowerCase(Locale.ROOT);

        int y = 6;
        for (AbstractModule module : ZombiesModClient.moduleManager.getModuleList()) {
            if (!normalizedQuery.isEmpty()
                    && !module.getName().toLowerCase(Locale.ROOT).contains(normalizedQuery)) continue;

            AbstractModule m = module;
            listPanel.addScrollWidget(Button.builder(
                    nameText(module),
                    b -> { selected = m; buildSettings(); buildModuleList(); }
            ).bounds(0, 0, LIST_W - 22, 18).build(), 6, y + 2);

            y += ROW_H + 2;
        }
        listPanel.setContentHeight(y + 6);
        listPanel.setScrollOffset(off);
    }

    /** 右：选中模块的设置。 */
    private void buildSettings() {
        int off = settingsPanel.getScrollOffset();
        settingsPanel.clearContent();

        if (selected == null) {
            settingsPanel.addScrollText("← 选择一个模块", 12, 12, 0xFFAAAAAA, false);
            settingsPanel.setContentHeight(40);
            return;
        }

        int sw = rightW - 24;
        int y = 8;

        settingsPanel.addScrollText(selected.getName(), 12, y, 0xFFFFFFFF, true);
        y += 18;

        // 开关 + 键位
        settingsPanel.addScrollWidget(Button.builder(
                boolText("Enabled", selected.isEnable()),
                b -> {
                    selected.toggle();
                    b.setMessage(boolText("Enabled", selected.isEnable()));
                    ZombiesConfig.save();
                    buildModuleList();
                }
        ).bounds(0, 0, sw - 66, 20).build(), 12, y);

        settingsPanel.addScrollWidget(Button.builder(
                moduleKeyText(selected),
                b -> {
                    listeningModule = selected;
                    b.setMessage(Component.literal("<…>").withStyle(ChatFormatting.YELLOW));
                }
        ).bounds(0, 0, 60, 20).build(), 12 + sw - 60, y);

        y += ITEM_H + 4;

        for (Setting<?> setting : SettingManager.getSettings(selected)) {
            if (!setting.isDisplay()) continue;

            switch (setting) {
                case BooleanSetting booleanSetting -> {
                    settingsPanel.addScrollWidget(Button.builder(
                            boolText(setting.getName(), Boolean.TRUE.equals(booleanSetting.getValue())),
                            button -> {
                                boolean nv = !Boolean.TRUE.equals(booleanSetting.getValue());
                                booleanSetting.setValue(nv);
                                button.setMessage(boolText(setting.getName(), nv));
                                ZombiesConfig.save();
                                buildSettings();
                            }
                    ).bounds(0, 0, sw, 20).build(), 12, y);
                    y += ITEM_H;
                }
                case NumberSetting numberSetting -> {
                    settingsPanel.addScrollWidget(new DoubleSliderButton(
                            0, 0, sw, 20,
                            setting.getName(),
                            numberSetting.getMin(), numberSetting.getMax(),
                            numberSetting.getValue().doubleValue(),
                            stepFromFormat(numberSetting.getPrecisePattern()),
                            value -> { numberSetting.setValue(value); ZombiesConfig.save(); }
                    ), 12, y);
                    y += ITEM_H;
                }
                case ModeSetting modeSetting -> {
                    settingsPanel.addScrollWidget(Button.builder(
                            modeText(setting.getName(), modeSetting.getValue()),
                            button -> {
                                String nm = modeSetting.next();
                                button.setMessage(modeText(setting.getName(), nm));
                                ZombiesConfig.save();
                                buildSettings();
                            }
                    ).bounds(0, 0, sw, 20).build(), 12, y);
                    y += ITEM_H;
                }
                case ButtonSetting buttonSetting -> {
                    settingsPanel.addScrollWidget(Button.builder(
                            Component.literal(setting.getName()),
                            button -> buttonSetting.onClickedButton()
                    ).bounds(0, 0, sw, 20).build(), 12, y);
                    y += ITEM_H;
                }
                default -> { }
            }
        }

        settingsPanel.setContentHeight(y + 10);
        settingsPanel.setScrollOffset(off);
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (this.listeningForKey) {
            int key = event.key();
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                ZombiesModClient.guiKey = key;
                ZombiesConfig.save();
            }
            this.listeningForKey = false;
            rebuild();
            return true;
        }
        if (this.listeningModule != null) {
            int key = event.key();
            this.listeningModule.setKey(key == GLFW.GLFW_KEY_ESCAPE ? 0 : key);
            this.listeningModule = null;
            ZombiesConfig.save();
            buildSettings();
            return true;
        }
        return super.keyPressed(event);
    }

    private void rebuild() {
        buildModuleList();
        buildSettings();
    }

    private Component nameText(AbstractModule m) {
        ChatFormatting c = (m == selected) ? ChatFormatting.YELLOW
                : (m.isEnable() ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        return Component.literal(m.getName()).withStyle(c);
    }

    private static Component bindText() {
        String keyName = InputBindingUtils.displayName(ZombiesModClient.guiKey);
        return Component.literal("Gui Bind: ").append(Component.literal(keyName).withStyle(ChatFormatting.AQUA));
    }

    private static Component moduleKeyText(AbstractModule module) {
        int key = module.getKey();
        if (key == InputBindingUtils.NONE) return Component.literal("None").withStyle(ChatFormatting.GRAY);
        String keyName = InputBindingUtils.displayName(key);
        return Component.literal(keyName).withStyle(ChatFormatting.AQUA);
    }

    private static double stepFromFormat(String pattern) {
        int dot = pattern.indexOf('.');
        if (dot == -1) return 1.0;
        int decimals = pattern.length() - dot - 1;
        return Math.pow(10, -decimals);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "Zombies Mod",
                this.width / 2 - this.font.width("Zombies Mod") / 2, 8, 0xFFFFFFFF, true);
        NavTabs.draw(graphics, this.font, this.width, 0);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    private static Component boolText(String name, boolean value) {
        return Component.literal(name + ": ")
                .append(Component.literal(value ? "ON" : "OFF")
                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static Component modeText(String name, Object value) {
        return Component.literal(name + ": ")
                .append(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.AQUA));
    }
}
