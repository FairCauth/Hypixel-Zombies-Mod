package com.example.client.events;

import com.darkmagician6.eventapi.EventManager;
import com.example.client.ZombiesModClient;
import com.example.client.tracker.GameStat;
import com.example.client.tracker.GameStatTracker;
import com.example.client.tracker.LatencyTracker;
import com.example.client.utils.IMinecraft;
import com.example.client.utils.PlayerUtils;
import com.example.client.utils.record.HitResult;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            HitResult hitResult = null;
            if (mc.player != null && PlayerUtils.isInHypZombies()) {
                boolean doubleGold = GameStatTracker.isActive(GameStat.DOUBLE_GOLD);
                hitResult = ZombiesModClient.serverTracker.confirmHit(message.getString(), doubleGold);
            }
            EventManager.call(new ChatEvent(message, hitResult));
        });
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            EventManager.call(new EntityLoadEvent(entity));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LatencyTracker.tick();
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
