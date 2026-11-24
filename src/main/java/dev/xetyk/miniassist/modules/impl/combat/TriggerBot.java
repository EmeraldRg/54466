package dev.xetyk.miniassist.modules.impl.combat;

import dev.xetyk.miniassist.modules.api.BooleanSetting;
import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.MultiBooleanSetting;
import dev.xetyk.miniassist.modules.api.NumberSetting;
import dev.xetyk.miniassist.util.AttackUtil;
import dev.xetyk.miniassist.util.TargetSelector;
import dev.xetyk.miniassist.util.TickGate;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Tick-scheduled trigger bot; attacks are gated by the vanilla attack-strength cooldown. */
public final class TriggerBot extends Module {
    private final NumberSetting cooldown = add(new NumberSetting(
            "Кулдаун атаки (%)", "Минимальная готовность ванильного удара", 100.0, 50.0, 100.0, 1.0));
    private final NumberSetting range = add(new NumberSetting(
            "Дистанция", "Максимальная дистанция атаки", 4.5, 2.0, 6.0, 0.5));
    private final NumberSetting delayTicks = add(new NumberSetting(
            "Доп. задержка (тики)", "Дополнительная пауза после удара в игровых тиках", 0.0, 0.0, 10.0, 1.0));
    private final BooleanSetting ignoreInvisible = add(new BooleanSetting(
            "Игнорировать невидимых", "Не атаковать невидимые цели", true));
    private final BooleanSetting criticalOnly = add(new BooleanSetting(
            "Только критические", "Ждать настоящего ванильного окна критического удара", false));
    private final BooleanSetting stopWhileUsingItem = add(new BooleanSetting(
            "Не бить с предметом", "Не атаковать во время использования предмета", true));
    private final MultiBooleanSetting targets = add(MultiBooleanSetting.create(
            "Типы целей", "Какие сущности можно атаковать", List.of("Игроков", "Враждебных", "Мирных")));

    private final TickGate tickGate = new TickGate();

    public TriggerBot() {
        super("TriggerBot", Category.COMBAT, "Атакует цель под прицелом по игровым тикам и кулдауну оружия");
        setKeyCode(GLFW.GLFW_KEY_F7);
        targets.set("Враждебных", false);
        targets.set("Мирных", false);
    }

    @Override
    protected void onEnable() {
        resetAttackClock();
    }

    @Override
    protected void onDisable() {
        resetAttackClock();
    }

    private void resetAttackClock() {
        tickGate.reset();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null
                || mc.getCameraEntity() != mc.player) return;
        if (!mc.player.isAlive() || mc.player.isSpectator()) return;
        if (stopWhileUsingItem.isEnabled() && mc.player.isUsingItem()) return;

        // The gate is checked once per END_CLIENT_TICK; 0 extra ticks still means one action per tick.
        if (!tickGate.isReady(mc.player, mc.world, mc.player.age, (int) delayTicks.getValue())) return;

        float attackStrength = mc.player.getAttackCooldownProgress(1.0f) * 100.0f;
        // Vanilla critical damage requires attack strength above 90%, even if the user lowers the general threshold.
        double requiredCooldown = criticalOnly.isEnabled() ? Math.max(90.1, cooldown.getValue()) : cooldown.getValue();
        if (attackStrength + 0.001f < requiredCooldown) return;
        if (criticalOnly.isEnabled() && !AttackUtil.isPlayerInCriticalState()) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity target) || !isValidTarget(target)) return;

        if (AttackUtil.attackEntity(target)) tickGate.markAction(mc.player.age);
    }

    private boolean isValidTarget(LivingEntity target) {
        if (target == mc.player || target.isRemoved() || !target.isAlive() || target.isSpectator()) return false;
        if (target.getWorld() != mc.world) return false;
        if (target instanceof ArmorStandEntity) return false;
        if (ignoreInvisible.isEnabled() && target.isInvisible()) return false;
        if (mc.player.isTeammate(target) || !mc.player.canSee(target)) return false;
        if (mc.player.getEyePos().distanceTo(target.getBoundingBox().getCenter()) > range.getValue()) return false;
        return TargetSelector.isValidType(target, targets);
    }
}
