package com.example.client.skia.state;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.*;

public class GLState {
    public void push() {
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glClearColor(0f, 0f, 0f, 0f);

        // Fix
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    public void pop() {
        RenderSystem.assertOnRenderThread();

        GlStateManager._glUseProgram(0);
        GL33.glUseProgram(0);

        GlStateManager._glBindVertexArray(0);
        GL33.glBindVertexArray(0);

        GL33.glBindSampler(0, 0);

        GlStateManager._disableBlend();
        GL11.glDisable(GL11.GL_BLEND);

        GlStateManager._blendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );
        GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );

        GL14.glBlendEquation(GL14.GL_FUNC_ADD);

        GlStateManager._colorMask(0xF);
        GL11.glColorMask(true, true, true, true);

        GlStateManager._depthMask(true);
        GL11.glDepthMask(true);

        GlStateManager._disableDepthTest();
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GlStateManager._disableScissorTest();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);


        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        GlStateManager._disableCull();
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(0xFF);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
//        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        // Keep GlStateManager's texture cache in sync with the actual OpenGL
        // binding. A raw glBindTexture(0) leaves Minecraft believing that its
        // font atlas is still bound, which makes later GUI text use a Skia
        // texture and produces the blue/rainbow discoloration.
        GlStateManager._bindTexture(0);
    }
}
