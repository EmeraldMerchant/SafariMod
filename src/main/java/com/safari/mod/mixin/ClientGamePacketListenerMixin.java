package com.safari.mod.mixin;

import com.safari.mod.SafariModClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientGamePacketListenerMixin {

    @Inject(
            method = "handleParticleEvent",
            at = @At("HEAD")
    )
    private void safari$handleParticle(
            ClientboundLevelParticlesPacket packet,
            CallbackInfo ci
    ) {
        SafariModClient.onParticlePacket(packet);
    }
}