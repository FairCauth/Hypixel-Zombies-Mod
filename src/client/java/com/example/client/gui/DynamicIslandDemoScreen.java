package com.example.client.gui;

import com.example.client.skia.CanvasStack;
import com.example.client.skia.Skia;
import com.example.client.skia.render.dynamicisland.DynamicIslandDemoScene;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A Skia-only demo screen. No world, network or notification state is involved. */
public final class DynamicIslandDemoScreen extends Screen {
    private final Screen parent;
    private DynamicIslandDemoScene scene;
    private long lastFrame;

    public DynamicIslandDemoScreen(Screen parent) {
        super(Component.literal("Dynamic Island · Skia Demo"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (scene == null) scene = new DynamicIslandDemoScene();
        lastFrame = System.nanoTime();
    }

    public void renderSkia(CanvasStack stack) {
        if (scene == null) return;
        long now = System.nanoTime();
        // Limit suspended-window catchup; normal animation remains frame-rate independent.
        scene.advance(Math.min((now - lastFrame) / 1_000_000_000.0, 0.1));
        lastFrame = now;
        scene.draw(stack.canvas(), width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (scene != null) scene.pointer(mouseX, mouseY);
        // Keep a vanilla draw in the frame so GuiRenderer captures its framebuffer on menus too.
        graphics.fill(0, 0, 1, 1, 0x01000000);
        if (!Skia.isInit()) {
            graphics.centeredText(font, "Skia could not initialize. Check the client log.", width / 2, height / 2, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && scene != null) {
            if (scene.pointerDown(event.x(), event.y())) return true;
            return scene.click(event.x(), event.y());
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && scene != null && scene.pointerUp(event.x(), event.y())) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (scene == null) return super.keyPressed(event);
        switch (event.key()) {
            case GLFW.GLFW_KEY_SPACE -> {
                if ((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) scene.expandSecondary();
                else scene.toggleExpanded();
            }
            case GLFW.GLFW_KEY_M -> scene.toggleSecondary();
            case GLFW.GLFW_KEY_1 -> scene.select(0);
            case GLFW.GLFW_KEY_2 -> scene.select(1);
            case GLFW.GLFW_KEY_3 -> scene.select(2);
            case GLFW.GLFW_KEY_A -> scene.toggleAuto();
            case GLFW.GLFW_KEY_S -> scene.toggleSlow();
            case GLFW.GLFW_KEY_F8, GLFW.GLFW_KEY_ESCAPE -> onClose();
            default -> { return super.keyPressed(event); }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        if (scene != null) { scene.close(); scene = null; }
        super.removed();
    }
}
