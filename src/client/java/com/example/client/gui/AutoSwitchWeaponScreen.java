package com.example.client.gui;

import com.example.client.config.AutoSwitchWeaponConfig;
import com.example.client.ZombiesGuns;
import com.example.client.config.ZombiesConfig;
import com.example.client.utils.render.DoubleSliderButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AutoSwitchWeaponScreen extends Screen {
    public static AutoSwitchWeaponScreen instance = null;
    private final Screen parent;
    private ScrollPanelWidget scrollPanel;

    private static final int TOP = 65;
    private static final int BOTTOM_SPACE = 45;

    private static final int ROW_HEIGHT = 46;

    private static final int NAME_WIDTH = 210;
    private static final int SWITCH_WIDTH = 110;
    private static final int SLIDER_WIDTH = 190;

    public AutoSwitchWeaponScreen(Screen parent) {
        super(Component.literal("Auto Switch Weapon"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelX = 24;
        int panelY = TOP;
        int panelW = this.width - 48;
        int panelH = this.height - TOP - BOTTOM_SPACE;

        this.scrollPanel = new ScrollPanelWidget(panelX, panelY, panelW, panelH);

        buildWeaponRows();

        this.addRenderableWidget(this.scrollPanel);

        this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                button -> {
                    ZombiesConfig.save();
                    this.onClose();
                }
        ).bounds(this.width / 2 - 80, this.height - 30, 160, 20).build());
    }

    private void buildWeaponRows() {
        this.scrollPanel.clearContent();

        int panelW = this.width - 48;

        int nameX = 28;
        int switchX = panelW - SLIDER_WIDTH - SWITCH_WIDTH - 70;
        int sliderX = panelW - SLIDER_WIDTH - 28;

        int y = 10;

        for (ZombiesGuns gun : ZombiesGuns.values()) {
            AutoSwitchWeaponConfig.GunSwitchSetting config = AutoSwitchWeaponConfig.get(gun);

            int rowStartY = y;


            this.scrollPanel.addModuleBox("", rowStartY, ROW_HEIGHT);
            this.scrollPanel.addScrollText(
                    gun.getDisplayName(),
                    nameX,
                    y + 7,
                    0xFFFFFFFF,
                    true
            );
            this.scrollPanel.addScrollText(
                    "Damage: " + gun.getDamage() + "  Gold: " + gun.getGold() + "/" + gun.getCriticalGold(),
                    nameX,
                    y + 23,
                    0xFFAAAAAA,
                    false
            );

            this.scrollPanel.addScrollWidget(Button.builder(
                    switchText(config.isEnabled()),
                    button -> {
                        config.setEnabled(!config.isEnabled());
                        button.setMessage(switchText(config.isEnabled()));
                        ZombiesConfig.save();
                    }
            ).bounds(0, 0, SWITCH_WIDTH, 20).build(), switchX, y + 13);


            this.scrollPanel.addScrollWidget(new DoubleSliderButton(
                    0,
                    0,
                    SLIDER_WIDTH,
                    20,
                    "Delay",
                    0,
                    5000,
                    config.getDelayMs(),
                    10,
                    value -> {
                        config.setDelayMs(value.intValue());
                        ZombiesConfig.save();
                    }
            ), sliderX, y + 13);

            y += ROW_HEIGHT;
        }

        this.scrollPanel.setContentHeight(y + 10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xFF0B0B0E);

        graphics.text(
                this.font,
                "Auto Switch Weapon",
                this.width / 2 - this.font.width("Auto Switch Weapon") / 2,
                22,
                0xFFFFFFFF,
                true
        );

        drawHeader(graphics);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        int panelX = 24;
        int panelW = this.width - 48;

        int nameX = panelX + 18;
        int switchX = panelX + panelW - SLIDER_WIDTH - SWITCH_WIDTH - 60;
        int sliderX = panelX + panelW - SLIDER_WIDTH - 22;

        graphics.text(this.font, "Weapon", nameX + 6, 49, 0xFFAAAAAA, false);
        graphics.text(this.font, "Switch", switchX + 30, 49, 0xFFAAAAAA, false);
        graphics.text(this.font, "Delay", sliderX + 75, 49, 0xFFAAAAAA, false);

        graphics.fill(24, 60, this.width - 24, 61, 0xFF333333);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private static Component weaponText(ZombiesGuns gun) {
        return Component.literal(gun.getDisplayName() + "  ")
                .append(Component.literal("DMG " + gun.getDamage())
                        .withStyle(ChatFormatting.GRAY));
    }

    private static Component switchText(boolean value) {
        return Component.literal("Switch: ")
                .append(Component.literal(value ? "ON" : "OFF")
                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}