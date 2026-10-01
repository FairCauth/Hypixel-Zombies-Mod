package com.example.client.tracker;

import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;
import com.example.client.*;
import com.example.client.data.PowerupPredictor;
import com.example.client.data.ZombiesGuns;
import com.example.client.data.ZombiesSpawnTable;
import com.example.client.events.ChatEvent;
import com.example.client.events.PacketEvent;
import com.example.client.events.SoundPacketEvent;
import com.example.client.events.TickEvent;
import com.example.client.language.GuiText;
import com.example.client.module.AbstractModule;
import com.example.client.module.modules.DPSCounter;
import com.example.client.module.modules.Notification;
import com.example.client.module.modules.TargetHud;
import com.example.client.utils.*;
import com.example.client.utils.record.HitResult;
import com.example.client.utils.record.ShotRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.swing.*;
import java.util.ArrayDeque;
import java.util.Iterator;
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

    public static int currentGold = 0;
    public static int currentRound = -1;
    public static long roundTime = 0L;
    private long lastRoundStartGold = -1; // 上一回合开始时我的计分板金币（用于算上回合变化）

    public static MovePlayerRecord serverPlayer = null;
    public static LivingEntity shootTarget = null;

    private boolean sound = false;
    private boolean probableShopping = false;
    private boolean probableDoubleGold = false;
    private boolean probableMaxAmmo = false;
    private boolean probableInstaKill = false;
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
            //double gold or ss
            sound = true;
        }

        if (soundName.equals("minecraft:entity.wolf.shake")) {
            //max ammo
            probableMaxAmmo = true;
        }

        if (soundName.equals("minecraft:block.anvil.use")) {
            //carpenter

        }

        if (soundName.equals("minecraft:entity.zombie_horse.death")) {
            //insta kill
            probableInstaKill = true;
        }
        //debug(text);
    }
    public static final PowerupDetector powerup = new PowerupDetector();
    @EventTarget
    public void onTick(TickEvent event) {
        if(mc.player == null || mc.level == null) return;

        if(!PlayerUtils.isInHypZombies()) {
            roundStartTitle = false;
            roundStartSound = false;
            sound = false;
            probableDoubleGold = false;
            probableShopping = false;
            probableMaxAmmo = false;
            probableInstaKill = false;
            TeammateTracker.clear();
            GameStatTracker.clear();
            lastRoundStartGold = -1; // 离开/换局，重置金币快照
            roundTime = 0L;
            currentRound = -1;
            // 注意：不在这里 reset powerup！倒地/过场会让挖掘疲劳瞬间消失被误判为"离开"，
            // 从而清掉已锁定的道具模式（INSTA 表只到 23 回合，清了就再也锁不回 → 永远 ?）。
            // 道具预测改为只在"新一局开始"时重置（见 onPacketTrack 回合标题处）。
            return;
        }
        powerup.tick();
        GameStatTracker.onTick();
        TeammateTracker.syncTeammates();
        if(roundStartTitle && roundStartSound) {
            roundStartTitle = false;
            roundStartSound = false;

            int lastRound = currentRound - 1;

            if (currentRound == 1) {
                roundTime = System.currentTimeMillis();
                return;
            }

            // 上回合金币变化：从计分板读我当前金币，与上回合开始时的快照对比
            long myGold = getMyScoreboardGold();
            if (myGold >= 0) {
                if (lastRound >= 1 && lastRoundStartGold >= 0) {
                    long delta = myGold - lastRoundStartGold;
                    String sign = delta >= 0 ? "+" : "-";
                            ChatUtils.print(GuiText.text("toast.round_gold",
                                Component.literal(String.valueOf(lastRound)).withStyle(ChatFormatting.RED),
                                Component.literal(sign + String.format("%,d", Math.abs(delta))).withStyle(ChatFormatting.GOLD))
                                .copy().withStyle(ChatFormatting.RED));
                }
                lastRoundStartGold = myGold;
            }

            long time = System.currentTimeMillis() - roundTime;
            String timeStr = formatSeconds((int) (time / 1000L));
                Component message = GuiText.text("toast.completed").copy().withStyle(ChatFormatting.AQUA)
                    .append(GuiText.text("toast.round", lastRound).copy().withStyle(ChatFormatting.RED))
                    .append(GuiText.text("toast.in").copy().withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(timeStr).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("!").withStyle(ChatFormatting.YELLOW));
            AbstractModule notification = ZombiesModClient.moduleManager.getModule("module.notification");
            if(notification.isEnable() && Notification.roundRecorder.getValue()) {
                ChatUtils.print(Component.literal("[")
                    .append(GuiText.text("toast.round_recorder"))
                    .append(Component.literal("] "))
                    .append(message));
            }

            roundTime = System.currentTimeMillis();
            if (notification.isEnable() && Notification.aaRoundChat.getValue()
                    && ZombiesUtils.getMap() == ZombiesMap.ALIEN_ARCADIUM) {
                ZombiesSpawnTable.SpawnInfo info = ZombiesSpawnTable.get(currentRound);
                if (info != null) {
                    Component spawnMessage = GuiText.text("toast.round", currentRound).copy()
                            .append(Component.literal(" "))
                            .append(GuiText.text("toast.monsters", info.monsters()))
                            .append(Component.literal(" "))
                            .append(GuiText.text("toast.location", info.location()));
                    mc.player.connection.sendChat(spawnMessage.getString());
                }
            }
//            debug("回合开始 " + currentRound);

            if(notification.isEnable() && Notification.roundSuggest.getValue()) {
                if(ZombiesUtils.getMap() == ZombiesMap.ALIEN_ARCADIUM) {
                    int nextRound = currentRound + 1;
                    ZombiesSpawnTable.SpawnInfo info = ZombiesSpawnTable.get(nextRound);
                    if (info != null) {
                        Component spawnMessage = GuiText.text("toast.monsters", info.monsters()).copy()
                            .withStyle(ChatFormatting.GREEN)
                                .append(Component.literal("\n"))
                                .append(GuiText.text("toast.location", info.location()).copy().withStyle(ChatFormatting.GREEN));
                        ChatUtils.print(GuiText.text("toast.next_round", nextRound).copy().withStyle(ChatFormatting.GREEN)
                            .append(Component.literal("\n"))
                            .append(spawnMessage));
                    }
                }

            }
//            var pred = powerup.getPredictor();
//            for (var t : PowerupPredictor.Type.values()) {
//                if (pred.isPowerupRound(t, currentRound))
//                    ToastUtils.show("Powerup", t + " 本回合!", 5000);
//                else {
//                    int next = pred.nextRound(t, currentRound);
//                    if (next > 0) ToastUtils.show("Powerup", t + " 下次 R" + next, 5000);
//                }
//            }
        }
        if(probableInstaKill) {
            probableInstaKill = false;
            powerup.onActivationSound(PowerupPredictor.Type.INSTA);
            GameStatTracker.activate(GameStat.INSTA_KILL);
        }
        if(probableMaxAmmo) {
            probableMaxAmmo = false;
            powerup.onActivationSound(PowerupPredictor.Type.MAX);
            GameStatTracker.announceCollected(PowerupPredictor.Type.MAX);
        }
        if(sound && probableShopping) {
            powerup.onActivationSound(PowerupPredictor.Type.SS);
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

//        if(!PlayerUtils.isInHypZombies()) return;
//        for (Entity e : mc.level.entitiesForRendering()) {
//            if (!(e instanceof ArmorStand stand)) continue;
//            Component name = stand.getCustomName();
//            if (name == null) continue;              // 没名字的(装备架等)跳过
//
//            String text  = name.getString();         // 完整文字(含子节点；带语言)
//            TextColor c  = firstColor(name);         // 第一个颜色
//            int rgb      = (c == null) ? -1 : c.getValue();   // 0xRRGGBB
//            //#5555FF Max ammo
//            //#FF5555 insta kill
              //#AA00AA ss
//            System.out.println("[STAND] '" + text + "'  color="
//                    + (rgb == -1 ? "none" : String.format("#%06X", rgb)));
//        }
    }
    private static TextColor firstColor(Component c) {
        if (c.getStyle().getColor() != null) return c.getStyle().getColor();
        for (Component sib : c.getSiblings()) {
            TextColor cc = firstColor(sib);
            if (cc != null) return cc;
        }
        return null;
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

    /** 从计分板侧栏读我自己当前的金币；-1 = 没读到。 */
    private static long getMyScoreboardGold() {
        if (mc.player == null) return -1;
        String me = mc.player.getName().getString();
        var players = ScoreboardUtils.getZombiesPlayers();
        for (ScoreboardUtils.ScorePlayer sp : players) {
            if (sp.name().equalsIgnoreCase(me)) return sp.gold();
        }
        for (ScoreboardUtils.ScorePlayer sp : players) { // 兜底：名字可能带前缀
            if (sp.name().toLowerCase().contains(me.toLowerCase())) return sp.gold();
        }
        return -1;
    }
    @EventTarget
    public void onPacketTrack(PacketEvent event) {
        Packet<?> packet = event.getPacket();
//        if(!PlayerUtils.isInHypZombies()) return;

        //subtitle check
        if (packet instanceof ClientboundSetSubtitleTextPacket(Component text)) {
            if(!PlayerUtils.isInHypZombies()) return;
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
            if(ZombiesUtils.getMap() != ZombiesMap.NULL) {
                String currentTitle = text.getString();
                Matcher matcher = Pattern.compile("\\d+").matcher(currentTitle);
                if (!matcher.find())
                    return;

                int round = Integer.parseInt(matcher.group());
                debug(GuiText.textString("debug.round", round));
                roundStartTitle = true;
                // 只在"进入第 1 回合（新一局）"时清道具预测。
                // 不用 round<currentRound：带数字的非回合标题（道具/特效等）会误触发清空。
                if (round == 1) {
                    powerup.reset();
                    roundTime = System.currentTimeMillis();
                }
                currentRound = round;
            }
            return;
        }

        if (packet instanceof ServerboundSetCarriedItemPacket setSlotPacket) {
            serverSelectedSlot = setSlotPacket.getSlot();
            return;
        }
        //ClientboundContainerSetSlotPacket
        if (packet instanceof ServerboundMovePlayerPacket movePlayerPacket) {
            double px = serverPlayer != null ? serverPlayer.x() : mc.player.getX();
            double py = serverPlayer != null ? serverPlayer.y() : mc.player.getY();
            double pz = serverPlayer != null ? serverPlayer.z() : mc.player.getZ();
            float pxr = serverPlayer != null ? serverPlayer.xRot() : mc.player.getXRot();
            float pyr = serverPlayer != null ? serverPlayer.yRot() : mc.player.getYRot();
            serverPlayer = new MovePlayerRecord(
                    movePlayerPacket.getX(px),
                    movePlayerPacket.getY(py),
                    movePlayerPacket.getZ(pz),
                    movePlayerPacket.getXRot(pxr),
                    movePlayerPacket.getYRot(pyr)
            );
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

            shootTarget = PlayerUtils.raycastTarget(serverPlayer, 64, TargetHud::isValidTarget);

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
            debug(GuiText.textString("debug.hit_no_matching_shot", gold, critical));
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
        if (DPSCounter.debug.getValue()) {
            ChatUtils.print(text);
        }
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
