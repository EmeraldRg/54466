package dev.xetyk.miniassist.modules.impl.movement;

import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/** Local free camera. Uses the player's configured Minecraft movement keys. */
public final class FreeCam extends Module {
    private final NumberSetting speed = add(new NumberSetting(
            "Скорость", "Скорость свободной камеры за игровой тик", 0.35, 0.10, 1.50, 0.05));
    private final NumberSetting sprintMultiplier = add(new NumberSetting(
            "Множитель Sprint", "Во сколько раз Sprint ускоряет камеру", 2.0, 1.0, 4.0, 0.1));

    private Entity cameraEntity;
    private Entity previousCameraEntity;
    private Object attachedWorld;
    private Vec3d playerStartPos;
    private Vec3d playerStartVelocity = Vec3d.ZERO;
    private float playerStartFallDistance;
    private float lockedYaw;
    private float lockedPitch;
    private float lastPlayerYaw;
    private float lastPlayerPitch;

    private double inputForward;
    private double inputSideways;
    private double inputVertical;
    private boolean fastMove;
    private boolean forwardHeld;
    private boolean backHeld;
    private boolean leftHeld;
    private boolean rightHeld;
    private boolean jumpHeld;
    private boolean sneakHeld;
    private boolean sprintHeld;

    public FreeCam() {
        super("FreeCam", Category.MOVEMENT, "Отделяет камеру от игрока; движение использует настройки управления Minecraft");
        setKeyCode(GLFW.GLFW_KEY_F8);
    }

    @Override
    protected void onEnable() {
        createCamera();
    }

    private void createCamera() {
        if (cameraEntity != null || mc.player == null || mc.world == null) return;

        Entity created = EntityType.ARMOR_STAND.create(mc.world, SpawnReason.TRIGGERED);
        if (created == null) {
            setEnabled(false);
            return;
        }

        previousCameraEntity = mc.getCameraEntity();
        attachedWorld = mc.world;
        playerStartPos = mc.player.getPos();
        playerStartVelocity = mc.player.getVelocity();
        playerStartFallDistance = mc.player.fallDistance;
        lockedYaw = mc.player.getYaw();
        lockedPitch = mc.player.getPitch();
        lastPlayerYaw = lockedYaw;
        lastPlayerPitch = lockedPitch;

        created.refreshPositionAndAngles(
                mc.player.getX(), mc.player.getEyeY() - created.getStandingEyeHeight(), mc.player.getZ(),
                lockedYaw, lockedPitch);
        created.setNoGravity(true);
        created.setInvisible(true);
        cameraEntity = created;
        mc.setCameraEntity(cameraEntity);
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.world == null) return;
        if (attachedWorld != null && attachedWorld != mc.world) {
            resetForWorldChange();
            setEnabled(false);
            return;
        }
        if (cameraEntity == null) createCamera();
        if (cameraEntity == null) return;

        // Mouse input still rotates the local player; copy that delta to the detached camera.
        float playerYaw = mc.player.getYaw();
        float playerPitch = mc.player.getPitch();
        float yawDelta = MathHelper.wrapDegrees(playerYaw - lastPlayerYaw);
        float pitchDelta = playerPitch - lastPlayerPitch;
        cameraEntity.setYaw(cameraEntity.getYaw() + yawDelta);
        cameraEntity.setPitch(MathHelper.clamp(cameraEntity.getPitch() + pitchDelta, -90.0f, 90.0f));
        lastPlayerYaw = playerYaw;
        lastPlayerPitch = playerPitch;

        mc.player.setYaw(lockedYaw);
        mc.player.setPitch(lockedPitch);

        // Read Minecraft's configured bindings before suppressing them for the real player.
        boolean allowMovement = mc.currentScreen == null;
        forwardHeld = allowMovement && mc.options.forwardKey.isPressed();
        backHeld = allowMovement && mc.options.backKey.isPressed();
        leftHeld = allowMovement && mc.options.leftKey.isPressed();
        rightHeld = allowMovement && mc.options.rightKey.isPressed();
        jumpHeld = allowMovement && mc.options.jumpKey.isPressed();
        sneakHeld = allowMovement && mc.options.sneakKey.isPressed();
        sprintHeld = allowMovement && mc.options.sprintKey.isPressed();
        inputForward = (forwardHeld ? 1.0 : 0.0) - (backHeld ? 1.0 : 0.0);
        inputSideways = (rightHeld ? 1.0 : 0.0) - (leftHeld ? 1.0 : 0.0);
        inputVertical = (jumpHeld ? 1.0 : 0.0) - (sneakHeld ? 1.0 : 0.0);
        fastMove = sprintHeld;

        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);

        freezePlayer();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (attachedWorld != null && attachedWorld != mc.world) {
            resetForWorldChange();
            setEnabled(false);
            return;
        }
        if (cameraEntity == null) createCamera();
        if (cameraEntity == null) return;

        if (mc.getCameraEntity() != cameraEntity) mc.setCameraEntity(cameraEntity);
        if (mc.currentScreen == null) moveCamera();
        freezePlayer();
        // Re-apply the sampled physical state after the player's movement phase.
        // The next START_CLIENT_TICK can therefore read held keys again instead of getting stuck at false.
        restoreMovementBindings();
    }

    private void freezePlayer() {
        if (mc.player == null || playerStartPos == null) return;
        mc.player.setPosition(playerStartPos);
        mc.player.setVelocity(Vec3d.ZERO);
    }

    private void moveCamera() {
        double length = Math.sqrt(inputForward * inputForward + inputSideways * inputSideways);
        double forward = inputForward;
        double sideways = inputSideways;
        if (length > 1.0) {
            forward /= length;
            sideways /= length;
        }

        double yaw = Math.toRadians(cameraEntity.getYaw());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double multiplier = fastMove ? sprintMultiplier.getValue() : 1.0;
        double moveSpeed = speed.getValue() * multiplier;

        cameraEntity.setPosition(
                cameraEntity.getX() + (forwardX * forward + rightX * sideways) * moveSpeed,
                cameraEntity.getY() + inputVertical * moveSpeed,
                cameraEntity.getZ() + (forwardZ * forward + rightZ * sideways) * moveSpeed);
    }

    private void restoreMovementBindings() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(forwardHeld);
        mc.options.backKey.setPressed(backHeld);
        mc.options.leftKey.setPressed(leftHeld);
        mc.options.rightKey.setPressed(rightHeld);
        mc.options.jumpKey.setPressed(jumpHeld);
        mc.options.sneakKey.setPressed(sneakHeld);
        mc.options.sprintKey.setPressed(sprintHeld);
    }

    private void resetForWorldChange() {
        if (mc.player != null && mc.getCameraEntity() == cameraEntity) mc.setCameraEntity(mc.player);
        cameraEntity = null;
        previousCameraEntity = null;
        attachedWorld = null;
        playerStartPos = null;
        playerStartVelocity = Vec3d.ZERO;
        inputForward = inputSideways = inputVertical = 0.0;
        fastMove = false;
        forwardHeld = backHeld = leftHeld = rightHeld = jumpHeld = sneakHeld = sprintHeld = false;
    }

    @Override
    protected void onDisable() {
        boolean sameWorld = mc.world != null && attachedWorld == mc.world;
        if (mc.player != null) {
            if (sameWorld && playerStartPos != null && mc.player.isAlive()) {
                mc.player.setPosition(playerStartPos);
                float finalYaw = cameraEntity != null ? cameraEntity.getYaw() : lockedYaw;
                float finalPitch = cameraEntity != null ? cameraEntity.getPitch() : lockedPitch;
                mc.player.setYaw(finalYaw);
                mc.player.setPitch(finalPitch);
                // Restore the momentum captured when FreeCam was enabled instead of silently cancelling a fall.
                mc.player.setVelocity(playerStartVelocity);
                mc.player.fallDistance = playerStartFallDistance;
            }
            Entity restoreCamera = sameWorld ? previousCameraEntity : null;
            if (restoreCamera == null || restoreCamera.isRemoved()) restoreCamera = mc.player;
            mc.setCameraEntity(restoreCamera);
        } else if (mc.getCameraEntity() == cameraEntity && previousCameraEntity != null
                && !previousCameraEntity.isRemoved()) {
            mc.setCameraEntity(previousCameraEntity);
        }

        restoreMovementBindings();
        cameraEntity = null;
        previousCameraEntity = null;
        attachedWorld = null;
        playerStartPos = null;
        playerStartVelocity = Vec3d.ZERO;
        inputForward = inputSideways = inputVertical = 0.0;
        fastMove = false;
        forwardHeld = backHeld = leftHeld = rightHeld = jumpHeld = sneakHeld = sprintHeld = false;
    }
}
