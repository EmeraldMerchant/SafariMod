package com.safari.mod.mixin;

import com.safari.mod.render.ArmorStandTracerRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void safari$customGlow(
            CallbackInfo ci,
            @Local(name = "entity") Entity entity,
            @Local(name = "state") EntityRenderState state) {
        int color = ArmorStandTracerRenderer.getGlowColor(entity);

        if (color != -1) {
            state.outlineColor = color;
        }
    }
}