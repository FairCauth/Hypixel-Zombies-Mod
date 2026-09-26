package com.example.client.gui;

import com.example.client.config.AutoSwitchWeaponConfig;
import com.example.client.data.ZombiesGuns;
import com.example.client.config.ZombiesConfig;
import com.example.client.config.AutoSwitchWeaponConfig.GunSwitchSetting;
import com.example.client.language.GuiText;
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

import java.util.EnumSet;

public class AutoSwitchWeaponScreen extends Screen {
    public static AutoSwitchWeaponScreen instance = null;
    private final Screen parent;
    private ScrollPanelWidget scrollPanel;

    /** 正在为哪把枪绑定按键（null = 没在绑） */
    private ZombiesGuns listeningGun = null;
    /** 仅记录界面状态；关闭再打开 Guns Config 时仍保留本次会话的折叠状态。 */
    private final EnumSet<ZombiesGuns> collapsedGuns = EnumSet.noneOf(ZombiesGuns.class);

    private static final int TOP = 65;
    private static final int BOTTOM_SPACE = 45;

    private static final int ROW_HEADER_HEIGHT = 46;
    private static final int COOLDOWN_ROW_HEIGHT = 25;
    private static final int ROW_BOTTOM_PADDING = 8;

    private static final int NAME_WIDTH = 210;
    private static final int SWITCH_WIDTH = 70;
    private static final int FOLD_WIDTH = 28;
    private static final int BIND_WIDTH = 52;

    public AutoSwitchWeaponScreen(Screen parent) {
        super(GuiText.text("gui.auto_switch_weapon"));
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
                GuiText.text("gui.done"),
                button -> {
                    ZombiesConfig.save();
                    this.onClose();
                }
        ).bounds(this.width / 2 - 80, this.height - 30, 160, 20).build());
    }

    /** 扫描玩家物品栏，返回当前拥有的枪。 */
    private static java.util.Set<ZombiesGuns> ownedGuns() {
        java.util.EnumSet<ZombiesGuns> set = java.util.EnumSet.noneOf(ZombiesGuns.class);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return set;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ZombiesGuns g = ZombiesGuns.getGunOrNull(inv.getItem(i));
            if (g != null) set.add(g);
        }
        return set;
    }

    private void buildWeaponRows() {
        this.scrollPanel.clearContent();

        java.util.Set<ZombiesGuns> owned = ownedGuns();

        int panelW = this.width - 48;

        int nameX = 28;
        int bindX = panelW - BIND_WIDTH - 28;
        int foldX = bindX - FOLD_WIDTH - 8;
        int switchX = foldX - SWITCH_WIDTH - 12;
        int cooldownX = nameX;
        int cooldownWidth = panelW - cooldownX - 28;

        int y = 10;

        for (ZombiesGuns gun : ZombiesGuns.values()) {
            AutoSwitchWeaponConfig.GunSwitchSetting config = AutoSwitchWeaponConfig.get(gun);

            int rowStartY = y;
            boolean collapsed = collapsedGuns.contains(gun);
            int cooldownCount = collapsed ? 0 : gun.getUltimateLevelCount() + 1; // Base + Ultimate I...
            int rowHeight = ROW_HEADER_HEIGHT + cooldownCount * COOLDOWN_ROW_HEIGHT + ROW_BOTTOM_PADDING;

            boolean has = owned.contains(gun); // 物品栏里有这把枪 → 高亮

            this.scrollPanel.addModuleBox("", rowStartY, rowHeight, has);
            this.scrollPanel.addScrollText(
                    gun.getLocalizedDisplayName(),
                    nameX,
                    y + 7,
                    has ? 0xFF55FF55 : 0xFFFFFFFF, // 拥有=绿色名字
                    true
            );
            this.scrollPanel.addScrollText(
                        GuiText.textString("gui.damage_label", gun.getDamage()) + "  "
                            + GuiText.textString("gui.gold_label", gun.getGold(), gun.getCriticalGold()),
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

            // 按键绑定：点一下进入监听，按下的键即为该枪的开关键；ESC 解绑
            this.scrollPanel.addScrollWidget(Button.builder(
                    keyLabel(config),
                    button -> {
                        listeningGun = gun;
                        button.setMessage(Component.literal("<…>").withStyle(ChatFormatting.YELLOW));
                    }
            ).bounds(0, 0, BIND_WIDTH, 20).build(), bindX, y + 13);

            // 每把枪独立折叠；只隐藏滑块，不改变任何 cooldown 配置。
            this.scrollPanel.addScrollWidget(Button.builder(
                    Component.literal(collapsed ? "+" : "−"),
                    button -> {
                        if (!collapsedGuns.add(gun)) {
                            collapsedGuns.remove(gun);
                        }
                        rebuild();
                    }
            ).bounds(0, 0, FOLD_WIDTH, 20).build(), foldX, y + 13);

            for (int level = 0; !collapsed && level <= gun.getUltimateLevelCount(); level++) {
                final int ultimateLevel = level;
                int cooldownY = y + ROW_HEADER_HEIGHT + level * COOLDOWN_ROW_HEIGHT;
                this.scrollPanel.addScrollWidget(new DoubleSliderButton(
                        0,
                        0,
                        cooldownWidth,
                        20,
                        cooldownLabel(gun, ultimateLevel),
                        0,
                        5000,
                        config.getDelayMs(ultimateLevel),
                        10,
                        value -> {
                            config.setDelayMs(ultimateLevel, value.intValue());
                            ZombiesConfig.save();
                        }
                ), cooldownX, cooldownY);
            }

            y += rowHeight;
        }

        this.scrollPanel.setContentHeight(y + 10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(
                this.font,
                GuiText.text("gui.auto_switch_weapon"),
                this.width / 2 - this.font.width(GuiText.text("gui.auto_switch_weapon")) / 2,
                10,
                0xFFFFFFFF,
                true
        );

        NavTabs.draw(graphics, this.font, this.width, 1);

        drawHeader(graphics);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        int panelX = 24;
        int panelW = this.width - 48;

        int bindX = panelW - BIND_WIDTH - 28;
        int foldX = bindX - FOLD_WIDTH - 8;
        int switchX = foldX - SWITCH_WIDTH - 12;

        graphics.text(this.font, GuiText.text("gui.weapon"), panelX + 28, 49, 0xFFAAAAAA, false);
        graphics.text(this.font, GuiText.text("gui.switch"), panelX + switchX, 49, 0xFFAAAAAA, false);
        graphics.text(this.font, GuiText.text("gui.fold"), panelX + foldX, 49, 0xFFAAAAAA, false);
        graphics.text(this.font, GuiText.text("gui.key"), panelX + bindX, 49, 0xFFAAAAAA, false);
        graphics.text(this.font, GuiText.text("gui.cooldowns"), panelX + 230, 49, 0xFFAAAAAA, false);

        graphics.fill(24, 60, this.width - 24, 61, 0xFF333333);
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (listeningGun != null) {
            int key = event.key();
            GunSwitchSetting config = AutoSwitchWeaponConfig.get(listeningGun);
            config.setKey(key == GLFW.GLFW_KEY_ESCAPE ? 0 : key); // ESC = 解绑
            listeningGun = null;
            ZombiesConfig.save();
            rebuild(); // 刷新按钮上显示的键名（保持滚动位置）
            return true;
        }
        return super.keyPressed(event);
    }

    /** 重建行内容并保持滚动位置。 */
    private void rebuild() {
        int off = this.scrollPanel.getScrollOffset();
        buildWeaponRows();
        this.scrollPanel.setScrollOffset(off);
    }

    /** 绑定按钮文字：显示该枪的开关键名，未绑定显示 -。 */
    private static Component keyLabel(GunSwitchSetting config) {
        int key = config.getKey();
        if (key <= 0) {
            return Component.literal("-").withStyle(ChatFormatting.GRAY);
        }
        String name = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        return Component.literal(name).withStyle(ChatFormatting.AQUA);
    }

    @Override
    public boolean mouseClicked(@NotNull net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int tab = NavTabs.hit(this.width, event.x(), event.y());
        if (tab >= 0 && tab != 1) { NavTabs.open(tab, this.parent); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private static Component weaponText(ZombiesGuns gun) {
        return Component.literal(gun.getDisplayName() + "  ")
                .append(GuiText.text("gui.damage").copy().append(Component.literal(String.valueOf(gun.getDamage())))
                        .withStyle(ChatFormatting.GRAY));
    }

    private static Component switchText(boolean value) {
        return GuiText.text("gui.switch").copy().append(Component.literal(": "))
            .append(GuiText.text(value ? "gui.on" : "gui.off").copy()
                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static String cooldownLabel(ZombiesGuns gun, int ultimateLevel) {
        double damage = gun.getDamageByUltimateLevel(ultimateLevel);
        if (ultimateLevel <= 0) {
            return GuiText.textString("gui.base") + " (" + GuiText.textString("gui.damage") + damage + ")";
        }
        return GuiText.textString("gui.ultimate") + " " + toRoman(ultimateLevel)
            + " (" + GuiText.textString("gui.damage") + damage + ")";
    }

    private static String toRoman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(value);
        };
    }
}
