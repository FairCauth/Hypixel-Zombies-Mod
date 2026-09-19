package com.example.client.module;

import com.darkmagician6.eventapi.EventManager;

import com.example.client.module.modules.*;
import com.example.client.chams.ZombieChams;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;

public class ModuleManager {
    @Getter
    private final ArrayList<AbstractModule> moduleList = new ArrayList<>();

    public ModuleManager() {
        add(new AutoSwitchWeapon(), new HideBlockingPlayer(), new HideZombies(), new RightClicker(), new Sprint(), new TargetHud(), new EyeHeight());
        add(new NoFireEffect(), new TeammatesGlow(), new SidebarModification(), new DPSCounter(), new Notification(), new NoGunFire());
        add(new ZombieChams());
        add(new BadHeadshot());
//        add(new BlockTransparency());
        add(new WaveDisplay());
        add(new LightningRodQueue());
        add(new ReviveAura());
        add(new OldOneDetector());
        add(new PowerupPredictor());
        add(new StatsQuery());
        add(new NameProtect());
        add(new DamageNumbers());
        add(new HologramFix());
        add(new LiquidGlassTest());
        add(new Hud());
//        new com.example.client.tracker.AmmoTracker(); // 弹药跟踪样本（自注册到 EventManager）
        EventManager.register(this);
    }
    public AbstractModule getModule(String name) {
        for (AbstractModule abstractModule : moduleList) {
            if(abstractModule.getNameKey().equals(name)) return abstractModule;
        }
        return null;
    }


    private void add(AbstractModule... modules) {
        moduleList.addAll(Arrays.stream(modules).toList());
    }

}
