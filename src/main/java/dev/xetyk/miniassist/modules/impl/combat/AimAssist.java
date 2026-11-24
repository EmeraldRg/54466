package dev.xetyk.miniassist.modules.impl.combat;

import dev.xetyk.miniassist.modules.api.BooleanSetting;
import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.MultiBooleanSetting;
import dev.xetyk.miniassist.modules.api.NumberSetting;
import dev.xetyk.miniassist.util.TargetSelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.UUID;
import java.util.List;

/** Visible, tick-based aim assistance. It rotates only the local player's view. */
public final class AimAssist extends Module {
    private final NumberSetting range = add(new NumberSetting(
            "Дистанция", "Максимальная дистанция выбора цели", 4.5, 2.0, 6.0, 0.5));
    private final NumberSetting fov = add(new NumberSetting(
            "FOV", "Полная ширина конуса поиска цели", 100.0, 20.0, 180.0, 5.0));
    private final NumberSetting smoothing = add(new NumberSetting(
            "Плавность", "Насколько быстро прицел доводится к цели", 0.30, 0.05, 1.0, 0.05));
    private final NumberSetting maxTurn = add(new NumberSetting(
            "Макс. поворот/тик", "Ограничение поворота за один игровой тик", 14.0, 1.0, 45.0, 1.0));
    private final NumberSetting prediction = add(new NumberSetting(
            "Упреждение (тики)", "Небольшое упреждение движения цели", 1.0, 0.0, 5.0, 0.5));
    private final BooleanSetting onlyWhileAttacking = add(new BooleanSetting(
            "Только при атаке", "Ограничить наведение удержанием ЛКМ", false));
    private final BooleanSetting ignoreInvisible = add(new BooleanSetting(
            "Игнорировать невидимых", "Не выбирать невидимые цели", true));
    private final BooleanSetting keepTarget = add(new BooleanSetting(
            "Удерживать цель", "Не переключаться между целями без необходимости", true));
    private final MultiBooleanSetting targets = add(MultiBooleanSetting.create(
            "Типы целей", "Какие сущности можно выбирать", List.of("Игроков", "Враждебных", "Мирных")));

    private int lockedTargetId = -1;
    private UUID lockedTargetUuid;
    private Object lockedWorld;
    private Object lockedPlayer;

    public AimAssist() {
        super("Aim Assist", Category.COMBAT, "Самостоятельно и плавно доводит обзор до выбранной цели");
        setKeyCode(GLFW.GLFW_KEY_F6);
        targets.set("Враждебных", false);
        targets.set("Мирных", false);
    }

    @Override
    protected void onDisable() {
        clearLockedTarget();
    }

    /** Current target used by the Target HUD; null when no valid target is held. */
    public LivingEntity getLockedTarget() {
        if (!isEnabled() || mc.world == null || lockedTargetId < 0
                || lockedWorld != mc.world || lockedPlayer != mc.player) return null;
        Entity entity = mc.world.getEntityById(lockedTargetId);
        return entity instanceof LivingEntity living && lockedTargetUuid != null
                && lockedTargetUuid.equals(living.getUuid()) && isValidTarget(living) ? living : null;
    }

    private void clearLockedTarget() {
        lockedTargetId = -1;
        lockedTargetUuid = null;
        lockedWorld = null;
        lockedPlayer = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null
                || mc.getCameraEntity() != mc.player || mc.player.isSpectator() || !mc.player.isAlive()) {
            clearLockedTarget();
            return;
        }
        if (lockedWorld != mc.world || lockedPlayer != mc.player) clearLockedTarget();
        if (onlyWhileAttacking.isEnabled() && !mc.options.attackKey.isPressed()) {
            clearLockedTarget();
            return;
        }

        LivingEntity target = findTarget();
        if (target == null) {
            clearLockedTarget();
            return;
        }
        lockedTargetId = target.getId();
        lockedTargetUuid = target.getUuid();
        lockedWorld = mc.world;
        lockedPlayer = mc.player;

        Vec3d aimPoint = getAimPoint(target);
        Vec3d delta = aimPoint.subtract(mc.player.getEyePos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (delta.lengthSquared() < 1.0E-5) return;

        float wantedYaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float wantedPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        float yawDelta = MathHelper.wrapDegrees(wantedYaw - mc.player.getYaw());
        float pitchDelta = MathHelper.wrapDegrees(wantedPitch - mc.player.getPitch());
        float factor = (float) smoothing.getValue();
        float maxStep = (float) maxTurn.getValue();
        float yawStep = MathHelper.clamp(yawDelta * factor, -maxStep, maxStep);
        float pitchStep = MathHelper.clamp(pitchDelta * factor, -maxStep, maxStep);

        mc.player.setYaw(mc.player.getYaw() + yawStep);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + pitchStep, -90.0f, 90.0f));
    }

    private LivingEntity findTarget() {
        var player = mc.player;
        double halfFov = fov.getValue() * 0.5;
        if (keepTarget.isEnabled() && lockedTargetId >= 0) {
            Entity existing = mc.world.getEntityById(lockedTargetId);
            if (existing instanceof LivingEntity living && lockedTargetUuid != null
                    && lockedTargetUuid.equals(living.getUuid()) && isValidTarget(living)
                    && angularDistance(living) <= halfFov + 7.0) {
                return living;
            }
        }

        List<Entity> nearby = mc.world.getOtherEntities(player,
                player.getBoundingBox().expand(range.getValue(), range.getValue() * 0.75, range.getValue()));
        return nearby.stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(this::isValidTarget)
                .filter(entity -> angularDistance(entity) <= halfFov)
                .min(Comparator.comparingDouble(this::targetScore))
                .orElse(null);
    }

    private double targetScore(LivingEntity target) {
        // Favor a target near the crosshair, with a small bias toward closer targets.
        return angularDistance(target) + mc.player.distanceTo(target) * 0.35;
    }

    private Vec3d getAimPoint(LivingEntity target) {
        double lead = prediction.getValue();
        Vec3d center = target.getBoundingBox().getCenter();
        Vec3d velocity = target.getVelocity();
        Vec3d leadOffset = velocity.multiply(lead, lead, lead);
        double leadLength = leadOffset.length();
        // Prediction is deliberately capped so one knockback tick cannot throw the aim point away.
        if (leadLength > 1.0) leadOffset = leadOffset.multiply(1.0 / leadLength);
        return center.add(0.0, target.getHeight() * 0.08, 0.0).add(leadOffset);
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player || entity.isRemoved() || !entity.isAlive() || entity.isSpectator()) return false;
        if (entity.getWorld() != mc.world) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (ignoreInvisible.isEnabled() && entity.isInvisible()) return false;
        if (mc.player.isTeammate(entity) || !mc.player.canSee(entity)) return false;
        if (mc.player.getEyePos().distanceTo(entity.getBoundingBox().getCenter()) > range.getValue()) return false;
        return TargetSelector.isValidType(entity, targets);
    }

    private float angularDistance(LivingEntity target) {
        Vec3d delta = getAimPoint(target).subtract(mc.player.getEyePos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        float yawDiff = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        float pitchDiff = targetPitch - mc.player.getPitch();
        return (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }
}
