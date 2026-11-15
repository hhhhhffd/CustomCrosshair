package com.customcrosshair;

import java.awt.Color;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static com.customcrosshair.CrosshairConfig.*;

public final class CrosshairScreen extends Screen {
    private static final int WHITE = 0xfff2f6fc, MUTE = 0xff98a9bf, ACCENT = 0xff79dcef;
    private static final int PANEL = 0xf2152131, CARD = 0xff1a293b, LINE = 0xff364d66;
    private static final String P = "customcrosshair.";
    private final Screen parent;
    private final CrosshairConfig c = INSTANCE;
    private int x, y, w, h, left, right, contentY, leftW, rightW, contentH;
    private int gridX, gridY, cell, lastX, lastY, paintButton = -1;
    private int presetPage, scene, previewTarget;
    private boolean centerPreview;
    private int noteColor = ACCENT;
    private Component note = Component.empty();
    private Button[] scenes;

    public CrosshairScreen(Screen parent) {
        super(t("title"));
        this.parent = parent;
    }

    private static Component t(String k, Object... values) { return Component.translatable(P + k, values); }

    @Override protected void init() {
        w = Math.min(390, width - 16);
        h = Math.min(268, height - 16);
        x = (width - w) / 2;
        y = (height - h) / 2;
        left = x + 9;
        leftW = (w - 23) / 2;
        right = left + leftW + 5;
        rightW = w - 23 - leftW;
        contentY = y + 61;
        contentH = h - 96;
        int tabWidth = (w - 26) / 3;
        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length; i++) {
            Mode mode = modes[i];
            var b = add(left + i * (tabWidth + 3), y + 34, tabWidth,
                    t("mode." + mode.name().toLowerCase(java.util.Locale.ROOT)), ignored -> {
                        c.mode = mode;
                        note = Component.empty();
                        rebuildWidgets();
                    });
            b.active = c.mode != mode;
        }

        if (c.mode == Mode.SIMPLE) initSimple();
        else if (c.mode == Mode.PRESET) initPresets();
        else initGrid();

        scenes = new Button[4];
        int sceneW = (rightW - 23) / 4;
        for (int i = 0; i < scenes.length; i++) {
            final int id = i;
            scenes[i] = add(right + 7 + i * (sceneW + 3), contentY + contentH - 46, sceneW,
                    Component.literal(Integer.toString(i + 1)), b -> {
                        scene = id;
                        updateScenes();
                    });
            scenes[i].setTooltip(Tooltip.create(t("scene." + i)));
        }
        updateScenes();
        add(right + 7, contentY + contentH - 23, rightW - 14, t("colors"), b ->
                minecraft.setScreen(new ColorScreen(this, c)));
        int fw = (w - 24) / 3;
        add(left, y + h - 28, fw, t("enabled", on(c.enabled)), b -> {
            c.enabled = !c.enabled;
            b.setMessage(t("enabled", on(c.enabled)));
        });
        Button scaleButton = add(left + fw + 3, y + h - 28, fw, scaleLabel(), b -> {
            if (!c.customScale) {
                c.customScale = true;
                c.scale = Scale.SMALL;
            } else if (c.scale == Scale.AUTO) {
                c.customScale = false;
            } else {
                c.scale = Scale.values()[c.scale.ordinal() + 1];
            }
            b.setMessage(scaleLabel());
        });
        scaleButton.active = c.mode != Mode.SIMPLE;
        scaleButton.setTooltip(Tooltip.create(t("scale.help")));
        add(left + 2 * (fw + 3), y + h - 28, fw, Component.translatable("gui.done"), b -> onClose());
    }

    private void updateScenes() {
        for (int i = 0; i < scenes.length; i++) scenes[i].active = i != scene;
    }

    private void initSimple() {
        int iy = contentY + 23;
        addRenderableWidget(new Slider(left + 7, iy, leftW - 14, "length", CrosshairPatterns.MIN_LENGTH, CrosshairPatterns.MAX_LENGTH, c.length, v -> c.length = v));
        addRenderableWidget(new Slider(left + 7, iy + 21, leftW - 14, "gap", CrosshairPatterns.MIN_GAP, CrosshairPatterns.MAX_GAP, c.gap, v -> c.gap = v));
        addRenderableWidget(new Slider(left + 7, iy + 42, leftW - 14, "thickness", CrosshairPatterns.MIN_THICKNESS, CrosshairPatterns.MAX_THICKNESS, c.thickness, v -> c.thickness = v));
        add(left + 7, iy + 63, leftW - 14, t("dot", on(c.dot)), b -> {
            c.dot = !c.dot;
            b.setMessage(t("dot", on(c.dot)));
        });
    }

    private int perPage() { return Math.max(2, Math.min(8, (contentH - 57) / 24 * 2)); }

    private void initPresets() {
        var names = CrosshairPatterns.PRESET_NAMES;
        int perPage = perPage();
        int count = (names.size() + perPage - 1) / perPage;
        presetPage = Math.clamp(presetPage, 0, Math.max(0, count - 1));
        int pw = (leftW - 17) / 2;
        for (int i = 0; i < perPage && i + presetPage * perPage < names.size(); i++) {
            int index = i + presetPage * perPage;
            int px = left + 7 + i % 2 * (pw + 3);
            int py = contentY + 23 + i / 2 * 24;
            var b = add(px, py, pw, presetLabel(index), ignored -> {
                c.preset = index;
                rebuildWidgets();
            });
            b.active = index != c.preset;
            b.setTooltip(Tooltip.create(Component.literal(names.get(index))));
        }
        if (count > 1) {
            var prev = add(left + 7, contentY + contentH - 25, 22, Component.literal("‹"), b -> {
                presetPage--;
                rebuildWidgets();
            });
            prev.active = presetPage != 0;
            var next = add(left + leftW - 29, contentY + contentH - 25, 22, Component.literal("›"), b -> {
                presetPage++;
                rebuildWidgets();
            });
            next.active = presetPage + 1 < count;
        }
        int inset = count > 1 ? 32 : 7;
        add(left + inset, contentY + contentH - 25, leftW - 2 * inset, t("preset.edit"), b -> {
            var confirm = new ConfirmScreen(ok -> {
                if (ok) {
                    var source = CrosshairPatterns.preset(c.preset);
                    c.custom = java.util.Arrays.stream(source).map(boolean[]::clone).toArray(boolean[][]::new);
                    c.mode = Mode.CUSTOM;
                    c.save();
                }
                minecraft.setScreen(this);
            }, t("preset.edit.title"), t("preset.edit.hint"));
            minecraft.setScreen(confirm);
        });
    }

    private Component presetLabel(int index) {
        String name = CrosshairPatterns.PRESET_NAMES.get(index);
        return Component.literal(font.plainSubstrByWidth(name, (leftW - 17) / 2 - 8));
    }

    private void initGrid() {
        int side = c.custom.length;
        cell = Math.max(1, Math.min(7, Math.min(leftW - 18, contentH - 60) / side));
        gridX = left + (leftW - side * cell) / 2;
        gridY = contentY + 22;
        int bw = (leftW - 17) / 2;
        add(left + 7, contentY + contentH - 25, bw, t("import"), b ->
                minecraft.setScreen(new ImportScreen(this, c)));
        add(left + 10 + bw, contentY + contentH - 25, bw, t("copy"), b -> {
            minecraft.keyboardHandler.setClipboard(CrosshairPatterns.exportCode(c.custom));
            status("copied", ACCENT);
        });
    }

    private void status(String key, int color) {
        note = t(key);
        noteColor = color;
    }

    private boolean[][] activePattern() {
        return switch (c.mode) {
            case SIMPLE -> CrosshairPatterns.simple(c.length, c.gap, c.thickness, c.dot);
            case PRESET -> CrosshairPatterns.preset(c.preset);
            case CUSTOM -> c.custom;
        };
    }

    private static Component on(boolean value) { return Component.translatable(value ? "options.on" : "options.off"); }
    private Component scaleLabel() {
        return t("scale", t(c.customScale ? "scale." + c.scale.name().toLowerCase(java.util.Locale.ROOT) : "scale.default"));
    }
    private Button add(int px, int py, int bw, Component label, Button.OnPress click) {
        return addRenderableWidget(Button.builder(label, click).bounds(px, py, bw, 20).build());
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.renderOutline(x, y, w, h, LINE);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, ACCENT);
        g.drawString(font, getTitle(), left, y + 10, WHITE);
        g.drawString(font, t("subtitle"), left, y + 22, MUTE);
        g.fill(left, contentY, left + leftW, contentY + contentH, CARD);
        g.fill(right, contentY, right + rightW, contentY + contentH, CARD);
        g.renderOutline(left, contentY, leftW, contentH, LINE);
        g.renderOutline(right, contentY, rightW, contentH, LINE);
        g.drawString(font, t("heading." + c.mode.name().toLowerCase(java.util.Locale.ROOT)), left + 7, contentY + 8, ACCENT);
        g.drawString(font, t("preview"), right + 7, contentY + 8, ACCENT);
        if (c.mode == Mode.CUSTOM) drawGrid(g, mouseX, mouseY);
        drawPreview(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
    }

    private void drawGrid(GuiGraphics g, int mx, int my) {
        int side = c.custom.length;
        int px = side * cell;
        g.renderOutline(gridX - 1, gridY - 1, px + 2, px + 2, LINE);
        for (int row = 0; row < side; row++) for (int col = 0; col < side; col++) {
            int l = gridX + col * cell, u = gridY + row * cell;
            int shade = c.custom[row][col] ? ACCENT : ((row + col) & 1) == 0 ? 0xff22374d : 0xff17283b;
            g.fill(l, u, l + cell, u + cell, shade);
            if (cell > 1 && mx >= l && mx < l + cell && my >= u && my < u + cell)
                g.renderOutline(l, u, cell, cell, WHITE);
        }
        Component hint = note.getString().isEmpty() ? t("grid.help") : note;
        if (gridY + px <= contentY + contentH - 38)
            g.drawCenteredString(font, hint, left + leftW / 2, contentY + contentH - 37,
                    note.getString().isEmpty() ? MUTE : noteColor);
    }

    private void drawPreview(GuiGraphics g, int mx, int my) {
        int l = right + 7, u = contentY + 22, rw = rightW - 14, rh = contentH - 73;
        int[] palettes = switch (scene) {
            case 1 -> new int[] {0xff28303d, 0xff0a1017, 0xff344152, 0xff192533};
            case 2 -> new int[] {0xffedf1f7, 0xffb2becc, 0xffcad7e0, 0xff758b9d};
            case 3 -> new int[] {0xffb07857, 0xff392332, 0xff7e4e46, 0xff572b38};
            default -> new int[] {0xff87bad9, 0xff315750, 0xff6193aa, 0xff496d5d};
        };
        g.fillGradient(l, u, l + rw, u + rh, palettes[0], palettes[1]);
        for (int i = 0; i < 8; i++) {
            int bx = l + rw * i / 8;
            int bx2 = l + rw * (i + 1) / 8;
            int by = u + rh * (48 + ((i * 173 + 21) % 34)) / 100;
            g.fill(bx, by, bx2, u + rh, palettes[2 + (i % 2)]);
        }
        g.renderOutline(l, u, rw, rh, LINE);
        int midX = l + rw / 2, midY = u + rh / 2;
        if (c.enabled) {
            if (!centerPreview && inside(mx, my, l, u, rw, rh)) {
                midX = mx;
                midY = my;
            }
            boolean[][] pattern = activePattern();
            float scale = !c.customScale || c.mode == Mode.SIMPLE ? 1 : switch (c.scale) {
                case SMALL -> .70f;
                case NORMAL -> 1;
                case LARGE -> 1.5f;
                case AUTO -> Math.min(1, 15f / pattern.length);
            };
            int rgb = previewRgb(c, previewTarget,
                    midY < u + rh * .55 ? palettes[0] : palettes[2]) | 0xff000000;
            int a = c.colorMode == ColorMode.VANILLA ? 255 : Math.round(c.alpha * 255 / 100f);
            g.enableScissor(l + 1, u + 1, l + rw - 1, u + rh - 1);
            drawPattern(g, pattern, midX, midY, scale, (rgb & 0xffffff) | (a << 24));
            g.disableScissor();
        }
        if (rh >= 48) {
            g.fill(l + 3, u + 3, l + 24, u + 13, 0xad0b1522);
            g.drawString(font, "TAB", l + 5, u + 4, WHITE);
        }
    }

    private static int previewRgb(CrosshairConfig c, int target, int backdrop) {
        if (c.colorMode == ColorMode.VANILLA) return ~backdrop & 0xffffff;
        if (c.colorMode == ColorMode.DYNAMIC && target != 0) return target == 1 ? c.friendly : c.enemy;
        if (c.chroma) return Color.HSBtoRGB((System.currentTimeMillis() % 4200L) / 4200f, .83f, 1);
        return c.color;
    }

    private static void drawPattern(GuiGraphics g, boolean[][] pattern, int mx, int my, float zoom, int argb) {
        int size = pattern.length;
        int display = Math.max(1, Math.round(size * zoom));
        int startX = mx - display / 2, startY = my - display / 2;
        for (int row = 0; row < display; row++) for (int col = 0; col < display; col++) {
            if (pattern[(int)((row + .5f) * size / display)][(int)((col + .5f) * size / display)])
                g.fill(startX + col, startY + row, startX + col + 1, startY + row + 1, argb);
        }
    }

    private static boolean inside(double mx, double my, int l, int t, int w, int h) {
        return mx >= l && my >= t && mx < l + w && my < t + h;
    }

    @Override public boolean keyPressed(KeyEvent key) {
        if (key.key() == GLFW.GLFW_KEY_TAB && !key.hasShiftDown()) {
            centerPreview = !centerPreview;
            return true;
        }
        if (key.key() == GLFW.GLFW_KEY_DELETE && c.mode == Mode.CUSTOM) {
            for (boolean[] row : c.custom) java.util.Arrays.fill(row, false);
            status("cleared", ACCENT);
            c.save();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (c.mode == Mode.CUSTOM && e.button() <= 1 &&
                inside(e.x(), e.y(), gridX, gridY, c.custom.length * cell, c.custom.length * cell)) {
            paintButton = e.button();
            lastX = gridCol(e.x()); lastY = gridRow(e.y());
            paint(lastX, lastY);
            note = Component.empty();
            return true;
        }
        if (c.colorMode == ColorMode.DYNAMIC && e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT &&
                inside(e.x(), e.y(), right + 7, contentY + 22, rightW - 14, contentH - 73)) {
            previewTarget = (previewTarget + 1) % 3;
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (paintButton != -1 && e.button() == paintButton) {
            int x1 = gridCol(e.x()), y1 = gridRow(e.y());
            int sx = Math.abs(x1 - lastX), sy = -Math.abs(y1 - lastY);
            int vx = lastX < x1 ? 1 : -1, vy = lastY < y1 ? 1 : -1, err = sx + sy;
            for (;;) {
                paint(lastX, lastY);
                if (lastX == x1 && lastY == y1) break;
                int nextError = 2 * err;
                if (nextError >= sy) { err += sy; lastX += vx; }
                if (nextError <= sx) { err += sx; lastY += vy; }
            }
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent e) {
        if (e.button() == paintButton) {
            paintButton = -1;
            c.save();
            return true;
        }
        return super.mouseReleased(e);
    }

    private int gridCol(double px) { return Math.clamp((int) Math.floor((px - gridX) / cell), 0, c.custom.length - 1); }
    private int gridRow(double py) { return Math.clamp((int) Math.floor((py - gridY) / cell), 0, c.custom.length - 1); }
    private void paint(int px, int py) { c.custom[py][px] = paintButton == GLFW.GLFW_MOUSE_BUTTON_LEFT; }

    @Override public void removed() { c.save(); super.removed(); }
    @Override public void onClose() { minecraft.setScreen(parent); }

    private static final class Slider extends AbstractSliderButton {
        private final String label;
        private final int min, max;
        private final IntConsumer changed;

        Slider(int x, int y, int w, String label, int min, int max, int current, IntConsumer changed) {
            super(x, y, w, 20, Component.empty(), Math.clamp((current - min) / (double) (max - min), 0d, 1d));
            this.label = label;
            this.min = min;
            this.max = max;
            this.changed = changed;
            updateMessage();
        }
        private int number() { return min + (int) Math.round(value * (max - min)); }
        @Override protected void updateMessage() { setMessage(t(label, number())); }
        @Override protected void applyValue() { changed.accept(number()); }
    }

    private static final class ImportScreen extends Screen {
        private final CrosshairScreen parent;
        private final CrosshairConfig c;
        private EditBox code;
        private int left, w, y;
        private boolean invalid;

        ImportScreen(CrosshairScreen parent, CrosshairConfig c) { super(t("import.title")); this.parent = parent; this.c = c; }

        @Override protected void init() {
            w = Math.min(280, width - 34);
            left = (width - w) / 2;
            y = height / 2 - 36;
            code = new EditBox(font, left, y, w, 20, t("import.title"));
            code.setMaxLength(5000);
            code.setHint(Component.literal("LCCH-…"));
            code.setResponder(ignored -> invalid = false);
            addRenderableWidget(code);
            addRenderableWidget(Button.builder(t("paste"), b -> code.setValue(minecraft.keyboardHandler.getClipboard().strip()))
                    .bounds(left, y + 25, w / 3 - 2, 20).build());
            addRenderableWidget(Button.builder(t("import"), b -> apply())
                    .bounds(left + w / 3, y + 25, w / 3 - 2, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                    .bounds(left + 2 * (w / 3), y + 25, w - 2 * (w / 3), 20).build());
            setInitialFocus(code);
        }

        private void apply() {
            boolean[][] pattern = CrosshairPatterns.importCode(code.getValue().strip());
            invalid = pattern == null;
            if (!invalid) {
                c.custom = pattern;
                c.mode = Mode.CUSTOM;
                parent.status("imported", ACCENT);
                c.save();
                onClose();
            }
        }

        @Override public boolean keyPressed(KeyEvent e) {
            if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) { apply(); return true; }
            return super.keyPressed(e);
        }

        @Override public void render(GuiGraphics g, int mx, int my, float delta) {
            g.fill(left - 8, y - 30, left + w + 8, y + 74, PANEL);
            g.renderOutline(left - 8, y - 30, w + 16, 104, LINE);
            super.render(g, mx, my, delta);
            g.drawCenteredString(font, getTitle(), width / 2, y - 20, WHITE);
            g.drawCenteredString(font, t(invalid ? "invalid" : "import.hint"), width / 2, y + 53,
                    invalid ? 0xffff8397 : MUTE);
        }

        @Override public void onClose() { minecraft.setScreen(parent); }
    }

    private static final class ColorScreen extends Screen {
        private final CrosshairScreen parent;
        private final CrosshairConfig c;
        private int x, y, w, left, top, wide, tall, bar, editSlot, drag;
        private EditBox hex;
        private final float[] hsv = new float[3];
        private boolean writingHex;

        ColorScreen(CrosshairScreen parent, CrosshairConfig c) {
            super(t("colors"));
            this.parent = parent;
            this.c = c;
            refreshHsv();
        }

        @Override protected void init() {
            w = Math.min(310, width - 24);
            x = (width - w) / 2;
            tall = Math.min(90, Math.max(32, height - 149));
            y = Math.max(7, (height - (tall + 137)) / 2);
            left = x + 9;
            top = y + 94;
            wide = Math.max(74, w - 84);
            bar = left + wide + 6;
            int hf = (w - 21) / 2;
            addColorButton(left, y + 28, w - 18, t("color.mode", colorMode()), b -> {
                c.colorMode = ColorMode.values()[(c.colorMode.ordinal() + 1) % ColorMode.values().length];
                if (c.colorMode != ColorMode.DYNAMIC) editSlot = 0;
                refreshHsv();
                rebuildWidgets();
            });
            var alpha = addRenderableWidget(new Slider(left, y + 50, hf, "alpha", 0, 100, c.alpha, v -> c.alpha = v));
            alpha.active = c.colorMode != ColorMode.VANILLA;
            var chroma = addColorButton(left + hf + 3, y + 50, hf, t("chroma", on(c.chroma)), b -> {
                c.chroma = !c.chroma;
                b.setMessage(t("chroma", on(c.chroma)));
            });
            chroma.active = c.colorMode != ColorMode.VANILLA;
            if (c.colorMode == ColorMode.DYNAMIC) {
                int pw = (w - 24) / 3;
                for (int i = 0; i < 3; i++) {
                    final int j = i;
                    var b = addColorButton(left + i * (pw + 3), y + 72, pw, t("color.slot." + i), ignored -> {
                        editSlot = j;
                        refreshHsv();
                        rebuildWidgets();
                    });
                    b.active = editSlot != i;
                }
            }
            if (c.colorMode != ColorMode.VANILLA) {
                hex = new EditBox(font, left + 25, top + tall + 5, 67, 20, t("hex"));
                hex.setMaxLength(7);
                hex.setValue(hexColor());
                hex.setResponder(input -> {
                    if (writingHex) return;
                    String value = input.startsWith("#") ? input.substring(1) : input;
                    boolean valid = value.matches("[0-9a-fA-F]{6}");
                    hex.setTextColor(valid ? WHITE : 0xffff8397);
                    if (valid) {
                        updateColor(Integer.parseInt(value, 16));
                        refreshHsv();
                    }
                });
                addRenderableWidget(hex);
            } else hex = null;
            int resetX = left + (hex == null ? 0 : 96);
            int resetW = Math.min(91, x + w - 96 - resetX);
            if (resetW >= 46) addColorButton(resetX, top + tall + 5, resetW, t("reset"), b -> {
                var confirm = new ConfirmScreen(ok -> {
                    if (ok) {
                        c.reset();
                        c.save();
                        minecraft.setScreen(parent);
                    } else minecraft.setScreen(this);
                }, t("reset.title"), t("reset.hint"));
                minecraft.setScreen(confirm);
            });
            addColorButton(x + w - 91, top + tall + 5, 82, Component.translatable("gui.done"), b -> onClose());
        }

        private Button addColorButton(int px, int py, int bw, Component label, Button.OnPress click) {
            return addRenderableWidget(Button.builder(label, click).bounds(px, py, bw, 20).build());
        }

        private Component colorMode() {
            return t("color." + c.colorMode.name().toLowerCase(java.util.Locale.ROOT));
        }

        private int pickedColor() {
            return switch (editSlot) { case 1 -> c.friendly; case 2 -> c.enemy; default -> c.color; };
        }

        private void updateColor(int rgb) {
            switch (editSlot) { case 1 -> c.friendly = rgb; case 2 -> c.enemy = rgb; default -> c.color = rgb; }
        }

        private String hexColor() { return String.format(java.util.Locale.ROOT, "#%06X", pickedColor() & 0xffffff); }

        private void refreshHsv() {
            int rgb = pickedColor();
            Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, hsv);
        }

        @Override public void render(GuiGraphics g, int mx, int my, float delta) {
            int bottom = top + tall + 31;
            g.fill(x, y, x + w, bottom, PANEL);
            g.renderOutline(x, y, w, bottom - y, LINE);
            g.fill(x + 1, y + 1, x + w - 1, y + 3, ACCENT);
            g.drawString(font, getTitle(), left, y + 12, WHITE);
            if (c.colorMode == ColorMode.VANILLA) {
                g.drawWordWrap(font, t("color.vanilla.help"), left, y + 81, w - 18, MUTE);
                int previewY = top + tall / 2;
                int previewW = Math.min(75, (w - 30) / 2);
                g.fill(x + w / 2 - previewW, previewY, x + w / 2, previewY + 26, 0xffe7e9f4);
                g.fill(x + w / 2, previewY, x + w / 2 + previewW, previewY + 26, 0xff273547);
                boolean[][] plus = CrosshairPatterns.simple(3, 2, 1, false);
                drawPattern(g, plus, x + w / 2 - previewW / 2, previewY + 13, 1, 0xff18160b);
                drawPattern(g, plus, x + w / 2 + previewW / 2, previewY + 13, 1, 0xffd8cab8);
            } else {
                if (c.colorMode == ColorMode.STATIC) g.drawString(font, t("color.pick"), left, y + 78, MUTE);
                drawPicker(g);
                g.drawString(font, "HEX", left, top + tall + 12, WHITE);
            }
            super.render(g, mx, my, delta);
        }

        private void drawPicker(GuiGraphics g) {
            g.renderOutline(left - 1, top - 1, wide + 2, tall + 2, LINE);
            g.renderOutline(bar - 1, top - 1, 15, tall + 2, LINE);
            for (int col = 0; col < wide; col++) {
                int shade = Color.HSBtoRGB(hsv[0], col / (float) (wide - 1), 1);
                g.fillGradient(left + col, top, left + col + 1, top + tall, shade, 0xff000000);
            }
            for (int row = 0; row < tall; row++) {
                int shade = Color.HSBtoRGB(row / (float) (tall - 1), 1, 1);
                g.fill(bar, top + row, bar + 13, top + row + 1, shade);
            }
            int cx = left + Math.round(hsv[1] * (wide - 1));
            int cy = top + Math.round((1 - hsv[2]) * (tall - 1));
            g.renderOutline(cx - 4, cy - 4, 9, 9, 0xff000000);
            g.renderOutline(cx - 3, cy - 3, 7, 7, WHITE);
            int hY = top + Math.round(hsv[0] * (tall - 1));
            g.renderOutline(bar - 2, hY - 2, 17, 5, 0xff000000);
            g.renderOutline(bar - 1, hY - 1, 15, 3, WHITE);
            int px = bar + 19, pw = x + w - 9 - px, py = top + tall / 2 - 11;
            for (int dx = 0; dx < pw; dx += 4) for (int dy = 0; dy < 24; dy += 4) {
                g.fill(px + dx, py + dy, px + Math.min(pw, dx + 4), py + dy + 4,
                        (((dx + dy) / 4) & 1) == 0 ? 0xff35465c : 0xff8190a3);
            }
            g.fill(px, py, px + pw, py + 24, (Math.round(c.alpha * 255 / 100f) << 24) | (pickedColor() & 0xffffff));
            g.renderOutline(px, py, pw, 24, LINE);
        }

        @Override public boolean mouseClicked(MouseButtonEvent e, boolean isDouble) {
            if (hex != null && e.button() == 0 && e.y() >= top && e.y() < top + tall) {
                if (e.x() >= left && e.x() < left + wide) drag = 1;
                else if (e.x() >= bar && e.x() < bar + 13) drag = 2;
                if (drag != 0) { clearFocus(); apply(e.x(), e.y()); return true; }
            }
            return super.mouseClicked(e, isDouble);
        }

        @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
            if (drag != 0 && e.button() == 0) { apply(e.x(), e.y()); return true; }
            return super.mouseDragged(e, dx, dy);
        }

        @Override public boolean mouseReleased(MouseButtonEvent e) {
            if (drag != 0 && e.button() == 0) { drag = 0; return true; }
            return super.mouseReleased(e);
        }

        private void apply(double mx, double my) {
            float v = Math.clamp((float) (my - top) / (tall - 1), 0, 1);
            if (drag == 2) hsv[0] = v;
            else {
                hsv[1] = Math.clamp((float) (mx - left) / (wide - 1), 0, 1);
                hsv[2] = 1 - v;
            }
            updateColor(Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xffffff);
            writingHex = true;
            try { hex.setValue(hexColor()); hex.setTextColor(WHITE); }
            finally { writingHex = false; }
        }

        @Override public void removed() { c.save(); super.removed(); }
        @Override public void onClose() { minecraft.setScreen(parent); }
    }
}
