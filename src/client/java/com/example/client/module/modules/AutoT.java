package com.example.client.module.modules;

import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.notification.NotificationManager;

@ModuleInfo(name = {
        @Text(label = "AutoT", language = Language.English),
        @Text(label = "AutoT", language = Language.Chinese)
}, enable = false)
public class AutoT extends AbstractModule {
    public static String message = "\uE737\uE737\uE737\uE737>>T_T_T_T_T_T_T<<\uE737\uE737\uE737\uE737";

    @Override
    public void onNotificationEnabled() {
        NotificationManager.info("Send Chat", "TTT");
    }

    @Override
    public void onNotificationDisabled() {

    }

    @Override
    protected void onEnable() {
        super.onEnable();

        if (mc.player != null && mc.getConnection() != null) {
            mc.getConnection().sendChat(message);
        }

        // AutoT 是一次性动作模块，发送后立即恢复关闭状态。
        toggle();
    }

}
