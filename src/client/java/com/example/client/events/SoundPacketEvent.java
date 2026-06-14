package com.example.client.events;

import com.darkmagician6.eventapi.events.callables.EventCancellable;
import lombok.Getter;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
@Getter
public class SoundPacketEvent extends EventCancellable {
    private final ClientboundSoundPacket packet;

    public SoundPacketEvent(ClientboundSoundPacket packet) {
        this.packet = packet;
    }
}
