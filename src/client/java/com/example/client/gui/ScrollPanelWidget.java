package com.example.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ScrollPanelWidget extends AbstractWidget {
    private final Minecraft mc = Minecraft.getInstance();

    private final List<ScrollItem> items = new ArrayList<>();
    private final List<ScrollText> texts = new ArrayList<>();
    private final List<ModuleBox> moduleBoxes = new ArrayList<>();

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int contentHeight = 0;

    private boolean draggingScrollbar = false;
    private int scrollbarGrabOffset = 0;

    private AbstractWidget focusedChild;

    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MARGIN = 2;

    public ScrollPanelWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    public void clearContent() {
        items.clear();
        texts.clear();
        moduleBoxes.clear();

        scrollOffset = 0;
        maxScroll = 0;
        contentHeight = 0;

        focusedChild = null;
        draggingScrollbar = false;
    }

    public <T extends AbstractWidget> T addScrollWidget(T widget, int baseY) {
        int widgetX = (this.width - 220) / 2;
        items.add(new ScrollItem(widget, widgetX, baseY));
        return widget;
    }

    public <T extends AbstractWidget> T addScrollWidget(T widget, int baseX, int baseY) {
        items.add(new ScrollItem(widget, baseX, baseY));
        return widget;
    }

    public void addScrollText(String text, int baseX, int baseY, int color, boolean shadow) {
        texts.add(new ScrollText(text, baseX, baseY, color, shadow));
    }

    public void addScrollText(String text, int baseX, int baseY) {
        addScrollText(text, baseX, baseY, 0xFFFFFFFF, false);
    }

    public void addModuleBox(String name, int baseY, int height) {
        moduleBoxes.add(new ModuleBox(name, baseY, height));
    }

    public void setContentHeight(int contentHeight) {
        this.contentHeight = Math.max(0, contentHeight);
        updateMaxScroll();
        updateChildPositions();
    }

    private void updateMaxScroll() {
        this.maxScroll = Math.max(0, contentHeight - this.height);
        this.scrollOffset = clamp(this.scrollOffset, 0, this.maxScroll);
    }

    private void updateChildPositions() {
        for (ScrollItem item : items) {
            int x = this.getX() + item.baseX;
            int y = this.getY() + item.baseY - scrollOffset;

            item.widget.setX(x);
            item.widget.setY(y);

            boolean visible = y + item.widget.getHeight() >= this.getY()
                    && y <= this.getY() + this.height;

            item.widget.visible = visible;
            item.widget.active = visible;
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        updateChildPositions();

        int x1 = this.getX();
        int y1 = this.getY();
        int x2 = this.getX() + this.width;
        int y2 = this.getY() + this.height;

        graphics.fill(x1, y1, x2, y2, 0x66000000);

        graphics.fill(x1, y1, x2, y1 + 1, 0xFF555555);
        graphics.fill(x1, y2 - 1, x2, y2, 0xFF555555);
        graphics.fill(x1, y1, x1 + 1, y2, 0xFF555555);
        graphics.fill(x2 - 1, y1, x2, y2, 0xFF555555);

        graphics.enableScissor(x1 + 1, y1 + 1, x2 - 1, y2 - 1);

        drawModuleBoxes(graphics);
        drawScrollTexts(graphics);

        for (ScrollItem item : items) {
            if (!item.widget.visible) {
                continue;
            }

            item.widget.extractRenderState(graphics, mouseX, mouseY, a);
        }

        graphics.disableScissor();

        drawScrollbar(graphics);
    }

    private void drawScrollTexts(GuiGraphicsExtractor graphics) {
        int panelX = this.getX();
        int panelY = this.getY();

        int visibleTop = this.getY();
        int visibleBottom = this.getY() + this.height;

        for (ScrollText text : texts) {
            int x = panelX + text.baseX;
            int y = panelY + text.baseY - scrollOffset;

            if (y + mc.font.lineHeight < visibleTop || y > visibleBottom) {
                continue;
            }

            graphics.text(
                    mc.font,
                    text.text,
                    x,
                    y,
                    text.color,
                    text.shadow
            );
        }
    }

    private void drawModuleBoxes(GuiGraphicsExtractor graphics) {
        int panelX = this.getX();
        int panelY = this.getY();

        int boxX = panelX + 8;
        int boxW = this.width - 18;

        int visibleTop = this.getY();
        int visibleBottom = this.getY() + this.height;

        for (ModuleBox box : moduleBoxes) {
            int y = panelY + box.baseY - scrollOffset;
            int h = box.height;

            if (y + h < visibleTop || y > visibleBottom) {
                continue;
            }

            int drawY1 = Math.max(y, visibleTop);
            int drawY2 = Math.min(y + h, visibleBottom);

            graphics.fill(boxX, drawY1, boxX + boxW, drawY2, 0xAA151515);

            graphics.fill(boxX, drawY1, boxX + boxW, drawY1 + 1, 0xFF444444);
            graphics.fill(boxX, drawY2 - 1, boxX + boxW, drawY2, 0xFF444444);
            graphics.fill(boxX, drawY1, boxX + 1, drawY2, 0xFF444444);
            graphics.fill(boxX + boxW - 1, drawY1, boxX + boxW, drawY2, 0xFF444444);

            if (box.name != null && !box.name.isEmpty()) {
                int titleY = y + 8;

                if (titleY >= visibleTop && titleY <= visibleBottom) {
                    graphics.text(
                            mc.font,
                            box.name,
                            boxX + 10,
                            titleY,
                            0xFFFFCC55,
                            true
                    );
                }
            }
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        if (maxScroll <= 0) {
            return;
        }

        int trackX1 = getScrollbarX();
        int trackY1 = this.getY() + SCROLLBAR_MARGIN;
        int trackX2 = trackX1 + SCROLLBAR_WIDTH;
        int trackY2 = this.getY() + this.height - SCROLLBAR_MARGIN;

        graphics.fill(trackX1, trackY1, trackX2, trackY2, 0xFF000000);

        int thumbY = getThumbY();
        int thumbHeight = getThumbHeight();

        graphics.fill(trackX1, thumbY, trackX2, thumbY + thumbHeight, 0xFF808080);

        graphics.fill(trackX1, thumbY, trackX2 - 1, thumbY + 1, 0xFFC0C0C0);
        graphics.fill(trackX1, thumbY, trackX1 + 1, thumbY + thumbHeight - 1, 0xFFC0C0C0);

        graphics.fill(trackX1, thumbY + thumbHeight - 1, trackX2, thumbY + thumbHeight, 0xFF404040);
        graphics.fill(trackX2 - 1, thumbY, trackX2, thumbY + thumbHeight, 0xFF404040);
    }

    private int getScrollbarX() {
        return this.getX() + this.width - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
    }

    private int getThumbHeight() {
        if (contentHeight <= 0) {
            return this.height;
        }

        int trackHeight = this.height - SCROLLBAR_MARGIN * 2;
        int thumbHeight = (int) ((double) this.height / (double) contentHeight * trackHeight);

        return clamp(thumbHeight, 20, trackHeight);
    }

    private int getThumbY() {
        int trackY = this.getY() + SCROLLBAR_MARGIN;
        int trackHeight = this.height - SCROLLBAR_MARGIN * 2;
        int thumbHeight = getThumbHeight();

        if (maxScroll <= 0) {
            return trackY;
        }

        double ratio = scrollOffset / (double) maxScroll;

        return trackY + (int) ((trackHeight - thumbHeight) * ratio);
    }

    private void setScrollOffsetFromMouse(double mouseY) {
        int trackY = this.getY() + SCROLLBAR_MARGIN;
        int trackHeight = this.height - SCROLLBAR_MARGIN * 2;
        int thumbHeight = getThumbHeight();

        int available = trackHeight - thumbHeight;

        if (available <= 0) {
            scrollOffset = 0;
            return;
        }

        double thumbTop = mouseY - scrollbarGrabOffset;
        double ratio = (thumbTop - trackY) / available;

        ratio = Math.max(0.0, Math.min(1.0, ratio));

        scrollOffset = (int) Math.round(ratio * maxScroll);
        scrollOffset = clamp(scrollOffset, 0, maxScroll);

        updateChildPositions();
    }

    private boolean isMouseInside(double mouseX, double mouseY) {
        return mouseX >= this.getX()
                && mouseX <= this.getX() + this.width
                && mouseY >= this.getY()
                && mouseY <= this.getY() + this.height;
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        if (maxScroll <= 0) {
            return false;
        }

        int x1 = getScrollbarX();
        int x2 = x1 + SCROLLBAR_WIDTH;

        return mouseX >= x1
                && mouseX <= x2
                && mouseY >= this.getY()
                && mouseY <= this.getY() + this.height;
    }

    private boolean isMouseOverThumb(double mouseX, double mouseY) {
        if (!isMouseOverScrollbar(mouseX, mouseY)) {
            return false;
        }

        int thumbY = getThumbY();
        int thumbHeight = getThumbHeight();

        return mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseInside(event.x(), event.y())) {
            return false;
        }

        if (event.button() == 0 && isMouseOverScrollbar(event.x(), event.y())) {
            int thumbY = getThumbY();

            if (isMouseOverThumb(event.x(), event.y())) {
                scrollbarGrabOffset = (int) event.y() - thumbY;
            } else {
                scrollbarGrabOffset = getThumbHeight() / 2;
                setScrollOffsetFromMouse(event.y());
            }

            draggingScrollbar = true;
            return true;
        }

        for (int i = items.size() - 1; i >= 0; i--) {
            AbstractWidget widget = items.get(i).widget;

            if (!widget.visible || !widget.active) {
                continue;
            }

            if (widget.mouseClicked(event, doubleClick)) {
                focusedChild = widget;
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScrollbar && event.button() == 0) {
            draggingScrollbar = false;
            return true;
        }

        if (focusedChild != null) {
            boolean result = focusedChild.mouseReleased(event);
            focusedChild = null;
            return result;
        }

        for (ScrollItem item : items) {
            if (item.widget.visible) {
                item.widget.mouseReleased(event);
            }
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingScrollbar && event.button() == 0) {
            setScrollOffsetFromMouse(event.y());
            return true;
        }

        if (focusedChild != null) {
            return focusedChild.mouseDragged(event, dx, dy);
        }

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseInside(mouseX, mouseY)) {
            return false;
        }

        if (maxScroll <= 0) {
            return false;
        }

        this.scrollOffset -= (int) (scrollY * 18);
        this.scrollOffset = clamp(this.scrollOffset, 0, this.maxScroll);

        updateChildPositions();

        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class ScrollItem {
        private final AbstractWidget widget;
        private final int baseX;
        private final int baseY;

        private ScrollItem(AbstractWidget widget, int baseX, int baseY) {
            this.widget = widget;
            this.baseX = baseX;
            this.baseY = baseY;
        }
    }

    private static class ScrollText {
        private final String text;
        private final int baseX;
        private final int baseY;
        private final int color;
        private final boolean shadow;

        private ScrollText(String text, int baseX, int baseY, int color, boolean shadow) {
            this.text = text;
            this.baseX = baseX;
            this.baseY = baseY;
            this.color = color;
            this.shadow = shadow;
        }
    }

    private static class ModuleBox {
        private final String name;
        private final int baseY;
        private final int height;

        private ModuleBox(String name, int baseY, int height) {
            this.name = name;
            this.baseY = baseY;
            this.height = height;
        }
    }
}