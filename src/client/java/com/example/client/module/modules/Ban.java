package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;

@ModuleInfo(name = {
        @Text(label = "Ban", language = Language.English),
        @Text(label = "Ban", language = Language.Chinese)
}, enable = false)
public class Ban extends AbstractModule {
    @EventTarget
    public void onTICK (TickEvent event) {
        int slot = 11;

        if (mc.player != null
                && mc.getConnection() != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if(mc.player == entity) continue;
                if(mc.player.distanceTo(entity) < 10)
                    mc.getConnection().send(
                            new ServerboundAttackPacket(entity.getId())
                    );
            }
//            mc.getConnection().send(
//                    new ServerboundSetCarriedItemPacket(2)
//            );
//            mc.getConnection().send(
//                    new ServerboundSetCarriedItemPacket(5)
//            );
//            mc.getConnection().send(
//                    new ServerboundSetCarriedItemPacket(1)
//            );


        }
    }
}
