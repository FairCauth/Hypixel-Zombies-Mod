package com.example.client;


import com.example.client.events.FabricEvents;
import com.example.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;

public class ZombiesModClient implements ClientModInitializer {
	public static ModuleManager moduleManager;
	public static boolean modEnabled = true;
	@Override
	public void onInitializeClient() {
		moduleManager = new ModuleManager();

		FabricEvents.register();

		ZombiesConfig.load();
	}

}