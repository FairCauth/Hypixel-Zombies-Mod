package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import net.minecraft.client.KeyMapping;

@ModuleInfo(name = "module.sprint", enable = true)
public class Sprint extends AbstractModule {
    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.gui.screen() != null) {
            return;
        }
        mc.options.keySprint.setDown(true);
    }
}
