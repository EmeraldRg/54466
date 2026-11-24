package dev.xetyk.miniassist.modules.api;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Numeric setting used by ClickGUI sliders and keyboard-free fine adjustment. */
public final class NumberSetting {
    private final String name;
    private final String description;
    private final double min;
    private final double max;
    private final double step;
    private final BooleanSupplier visible;
    private double value;

    public NumberSetting(String name, String description, double value, double min, double max, double step) {
        this(name, description, value, min, max, step, () -> true);
    }

    public NumberSetting(String name, String description, double value, double min, double max,
                         double step, BooleanSupplier visible) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(step)
                || min > max || step <= 0.0) {
            throw new IllegalArgumentException("Invalid number setting range");
        }
        this.name = name;
        this.description = description;
        this.min = min;
        this.max = max;
        this.step = step;
        this.visible = visible;
        setValue(value);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isVisible() { return visible.getAsBoolean(); }
    public double getValue() { return value; }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }

    public void setValue(double next) {
        if (!Double.isFinite(next)) return;
        double clamped = Math.max(min, Math.min(max, next));
        double steps = Math.round((clamped - min) / step);
        value = Math.max(min, Math.min(max, min + steps * step));
    }

    public void setFromRatio(double ratio) {
        double clampedRatio = Math.max(0.0, Math.min(1.0, ratio));
        setValue(min + (max - min) * clampedRatio);
    }

    public double getRatio() {
        if (max <= min) return 0.0;
        return (value - min) / (max - min);
    }

    public void increase() { setValue(value + step); }
    public void decrease() { setValue(value - step); }

    public String getDisplayValue() {
        if (step >= 1.0) return String.format(Locale.ROOT, "%.0f", value);
        if (step >= 0.1) return String.format(Locale.ROOT, "%.1f", value);
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
