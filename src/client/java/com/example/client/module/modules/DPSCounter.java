package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.tracker.GameStat;
import com.example.client.tracker.GameStatTracker;
import com.example.client.tracker.ServerTracker;
import com.example.client.events.ChatEvent;
import com.example.client.events.RenderEvent;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.utils.*;
import com.example.client.utils.record.HitResult;
import lombok.Getter;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayDeque;

@ModuleInfo(name = {
        @Text(label = "DPS Counter", language = Language.English),
        @Text(label = "每秒伤害计算", language = Language.Chinese)
}, enable = true)
public class DPSCounter extends AbstractModule {
    private record DamageEntry(
            long timeMs,
            double damage
    ) { }

    private static final ArrayDeque<DamageEntry> DAMAGE_HISTORY = new ArrayDeque<>();
    private static final long DPS_WINDOW_MS = 1000L;
    @Getter
    private static double dps = 0.0D;
    private static double displayDps = 0.0D;
    private final ServerTracker serverTracker = new ServerTracker();

    @SettingInfo(name = {
            @Text(label = "Debug", language = Language.English),
            @Text(label = "Debug", language = Language.Chinese)
    })
    public static final BooleanSetting debug = new BooleanSetting(true);

    public DPSCounter() {
        registerSetting(debug);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!PlayerUtils.isInHypZombies()) {
            DAMAGE_HISTORY.clear();
            dps = 0.0D;
            return;
        }

        updateDps();
    }
    private static void updateDps() {
        long now = System.currentTimeMillis();

        while (!DAMAGE_HISTORY.isEmpty()) {
            DamageEntry first = DAMAGE_HISTORY.peekFirst();

            if (now - first.timeMs() > DPS_WINDOW_MS) {
                DAMAGE_HISTORY.removeFirst();
            } else {
                break;
            }
        }

        double total = 0.0D;

        for (DamageEntry entry : DAMAGE_HISTORY) {
            total += entry.damage();
        }

        dps = total;
        displayDps += (dps - displayDps) * 0.2D;
    }
    public static int getRoundedDps() {
        return (int) Math.round(dps);
    }
    @EventTarget
    public void onChat(ChatEvent event) {
        if (!PlayerUtils.isInHypZombies())
            return;

        String message = event.getComponent().getString();
        if (message.isEmpty())
            return;
        message = ZombiesUtils.cleanChat(message);
        if (message.contains(":"))
            return;
        if (!message.startsWith("+"))
            return;
        boolean doubleGold = GameStatTracker.isActive(GameStat.DOUBLE_GOLD);
        HitResult hit = serverTracker.confirmHit(message, doubleGold);
        if (hit == null) {
            return;
        }
        DAMAGE_HISTORY.addLast(new DamageEntry(
                System.currentTimeMillis(),
                hit.damage()
        ));
//        damageThisSecond += hit.damage();
        ServerTracker.debug(
                "Gun: " + hit.gun().getDisplayName() + " | Damage: " + hit.damage()
        );
    }
    @EventTarget
    public void onRender(RenderEvent event) {
        GuiGraphicsExtractor graphics = event.getGuiGraphicsExtractor();

        String text = "DPS: " + Math.round(displayDps);

        int textWidth = mc.font.width(text);
        int boxWidth = textWidth + 12;
        int boxHeight = 18;

        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int x = 2;
        int y = screenHeight - boxHeight - 17;

        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xAA000000);
        graphics.fill(x, y, x + boxWidth, y + 1, 0xFF555555);
        graphics.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF555555);
        graphics.fill(x, y, x + 1, y + boxHeight, 0xFF555555);
        graphics.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, 0xFF555555);

        graphics.text(
                mc.font,
                text,
                x + 6,
                y + 5,
                0xFFFFFFFF,
                true
        );
    }

}
