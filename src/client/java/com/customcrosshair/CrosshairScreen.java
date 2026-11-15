package com.customcrosshair;

import java.awt.Color;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
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
    private static final int WHITE = 0xfff4f4f7, MUTE = 0xffa7a7b5, ACCENT = 0xffc1a5ff;
    private static final int PANEL = 0xf918181e, CARD = 0xff222229, LINE = 0xff393943;
    private static final String P = "customcrosshair.";
    private final Screen parent;
    private final CrosshairConfig c = INSTANCE;
    private int x, y, w, h, contentY, contentH, gridX, gridY, cell, lastX, lastY, paintButton = -1;
    private int presetPage;
    private boolean preview;
    private int noteColor = ACCENT;
    private Component note = Component.empty();

    public CrosshairScreen(Screen parent) {
        super(t("title"));
        this.parent = parent;
    }

    private static Component t(String k, Object... values) { return Component.translatable(P + k, values); }
    private static String key(Enum<?> option) { return option.name().toLowerCase(java.util.Locale.ROOT); }
    public boolean previewMode() { return preview; }

    @Override protected void init() {
        w = Math.min(372, width - 18);
        h = Math.min(268, height - 18);
        x = (width - w) / 2;
        y = (height - h) / 2;
        contentY = y + 61;
        contentH = h - 91;
        int tabsWidth = Math.min(247, w - 24);
        int tx = (width - tabsWidth) / 2;
        int tabW = (tabsWidth - 4) / 3;
        for (Mode mode : Mode.values()) {
            boolean selected = c.mode == mode;
            flat(tx + mode.ordinal() * (tabW + 2), y + 31, tabW, 21, t("mode." + key(mode)), selected, ignored -> {
                c.mode = mode;
                note = Component.empty();
                rebuildWidgets();
            });
        }
        switch (c.mode) {
            case SIMPLE -> initSimple();
            case PRESET -> initPresets();
            case CUSTOM -> initGrid();
        }

        int fy = y + h - 25, step = (w - 20) / 4, fx = x + 11;
        flat(fx, fy, step - 3, 18, t("enabled", on(c.enabled)), c.enabled, b -> {
            c.enabled = !c.enabled;
            rebuildWidgets();
        });
        flat(fx + step, fy, step - 3, 18, t("colors"), false, b -> minecraft.setScreen(new ColorScreen(this, c)));
        flat(fx + 2 * step, fy, step - 3, 18, t("show.preview"), false, b -> togglePreview())
                .setTooltip(Tooltip.create(t("preview.help")));
        flat(fx + 3 * step, fy, step - 3, 18, Component.translatable("gui.done"), false, b -> onClose());
    }

    private void initSimple() {
        int sw = Math.min(176, w - 45), sx = (width - sw) / 2;
        int iy = contentY + Math.max(12, (contentH - 98) / 2 + 3);
        addRenderableWidget(new Slider(sx, iy, sw, "length", CrosshairPatterns.MIN_LENGTH, CrosshairPatterns.MAX_LENGTH, c.length, v -> c.length = v));
        addRenderableWidget(new Slider(sx, iy + 23, sw, "gap", CrosshairPatterns.MIN_GAP, CrosshairPatterns.MAX_GAP, c.gap, v -> c.gap = v));
        addRenderableWidget(new Slider(sx, iy + 46, sw, "thickness", CrosshairPatterns.MIN_THICKNESS, CrosshairPatterns.MAX_THICKNESS, c.thickness, v -> c.thickness = v));
        flat(sx, iy + 72, sw, 18, t("dot", on(c.dot)), c.dot, b -> {
            c.dot = !c.dot;
            rebuildWidgets();
        });
    }

    private void initPresets() {
        int cols = 7, rows = 2, total = CrosshairPatterns.PRESET_NAMES.size();
        int gap = 4, tile = Math.min(Math.min(41, (contentH - 47) / 2), (w - 37 - gap * (cols - 1)) / cols);
        int sx = (width - cols * tile - (cols - 1) * gap) / 2;
        int sy = contentY + 20 + Math.max(0, (contentH - 120) / 3);
        int perPage = rows * cols, pages = (total + perPage - 1) / perPage;
        presetPage = Math.clamp(presetPage, 0, Math.max(0, pages - 1));
        for (int i = 0; i < perPage && i + presetPage * perPage < total; i++) {
            int index = i + presetPage * perPage;
            int px = sx + i % cols * (tile + gap);
            int py = sy + i / cols * (tile + gap);
            Component name = Component.literal(CrosshairPatterns.PRESET_NAMES.get(index));
            var b = new FlatButton(px, py, tile, tile, name, index == c.preset, ignored -> {
                c.preset = index;
                rebuildWidgets();
            }, CrosshairPatterns.preset(index));
            b.setTooltip(Tooltip.create(name));
            addRenderableWidget(b);
        }

        if (pages > 1) {
            flat(x + 12, contentY + contentH - 18, 23, 16, Component.literal("‹"), false, b -> {
                presetPage = (presetPage + pages - 1) % pages;
                rebuildWidgets();
            });
            flat(x + w - 35, contentY + contentH - 18, 23, 16, Component.literal("›"), false, b -> {
                presetPage = (presetPage + 1) % pages;
                rebuildWidgets();
            });
        }
        flat(width / 2 - 105, contentY + contentH - 19, 103, 17, t("preset.edit"), false, b -> {
            minecraft.setScreen(new ConfirmScreen(ok -> {
                if (ok) {
                    var source = CrosshairPatterns.preset(c.preset);
                    c.custom = java.util.Arrays.stream(source).map(boolean[]::clone).toArray(boolean[][]::new);
                    c.mode = Mode.CUSTOM;
                    c.save();
                }
                minecraft.setScreen(this);
            }, t("preset.edit.title"), t("preset.edit.hint")));
        });
        scale(width / 2 + 3, contentY + contentH - 19, 102);
    }

    private int canvasSide() { return Math.max(CrosshairPatterns.EDITOR_SIZE, c.custom.length); }
    private int gridOffset() { return canvasSide() / 2 - c.custom.length / 2; }

    private void initGrid() {
        int side = canvasSide();
        cell = Math.max(1, Math.min(9, Math.min(w - 142, contentH - 25) / side));
        gridX = (width - side * cell) / 2;
        gridY = contentY + 13;
        int sideWidth = Math.min(89, (w - side * cell) / 2 - 15);
        int lx = gridX - 9 - sideWidth, rx = gridX + side * cell + 9, by = gridY + 16;
        flat(lx, by, sideWidth, 18, t("import"), false, b -> minecraft.setScreen(new ImportScreen(this, c)));
        flat(lx, by + 21, sideWidth, 18, t("copy"), false, b -> {
            minecraft.keyboardHandler.setClipboard(CrosshairPatterns.exportCode(c.custom));
            status("copied", ACCENT);
        });
        scale(rx, by, sideWidth);
        flat(rx, by + 21, sideWidth, 18, t("clear.button"), false, b -> clearGrid());
    }

    private Button scale(int px, int py, int bw) {
        var b = flat(px, py, bw, 17, scaleLabel(), c.customScale, ignored -> {
            if (!c.customScale) { c.customScale = true; c.scale = Scale.SMALL; }
            else if (c.scale == Scale.AUTO) c.customScale = false;
            else c.scale = Scale.values()[c.scale.ordinal() + 1];
            rebuildWidgets();
        });
        b.setTooltip(Tooltip.create(t("scale.help")));
        return b;
    }

    private void clearGrid() {
        c.custom = new boolean[canvasSide()][canvasSide()];
        status("cleared", ACCENT);
        c.save();
    }

    private void status(String key, int color) {
        note = t(key);
        noteColor = color;
    }

    private static Component on(boolean value) { return Component.translatable(value ? "options.on" : "options.off"); }
    private Component scaleLabel() { return t("scale", t(c.customScale ? "scale." + key(c.scale) : "scale.default")); }
    private Button flat(int px, int py, int bw, int bh, Component label, boolean selected, Button.OnPress click) {
        return addRenderableWidget(new FlatButton(px, py, bw, bh, label, selected, click, null));
    }

    private static final class FlatButton extends Button {
        private final boolean selected;
        private final boolean[][] glyph;
        FlatButton(int x, int y, int w, int h, Component title, boolean selected, Button.OnPress pressed, boolean[][] glyph) {
            super(x, y, w, h, title, pressed, DEFAULT_NARRATION);
            this.selected = selected;
            this.glyph = glyph;
        }

        @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
            int x = getX(), y = getY();
            int fill = selected ? 0xff3b3053 : isHoveredOrFocused() ? 0xff35353f : CARD;
            g.fill(x, y, x + width, y + height, fill);
            if (selected || isHoveredOrFocused()) g.renderOutline(x, y, width, height, selected ? ACCENT : LINE);
            if (glyph != null) {
                float zoom = Math.min(1.45f, (Math.min(width, height) - 7f) / glyph.length);
                drawPattern(g, glyph, x + width / 2, y + height / 2, zoom, selected ? ACCENT : WHITE);
            } else {
                var font = Minecraft.getInstance().font;
                g.drawCenteredString(font, font.plainSubstrByWidth(getMessage().getString(), width - 5),
                        x + width / 2, y + (height - 8) / 2, active ? WHITE : MUTE);
            }
        }
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!preview) super.renderBackground(g, mouseX, mouseY, delta);
        else if (minecraft.level == null) g.fill(0, 0, width, height, 0xff30323b);
    }

    private void togglePreview() { preview = !preview; }
    @Override public boolean isPauseScreen() { return !preview; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (preview) {
            if (c.enabled) CrosshairRender.draw(g);
            if (minecraft.level == null)
                g.drawCenteredString(font, t("preview.help"), width / 2, height - 23, MUTE);
            return;
        }
        g.fill(x, y, x + w, y + h, PANEL);
        g.renderOutline(x, y, w, h, LINE);
        g.drawCenteredString(font, getTitle(), width / 2, y + 14, WHITE);
        g.fill(x + 11, y + 57, x + w - 11, y + 58, LINE);
        g.fill(x + 11, y + h - 31, x + w - 11, y + h - 30, LINE);
        String heading = c.mode == Mode.CUSTOM ? "heading.custom" : "heading." + key(c.mode);
        Component label = c.mode == Mode.CUSTOM ? t(heading, canvasSide(), canvasSide()) : t(heading);
        g.drawCenteredString(font, label, width / 2, contentY + 2, ACCENT);
        if (c.mode == Mode.CUSTOM) drawGrid(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
    }

    private void drawGrid(GuiGraphics g, int mx, int my) {
        int side = canvasSide(), offset = gridOffset(), px = side * cell;
        g.renderOutline(gridX - 2, gridY - 2, px + 4, px + 4, LINE);
        for (int row = 0; row < side; row++) for (int col = 0; col < side; col++) {
            int l = gridX + col * cell, u = gridY + row * cell, inX = col - offset, inY = row - offset;
            boolean on = inY >= 0 && inY < c.custom.length && inX >= 0 && inX < c.custom.length && c.custom[inY][inX];
            int base = ((row + col) & 1) == 0 ? 0xff2c2c35 : 0xff25252e;
            g.fill(l, u, l + cell, u + cell, on ? ACCENT : base);
            if (cell >= 4 && (col == side / 2 || row == side / 2))
                g.fill(l, u, l + 1, u + 1, on ? 0xff4b3e6a : 0xff484851);
            if (cell > 1 && inside(mx, my, l, u, cell, cell))
                g.renderOutline(l, u, cell, cell, WHITE);
        }
        boolean noNote = note.getString().isEmpty();
        Component hint = noNote ? t("grid.help") : note;
        if (gridY + px + 13 <= y + h - 31)
            g.drawCenteredString(font, hint, width / 2, gridY + px + 5, noNote ? MUTE : noteColor);
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
            togglePreview();
            return true;
        }
        if (preview && key.key() == GLFW.GLFW_KEY_ESCAPE) {
            togglePreview();
            return true;
        }
        if (preview) return true;
        if (key.key() == GLFW.GLFW_KEY_DELETE && c.mode == Mode.CUSTOM) {
            clearGrid();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (preview) return true;
        if (c.mode == Mode.CUSTOM && e.button() <= 1 &&
                inside(e.x(), e.y(), gridX, gridY, canvasSide() * cell, canvasSide() * cell)) {
            paintButton = e.button();
            lastX = gridCol(e.x()); lastY = gridRow(e.y());
            paint(lastX, lastY);
            note = Component.empty();
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

    private int gridCol(double px) { return Math.clamp((int) Math.floor((px - gridX) / cell), 0, canvasSide() - 1); }
    private int gridRow(double py) { return Math.clamp((int) Math.floor((py - gridY) / cell), 0, canvasSide() - 1); }
    private void paint(int px, int py) {
        int offset = gridOffset();
        boolean draw = paintButton == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        int i = px - offset, j = py - offset;
        boolean in = i >= 0 && j >= 0 && i < c.custom.length && j < c.custom.length;
        if ((in && c.custom[j][i] == draw) || (!in && !draw)) return;
        if (c.custom.length < canvasSide()) {
            c.custom = CrosshairPatterns.centerOnCanvas(c.custom, canvasSide());
            i = px; j = py;
        }
        c.custom[j][i] = draw;
    }

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
        @Override public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            int x = getX(), y = getY();
            int track = width - 8, thumb = x + 4 + (int)Math.round(value * track);
            g.fill(x, y, x + width, y + height, isHoveredOrFocused() ? 0xff30303a : CARD);
            var font = Minecraft.getInstance().font;
            g.drawString(font, getMessage(), x + 5, y + 3, active ? WHITE : MUTE);
            g.fill(x + 4, y + height - 4, x + width - 4, y + height - 2, LINE);
            if (active) {
                g.fill(x + 4, y + height - 4, thumb, y + height - 2, ACCENT);
                g.fill(thumb - 1, y + height - 5, thumb + 1, y + height - 1, WHITE);
            }
        }
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
            return addRenderableWidget(new FlatButton(px, py, bw, 20, label, false, click, null));
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
