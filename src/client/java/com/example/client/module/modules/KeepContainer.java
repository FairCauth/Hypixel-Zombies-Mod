package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.KeyInputEvent;
import com.example.client.events.PacketEvent;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.lwjgl.glfw.GLFW;

/**
 * LiquidBounce Legacy KeepContainer 的现代版本实现：
 * 保存最后打开的容器、取消客户端关闭、Insert 重开、服务端关闭时作废。
 */
@ModuleInfo(name = {
        @Text(label = "KeepContainer", language = Language.English),
        @Text(label = "保留容器", language = Language.Chinese)
}, enable = false)
public class KeepContainer extends AbstractModule {
    private static final int HEARTBEAT_INTERVAL_TICKS = 30;

    private static KeepContainer instance;
    private static AbstractContainerScreen<?> container;
    private static boolean hidden;
    private static boolean restoring;
    private static int heartbeatCounter;
    private static int allowCloseContainerId = -1;

    @SettingInfo(name = {
            @Text(label = "Hide Window", language = Language.English),
            @Text(label = "隐藏窗口", language = Language.Chinese)
    })
    public static final BooleanSetting hideWindow = new BooleanSetting(true);

    @SettingInfo(name = {
            @Text(label = "Only While Crouching", language = Language.English),
            @Text(label = "仅潜行时隐藏", language = Language.Chinese)
    })
    public static final BooleanSetting onlyWhileCrouching = new BooleanSetting(false);

    @SettingInfo(name = {
            @Text(label = "Container Heartbeat", language = Language.English),
            @Text(label = "容器心跳", language = Language.Chinese)
    })
    public static final BooleanSetting containerHeartbeat = new BooleanSetting(true);

    public KeepContainer() {
        instance = this;
        registerSetting(hideWindow, onlyWhileCrouching, containerHeartbeat);
    }

    /**
     * 对应 Raven Hide Window 的 GuiOpenEvent：
     * 外部容器可在打开瞬间被替换为 null；请求打开玩家背包时则恢复隐藏容器。
     */
    public static Screen transformScreen(Screen requested) {
        if (!isActive() || restoring || mc.player == null) return requested;

        if (requested instanceof InventoryScreen
                && hidden
                && isContainerValid()) {
            hidden = false;
            return container;
        }

        if (!(requested instanceof AbstractContainerScreen<?> containerScreen)
                || requested instanceof InventoryScreen) {
            return requested;
        }

        container = containerScreen;
        hidden = false;

        boolean shouldHide = hideWindow.getValue()
                && (!onlyWhileCrouching.getValue() || mc.player.isShiftKeyDown());
        // 与 Raven 一致：一个容器界面之上又打开新容器时，不静默吞掉新界面。
        if (shouldHide && !(mc.screen instanceof AbstractContainerScreen<?>)) {
            hidden = true;
            return null;
        }
        return requested;
    }

    /** 供 LocalPlayer mixin 判断是否需要保留现代版本的本地 containerMenu。 */
    public static boolean shouldKeepMenu(AbstractContainerMenu menu) {
        boolean keep = isActive()
                && container != null
                && container.getMenu() == menu;
        if (!keep) return false;

        // OceanClient 行为：按住任意 Shift 再关闭时，真正结束容器会话。
        if (isPhysicalShiftDown()) {
            allowCloseContainerId = menu.containerId;
            clearContainer(false);
            return false;
        }

        hidden = true;
        return keep;
    }

    /** 对应 LB 的固定 Insert 重开快捷键。 */
    @EventTarget
    public void onKey(KeyInputEvent event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_INSERT) return;
        reopenContainer();
    }

    /** OceanClient 的 30 tick C0E 心跳在现代协议中的等价实现。 */
    @EventTarget
    public void onTick(TickEvent event) {
        if (!containerHeartbeat.getValue() || !isContainerValid()) {
            heartbeatCounter = 0;
            return;
        }

        if (++heartbeatCounter < HEARTBEAT_INTERVAL_TICKS) return;
        heartbeatCounter = 0;
        sendHeartbeat();
    }

    /** 对应 LB 的 C0D/S2E 处理。 */
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof ServerboundContainerClosePacket packet) {
            int containerId = packet.getContainerId();
            if (containerId == allowCloseContainerId) {
                allowCloseContainerId = -1;
                return;
            }

            if (container != null && containerId == container.getMenu().containerId) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getPacket() instanceof ClientboundContainerClosePacket packet
                && container != null
                && packet.getContainerId() == container.getMenu().containerId) {
            clearContainer();
        }
    }

    /** 对应 LB：关闭模块时补发最后一个容器的关闭包并清空缓存。 */
    @Override
    protected void onDisable() {
        AbstractContainerScreen<?> saved = container;
        clearContainer();

        if (saved == null || mc.player == null) return;
        AbstractContainerMenu menu = saved.getMenu();

        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundContainerClosePacket(menu.containerId));
        }
        if (mc.player.containerMenu == menu) {
            mc.player.containerMenu = mc.player.inventoryMenu;
        }
    }

    @Override
    public void cleanup() {
        onDisable();
    }

    private static boolean isActive() {
        return instance != null && instance.isEnable();
    }

    private static boolean isContainerValid() {
        if (container == null || mc.player == null
                || mc.player.containerMenu != container.getMenu()) {
            clearContainer();
            return false;
        }
        return true;
    }

    private static void reopenContainer() {
        if (!isContainerValid()) return;

        hidden = false;
        container.clearDraggingState();
        restoring = true;
        try {
            mc.setScreen(container);
        } finally {
            restoring = false;
        }
    }

    private static void clearContainer() {
        clearContainer(true);
    }

    private static void clearContainer(boolean resetAllowedClose) {
        container = null;
        hidden = false;
        restoring = false;
        heartbeatCounter = 0;
        if (resetAllowedClose) {
            allowCloseContainerId = -1;
        }
    }

    private static void sendHeartbeat() {
        if (mc.player == null || mc.getConnection() == null || container == null) return;

        AbstractContainerMenu menu = container.getMenu();
        // PICKUP(-999) 在服务端表示点击窗口外；光标非空时会丢物品，因此只在为空时发送。
        if (!menu.getCarried().isEmpty()) return;

        mc.getConnection().send(new ServerboundContainerClickPacket(
                menu.containerId,
                menu.getStateId(),
                (short) -999,
                (byte) 0,
                ContainerInput.PICKUP,
                new Int2ObjectOpenHashMap<>(),
                HashedStack.EMPTY
        ));
    }

    private static boolean isPhysicalShiftDown() {
        if (!mc.getWindow().isFocused()) return false;
        long window = mc.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
}
