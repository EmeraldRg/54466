package dev.xetyk.miniassist.modules.impl.render;

import dev.xetyk.miniassist.modules.api.BooleanSetting;
import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import org.lwjgl.glfw.GLFW;

/** Configurable collection of compact Nursultan-inspired HUD widgets. */
public final class ClientHudModule extends Module {
    private final BooleanSetting watermark = add(new BooleanSetting(
            "Логотип и статус", "Логотип клиента, FPS и ping", true));
    private final BooleanSetting activeModules = add(new BooleanSetting(
            "Активные модули", "Список включённых функций справа", true));
    private final BooleanSetting coordinates = add(new BooleanSetting(
            "Координаты и скорость", "XYZ, направление и скорость игрока", true));
    private final BooleanSetting armor = add(new BooleanSetting(
            "Броня", "Иконки брони и её прочность", true));
    private final BooleanSetting effects = add(new BooleanSetting(
            "Эффекты", "Активные эффекты и время действия", true));
    private final BooleanSetting target = add(new BooleanSetting(
            "Target HUD", "Цель, здоровье, дистанция и ping", true));
    private final BooleanSetting keystrokes = add(new BooleanSetting(
            "Keystrokes", "WASD, прыжок и кнопки мыши", true));

    public ClientHudModule() {
        super("HUD", Category.RENDER, "Настраиваемые игровые панели MiniAssist");
        setKeyCode(GLFW.GLFW_KEY_UNKNOWN);
    }

    public boolean showWatermark() { return watermark.isEnabled(); }
    public boolean showActiveModules() { return activeModules.isEnabled(); }
    public boolean showCoordinates() { return coordinates.isEnabled(); }
    public boolean showArmor() { return armor.isEnabled(); }
    public boolean showEffects() { return effects.isEnabled(); }
    public boolean showTarget() { return target.isEnabled(); }
    public boolean showKeystrokes() { return keystrokes.isEnabled(); }
}
