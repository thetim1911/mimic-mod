package com.example.mimic.mixin;

import com.example.mimic.MimicManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

	/**
	 * Runs right after a mob's own AI has decided how to move this tick,
	 * so we can overwrite its walking input with the player's.
	 */
	@Inject(method = "tickMovement()V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;tickNewAi()V", shift = At.Shift.AFTER),
		require = 0)
	private void copycatmobs$drive(CallbackInfo ci) {
		if ((Object) this instanceof MobEntity mob) {
			MimicManager.driveMob(mob);
		}
	}
}
