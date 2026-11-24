package dev.xetyk.miniassist.modules.api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Base class for client-side modules, including persistent, GUI-editable key binds. */
public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final Category category;
    private final String description;
    private final List<BooleanSetting> booleanSettings = new ArrayList<>();
    private final List<NumberSetting> numberSettings = new ArrayList<>();
    private final List<MultiBooleanSetting> multiSettings = new ArrayList<>();
    private boolean enabled;
    private int keyCode = GLFW.GLFW_KEY_UNKNOWN;
    private boolean keyWasDown;

    protected Module(String name, Category category, String description) {
        this.name = name;
        this.category = category;
        this.description = description;
    }

    protected <T extends BooleanSetting> T add(T setting) {
        booleanSettings.add(setting);
        return setting;
    }

    protected <T extends NumberSetting> T add(T setting) {
        numberSettings.add(setting);
        return setting;
    }

    protected MultiBooleanSetting add(MultiBooleanSetting setting) {
        multiSettings.add(setting);
        return setting;
    }

    public String getName() { return name; }
    public Category getCategory() { return category; }
    public String getDescription() { return description; }
    public List<BooleanSetting> getBooleanSettings() { return booleanSettings; }
    public List<NumberSetting> getNumberSettings() { return numberSettings; }
    public List<MultiBooleanSetting> getMultiSettings() { return multiSettings; }
    public boolean isEnabled() { return enabled; }
    public int getKeyCode() { return keyCode; }

    public String getBindName() {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return "NONE";
        return InputUtil.fromKeyCode(keyCode, 0).getLocalizedText().getString();
    }

    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode < 0 ? GLFW.GLFW_KEY_UNKNOWN : keyCode;
        this.keyWasDown = false;
    }

    /** Called once per client tick. Updates the latch even while a screen is open. */
    public boolean pollKeybind(long windowHandle, boolean allowToggle) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN || windowHandle == 0L) {
            keyWasDown = false;
            return false;
        }
        boolean down = GLFW.glfwGetKey(windowHandle, keyCode) == GLFW.GLFW_PRESS;
        boolean pressedNow = down && !keyWasDown;
        keyWasDown = down;
        if (allowToggle && pressedNow) {
            toggle();
            return true;
        }
        return false;
    }

    public void setEnabled(boolean value) {
        if (enabled == value) return;
        if (value) {
            enabled = true;
            try {
                onEnable();
            } catch (RuntimeException exception) {
                // Roll back partial resources (camera entities, temporary input state, etc.)
                // before exposing the failed module as disabled.
                try {
                    onDisable();
                } catch (RuntimeException cleanupException) {
                    exception.addSuppressed(cleanupException);
                } finally {
                    enabled = false;
                }
                throw exception;
            }
        } else {
            try {
                onDisable();
            } finally {
                enabled = false;
            }
        }
    }

    public void toggle() { setEnabled(!enabled); }

    protected void onEnable() {}
    protected void onDisable() {}

    /** Called at the beginning of the client tick, before player movement. */
    public void onPreTick() {}

    /** Called at the end of the client tick while the module is enabled. */
    public void onTick() {}
}
