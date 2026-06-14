package com.example.client.events;

import com.darkmagician6.eventapi.events.callables.EventCancellable;
import lombok.Getter;
import net.minecraft.network.chat.Component;

@Getter
public class ChatEvent extends EventCancellable {
    private final Component component;

    public ChatEvent(Component component) {
        this.component = component;
    }
}
