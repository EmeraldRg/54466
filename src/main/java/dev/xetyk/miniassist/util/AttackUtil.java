package dev.xetyk.miniassist.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Hand;

public final class AttackUtil {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private AttackUtil() {}

    /** Returns true only when an attack was actually issued against an entity in the current world. */
    public static boolean attackEntity(Entity target) {
        ClientPlayerEntity player = MC.player;
        if (player == null || MC.world == null || MC.interactionManager == null || target == null) return false;
        if (target.isRemoved() || MC.world.getEntityById(target.getId()) != target) return false;
        MC.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    /** Mirrors the ordinary vanilla critical-hit conditions; no packet spoofing or forced crits. */
    public static boolean isPlayerInCriticalState() {
        ClientPlayerEntity player = MC.player;
        if (player == null) return false;
        return player.isAlive()
                && player.fallDistance > 0.0f
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.isSubmergedInWater()
                && !player.isInLava()
                && !player.isGliding()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !player.hasVehicle()
                && !player.isSprinting();
    }
}
