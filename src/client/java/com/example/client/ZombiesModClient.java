package com.example.client;


import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;
import com.example.client.config.ZombiesConfig;
import com.example.client.events.FabricEvents;
import com.example.client.events.KeyInputEvent;
import com.example.client.gui.ZombiesConfigScreen;
import com.example.client.module.ModuleManager;
import com.example.client.utils.IMinecraft;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public class ZombiesModClient implements ClientModInitializer, IMinecraft {
	public static ModuleManager moduleManager;

	public static int guiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
	@Override
	public void onInitializeClient() {
		moduleManager = new ModuleManager();

		FabricEvents.register();

		ZombiesConfig.load();
		EventManager.register(this);
	}
	@EventTarget
	public void onKey(KeyInputEvent event) {
		if (mc.player == null || mc.level == null) {
			return;
		}

		if (event.getAction() != GLFW.GLFW_PRESS) {
			return;
		}

		if (mc.screen != null) {
			return;
		}

		if (event.getKey() == guiKey) {
			if (ZombiesConfigScreen.instance == null) {
				ZombiesConfigScreen.instance = new ZombiesConfigScreen(null);
			}
			ZombiesConfigScreen.instance.setParent(null);
			mc.setScreen(ZombiesConfigScreen.instance);
		}
	}
}