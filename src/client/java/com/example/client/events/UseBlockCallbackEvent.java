package com.example.client.events;

import com.darkmagician6.eventapi.events.callables.EventCancellable;
import lombok.Getter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
@Getter
public class UseBlockCallbackEvent extends EventCancellable {
    private final Player player;
    private final Level level;
    private final InteractionHand interactionHand;
    private final BlockHitResult blockHitResult;

    public UseBlockCallbackEvent(Player player, Level level, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        this.player = player;
        this.level = level;
        this.interactionHand = interactionHand;
        this.blockHitResult = blockHitResult;
    }
}
