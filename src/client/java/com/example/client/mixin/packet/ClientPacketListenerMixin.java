package com.example.client.mixin.packet;

import com.example.client.module.modules.NoGunFire;
import com.example.client.tracker.LatencyTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(
            method = "handleParticleEvent(Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void zombiesmod$handleParticleEvent(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (NoGunFire.shouldCancel(packet)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "handlePongResponse(Lnet/minecraft/network/protocol/ping/ClientboundPongResponsePacket;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void zombiesmod$handlePongResponse(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        if (LatencyTracker.onPong((ClientPacketListener) (Object) this, packet.time())) {
            ci.cancel();
        }
    }
}
