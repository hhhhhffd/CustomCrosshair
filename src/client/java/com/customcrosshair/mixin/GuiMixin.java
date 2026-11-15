package com.customcrosshair.mixin;

import com.customcrosshair.CrosshairConfig;
import com.customcrosshair.CrosshairRender;
import com.customcrosshair.CrosshairScreen;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class GuiMixin {
    // Redirect only the base sprite, retaining spectator gates and the vanilla attack indicator.
    @Redirect(method = "renderCrosshair", at = @At(value = "INVOKE", ordinal = 0, target =
        "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void customcrosshair$base(GuiGraphics graphics, RenderPipeline pipeline, Identifier sprite,
                                     int x, int y, int w, int h) {
        if (Minecraft.getInstance().screen instanceof CrosshairScreen editor && editor.previewMode()) return;
        if (CrosshairConfig.INSTANCE.enabled) CrosshairRender.draw(graphics);
        else graphics.blitSprite(pipeline, sprite, x, y, w, h);
    }
}
