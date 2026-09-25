package com.example.client.skia;


import com.example.client.skia.fbo.GameFramebuffer;
import com.example.client.skia.state.GLStateStack;
import com.example.client.utils.IMinecraft;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import io.github.humbleui.skija.*;
import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.Objects;
import java.util.function.BiConsumer;

public final class Skia implements IMinecraft {
    private static Surface surface;
    private static Canvas canvas;
    private static DirectContext context;
    private static BackendRenderTarget renderTarget;

    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static int lastFbId = -1;
    private static int mainGuiFramebuffer = -1;
    private static int mainGuiFramebufferWidth = -1;
    private static int mainGuiFramebufferHeight = -1;
    @Getter
    private static boolean init = false;
    private static BiConsumer<CanvasStack, GameFramebuffer> renderCallback;

    private static Image image = null;


    private static GameFramebuffer customLayer;

    public static Image getGameImage() {
        return image;
    }

    public static void queueAndInit(BiConsumer<CanvasStack, GameFramebuffer> renderCallback) {
        init = false;
        Skia.renderCallback = Objects.requireNonNull(renderCallback, "renderCallback");
        customLayer = GameFramebuffer.registerFramebuffer("Skia");
        mc.execute(() -> {
            try {
                if (context == null) {
                    context = DirectContext.makeGL();
                }

                createSurface();
                init = true;
                System.out.println("Skia initialized using bundled native library");
            } catch (Throwable throwable) {
                init = false;
                System.err.println("Failed to initialize Skia; Skia rendering has been disabled");
                throwable.printStackTrace();
            }
        });


    }

    private static void createSurface() {
        if (surface != null) {
            surface.close();
        }

        if (renderTarget != null) {
            renderTarget.close();
        }

        lastWidth = mc.getWindow().getWidth();
        lastHeight = mc.getWindow().getHeight();
        lastFbId = GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER);


        int samples = GL11.glGetInteger(GL13.GL_SAMPLES);
        int stencilBits = GL11.glGetInteger(GL11.GL_STENCIL_BITS);
        renderTarget = BackendRenderTarget.makeGL(
                lastWidth,
                lastHeight,
                samples,
                stencilBits,
                lastFbId,
                FramebufferFormat.GR_GL_RGBA8
        );

        surface = Surface.wrapBackendRenderTarget(
                context,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                SurfaceColorFormat.RGBA_8888,
                ColorSpace.getSRGB()
        );
        canvas = surface.getCanvas();
    }

    private static void checkAndUpdateSurface() {
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        int framebufferId =
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        if (lastWidth != width
                || lastHeight != height
                || lastFbId != framebufferId) {
            createSurface();
        }
    }

    private static void beginFrame() {
        GLStateStack.push();

        if (context != null) {
            context.resetGLAll();
        }
    }

    private static void endFrame() {
        try {
            if (surface != null) {
                surface.flushAndSubmit();
            }
        } finally {
            try {
                GLStateStack.pop();
            } catch (Exception e) {
                GLStateStack.clear();
            }
        }
    }

    public static int getColorTextureId(RenderTarget target) {
        if (target.getColorTexture() instanceof GlTexture texture) {
            return texture.glId();
        }

        return -1;
    }

    public static void tick() {
        if (!Skia.isInit()) return;
        if (mainGuiFramebuffer <= 0
                || mainGuiFramebufferWidth != mc.getWindow().getWidth()
                || mainGuiFramebufferHeight != mc.getWindow().getHeight()
                || !GL30.glIsFramebuffer(mainGuiFramebuffer)) {
            return;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainGuiFramebuffer);
        checkAndUpdateSurface();
        beginFrame();

        Image frameSnapshot = null;
        try {
            // Blur must never sample the same framebuffer that the Canvas is writing to.
            // A surface snapshot stays immutable while this frame's Skia UI is rendered.
            frameSnapshot = surface.makeImageSnapshot();
            image = frameSnapshot;

            CanvasStack canvasStack = new CanvasStack(canvas);
            try {
                canvasStack.push();
                float scale = (float) mc.getWindow().getGuiScale();
                canvasStack.scale(scale, scale);
                renderCallback.accept(canvasStack, customLayer);
//                customLayer.render(canvasStack);
//                EventManager.call(new RenderSkiaEvent(canvasStack));
            } finally {
                canvasStack.pop();
            }
        } finally {
            try {
                endFrame();
            } finally {
                image = null;
                if (frameSnapshot != null) {
                    frameSnapshot.close();
                }
            }
        }
    }

    /**
     * Called while Minecraft's main GUI RenderPass is still active. Its close()
     * immediately binds framebuffer 0, so remember the real target first and
     * rebind it from tick() before vanilla starts drawing the next GUI frame.
     */
    public static void captureMainGuiFramebuffer() {
        int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (framebuffer > 0) {
            mainGuiFramebuffer = framebuffer;
            mainGuiFramebufferWidth = mc.getWindow().getWidth();
            mainGuiFramebufferHeight = mc.getWindow().getHeight();
        }
    }

    public static DirectContext getContext() {
        return context;
    }
}
