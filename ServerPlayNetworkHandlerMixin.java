package com.example.mimic.mixin;

import com.example.mimic.MimicManager;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

	@Shadow
	public ServerPlayerEntity player;

	/** Q = drop one item, Ctrl+Q = drop the whole stack. */
	@Inject(method = "onPlayerAction(Lnet/minecraft/network/packet/c2s/play/PlayerActionC2SPacket;)V", at = @At("HEAD"))
	private void copycatmobs$onDrop(PlayerActionC2SPacket packet, CallbackInfo ci) {
		// This method first runs on the network thread, then again on the server thread.
		if (this.player.getServer() == null || !this.player.getServer().isOnThread()) {
			return;
		}
		PlayerActionC2SPacket.Action action = packet.getAction();
		if (action != PlayerActionC2SPacket.Action.DROP_ITEM
				&& action != PlayerActionC2SPacket.Action.DROP_ALL_ITEMS) {
			return;
		}
		if (this.player.getMainHandStack().isEmpty()) {
			return;
		}
		MimicManager.onDrop(this.player.getServerWorld(), this.player,
			action == PlayerActionC2SPacket.Action.DROP_ALL_ITEMS);
	}
}
