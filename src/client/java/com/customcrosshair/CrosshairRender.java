package com.customcrosshair;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/** Texture-based HUD glyph; no ray casts, packets or modification of the vanilla attack timer. */
public final class CrosshairRender {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("customcrosshair", "mask");
    private static DynamicTexture texture;
    private static int key = Integer.MIN_VALUE;
    private static CrosshairConfig.Mode cachedMode;
    private static int maskWidth, maskHeight;

    private CrosshairRender() {}

    private static final class PixelTexture extends DynamicTexture {
        PixelTexture(int w, int h) {
            super(() -> "Custom Crosshair mask", w, h, true);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }
    }

    public static void draw(GuiGraphics graphics) {
        var client = Minecraft.getInstance();
        var cfg = CrosshairConfig.INSTANCE;
        int state = switch (cfg.mode) {
            case SIMPLE -> (((cfg.length * 31 + cfg.gap) * 31 + cfg.thickness) * 2) + (cfg.dot ? 1 : 0);
            case PRESET -> cfg.preset;
            case CUSTOM -> java.util.Arrays.deepHashCode(cfg.custom);
        };
        if (texture == null || key != state || cachedMode != cfg.mode) {
            boolean[][] pattern = switch (cfg.mode) {
                case SIMPLE -> CrosshairPatterns.simple(cfg.length, cfg.gap, cfg.thickness, cfg.dot);
                case PRESET -> CrosshairPatterns.preset(cfg.preset);
                case CUSTOM -> cfg.custom;
            };
            if (pattern == null || pattern.length == 0 || pattern[0].length == 0) return;
            int w = pattern[0].length, h = pattern.length;
            if (texture == null || maskWidth != w || maskHeight != h) {
                texture = new PixelTexture(w, h);
                client.getTextureManager().register(ID, texture);
                maskWidth = w;
                maskHeight = h;
            }
            var img = texture.getPixels();
            for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
                img.setPixel(col, row, pattern[row][col] ? 0xFFFFFFFF : 0);
            }
            texture.upload();
            key = state;
            cachedMode = cfg.mode;
        }

        float zoom = 1;
        if (cfg.mode != CrosshairConfig.Mode.SIMPLE && cfg.customScale) {
            zoom = switch (cfg.scale) {
                case SMALL -> 0.70f;
                case NORMAL -> 1f;
                case LARGE -> 1.5f;
                case AUTO -> Math.min(1f, 15f / Math.max(maskWidth, maskHeight));
            };
        }
        int w = Math.max(1, Math.round(maskWidth * zoom));
        int h = Math.max(1, Math.round(maskHeight * zoom));
        int x = (graphics.guiWidth() - w) / 2;
        int y = (graphics.guiHeight() - h) / 2;
        boolean vanilla = cfg.colorMode == CrosshairConfig.ColorMode.VANILLA;
        int alpha = vanilla ? 255 : Math.round(Math.clamp(cfg.alpha, 0, 100) * 2.55f);
        if (alpha == 0) return;
        int rgb = vanilla ? 0xFFFFFF : color(client, cfg);
        graphics.blit(vanilla ? RenderPipelines.CROSSHAIR : RenderPipelines.GUI_TEXTURED,
                ID, x, y, 0f, 0f, w, h, maskWidth, maskHeight, maskWidth, maskHeight, (alpha << 24) | rgb);
    }

    private static int color(Minecraft client, CrosshairConfig cfg) {
        if (cfg.colorMode == CrosshairConfig.ColorMode.DYNAMIC
                && client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity other) {
            if (client.player != null && client.player.isAlliedTo(other)) return cfg.friendly & 0xFFFFFF;
            if (other instanceof Player || other instanceof Enemy) return cfg.enemy & 0xFFFFFF;
            return cfg.friendly & 0xFFFFFF;
        }
        if (cfg.chroma) {
            float hue = (System.nanoTime() % 4_000_000_000L) / 4_000_000_000f;
            return Color.HSBtoRGB(hue, 0.90f, 1f) & 0xFFFFFF;
        }
        return cfg.color & 0xFFFFFF;
    }
}
