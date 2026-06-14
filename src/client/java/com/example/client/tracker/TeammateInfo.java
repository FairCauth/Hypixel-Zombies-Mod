package com.example.client.tracker;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

@Getter
@Setter
public class TeammateInfo {
    /** 主键：计分板里的真实用户名（语言无关，倒地也不会变） */
    private final String name;
    /** 计分板里的金币数 */
    private long gold;
    /** 账号 UUID，解析到实体时填充（画头颅用，可为 null） */
    private UUID uuid;
    private boolean blocking;
    private boolean isDown;
    /** 当前用于渲染的实体：活着=真玩家，倒地=Hypixel 假人，离屏=null */
    private transient Player renderEntity;

    public TeammateInfo(String name) {
        this.name = name;
    }

    public static TeammateInfo[] teammates = new TeammateInfo[0];
}
