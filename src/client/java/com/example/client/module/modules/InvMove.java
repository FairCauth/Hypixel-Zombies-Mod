package com.example.client.module.modules;

import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.mixin.KeyMappingAccessor;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.lwjgl.glfw.GLFW;

@ModuleInfo(name = {
        @Text(label = "InvMove", language = Language.English),
        @Text(label = "背包移动", language = Language.Chinese)
}, enable = false)
public class InvMove extends AbstractModule {
    private static InvMove instance;
    private static boolean controllingKeys;

    @SettingInfo(name = {
            @Text(label = "Jump", language = Language.English),
            @Text(label = "跳跃", language = Language.Chinese)
    })
    public static final BooleanSetting jump = new BooleanSetting(true);

    @SettingInfo(name = {
            @Text(label = "Sneak", language = Language.English),
            @Text(label = "潜行", language = Language.Chinese)
    })
    public static final BooleanSetting sneak = new BooleanSetting(true);

    @SettingInfo(name = {
            @Text(label = "Sprint", language = Language.English),
            @Text(label = "疾跑", language = Language.Chinese)
    })
    public static final BooleanSetting sprint = new BooleanSetting(true);

    public InvMove() {
        instance = this;
        registerSetting(jump, sneak, sprint);
    }

    /** Called by KeyboardInputMixin immediately before vanilla samples KeyMappings. */
    public static void updateMovementKeys() {
        boolean containerOpen = isActive()
                && mc.player != null
                && mc.screen instanceof AbstractContainerScreen<?>;

        if (!containerOpen) {
            if (controllingKeys) {
                // When returning to the game, keep keys that are still physically held.
                // For every other GUI, release them so chat/config screens never move the player.
                syncAll(mc.screen == null);
                controllingKeys = false;
            }
            return;
        }

        controllingKeys = true;
        sync(mc.options.keyUp, true);
        sync(mc.options.keyDown, true);
        sync(mc.options.keyLeft, true);
        sync(mc.options.keyRight, true);
        sync(mc.options.keyJump, jump.getValue());
        sync(mc.options.keyShift, sneak.getValue());
        sync(mc.options.keySprint, sprint.getValue());
    }

    @Override
    protected void onDisable() {
        if (!controllingKeys) return;
        syncAll(false);
        controllingKeys = false;
    }

    @Override
    public void cleanup() {
        onDisable();
    }

    private static boolean isActive() {
        return instance != null && instance.isEnable();
    }

    private static void syncAll(boolean readPhysicalState) {
        if (mc.options == null) return;
        sync(mc.options.keyUp, readPhysicalState);
        sync(mc.options.keyDown, readPhysicalState);
        sync(mc.options.keyLeft, readPhysicalState);
        sync(mc.options.keyRight, readPhysicalState);
        sync(mc.options.keyJump, readPhysicalState && jump.getValue());
        sync(mc.options.keyShift, readPhysicalState && sneak.getValue());
        sync(mc.options.keySprint, readPhysicalState && sprint.getValue());
    }

    private static void sync(KeyMapping mapping, boolean enabled) {
        mapping.setDown(enabled && isPhysicallyDown(mapping));
    }

    private static boolean isPhysicallyDown(KeyMapping mapping) {
        if (!mc.getWindow().isFocused()) return false;

        InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).zombiesmod$getBoundKey();
        if (key == InputConstants.UNKNOWN) return false;

        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
        }
        return false;
    }
}
