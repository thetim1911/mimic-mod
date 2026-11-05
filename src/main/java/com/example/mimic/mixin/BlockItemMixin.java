package com.example.mimic.mixin;

import com.example.mimic.MimicManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {

	@Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
	private void copycatmobs$afterPlace(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
		if (!cir.getReturnValue().isAccepted()) {
			return;
		}
		PlayerEntity player = context.getPlayer();
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return;
		}
		if (!(context.getWorld() instanceof ServerWorld world)) {
			return;
		}
		BlockPos pos = context.getBlockPos();
		BlockState placed = world.getBlockState(pos);
		MimicManager.onPlace(world, serverPlayer, pos, placed);
	}
}
