package com.example.client.events;

import com.darkmagician6.eventapi.events.callables.EventCancellable;
import lombok.Getter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
@Getter
public class RenderEvent extends EventCancellable {
    private final GuiGraphicsExtractor guiGraphicsExtractor;
    private final DeltaTracker deltaTracker;

    public RenderEvent(GuiGraphicsExtractor guiGraphicsExtractor, DeltaTracker deltaTracker) {
        this.guiGraphicsExtractor = guiGraphicsExtractor;
        this.deltaTracker = deltaTracker;
    }
}
