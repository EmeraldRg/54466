package dev.xetyk.miniassist;

import dev.xetyk.miniassist.gui.MiniAssistMenuScreen;
import dev.xetyk.miniassist.hud.MiniAssistHud;
import dev.xetyk.miniassist.modules.api.ModuleManager;
import net.fabricmc.api.ClientModInitializer;

public final class MiniAssistClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        ClientKeybinds.init(MiniAssistMenuScreen::new);
        MiniAssistHud.init();
    }
}
