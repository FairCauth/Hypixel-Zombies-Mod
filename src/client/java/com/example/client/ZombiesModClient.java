package com.example.client;


import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;
import com.example.client.config.AutoSwitchWeaponConfig;
import com.example.client.config.ZombiesConfig;
import com.example.client.language.GuiText;
import com.example.client.data.ZombiesGuns;
import com.example.client.events.FabricEvents;
import com.example.client.events.KeyInputEvent;
import com.example.client.gui.ZombiesConfigScreen;
import com.example.client.module.AbstractModule;
import com.example.client.module.ModuleManager;
import com.example.client.tracker.ServerTracker;
import com.example.client.utils.ChatUtils;
import com.example.client.utils.IMinecraft;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class ZombiesModClient implements ClientModInitializer, IMinecraft {
	public static ModuleManager moduleManager;

	public static int guiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
	public static final ServerTracker serverTracker = new ServerTracker();
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

		if (mc.gui.screen() != null) {
			return;
		}
//		if(event.getKey() == GLFW.GLFW_KEY_O) {
//
//			ItemStack s = mc.player.getMainHandItem();
//			System.out.println("=== GUN DUMP ===");
//			System.out.println("name=" + s.getHoverName().getString()
//					+ "  count=" + s.getCount()
//					+ "  dmg=" + s.getDamageValue() + "/" + s.getMaxDamage());
//
//			var lore = s.get(net.minecraft.core.component.DataComponents.LORE);
//			if (lore != null) lore.lines().forEach(l -> System.out.println("  lore: " + l.getString()));
//
//			var custom = s.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
//			if (custom != null) System.out.println("  NBT: " + custom.copyTag());   // ★ 弹药很可能在这
//
//			System.out.println("  components: " + s.getComponents());
//		}
		if (event.getKey() == guiKey) {
			if (ZombiesConfigScreen.instance == null) {
				ZombiesConfigScreen.instance = new ZombiesConfigScreen(null);
			}
			ZombiesConfigScreen.instance.setParent(null);
			mc.gui.setScreen(ZombiesConfigScreen.instance);
		}

		// 模块快捷键：切换绑定了该键的模块
		for (AbstractModule m : moduleManager.getModuleList()) {
			if (m.getKey() != 0 && m.getKey() == event.getKey()) {
				m.toggle();
				ZombiesConfig.save();
			}
		}

		// 枪械快捷键：切换绑定了该键的枪的自动切换开关
		for (ZombiesGuns gun : ZombiesGuns.values()) {
			AutoSwitchWeaponConfig.GunSwitchSetting cfg = AutoSwitchWeaponConfig.get(gun);
			if (cfg.getKey() != 0 && cfg.getKey() == event.getKey()) {
				cfg.setEnabled(!cfg.isEnabled());
				ChatUtils.print(Component.literal(gun.getDisplayName()).withStyle(ChatFormatting.YELLOW)
						.append(GuiText.text(cfg.isEnabled() ? "chat.enabled" : "chat.disabled").copy()
								.withStyle(cfg.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED)));
				ZombiesConfig.save();
			}
		}

	}
}
