package com.example.client.module;

import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;

import com.example.client.module.modules.*;
import lombok.Getter;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;

public class ModuleManager {
    @Getter
    private final ArrayList<AbstractModule> moduleList = new ArrayList<>();

    public ModuleManager() {
        add(new AutoSwitchWeapon(), new HideBlockingPlayer(), new RightClicker(), new Sprint(), new TargetHud());
        add(new NoFireEffect(), new TeammatesGlow(), new DPSCounter(), new Notification(), new NoGunFire());
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

    public void cleanup() {
        for (AbstractModule m : moduleList) {
            if (m.isEnable()) {
                m.toggle();
            }
            EventManager.unregister(m);
            m.cleanup();
        }
        moduleList.clear();
        EventManager.unregister(this);
    }
}
