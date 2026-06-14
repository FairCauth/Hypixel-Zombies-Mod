package com.example.client.events;

import com.darkmagician6.eventapi.events.callables.EventCancellable;
import lombok.Getter;
import net.minecraft.network.protocol.Packet;
@Getter
public class PacketEvent extends EventCancellable {
    private final Packet<?> packet;

    public PacketEvent(Packet<?> packet) {
        this.packet = packet;
    }
}
