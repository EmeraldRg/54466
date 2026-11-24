package dev.xetyk.miniassist;

import dev.xetyk.miniassist.gui.MiniAssistMenuScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Supplier;

/** Global client binds; individual module binds are edited in ClickGUI and stored in config. */
public final class ClientKeybinds {
    private static final String CATEGORY = "key.category.miniassist.main";
    private static KeyBinding openMenuKey;
    private static KeyBinding hideKey;
    private static Supplier<MiniAssistMenuScreen> menuFactory;
    private static boolean hidden;
    private static boolean initialized;

    private ClientKeybinds() {}

    public static synchronized void init(Supplier<MiniAssistMenuScreen> factory) {
        menuFactory = Objects.requireNonNull(factory, "factory");
        if (initialized) return;
        openMenuKey = register("key.miniassist.open_menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        hideKey = register("key.miniassist.hide", GLFW.GLFW_KEY_G);
        ClientTickEvents.END_CLIENT_TICK.register(ClientKeybinds::onEndClientTick);
        initialized = true;
    }

    private static KeyBinding register(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                translationKey, InputUtil.Type.KEYSYM, keyCode, CATEGORY));
    }

    private static void onEndClientTick(MinecraftClient client) {
        while (hideKey.wasPressed()) hidden = !hidden;
        if (client.currentScreen != null) {
            while (openMenuKey.wasPressed()) { /* Screen handles Right Shift close. */ }
            return;
        }
        while (openMenuKey.wasPressed()) {
            if (menuFactory != null) client.setScreen(menuFactory.get());
        }
    }

    public static boolean isHidden() { return hidden; }
}
