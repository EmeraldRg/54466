package dev.xetyk.miniassist.modules.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MultiBooleanSetting {
    private final String name;
    private final String description;
    private final Map<String, Boolean> values = new LinkedHashMap<>();

    private MultiBooleanSetting(String name, String description, List<String> options) {
        this.name = name;
        this.description = description;
        for (String option : options) values.put(option, true);
    }

    public static MultiBooleanSetting create(String name, List<String> options) {
        return new MultiBooleanSetting(name, "", options);
    }

    public static MultiBooleanSetting create(String name, String description, List<String> options) {
        return new MultiBooleanSetting(name, description, options);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getOptions() { return new ArrayList<>(values.keySet()); }
    public boolean isSelected(String option) { return values.getOrDefault(option, false); }
    public void toggle(String option) { values.computeIfPresent(option, (k, v) -> !v); }
    public void set(String option, boolean value) { values.computeIfPresent(option, (k, v) -> value); }

    public List<String> getSelectedNames() {
        List<String> selected = new ArrayList<>();
        values.forEach((key, value) -> { if (value) selected.add(key); });
        return selected;
    }
}
