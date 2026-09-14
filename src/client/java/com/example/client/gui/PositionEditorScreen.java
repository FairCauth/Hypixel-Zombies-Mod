package com.example.client.gui;

import com.example.client.config.ZombiesConfig;
import com.example.client.setting.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class PositionEditorScreen extends Screen {
    private final Screen parent;
    private final NumberSetting posX;
    private final NumberSetting posY;
    private final NumberSetting scale;
    private final boolean centerX;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    private static final int DEFAULT_PREVIEW_WIDTH = 180;
    private static final int DEFAULT_PREVIEW_HEIGHT = 42;
    private final int previewWidth;
    private final int previewHeight;

    public PositionEditorScreen(Screen parent, NumberSetting posX, NumberSetting posY) {
        this(parent, posX, posY, false, DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT);
    }

    public PositionEditorScreen(Screen parent, NumberSetting posX, NumberSetting posY, boolean centerX) {
        this(parent, posX, posY, centerX, DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT);
    }

    public PositionEditorScreen(Screen parent, NumberSetting posX, NumberSetting posY,
                                boolean centerX, int previewWidth, int previewHeight) {
        this(parent, posX, posY, null, centerX, previewWidth, previewHeight);
    }

    public PositionEditorScreen(Screen parent, NumberSetting posX, NumberSetting posY,
                                NumberSetting scale, boolean centerX, int previewWidth, int previewHeight) {
        super(Component.translatable("zombies-mod.gui.position_editor"));
        this.parent = parent;
        this.posX = posX;
        this.posY = posY;
        this.scale = scale;
        this.centerX = centerX;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Component title = Component.translatable("zombies-mod.gui.position_editor");
        graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, 12, 0xFFFFFFFF, true);

        int x = previewX();
        int y = previewY();
        int width = scaledPreviewWidth();
        int height = scaledPreviewHeight();
        graphics.fill(x, y, x + width, y + height, 0xDD26384A);
        graphics.fill(x, y, x + width, y + 1, 0xFF66CCFF);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF66CCFF);
        graphics.fill(x, y, x + 1, y + height, 0xFF66CCFF);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF66CCFF);

        Component coordinates = Component.literal(String.format("X: %.2f  Y: %.2f",
                posX.getValue().doubleValue(), posY.getValue().doubleValue()));
        graphics.text(this.font, coordinates, 12, this.height - 28, 0xFFFFFFFF, true);
        graphics.text(this.font, Component.translatable("zombies-mod.gui.drag_to_move"),
                this.width - this.font.width(Component.translatable("zombies-mod.gui.drag_to_move")) - 12,
                this.height - 28, 0xFFAAAAAA, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isInsidePreview(event.x(), event.y())) {
            dragging = true;
            dragOffsetX = event.x() - previewX();
            dragOffsetY = event.y() - previewY();
            updatePosition(event.x(), event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging && event.button() == 0) {
            updatePosition(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && dragging) {
            dragging = false;
            ZombiesConfig.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        ZombiesConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private int previewX() {
        return (int) Math.round(this.width * posX.getValue().doubleValue())
            - (centerX ? scaledPreviewWidth() / 2 : 0);
    }

    private int previewY() {
        return (int) Math.round(this.height * posY.getValue().doubleValue());
    }

    private int scaledPreviewWidth() {
        return Math.max(1, (int) Math.round(previewWidth * previewScale()));
    }

    private int scaledPreviewHeight() {
        return Math.max(1, (int) Math.round(previewHeight * previewScale()));
    }

    private double previewScale() {
        return scale == null ? 1.0D : scale.getValue().doubleValue();
    }

    private boolean isInsidePreview(double mouseX, double mouseY) {
        return mouseX >= previewX() && mouseX <= previewX() + scaledPreviewWidth()
            && mouseY >= previewY() && mouseY <= previewY() + scaledPreviewHeight();
    }

    private void updatePosition(double mouseX, double mouseY) {
        double left = mouseX - dragOffsetX;
        double top = mouseY - dragOffsetY;
        double width = Math.max(1, this.width);
        double height = Math.max(1, this.height);

        double x = (left + (centerX ? scaledPreviewWidth() / 2.0D : 0.0D)) / width;
        double y = top / height;

        double minX = centerX ? (scaledPreviewWidth() / 2.0D) / width : 0.0D;
        double maxX = 1.0D;
        double maxY = 1.0D;

        x = Math.clamp(x, minX, maxX);
        y = Math.clamp(y, 0.0D, maxY);
        posX.setValue(x);
        posY.setValue(y);
    }
}