package com.example.client.events;

import com.darkmagician6.eventapi.EventManager;
import com.example.client.utils.IMinecraft;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public class FabricEvents implements IMinecraft {

    public static void register() {
//        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
//            System.out.println("右键了方块: " + hitResult.getBlockPos());
//            return InteractionResult.PASS;
//        });
//        UseItemCallback.EVENT.register((player, level, hand) -> {
//            System.out.println("玩家右键使用物品");
//
//            return InteractionResultHolder.pass(player.getItemInHand(hand));
//        });
//        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
//            System.out.println("右键了实体: " + entity.getName().getString());
//
//            return InteractionResult.PASS;
//        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (mc.player == null || mc.level == null) {
                return;
            }
            EventManager.call(new TickEvent());

        });
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("zombies-mod", "target_hud"),
                (graphics, deltaTracker) -> {
                    EventManager.call(new RenderEvent(graphics, deltaTracker));
                }
        );
    }
}
