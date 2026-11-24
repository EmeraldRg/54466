package dev.xetyk.miniassist.util;

/**
 * Enforces a minimum interval between actions in game ticks.
 * A zero extra delay still means at most one action per tick.
 */
public final class TickGate {
    private Object observedPlayer;
    private Object observedWorld;
    private int lastActionAge = Integer.MIN_VALUE;

    public boolean isReady(Object player, Object world, int currentAge, int extraDelayTicks) {
        if (player == null || world == null) return false;

        // The player object/world can change after death, respawn, or dimension travel.
        if (observedPlayer != player || observedWorld != world ||
                (lastActionAge != Integer.MIN_VALUE && currentAge < lastActionAge)) {
            observedPlayer = player;
            observedWorld = world;
            lastActionAge = Integer.MIN_VALUE;
        }

        int minimumInterval = Math.max(0, extraDelayTicks) + 1;
        return lastActionAge == Integer.MIN_VALUE || currentAge - lastActionAge >= minimumInterval;
    }

    public void markAction(int currentAge) {
        lastActionAge = currentAge;
    }

    public void reset() {
        observedPlayer = null;
        observedWorld = null;
        lastActionAge = Integer.MIN_VALUE;
    }
}
