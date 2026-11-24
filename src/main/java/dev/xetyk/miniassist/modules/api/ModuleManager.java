package dev.xetyk.miniassist.modules.api;

import dev.xetyk.miniassist.config.ConfigManager;
import dev.xetyk.miniassist.modules.impl.combat.AimAssist;
import dev.xetyk.miniassist.modules.impl.combat.TriggerBot;
import dev.xetyk.miniassist.modules.impl.movement.FreeCam;
import dev.xetyk.miniassist.modules.impl.render.ClientHudModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();
    private static final Logger LOGGER = LoggerFactory.getLogger("MiniAssist");
    private static boolean initialized;

    private ModuleManager() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        MODULES.add(new AimAssist());
        MODULES.add(new TriggerBot());
        MODULES.add(new FreeCam());
        ClientHudModule hud = new ClientHudModule();
        hud.setEnabled(true);
        MODULES.add(hud);

        ConfigManager.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.save());
        // FreeCam owns a temporary camera entity and temporarily captures movement keys.
        // Always restore both when leaving a server/world, even if the player object is removed first.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            Module freeCam = get("FreeCam");
            if (freeCam != null && freeCam.isEnabled()) {
                try {
                    freeCam.setEnabled(false);
                } catch (RuntimeException exception) {
                    LOGGER.error("Could not clean up FreeCam during disconnect", exception);
                }
            }
            ConfigManager.save();
        }));
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            for (Module module : MODULES) {
                if (!module.isEnabled()) continue;
                try {
                    module.onPreTick();
                } catch (RuntimeException exception) {
                    disableFailedModule(module, client, exception);
                }
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(ModuleManager::onEndClientTick);
    }

    private static void onEndClientTick(MinecraftClient client) {
        boolean allowModuleBinds = client.currentScreen == null && client.getWindow() != null;
        boolean changed = false;
        long window = client.getWindow() == null ? 0L : client.getWindow().getHandle();

        for (Module module : MODULES) {
            try {
                if (module.pollKeybind(window, allowModuleBinds)) {
                    changed = true;
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("[MiniAssist] " + module.getName() + ": "
                                + (module.isEnabled() ? "ON" : "OFF")), true);
                    }
                }
            } catch (RuntimeException exception) {
                disableFailedModule(module, client, exception);
            }
        }
        if (changed) ConfigManager.save();

        if (client.player == null || client.world == null) return;
        for (Module module : MODULES) {
            if (!module.isEnabled()) continue;
            try {
                module.onTick();
            } catch (RuntimeException exception) {
                disableFailedModule(module, client, exception);
            }
        }
    }

    private static void disableFailedModule(Module module, MinecraftClient client, RuntimeException exception) {
        LOGGER.error("Module {} failed; disabling it to keep the client running", module.getName(), exception);
        try {
            module.setEnabled(false);
        } catch (RuntimeException cleanupException) {
            LOGGER.error("Cleanup also failed for module {}", module.getName(), cleanupException);
        }
        ConfigManager.save();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[MiniAssist] " + module.getName()
                    + " отключён из-за ошибки. Подробности — в latest.log."), true);
        }
    }

    public static List<Module> getModules() {
        return Collections.unmodifiableList(MODULES);
    }

    public static Module get(String name) {
        for (Module module : MODULES) {
            if (module.getName().equalsIgnoreCase(name)) return module;
        }
        return null;
    }
}
