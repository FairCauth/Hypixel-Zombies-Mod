package com.example.client.gui;

import com.example.client.config.ZombiesConfig;
import com.example.client.language.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 名字保护 GUI：输入框添加要保护的名字，列表展示已保护的名字（及其别名 PlayerN）+ 删除按钮。
 * 名字存在 {@link ZombiesConfig#protectedNames}。
 */
public class NameProtectScreen extends Screen {

    private final Screen parent;
    private EditBox nameBox;

    private ScrollPanelWidget scrollPanel;
    private int panelX, panelY, panelW, panelH;

    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 84;
    private static final int BOTTOM_SPACE = 40;

    public NameProtectScreen(Screen parent) {
        super(GuiText.text("gui.name_protect"));
        this.parent = parent;
    }

    private static List<String> names() {
        return ZombiesConfig.protectedNames;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        // 输入框 + Add 按钮
        this.nameBox = new EditBox(this.font, cx - 150, 58, 220, 20, GuiText.text("gui.name"));
        this.nameBox.setMaxLength(16);
        this.addRenderableWidget(this.nameBox);

        this.addRenderableWidget(Button.builder(GuiText.text("gui.add"), b -> addName())
                .bounds(cx + 80, 58, 60, 20).build());

        // 滚动列表
        panelW = 300;
        panelX = cx - panelW / 2;
        panelY = LIST_TOP;
        panelH = this.height - LIST_TOP - BOTTOM_SPACE;
        this.scrollPanel = new ScrollPanelWidget(panelX, panelY, panelW, panelH);
        buildList();
        this.addRenderableWidget(this.scrollPanel);

        this.addRenderableWidget(Button.builder(GuiText.text("gui.done"), b -> onClose())
                .bounds(cx - 100, this.height - 28, 200, 20).build());
    }

    private void addName() {
        String n = this.nameBox.getValue().trim();
        if (n.isEmpty()) return;
        for (String exist : names()) {
            if (exist.equalsIgnoreCase(n)) {       // 去重
                this.nameBox.setValue("");
                return;
            }
        }
        names().add(n);
        ZombiesConfig.save();
        this.nameBox.setValue("");
        buildList();
    }

    private void removeName(String n) {
        names().removeIf(s -> s.equalsIgnoreCase(n));
        ZombiesConfig.save();
        buildList();
    }

    /** 重建列表内容（每行右侧一个删除按钮；名字/别名文字在 render 里画）。 */
    private void buildList() {
        int off = this.scrollPanel.getScrollOffset();
        this.scrollPanel.clearContent();

        List<String> list = names();
        for (int i = 0; i < list.size(); i++) {
            String n = list.get(i);
            Button del = Button.builder(Component.literal("X"), b -> removeName(n))
                    .bounds(0, 0, 20, 20).build();
            this.scrollPanel.addScrollWidget(del, panelW - 20 - 14, i * ROW_HEIGHT + 2);
        }
        this.scrollPanel.setContentHeight(list.size() * ROW_HEIGHT + 6);
        this.scrollPanel.setScrollOffset(off);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int cx = this.width / 2;
        Component title = GuiText.text("gui.name_protect");
        graphics.text(this.font, title, cx - this.font.width(title) / 2, 8, 0xFFFFFFFF, true);
        NavTabs.draw(graphics, this.font, this.width, 2);
        graphics.text(this.font, GuiText.text("gui.add_name"), cx - 150, 48, 0xFFAAAAAA, true);

        List<String> list = names();
        int scroll = this.scrollPanel.getScrollOffset();
        graphics.enableScissor(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1);
        for (int i = 0; i < list.size(); i++) {
            int rowY = panelY + i * ROW_HEIGHT - scroll;
            if (rowY + ROW_HEIGHT <= panelY || rowY >= panelY + panelH) continue;

            graphics.text(this.font, list.get(i), panelX + 8, rowY + 6, 0xFFFFFFFF, true);
            String alias = GuiText.textString("gui.player") + (i + 1);
            graphics.text(this.font, alias, panelX + 150, rowY + 6, 0xFF66CCFF, true);
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int tab = NavTabs.hit(this.width, event.x(), event.y());
        if (tab >= 0 && tab != 2) { NavTabs.open(tab, this.parent); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        ZombiesConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
