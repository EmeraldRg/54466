package dev.xetyk.miniassist.hud;

import dev.xetyk.miniassist.ClientKeybinds;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.ModuleManager;
import dev.xetyk.miniassist.modules.impl.combat.AimAssist;
import dev.xetyk.miniassist.modules.impl.render.ClientHudModule;
import dev.xetyk.miniassist.util.Draw;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Modular, compact HUD with dark panels and violet/cyan accents. */
public final class MiniAssistHud {
    private static final int PANEL = 0xC80E0E16;
    private static final int PANEL_ALT = 0xDC17172A;
    private static final int ACCENT = 0xFF8F7CFF;
    private static final int ACCENT_BLUE = 0xFF5CC8FF;
    private static final int TEXT = 0xFFF2F4FA;
    private static final int MUTED = 0xFF9AA6B9;
    private static final int FAINT = 0xFF68768B;
    private static final int TRACK = 0xFF2A2A3A;
    private static float targetHealthAnim = -1f;

    private MiniAssistHud() {}

    public static void init() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ClientKeybinds.isHidden() || client.options.hudHidden || client.player == null || client.world == null) return;

        Module module = ModuleManager.get("HUD");
        if (!(module instanceof ClientHudModule hud) || !hud.isEnabled()) return;

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        if (width < 180 || height < 100) return;

        if (hud.showWatermark()) drawWatermark(context, client, width);
        if (hud.showActiveModules()) drawActiveModules(context, client, width);
        if (hud.showEffects() && height >= 250) drawEffects(context, client);
        if (hud.showCoordinates()) drawCoordinates(context, client, height);
        // On narrow windows, stack armor above the bottom panels instead of letting them overlap.
        if (hud.showArmor() && (width >= 460 || height >= 190)) drawArmor(context, client, width, height);
        if (hud.showKeystrokes() && width >= 380 && height >= 200) drawKeystrokes(context, client, width, height);
        if (hud.showTarget() && width >= 360) drawTarget(context, client, width, height);
    }

    private static void drawWatermark(DrawContext context, MinecraftClient client, int screenWidth) {
        var tr = client.textRenderer;
        int x = 6, y = 6, h = 18;
        String name = "Zenith";
        String user = client.getSession().getUsername();
        String fps = client.getCurrentFps() + " fps";
        String ping = getLocalPing(client);
        String sep = "  |  ";

        int w = 8 + 12 + 5 + tr.getWidth(name) + tr.getWidth(sep) + tr.getWidth(user)
                + tr.getWidth(sep) + tr.getWidth(fps) + tr.getWidth(sep) + tr.getWidth(ping) + 8;
        if (w > screenWidth - 12) w = screenWidth - 12;
        panel(context, x, y, w, h);
        Draw.rr(context, x + 4, y + 4, 10, 10, 3, ACCENT);
        int tx = x + 19;
        int ty = y + 5;
        context.drawTextWithShadow(tr, name, tx, ty, ACCENT);
        tx += tr.getWidth(name);
        context.drawTextWithShadow(tr, sep, tx, ty, FAINT);
        tx += tr.getWidth(sep);
        context.drawTextWithShadow(tr, user, tx, ty, TEXT);
        tx += tr.getWidth(user);
        context.drawTextWithShadow(tr, sep, tx, ty, FAINT);
        tx += tr.getWidth(sep);
        context.drawTextWithShadow(tr, fps, tx, ty, ACCENT_BLUE);
        tx += tr.getWidth(fps);
        context.drawTextWithShadow(tr, sep, tx, ty, FAINT);
        tx += tr.getWidth(sep);
        context.drawTextWithShadow(tr, ping, tx, ty, MUTED);
    }

    private static String getLocalPing(MinecraftClient client) {
        if (client.getNetworkHandler() == null || client.player == null) return "singleplayer";
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? "-- ms" : entry.getLatency() + " ms";
    }

    private static void drawActiveModules(DrawContext context, MinecraftClient client, int screenWidth) {
        var tr = client.textRenderer;
        List<Module> active = new ArrayList<>();
        for (Module module : ModuleManager.getModules()) {
            if (module.isEnabled() && !module.getName().equalsIgnoreCase("HUD")) active.add(module);
        }
        active.sort(Comparator.comparingInt((Module m) -> tr.getWidth(m.getName())).reversed());

        long t = System.currentTimeMillis();
        int y = 6;
        int i = 0;
        for (Module module : active.stream().limit(10).toList()) {
            String name = module.getName();
            int w = tr.getWidth(name) + 12;
            int x = screenWidth - w - 6;
            float wave = (float) (Math.sin(t / 700.0 + i * 0.6) * 0.5 + 0.5);
            int col = Draw.mix(ACCENT, ACCENT_BLUE, wave);
            Draw.rr(context, x, y, w, 14, 3, PANEL);
            Draw.rr(context, x + w - 2, y + 3, 2, 8, 1, col);
            context.drawTextWithShadow(tr, name, x + 5, y + 3, col);
            y += 16;
            i++;
        }
    }

    private static void drawEffects(DrawContext context, MinecraftClient client) {
        List<StatusEffectInstance> effects = client.player.getStatusEffects().stream()
                .sorted(Comparator.comparing(effect -> effect.getEffectType().value().getName().getString()))
                .limit(5)
                .toList();
        if (effects.isEmpty()) return;

        int x = 7;
        int y = 30;
        int w = 150;
        int h = 16 + effects.size() * 14;
        panel(context, x, y, w, h);
        context.drawTextWithShadow(client.textRenderer, "EFFECTS", x + 7, y + 5, ACCENT);
        int rowY = y + 19;
        for (StatusEffectInstance effect : effects) {
            String name = effect.getEffectType().value().getName().getString();
            String level = effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : "";
            String duration = StatusEffectUtil.getDurationText(effect, 1.0F, 20.0F).getString();
            String safeDuration = trimToWidth(client, duration, w - 14 - 24 - 5);
            int durationW = client.textRenderer.getWidth(safeDuration);
            int maxNameWidth = Math.max(24, w - 14 - durationW - 5);
            String left = trimToWidth(client, name + level, maxNameWidth);
            context.drawTextWithShadow(client.textRenderer, left, x + 7, rowY, TEXT);
            context.drawTextWithShadow(client.textRenderer, safeDuration,
                    x + w - durationW - 7, rowY, MUTED);
            rowY += 14;
        }
    }

    private static void drawCoordinates(DrawContext context, MinecraftClient client, int screenHeight) {
        var tr = client.textRenderer;
        var player = client.player;
        double speed = Math.sqrt(player.getVelocity().x * player.getVelocity().x
                + player.getVelocity().z * player.getVelocity().z) * 20.0;
        String xyz = String.format(Locale.ROOT, "%.0f, %.0f, %.0f", player.getX(), player.getY(), player.getZ());
        String bps = String.format(Locale.ROOT, "%.1f", speed);
        String dir = direction(player.getYaw());

        int w = Math.max(tr.getWidth("XYZ  " + xyz), tr.getWidth("BPS  " + bps + "   " + dir)) + 14;
        int h = 28;
        int x = 6;
        int y = screenHeight - h - 6;
        panel(context, x, y, w, h);
        context.drawTextWithShadow(tr, "XYZ", x + 7, y + 5, ACCENT);
        context.drawTextWithShadow(tr, xyz, x + 7 + tr.getWidth("XYZ  "), y + 5, TEXT);
        context.drawTextWithShadow(tr, "BPS", x + 7, y + 16, ACCENT);
        context.drawTextWithShadow(tr, bps, x + 7 + tr.getWidth("BPS  "), y + 16, TEXT);
        context.drawTextWithShadow(tr, dir, x + 7 + tr.getWidth("BPS  " + bps + "   "), y + 16, MUTED);
    }

    private static String direction(float yaw) {
        float angle = MathHelper.wrapDegrees(yaw);
        if (angle >= -45.0f && angle < 45.0f) return "S";
        if (angle >= 45.0f && angle < 135.0f) return "W";
        if (angle >= -135.0f && angle < -45.0f) return "E";
        return "N";
    }

    private static void drawArmor(DrawContext context, MinecraftClient client, int screenWidth, int screenHeight) {
        List<ItemStack> armor = new ArrayList<>();
        for (ItemStack stack : client.player.getArmorItems()) armor.add(stack);
        // The inventory iterable is boots -> helmet; display it in the familiar helmet -> boots order.
        java.util.Collections.reverse(armor);
        int slotW = 22;
        int w = armor.size() * slotW + 12;
        int x = (screenWidth - w) / 2;
        int y = screenWidth < 460 ? screenHeight - 94 : screenHeight - 48;
        panel(context, x, y, w, 38);

        for (int i = 0; i < armor.size(); i++) {
            ItemStack stack = armor.get(i);
            int sx = x + 7 + i * slotW;
            if (stack.isEmpty()) {
                Draw.rr(context, sx + 2, y + 9, 16, 16, 3, 0x55313B4D);
                continue;
            }
            context.drawItem(stack, sx + 1, y + 4);
            context.drawStackOverlay(client.textRenderer, stack, sx + 1, y + 4);
            if (stack.isDamageable()) {
                double durability = 1.0 - (double) stack.getDamage() / Math.max(1, stack.getMaxDamage());
                int color = durability > 0.5 ? 0xFF70D7A2 : (durability > 0.2 ? 0xFFFFC56B : 0xFFFF6B83);
                Draw.rr(context, sx + 2, y + 32, 16, 2, 1, TRACK);
                Draw.rr(context, sx + 2, y + 32, Math.max(2, (int) Math.round(16 * durability)), 2, 1, color);
            }
        }
    }


    private static void drawKeystrokes(DrawContext context, MinecraftClient client, int screenWidth, int screenHeight) {
        int w = 104;
        int h = 70;
        int x = screenWidth - w - 7;
        int y = screenHeight - 135;
        panel(context, x, y, w, h);
        context.drawTextWithShadow(client.textRenderer, "KEYSTROKES", x + 7, y + 5, ACCENT);

        drawKey(context, client, "W", x + 41, y + 18, client.options.forwardKey.isPressed());
        drawKey(context, client, "A", x + 17, y + 34, client.options.leftKey.isPressed());
        drawKey(context, client, "S", x + 41, y + 34, client.options.backKey.isPressed());
        drawKey(context, client, "D", x + 65, y + 34, client.options.rightKey.isPressed());
        drawWideKey(context, client, "JUMP", x + 8, y + 52, 40, client.options.jumpKey.isPressed());
        drawMouseKey(context, client, "LMB", x + 51, y + 52, client.options.attackKey.isPressed());
        drawMouseKey(context, client, "RMB", x + 77, y + 52, client.options.useKey.isPressed());
    }

    private static void drawKey(DrawContext context, MinecraftClient client, String label,
                                int x, int y, boolean pressed) {
        int background = pressed ? 0xFF4A3F8F : 0xFF1E1E2C;
        Draw.rr(context, x, y, 22, 14, 4, background);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), x + 11, y + 3,
                pressed ? TEXT : MUTED);
    }

    private static void drawWideKey(DrawContext context, MinecraftClient client, String label,
                                    int x, int y, int width, boolean pressed) {
        int background = pressed ? 0xFF4A3F8F : 0xFF1E1E2C;
        Draw.rr(context, x, y, width, 12, 4, background);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), x + width / 2, y + 2,
                pressed ? ACCENT_BLUE : MUTED);
    }

    private static void drawMouseKey(DrawContext context, MinecraftClient client, String label,
                                     int x, int y, boolean pressed) {
        int background = pressed ? 0xFF4A3F8F : 0xFF1E1E2C;
        Draw.rr(context, x, y, 22, 12, 4, background);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), x + 11, y + 2,
                pressed ? ACCENT_BLUE : MUTED);
    }

    private static void drawTarget(DrawContext context, MinecraftClient client, int screenWidth, int screenHeight) {
        LivingEntity target = findHudTarget(client);
        if (target == null || !target.isAlive()) { targetHealthAnim = -1f; return; }
        var tr = client.textRenderer;

        int w = 130;
        int h = 36;
        int x = screenWidth / 2 + 24;
        int y = screenHeight / 2 + 20;
        panel(context, x, y, w, h);

        float ratio = target.getMaxHealth() <= 0.0f ? 0.0f
                : MathHelper.clamp(target.getHealth() / target.getMaxHealth(), 0.0f, 1.0f);
        targetHealthAnim = targetHealthAnim < 0 ? ratio : targetHealthAnim + (ratio - targetHealthAnim) * 0.15f;

        String healthText = String.format(Locale.ROOT, "%.1f", target.getHealth());
        int hw = tr.getWidth(healthText);
        String name = trimToWidth(client, target.getDisplayName().getString(), w - 20 - hw);
        context.drawTextWithShadow(tr, name, x + 7, y + 6, TEXT);
        context.drawTextWithShadow(tr, healthText, x + w - hw - 7, y + 6, MUTED);

        int barX = x + 7, barY = y + 19, barW = w - 14;
        Draw.rr(context, barX, barY, barW, 5, 2, TRACK);
        int fw = Math.round(barW * targetHealthAnim);
        if (fw > 0) Draw.rr(context, barX, barY, Math.max(4, fw), 5, 2,
                Draw.mix(0xFFFF6B83, 0xFF67D99A, targetHealthAnim));

        String meta = String.format(Locale.ROOT, "%.1fm", client.player.distanceTo(target));
        if (target instanceof PlayerEntity targetPlayer && client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(targetPlayer.getUuid());
            if (entry != null) meta += "   " + entry.getLatency() + "ms";
        }
        context.drawTextWithShadow(tr, meta, x + 7, y + 27, FAINT);
    }

    private static LivingEntity findHudTarget(MinecraftClient client) {
        Module module = ModuleManager.get("Aim Assist");
        if (module instanceof AimAssist aimAssist && aimAssist.isEnabled()) {
            LivingEntity target = aimAssist.getLockedTarget();
            if (target != null && target.isAlive()) return target;
        }
        if (client.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living && living != client.player) return living;
        return null;
    }

    private static String trimToWidth(MinecraftClient client, String value, int maxWidth) {
        if (client.textRenderer.getWidth(value) <= maxWidth) return value;
        String suffix = "…";
        String result = value;
        while (!result.isEmpty() && client.textRenderer.getWidth(result + suffix) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + suffix;
    }

    private static void panel(DrawContext context, int x, int y, int width, int height) {
        Draw.rr(context, x - 1, y - 1, width + 2, height + 2, 5, 0x40000000);
        Draw.rr(context, x, y, width, height, 4, PANEL);
    }
}
