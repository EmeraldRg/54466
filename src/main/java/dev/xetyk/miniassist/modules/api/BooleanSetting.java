package dev.xetyk.miniassist.modules.api;

import java.util.function.BooleanSupplier;

public final class BooleanSetting {
    private final String name;
    private final String description;
    private final BooleanSupplier visible;
    private boolean enabled;

    public BooleanSetting(String name, boolean enabled) {
        this(name, "", enabled, () -> true);
    }

    public BooleanSetting(String name, String description, boolean enabled) {
        this(name, description, enabled, () -> true);
    }

    public BooleanSetting(String name, String description, boolean enabled, BooleanSupplier visible) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.visible = visible;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isVisible() { return visible.getAsBoolean(); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void toggle() { this.enabled = !this.enabled; }
}
