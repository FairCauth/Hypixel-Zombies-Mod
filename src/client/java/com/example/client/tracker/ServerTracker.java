package com.example.client.tracker;

import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;
import com.example.client.ZombiesGuns;
import com.example.client.ZombiesModClient;
import com.example.client.ZombiesSpawnTable;
import com.example.client.events.ChatEvent;
import com.example.client.events.PacketEvent;
import com.example.client.events.SoundPacketEvent;
import com.example.client.events.TickEvent;
import com.example.client.module.AbstractModule;
import com.example.client.module.modules.DPSCounter;
import com.example.client.module.modules.Notification;
import com.example.client.utils.IMinecraft;
import com.example.client.utils.PlayerUtils;
import com.example.client.utils.TextUtils;
import com.example.client.utils.render.ToastUtils;
import com.example.client.utils.ZombiesUtils;
import com.example.client.utils.record.HitResult;
import com.example.client.utils.record.ShotRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerTracker implements IMinecraft {
    private int serverSelectedSlot = -1;
    private long nextShotId = 0;
    private static final ArrayDeque<ShotRecord> SHOTS = new ArrayDeque<>();

    public ServerTracker() {
        EventManager.register(this);
    }
    boolean roundStartSound = false, roundStartTitle = false;
    int currentRound = -1;
    long roundTime = 0L;
    public static String cleanNameText(String text) {
        if (text == null) return "";

        return text
                .replaceAll("(?i)§[0-9A-FK-ORX]", "")
                .trim();
    }
    private boolean sound = false;
    private boolean probableShopping = false;
    private boolean probableDoubleGold = false;
    @EventTarget
    public void onSoundTrack(SoundPacketEvent event) {
        if(!PlayerUtils.isInHypZombies()) return;
        ClientboundSoundPacket packet = event.getPacket();
        String soundName = getSoundName(packet);
        //minecraft:entity.wither.spawn round start
        //minecraft:entity.horse.armor double gold 30s
        //minecraft:entity.zombie_horse.death insta kill 10s
        if (soundName.equals("minecraft:entity.elder_guardian.curse")) {
            //1.3968254
//            mc.gui.getChat().addClientSystemMessage(Component.literal("[elder_guardian.curse] " + packet.getPitch()));
            roundStartSound = true;
        }
        if (soundName.equals("minecraft:entity.wither.spawn")) {
            roundStartSound = true;
        }
        if (soundName.equals("minecraft:entity.horse.armor")) {
            sound = true;
        }
        if (soundName.equals("minecraft:entity.zombie_horse.death")) {
            GameStatTracker.activate(GameStat.INSTA_KILL);
        }
        //debug(text);
    }
    @EventTarget
    public void onTick(TickEvent event) {


        if(!PlayerUtils.isInHypZombies()) {
            roundStartTitle = false;
            roundStartSound = false;
            sound = false;
            probableDoubleGold = false;
            probableShopping = false;
            TeammateTracker.clear();
            GameStatTracker.clear();
            return;
        }
        GameStatTracker.onTick();
        TeammateTracker.syncTeammates();
        if(roundStartTitle && roundStartSound) {
            roundStartTitle = false;
            roundStartSound = false;

            int lastRound = currentRound - 1;

            long time = System.currentTimeMillis() - roundTime;
            String timeStr = formatSeconds((int) (time / 1000L));
            Component message = Component.literal("You completed ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("Round " + lastRound).withStyle(ChatFormatting.RED))
                    .append(Component.literal(" in ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(timeStr)).withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("!").withStyle(ChatFormatting.YELLOW));
            AbstractModule notification = ZombiesModClient.moduleManager.getModule("Notification");
            if(notification.isEnable() && Notification.roundRecorder.getValue()) {
                ToastUtils.show("Round Recorder", message);
            }

            roundTime = System.currentTimeMillis();
            debug("回合开始 " + currentRound);

            if(notification.isEnable() && Notification.roundSuggest.getValue()) {

                ToastUtils.show("Round " + currentRound, ZombiesSpawnTable.getMonsters(currentRound), 8000);
                ToastUtils.show("Round " + currentRound, ZombiesSpawnTable.getLocation(currentRound), 8000);
            }
        }

        if(sound && probableShopping) {
            GameStatTracker.activate(GameStat.SHOPPING_SPREE);
            sound = false;
            probableDoubleGold = false;
            probableShopping = false;
        }
        if(sound && probableDoubleGold) {
            GameStatTracker.activate(GameStat.DOUBLE_GOLD);
            sound = false;
            probableDoubleGold = false;
            probableShopping = false;
        }
    }
    public static String formatSeconds(int totalSeconds) {
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }

        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        if (minutes > 0) {
            return minutes + "min " + seconds + "s";
        }

        return seconds + "s";
    }
    private static String getSoundName(ClientboundSoundPacket packet) {
        String raw = String.valueOf(packet.getSound());

        int start = raw.indexOf("location=");

        if (start != -1) {
            start += "location=".length();

            int end = raw.indexOf(",", start);

            if (end == -1) {
                end = raw.indexOf("]", start);
            }

            if (end != -1) {
                return raw.substring(start, end);
            }
        }

        return raw;
    }

    @EventTarget
    public void onChatTrack(ChatEvent event) {
        String message = event.getComponent().getString();
//        message = cleanNameText(message);
    }
    @EventTarget
    public void onPacketTrack(PacketEvent event) {
        Packet<?> packet = event.getPacket();
        if(!PlayerUtils.isInHypZombies()) return;

        //subtitle check
        if (packet instanceof ClientboundSetSubtitleTextPacket(Component text)) {
            String subtitle = text.getString();
//            System.out.println(subtitle);
            if(subtitle.startsWith("§5")) {//dark purple
                probableShopping = true;
            } else if (subtitle.startsWith("§6")) {//gold
                probableDoubleGold = true;
            }
//            if(subtitle.contains("is down in")) {
//                int index = subtitle.indexOf("is down in");
//                String leftText = subtitle.substring(0, index).trim();
//                String downName = cleanNameText(leftText);
//                System.out.println("1111111111111111111111111 " + downName);
//                mc.gui.getChat().addClientSystemMessage(Component.literal("Down player name " + downName));
//                TeammateTracker.setDown(downName, true);
//            }
            return;
        }
        if (packet instanceof ClientboundSetTitleTextPacket(Component text)) {
            if(PlayerUtils.isInHypZombies()) {
                String currentTitle = text.getString();
                Matcher matcher = Pattern.compile("\\d+").matcher(currentTitle);
                if (!matcher.find())
                    return;

                int round = Integer.parseInt(matcher.group());
                debug("Round " + round);
                roundStartTitle = true;
                currentRound = round;
            }
            return;
        }

        if (packet instanceof ServerboundSetCarriedItemPacket setSlotPacket) {
            serverSelectedSlot = setSlotPacket.getSlot();
            return;
        }

        if (packet instanceof ServerboundUseItemPacket useItemPacket) {
            if (useItemPacket.getHand() != InteractionHand.MAIN_HAND) {
                return;
            }
            int slot = serverSelectedSlot;
            if (slot < 0 || slot > 8) {
                return;
            }
            ItemStack stack = mc.player.getInventory().getItem(slot);

            if (stack.isEmpty()) {
                return;
            }

            ZombiesGuns gun = ZombiesGuns.getGunOrNull(stack);

            if (gun == null) {
                return;
            }

            int ultimateLevel = ZombiesUtils.getUltimateLevel(stack, gun);
            ShotRecord record = new ShotRecord(
                    nextShotId++,
                    gun,
                    ultimateLevel,
                    slot,
                    stack.copy(),
                    System.currentTimeMillis()
            );

            SHOTS.addLast(record);
            cleanup();
        }
    }

    public HitResult confirmHit(String chatMessage, boolean doubleGold) {
        if (chatMessage == null || chatMessage.isEmpty()) {
            return null;
        }

        String message = ZombiesUtils.cleanChat(chatMessage);

        if (message.contains(":")) {
            return null;
        }

        if (!message.startsWith("+")) {
            return null;
        }

        int gold = ZombiesUtils.getGoldFromChat(message);

        if (gold <= 0) {
            return null;
        }

        if (doubleGold) {
            gold /= 2;
        }

        boolean critical = ZombiesUtils.isCritical(message);

        ShotRecord shot = findMatchingShot(gold, critical);

        if (shot == null) {
            debug("HIT but no matching shot | gold=" + gold + " | critical=" + critical);
            return null;
        }

        double damage = shot.gun().getDamageByUltimateLevel(shot.ultimateLevel());

        return new HitResult(
                shot.id(),
                shot.gun(),
                shot.ultimateLevel(),
                shot.slot(),
                gold,
                critical,
                damage,
                System.currentTimeMillis() - shot.timeMs()
        );
    }

    //霰弹发射一次会获得多次金币反馈
    private boolean isMultiHitGun(ZombiesGuns gun) {
        return gun == ZombiesGuns.Shotgun
                || gun == ZombiesGuns.Double_Barrel_Shotgun || gun == ZombiesGuns.Zombie_Zapper;
    }

    public static void debug(String text) {
        if (mc.player == null) {
            return;
        }
        if (DPSCounter.debug.getValue())
            mc.gui.getChat().addClientSystemMessage(Component.literal("[ShotPacket] " + text));
    }

    private ShotRecord findMatchingShot(int gold, boolean critical) {
        cleanup();

        Iterator<ShotRecord> iterator = SHOTS.iterator();

        while (iterator.hasNext()) {
            ShotRecord shot = iterator.next();

            int targetGold = critical
                    ? shot.gun().getCriticalGold()
                    : shot.gun().getGold();

            if (targetGold == gold) {
                //不是霰弹枪 删除这次开枪记录
                if (!isMultiHitGun(shot.gun())) {
                    iterator.remove();
                }
                //霰弹枪让他自己超时remove
                return shot;
            }
        }

        return null;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();

        while (!SHOTS.isEmpty()) {
            ShotRecord first = SHOTS.peekFirst();

            //500ms没没反馈就算作没打中
            if (now - first.timeMs() > 500L) {
                SHOTS.removeFirst();
            } else {
                break;
            }
        }
    }
}
