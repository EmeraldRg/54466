package dev.xetyk.miniassist.gui;

import dev.xetyk.miniassist.config.ConfigManager;
import dev.xetyk.miniassist.modules.api.BooleanSetting;
import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.ModuleManager;
import dev.xetyk.miniassist.modules.api.MultiBooleanSetting;
import dev.xetyk.miniassist.modules.api.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rounded floating ClickGUI: category tabs on the left, two-column module list,
 * smooth animations. LMB toggles a module, RMB (or the arrow) expands its settings,
 * MMB (or the key chip) starts key capture.
 */
public final class MiniAssistMenuScreen extends Screen {
    // ---- palette ----
    private static final int WIN_BG      = 0xF2101018;
    private static final int SIDE_BG      = 0xF20B0B12;
    private static final int CARD_BG      = 0xFF171722;
    private static final int CARD_HOVER   = 0xFF1E1E2C;
    private static final int CARD_ON      = 0xFF241F3D;
    private static final int CHIP_BG      = 0xFF21212F;
    private static final int TRACK        = 0xFF2A2A3A;
    private static final int ACCENT       = 0xFF8F7CFF;
    private static final int ACCENT_2     = 0xFF5CC8FF;
    private static final int TEXT         = 0xFFF1F2F8;
    private static final int TEXT_DIM     = 0xFF9A9BB0;
    private static final int TEXT_FAINT   = 0xFF5F6075;

    // ---- layout ----
    private static final int SIDE_W = 104;
    private static final int HEAD_H = 26;      // collapsed card height
    private static final int GAP = 6;

    private static final class HitTarget {
        final int x, y, w, h;
        final boolean inContent;
        final Runnable left, right, middle;
        final NumberSetting slider;

        HitTarget(int x, int y, int w, int h, boolean inContent,
                  Runnable left, Runnable right, Runnable middle, NumberSetting slider) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.inContent = inContent;
            this.left = left; this.right = right; this.middle = middle; this.slider = slider;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<HitTarget> hits = new ArrayList<>();
    private final Map<Module, float[]> anim = new HashMap<>();   // {toggle, expand, hover}
    private final Set<Module> expanded = new HashSet<>();
    private final float[] tabAnim = new float[Category.values().length];

    private Category selectedCategory = Category.COMBAT;
    private Module bindingModule;
    private String bindNotice;
    private NumberSetting draggingSlider;
    private HitTarget activeSliderArea;

    private float open;
    private float scroll, scrollTarget;
    private int maxScroll;
    private long lastNano = System.nanoTime();

    private int winX, winY, winW, winH;
    private int vX, vY, vW, vH;   // content viewport

    public MiniAssistMenuScreen() {
        super(Text.literal("MiniAssist"));
    }

    /** Vanilla 1.21.4 blurs everything drawn so far in here; that made the menu look soapy. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    // ======================= helpers =======================

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static int mix(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) lerp(c1 >>> 24, c2 >>> 24, t);
        int r = (int) lerp((c1 >> 16) & 255, (c2 >> 16) & 255, t);
        int g = (int) lerp((c1 >> 8) & 255, (c2 >> 8) & 255, t);
        int b = (int) lerp(c1 & 255, c2 & 255, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Applies the window fade-in to a colour. */
    private int c(int col) {
        int a = (int) ((col >>> 24) * open);
        return (a << 24) | (col & 0xFFFFFF);
    }

    private void rr(DrawContext ctx, int x, int y, int w, int h, int r, int col) {
        col = c(col);
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { ctx.fill(x, y, x + w, y + h, col); return; }
        ctx.fill(x, y + r, x + w, y + h - r, col);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, col);
        }
    }

    private void txt(DrawContext ctx, String s, int x, int y, int col) {
        col = c(col);
        if ((col >>> 24) < 12) return;   // vanilla treats very low alpha as opaque
        ctx.drawTextWithShadow(textRenderer, s, x, y, col);
    }

    private void txtCentered(DrawContext ctx, String s, int cx, int y, int col) {
        txt(ctx, s, cx - textRenderer.getWidth(s) / 2, y, col);
    }

    private float[] anim(Module m) {
        return anim.computeIfAbsent(m, k -> new float[]{k.isEnabled() ? 1f : 0f, 0f, 0f});
    }

    // ======================= sizing =======================

    private int chipLines(MultiBooleanSetting multi, int usableW) {
        int lineW = 0, lines = 1;
        for (String option : multi.getOptions()) {
            int chipW = Math.max(40, textRenderer.getWidth(option) + 14);
            if (lineW > 0 && lineW + chipW > usableW) { lines++; lineW = 0; }
            lineW += chipW + 4;
        }
        return lines;
    }

    private int settingsHeight(Module m, int cardW) {
        int h = 16; // description line
        for (BooleanSetting s : m.getBooleanSettings()) if (s.isVisible()) h += 20;
        for (NumberSetting s : m.getNumberSettings()) if (s.isVisible()) h += 28;
        for (MultiBooleanSetting s : m.getMultiSettings()) h += 14 + chipLines(s, cardW - 20) * 19;
        return h + 6;
    }

    // ======================= render =======================

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNano) / 1_000_000_000f);
        lastNano = now;
        float k = 1f - (float) Math.exp(-dt * 14f);
        open = lerp(open, 1f, 1f - (float) Math.exp(-dt * 12f));
        scroll = lerp(scroll, scrollTarget, k);

        hits.clear();
        winW = Math.min(width - 24, 580);
        winH = Math.min(height - 24, 340);
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;
        vX = winX + SIDE_W + 8;
        vY = winY + 36;
        vW = winW - SIDE_W - 16;
        vH = winH - 36 - 24;

        ctx.fill(0, 0, width, height, c(0x90050508));

        rr(ctx, winX, winY, winW, winH, 8, WIN_BG);
        rr(ctx, winX, winY, SIDE_W, winH, 8, SIDE_BG);
        ctx.fill(winX + SIDE_W - 8, winY + 8, winX + SIDE_W, winY + winH - 8, c(SIDE_BG));

        drawSidebar(ctx, mouseX, mouseY, k);
        drawHeading(ctx);

        ctx.enableScissor(vX, vY, vX + vW, vY + vH);
        drawCards(ctx, mouseX, mouseY, k);
        drawScrollbar(ctx);
        ctx.disableScissor();

        drawFooter(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawSidebar(DrawContext ctx, int mouseX, int mouseY, float k) {
        // logo
        rr(ctx, winX + 12, winY + 12, 18, 18, 5, ACCENT);
        txtCentered(ctx, "Z", winX + 21, winY + 17, TEXT);
        txt(ctx, "ZENITH", winX + 36, winY + 13, TEXT);
        txt(ctx, "client 1.4.1", winX + 36, winY + 23, TEXT_FAINT);

        int y = winY + 44;
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            boolean active = selectedCategory == cat;
            int rx = winX + 8, rw = SIDE_W - 16, rh = 24;
            boolean hov = mouseX >= rx && mouseX < rx + rw && mouseY >= y && mouseY < y + rh;
            tabAnim[i] = lerp(tabAnim[i], active ? 1f : (hov ? 0.4f : 0f), k);
            if (tabAnim[i] > 0.02f) {
                int bg = mix(0x00000000, 0xFF23203D, tabAnim[i]);
                rr(ctx, rx, y, rw, rh, 6, bg);
                rr(ctx, rx + 2, y + 6, 2, rh - 12, 1, mix(0x00000000, ACCENT, tabAnim[i]));
            }
            txt(ctx, cat.title, rx + 12 + (int) (3 * tabAnim[i]), y + 8,
                    mix(TEXT_DIM, TEXT, tabAnim[i]));
            int cnt = 0;
            for (Module m : ModuleManager.getModules()) if (m.getCategory() == cat) cnt++;
            String n = String.valueOf(cnt);
            txt(ctx, n, rx + rw - 8 - textRenderer.getWidth(n), y + 8,
                    mix(TEXT_FAINT, ACCENT, tabAnim[i]));
            hits.add(new HitTarget(rx, y, rw, rh, false, () -> {
                selectedCategory = cat; scroll = 0; scrollTarget = 0;
            }, null, null, null));
            y += rh + 3;
        }

        int active = 0;
        for (Module m : ModuleManager.getModules()) if (m.isEnabled()) active++;
        String st = active + " active";
        int by = winY + winH - 30;
        rr(ctx, winX + 8, by, SIDE_W - 16, 22, 6, CHIP_BG);
        rr(ctx, winX + 16, by + 8, 6, 6, 3, active > 0 ? ACCENT_2 : TEXT_FAINT);
        txt(ctx, st, winX + 28, by + 7, TEXT_DIM);
    }

    private void drawHeading(DrawContext ctx) {
        txt(ctx, selectedCategory.title, vX + 2, winY + 12, TEXT);
        int count = 0, en = 0;
        for (Module m : ModuleManager.getModules()) {
            if (m.getCategory() == selectedCategory) { count++; if (m.isEnabled()) en++; }
        }
        String s = en + "/" + count + " enabled";
        txt(ctx, s, vX + vW - textRenderer.getWidth(s) - 2, winY + 12, TEXT_FAINT);
        ctx.fill(vX, winY + 28, vX + vW, winY + 29, c(0xFF1F1F2C));
    }

    private void drawCards(DrawContext ctx, int mouseX, int mouseY, float k) {
        List<Module> mods = new ArrayList<>();
        for (Module m : ModuleManager.getModules()) {
            if (m.getCategory() == selectedCategory) mods.add(m);
        }
        int cols = vW >= 360 ? 2 : 1;
        int sbW = 6;
        int cardW = (vW - sbW - (cols - 1) * GAP) / cols;
        int[] colY = new int[cols];

        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int col = i % cols;
            float[] a = anim(m);
            boolean isOn = m.isEnabled();
            boolean isExp = expanded.contains(m);
            a[0] = lerp(a[0], isOn ? 1f : 0f, k);
            a[1] = lerp(a[1], isExp ? 1f : 0f, k);

            int fullSet = settingsHeight(m, cardW);
            int h = HEAD_H + Math.round(fullSet * a[1]);
            int x = vX + col * (cardW + GAP);
            int y = vY + colY[col] - Math.round(scroll);
            colY[col] += h + GAP;

            boolean headHover = mouseX >= x && mouseX < x + cardW
                    && mouseY >= y && mouseY < y + HEAD_H
                    && mouseX >= vX && mouseX < vX + vW && mouseY >= vY && mouseY < vY + vH;
            a[2] = lerp(a[2], headHover ? 1f : 0f, k);

            int bg = mix(CARD_BG, CARD_HOVER, a[2]);
            bg = mix(bg, CARD_ON, a[0]);
            rr(ctx, x, y, cardW, h, 6, bg);
            if (a[0] > 0.02f) rr(ctx, x, y + 6, 2, HEAD_H - 12, 1, mix(0x00000000, ACCENT, a[0]));

            txt(ctx, m.getName(), x + 10, y + 9, mix(TEXT_DIM, TEXT, Math.max(a[0], a[2] * 0.6f)));

            // switch
            int swX = x + cardW - 32, swY = y + 7;
            rr(ctx, swX, swY, 24, 12, 6, mix(TRACK, ACCENT, a[0]));
            int knob = swX + 2 + Math.round(12 * a[0]);
            rr(ctx, knob, swY + 2, 8, 8, 4, mix(0xFFB4B5C6, TEXT, a[0]));

            // bind chip
            boolean binding = bindingModule == m;
            String bt = binding ? "..." : shorten(m.getBindName());
            int bw = Math.max(22, textRenderer.getWidth(bt) + 10);
            int bx = swX - 6 - bw;
            boolean bhov = mouseX >= bx && mouseX < bx + bw && mouseY >= y + 5 && mouseY < y + 21;
            rr(ctx, bx, y + 5, bw, 16, 5, binding ? 0xFF332C5C : (bhov ? 0xFF2B2B3D : CHIP_BG));
            txtCentered(ctx, bt, bx + bw / 2, y + 9, binding ? ACCENT : TEXT_FAINT);

            // arrow
            boolean hasSettings = fullSet > 22;
            if (hasSettings) {
                int ax = bx - 14, ay = y + 13;
                int ac = mix(TEXT_FAINT, TEXT_DIM, a[2]);
                // tiny chevron drawn with fills; points down, flips up when expanded
                for (int j = 0; j < 4; j++) {
                    int off = a[1] > 0.5f ? 3 - j : j;
                    ctx.fill(ax - 3 + off, ay - 2 + j, ax + 4 - off, ay - 1 + j, c(ac));
                }
                hits.add(new HitTarget(ax - 7, y + 3, 16, 20, true, () -> toggleExpand(m), null, null, null));
            }

            final Module fm = m;
            hits.add(new HitTarget(bx, y + 5, bw, 16, true, () -> startBind(fm), null, null, null));
            hits.add(new HitTarget(x, y, cardW, HEAD_H, true,
                    fm::toggle, hasSettings ? () -> toggleExpand(fm) : null, () -> startBind(fm), null));

            if (a[1] > 0.03f) {
                ctx.enableScissor(x, y + HEAD_H, x + cardW, y + h);
                drawSettings(ctx, m, x, y + HEAD_H, cardW, mouseX, mouseY, a[1], isExp && a[1] > 0.9f);
                ctx.disableScissor();
            }
        }

        int total = 0;
        for (int v : colY) total = Math.max(total, v);
        maxScroll = Math.max(0, total - GAP - vH);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }

    private void drawSettings(DrawContext ctx, Module m, int x, int top, int w,
                              int mouseX, int mouseY, float vis, boolean interactive) {
        int cy = top + 4;
        String desc = m.getDescription();
        while (!desc.isEmpty() && textRenderer.getWidth(desc) > w - 20) desc = desc.substring(0, desc.length() - 1);
        if (desc.length() < m.getDescription().length() && desc.length() > 3) desc = desc.substring(0, desc.length() - 3) + "...";
        txt(ctx, desc, x + 10, cy, TEXT_FAINT);
        cy += 16;

        for (BooleanSetting s : m.getBooleanSettings()) {
            if (!s.isVisible()) continue;
            txt(ctx, s.getName(), x + 10, cy + 5, TEXT_DIM);
            int sx = x + w - 30;
            boolean on = s.isEnabled();
            rr(ctx, sx, cy + 4, 20, 10, 5, on ? ACCENT : TRACK);
            rr(ctx, sx + (on ? 11 : 2), cy + 6, 6, 6, 3, on ? TEXT : 0xFFB4B5C6);
            if (interactive) hits.add(new HitTarget(x + 4, cy, w - 8, 18, true, s::toggle, null, null, null));
            cy += 20;
        }

        for (NumberSetting s : m.getNumberSettings()) {
            if (!s.isVisible()) continue;
            txt(ctx, s.getName(), x + 10, cy + 1, TEXT_DIM);
            String v = s.getDisplayValue();
            txt(ctx, v, x + w - 10 - textRenderer.getWidth(v), cy + 1, ACCENT_2);
            int sx = x + 10, sy = cy + 15, sw = w - 20;
            rr(ctx, sx, sy, sw, 4, 2, TRACK);
            int filled = (int) Math.round(sw * s.getRatio());
            if (filled > 0) rr(ctx, sx, sy, Math.max(4, filled), 4, 2, mix(ACCENT, ACCENT_2, (float) s.getRatio()));
            rr(ctx, sx + filled - 4, sy - 2, 8, 8, 4, TEXT);
            if (interactive) hits.add(new HitTarget(sx, sy - 6, sw, 16, true, null, null, null, s));
            cy += 28;
        }

        for (MultiBooleanSetting multi : m.getMultiSettings()) {
            txt(ctx, multi.getName(), x + 10, cy + 1, TEXT_DIM);
            cy += 14;
            int chipX = x + 10, usable = w - 20;
            for (String option : multi.getOptions()) {
                int chipW = Math.max(40, textRenderer.getWidth(option) + 14);
                if (chipX + chipW > x + 10 + usable) { chipX = x + 10; cy += 19; }
                boolean sel = multi.isSelected(option);
                boolean hov = mouseX >= chipX && mouseX < chipX + chipW && mouseY >= cy && mouseY < cy + 15;
                rr(ctx, chipX, cy, chipW, 15, 5, sel ? 0xFF3A3170 : (hov ? 0xFF2B2B3D : CHIP_BG));
                txtCentered(ctx, option, chipX + chipW / 2, cy + 4, sel ? TEXT : TEXT_DIM);
                if (interactive) hits.add(new HitTarget(chipX, cy, chipW, 15, true,
                        () -> multi.toggle(option), null, null, null));
                chipX += chipW + 4;
            }
            cy += 19;
        }
    }

    private void drawScrollbar(DrawContext ctx) {
        if (maxScroll <= 0) return;
        int tx = vX + vW - 3;
        int th = Math.max(20, (int) ((double) vH * vH / (vH + maxScroll)));
        int ty = vY + (int) ((double) (vH - th) * scroll / maxScroll);
        rr(ctx, tx, vY, 2, vH, 1, 0xFF1C1C28);
        rr(ctx, tx, ty, 2, th, 1, ACCENT);
    }

    private void drawFooter(DrawContext ctx) {
        String f;
        int col = TEXT_FAINT;
        if (bindingModule != null) {
            f = "Bind: " + bindingModule.getName() + "  -  press a key, DEL to clear, ESC to cancel";
            col = ACCENT;
        } else if (bindNotice != null) {
            f = bindNotice;
            col = ACCENT;
        } else {
            f = "LMB toggle   RMB settings   MMB bind   RSHIFT close";
        }
        txt(ctx, f, vX + 2, winY + winH - 16, col);
    }

    private String shorten(String s) {
        if (s == null || s.isEmpty()) return "-";
        return s.length() > 6 ? s.substring(0, 6) : s;
    }

    // ======================= actions =======================

    private void toggleExpand(Module m) {
        if (!expanded.remove(m)) expanded.add(m);
    }

    private void startBind(Module m) {
        bindingModule = m;
        bindNotice = null;
    }

    private void updateSlider(HitTarget area, double mouseX) {
        if (area == null || area.slider == null) return;
        area.slider.setFromRatio((mouseX - area.x) / Math.max(1.0, area.w));
    }

    // ======================= input =======================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inView = mouseX >= vX && mouseX < vX + vW && mouseY >= vY && mouseY < vY + vH;
        for (int i = hits.size() - 1; i >= 0; i--) {
            HitTarget t = hits.get(i);
            if (!t.contains(mouseX, mouseY)) continue;
            if (t.inContent && !inView) continue;
            if (button == 0 && t.slider != null) {
                draggingSlider = t.slider;
                activeSliderArea = t;
                updateSlider(t, mouseX);
                return true;
            }
            Runnable r = button == 0 ? t.left : (button == 1 ? t.right : (button == 2 ? t.middle : null));
            if (r != null) {
                r.run();
                ConfigManager.save();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && draggingSlider != null && activeSliderArea != null) {
            updateSlider(activeSliderArea, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingSlider != null) {
            draggingSlider = null;
            activeSliderArea = null;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (mouseX >= vX && mouseX < vX + vW && mouseY >= vY && mouseY < vY + vH && maxScroll > 0) {
            scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) (vAmount * 30.0)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingModule != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                bindingModule = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String name = bindingModule.getName();
                bindingModule.setKeyCode(GLFW.GLFW_KEY_UNKNOWN);
                bindingModule = null;
                bindNotice = name + " - key cleared";
                ConfigManager.save();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_G) {
                bindingModule = null;
                bindNotice = "G is reserved for HUD toggle";
                return true;
            }
            if (keyCode >= 0) {
                Module changed = bindingModule;
                boolean cleared = false;
                for (Module other : ModuleManager.getModules()) {
                    if (other != changed && other.getKeyCode() == keyCode) {
                        other.setKeyCode(GLFW.GLFW_KEY_UNKNOWN);
                        cleared = true;
                    }
                }
                changed.setKeyCode(keyCode);
                bindNotice = changed.getName() + " -> " + changed.getBindName()
                        + (cleared ? "  (conflicting bind cleared)" : "");
                bindingModule = null;
                ConfigManager.save();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        draggingSlider = null;
        activeSliderArea = null;
        bindingModule = null;
        ConfigManager.save();
        super.removed();
    }
}
