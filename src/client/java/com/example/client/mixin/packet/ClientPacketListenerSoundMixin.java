package com.example.client.mixin.packet;
import com.darkmagician6.eventapi.EventManager;
import com.example.client.events.SoundPacketEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerSoundMixin {
    @Inject(method = "handleSoundEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundPacket;)V", at = @At("HEAD"))
    private void zombiesmod$handleSoundEvent(ClientboundSoundPacket packet, CallbackInfo ci) {
        EventManager.call(new SoundPacketEvent(packet));
    }
}
