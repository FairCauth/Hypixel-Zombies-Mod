package com.example.client.mixin.packet;

import com.darkmagician6.eventapi.EventManager;
import com.example.client.events.PacketEvent;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD")
    )
    private void zombiesmod$channelRead0(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (packet == null) {
            return;
        }
        EventManager.call(new PacketEvent(packet));
    }
    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD")
    )
    private void zombiesmod$send(Packet<?> packet, CallbackInfo ci) {
        EventManager.call(new PacketEvent(packet));
    }
}
