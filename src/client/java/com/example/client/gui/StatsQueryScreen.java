package com.example.client.gui;

import com.example.client.api.HypixelStats;
import com.example.client.config.ZombiesConfig;
import com.example.client.language.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StatsQueryScreen extends Screen {

    private final Screen parent;
    private EditBox apiKeyBox;
    private boolean apiKeyHidden = true;

    private record Entry(String name, UUID uuid) {}
    private final List<Entry> players = new ArrayList<>();

    private ScrollPanelWidget scrollPanel;
    private int panelX, panelY, panelW, panelH;

    private static final int ROW_HEIGHT = 30;
    private static final int API_Y = 58;
    private static final int QUERY_Y = 84;
    private static final int LIST_TOP = 112;
    private static final int BOTTOM_SPACE = 44;

    public StatsQueryScreen(Screen parent) {
        super(GuiText.text("stats_query"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        Minecraft mc = Minecraft.getInstance();

        // API key 输入框
        this.apiKeyBox = new EditBox(this.font, cx - 150, API_Y, 274, 20, GuiText.text("api_key"));
        this.apiKeyBox.setMaxLength(64);
        this.apiKeyBox.setResponder(s -> { if (!apiKeyHidden) ZombiesConfig.apiKey = s; });
        applyMask();
        this.addRenderableWidget(this.apiKeyBox);

        // 显示/隐藏切换
        this.addRenderableWidget(Button.builder(eyeLabel(), b -> {
            apiKeyHidden = !apiKeyHidden;
            applyMask();
            b.setMessage(eyeLabel());
        }).bounds(cx + 130, API_Y, 20, 20).build());

        // 玩家列表（TAB 在场玩家）
        players.clear();
        if (mc.getConnection() != null) {
            for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
                players.add(new Entry(pi.getProfile().name(), pi.getProfile().id()));
            }
        }

        // 一键查询
        this.addRenderableWidget(Button.builder(GuiText.text("query_all"), b -> {
            for (Entry e : players) HypixelStats.query(e.name(), e.uuid());
        }).bounds(cx - 100, QUERY_Y, 200, 20).build());

        // 滚动面板（你的 ScrollPanelWidget，自带滚动条/拖动/滚轮）
        panelW = 320;
        panelX = cx - panelW / 2;
        panelY = LIST_TOP;
        panelH = this.height - LIST_TOP - BOTTOM_SPACE;
        this.scrollPanel = new ScrollPanelWidget(panelX, panelY, panelW, panelH);

        // 每行：右侧放一个真正的 Query 按钮；名字/战绩文字在 render 里按滚动对齐画
        for (int i = 0; i < players.size(); i++) {
            Entry e = players.get(i);
            Button q = Button.builder(GuiText.text("query"),
                    b -> HypixelStats.query(e.name(), e.uuid())).bounds(0, 0, 70, 20).build();
            this.scrollPanel.addScrollWidget(q, panelW - 70 - 16, i * ROW_HEIGHT + 4);
        }
        this.scrollPanel.setContentHeight(players.size() * ROW_HEIGHT + 8);
        this.addRenderableWidget(this.scrollPanel);

        // Done
        this.addRenderableWidget(Button.builder(GuiText.text("done"), b -> onClose())
                .bounds(cx - 100, this.height - 28, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int cx = this.width / 2;
        Component title = GuiText.text("stats_query");
        graphics.text(this.font, title, cx - this.font.width(title) / 2, 8, 0xFFFFFFFF, true);
        NavTabs.draw(graphics, this.font, this.width, 3);
        graphics.text(this.font, GuiText.text("api_key_label"), cx - 150, 48, 0xFFAAAAAA, true);

        // 名字/战绩文字：和按钮同样跟随滚动，裁剪到面板内
        int scroll = this.scrollPanel.getScrollOffset();
        graphics.enableScissor(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1);
        for (int i = 0; i < players.size(); i++) {
            Entry e = players.get(i);
            int rowY = panelY + i * ROW_HEIGHT - scroll;
            if (rowY + ROW_HEIGHT <= panelY || rowY >= panelY + panelH) continue;

            graphics.text(this.font, e.name(), panelX + 8, rowY + 2, 0xFFFFFFFF, true);
            HypixelStats.Result r = HypixelStats.get(e.uuid());
            if (r != null) {
                int color = r.error() ? 0xFFFF5555 : (r.loading() ? 0xFFAAAAAA : 0xFF66FF66);
                graphics.text(this.font, r.text(), panelX + 8, rowY + 14, color, true);
            }
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int tab = NavTabs.hit(this.width, event.x(), event.y());
        if (tab >= 0 && tab != 3) {
            NavTabs.open(tab, this.parent);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private Component eyeLabel() {
        return Component.literal(apiKeyHidden ? "*" : "A");
    }

    private void applyMask() {
        if (apiKeyHidden) {
            this.apiKeyBox.setEditable(false);
            this.apiKeyBox.setValue("*".repeat(ZombiesConfig.apiKey.length()));
        } else {
            this.apiKeyBox.setEditable(true);
            this.apiKeyBox.setValue(ZombiesConfig.apiKey);
        }
    }

    @Override
    public void onClose() {
        ZombiesConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
