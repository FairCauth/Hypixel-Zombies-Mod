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
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public class ZombiesConfigScreen extends Screen {
    public static ZombiesConfigScreen instance = null;

    private Screen parent;

    /** 设置关闭后返回的界面。按键打开传 null（回到游戏），从某界面打开就传那个界面。 */
    public void setParent(Screen parent) {
        this.parent = parent;
    }

    private ScrollPanelWidget scrollPanel;

    private Button bindButton;
    private boolean listeningForKey = false;

    private static final int PANEL_WIDTH = 300;
    private static final int WIDGET_WIDTH = 220;

    private static final int TOP = 80;
    private static final int BOTTOM_SPACE = 45;

    private static final int ITEM_HEIGHT = 25;
    private static final int MODULE_PADDING = 8;
    private static final int MODULE_GAP = 10;

    public ZombiesConfigScreen(Screen parent) {
        super(Component.literal("Zombies Mod Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        this.listeningForKey = false;
        this.bindButton = Button.builder(
                bindText(),
                button -> {
                    this.listeningForKey = true;
                    button.setMessage(Component.literal("Gui Bind: ")
                            .append(Component.literal("<按任意键…>").withStyle(ChatFormatting.YELLOW)));
                }
        ).bounds(centerX - 100, 50, 200, 20).build();
        this.addRenderableWidget(this.bindButton);

        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = TOP;
        int panelH = this.height - TOP - BOTTOM_SPACE;

        this.scrollPanel = new ScrollPanelWidget(
                panelX,
                panelY,
                PANEL_WIDTH,
                panelH
        );

        buildModuleContent();

        this.addRenderableWidget(this.scrollPanel);

        this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                button -> {
                    ZombiesConfig.save();
                    this.onClose();
                }
        ).bounds(centerX - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (this.listeningForKey) {
            int key = event.key();
            if (key != GLFW.GLFW_KEY_ESCAPE) {   // ESC = 取消绑定，不改键
                ZombiesModClient.guiKey = key;
                ZombiesConfig.save();
            }
            this.listeningForKey = false;
            if (this.bindButton != null) {
                this.bindButton.setMessage(bindText());
            }
            return true; // 消费按键，避免误触发关闭等
        }
        return super.keyPressed(event);
    }

    /** 绑定按钮文字：显示当前打开 GUI 的按键名。 */
    private static Component bindText() {
        String keyName = InputConstants.Type.KEYSYM
                .getOrCreate(ZombiesModClient.guiKey)
                .getDisplayName()
                .getString();
        return Component.literal("Gui Bind: ")
                .append(Component.literal(keyName).withStyle(ChatFormatting.AQUA));
    }

    private void buildModuleContent() {
        this.scrollPanel.clearContent();

        int y = 10;

        for (AbstractModule module : ZombiesModClient.moduleManager.getModuleList()) {
            int moduleStartY = y;


            y += MODULE_PADDING + 12;

            this.scrollPanel.addScrollWidget(Button.builder(
                    boolText(module.getName(), module.isEnable()),
                    button -> {
                        module.toggle();
                        button.setMessage(boolText(module.getName(), module.isEnable()));
                        ZombiesConfig.save();
                    }
            ).bounds(0, 0, WIDGET_WIDTH, 20).build(), y);

            y += ITEM_HEIGHT;

            for (Setting<?> setting : SettingManager.getSettings(module)) {
                if (setting instanceof BooleanSetting booleanSetting) {
                    this.scrollPanel.addScrollWidget(Button.builder(
                            boolText(setting.getName(), Boolean.TRUE.equals(booleanSetting.getValue())),
                            button -> {
                                boolean newValue = !Boolean.TRUE.equals(booleanSetting.getValue());

                                booleanSetting.setValue(newValue);
                                button.setMessage(boolText(setting.getName(), newValue));

                                ZombiesConfig.save();
                            }
                    ).bounds(0, 0, WIDGET_WIDTH, 20).build(), y);

                    y += ITEM_HEIGHT;
                } else if (setting instanceof NumberSetting numberSetting) {
                    this.scrollPanel.addScrollWidget(new DoubleSliderButton(
                            0,
                            0,
                            WIDGET_WIDTH,
                            20,
                            setting.getName(),
                            numberSetting.getMin(),
                            numberSetting.getMax(),
                            numberSetting.getValue().doubleValue(),
                            stepFromFormat(numberSetting.getPrecisePattern()),
                            value -> {
                                numberSetting.setValue(value);
                                ZombiesConfig.save();
                            }
                    ), y);

                    y += ITEM_HEIGHT;
                } else if (setting instanceof ModeSetting modeSetting) {
                    this.scrollPanel.addScrollWidget(Button.builder(
                            modeText(setting.getName(), modeSetting.getValue()),
                            button -> {
                                String newMode = modeSetting.next();

                                button.setMessage(modeText(setting.getName(), newMode));

                                ZombiesConfig.save();
                            }
                    ).bounds(0, 0, WIDGET_WIDTH, 20).build(), y);
                    y += ITEM_HEIGHT;
                } else if (setting instanceof ButtonSetting buttonSetting) {
                    this.scrollPanel.addScrollWidget(Button.builder(
                            Component.literal(setting.getName()),
                            button ->
                                    buttonSetting.onClickedButton()
                    ).bounds(0, 0, WIDGET_WIDTH, 20).build(), y);
                    y += ITEM_HEIGHT;
                }
            }

            y += MODULE_PADDING;

            int moduleHeight = y - moduleStartY;

            this.scrollPanel.addModuleBox(
                    module.getName(),
                    moduleStartY,
                    moduleHeight
            );

            y += MODULE_GAP;
        }

        this.scrollPanel.setContentHeight(y);
    }

    private static double stepFromFormat(String pattern) {
        int dot = pattern.indexOf('.');

        if (dot == -1) {
            return 1.0;
        }

        int decimals = pattern.length() - dot - 1;

        return Math.pow(10, -decimals);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(
                this.font,
                "Zombies Mod Settings",
                this.width / 2 - 55,
                25,
                0xFFFFFFFF,
                true
        );
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
                .append(Component.literal(String.valueOf(value))
                        .withStyle(ChatFormatting.AQUA));
    }
}