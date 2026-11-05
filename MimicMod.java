package com.example.mimic;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class MimicMod implements ModInitializer {

	@Override
	public void onInitialize() {
		// Armor + held items: sync every 5 ticks.
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (world.getTime() % 5 == 0) {
				MimicManager.syncEquipment(world);
			}
		});

		// Walking / jumping: watch how each player moves.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				MimicManager.trackPlayer(player);
			}
		});

		// Breaking blocks.
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity sp) {
				MimicManager.onBreak(serverWorld, sp, pos);
			}
		});

		// Attacking.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (hand == Hand.MAIN_HAND && world instanceof ServerWorld serverWorld
					&& player instanceof ServerPlayerEntity sp) {
				MimicManager.onAttack(serverWorld, sp);
			}
			return ActionResult.PASS;
		});

		// /mimic on | off | range <blocks>   (range 0 = every loaded mob)
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(CommandManager.literal("mimic")
				.then(CommandManager.literal("on").executes(ctx -> {
					MimicManager.enabled = true;
					ctx.getSource().sendFeedback(() -> Text.literal("Copycat mobs: ON"), true);
					return 1;
				}))
				.then(CommandManager.literal("off").executes(ctx -> {
					MimicManager.enabled = false;
					ctx.getSource().sendFeedback(() -> Text.literal("Copycat mobs: OFF"), true);
					return 1;
				}))
				.then(CommandManager.literal("move")
					.then(CommandManager.literal("on").executes(ctx -> {
						MimicManager.copyMovement = true;
						ctx.getSource().sendFeedback(() -> Text.literal("Copycat movement: ON"), true);
						return 1;
					}))
					.then(CommandManager.literal("off").executes(ctx -> {
						MimicManager.copyMovement = false;
						ctx.getSource().sendFeedback(() -> Text.literal("Copycat movement: OFF"), true);
						return 1;
					})))
				.then(CommandManager.literal("drops")
					.then(CommandManager.literal("on").executes(ctx -> {
						MimicManager.dropEquipment = true;
						ctx.getSource().sendFeedback(() -> Text.literal("Copycat drops: ON"), true);
						return 1;
					}))
					.then(CommandManager.literal("off").executes(ctx -> {
						MimicManager.dropEquipment = false;
						ctx.getSource().sendFeedback(() -> Text.literal("Copycat drops: OFF"), true);
						return 1;
					})))
				.then(CommandManager.literal("range")
					.then(CommandManager.argument("blocks", IntegerArgumentType.integer(0, 1000))
						.executes(ctx -> {
							int r = IntegerArgumentType.getInteger(ctx, "blocks");
							MimicManager.range = r;
							ctx.getSource().sendFeedback(() -> Text.literal(
								r == 0 ? "Copycat range: every loaded mob" : "Copycat range: " + r + " blocks"), true);
							return 1;
						})))));
	}
}
