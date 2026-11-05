package com.example.mimic;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core logic: every MobEntity copies what the nearest player does.
 * Block actions are copied relative to each mob's own position
 * (the same offset the player used from their own feet).
 */
public final class MimicManager {

	public static volatile boolean enabled = true;
	/** 0 = every loaded mob in the world, otherwise radius in blocks. */
	public static volatile int range = 0;
	/** true = copied armor/items drop when the mob dies (duplicates them). */
	public static volatile boolean dropEquipment = true;

	private static final double ATTACK_REACH = 3.0;

	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
		EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
	};

	private MimicManager() {
	}

	/** Mobs that currently follow this player (this player is their nearest player). */
	private static List<MobEntity> followers(ServerWorld world, ServerPlayerEntity player) {
		List<MobEntity> result = new ArrayList<>();
		if (!enabled || player.isSpectator()) {
			return result;
		}
		double maxSq = range <= 0 ? Double.MAX_VALUE : (double) range * range;
		boolean multiplayer = world.getPlayers().size() > 1;
		for (MobEntity mob : world.getEntitiesByType(TypeFilter.instanceOf(MobEntity.class), m -> m.isAlive())) {
			if (mob.squaredDistanceTo(player) > maxSq) {
				continue;
			}
			if (multiplayer && world.getClosestPlayer(mob, -1.0) != player) {
				continue;
			}
			result.add(mob);
		}
		return result;
	}

	private static void faceLikePlayer(MobEntity mob, PlayerEntity player) {
		mob.setYaw(player.getYaw());
		mob.setHeadYaw(player.getHeadYaw());
		mob.setBodyYaw(player.getBodyYaw());
		mob.setPitch(player.getPitch());
	}

	// ---------------------------------------------------------------- walking / jumping / looking

	/** true = mobs walk, jump and look exactly like the nearest player (their own AI is overridden). */
	public static volatile boolean copyMovement = true;

	private static final double WALK_SPEED = 0.215; // blocks per tick for a walking player
	private static final Map<UUID, MoveState> STATES = new ConcurrentHashMap<>();

	private static final class MoveState {
		Vec3d last;
		boolean lastOnGround;
		double sx; // smoothed horizontal movement per tick (world x)
		double sz; // smoothed horizontal movement per tick (world z)
		int jumpTicks;
	}

	/** Called every server tick for every player: works out how the player is moving. */
	public static void trackPlayer(ServerPlayerEntity player) {
		MoveState st = STATES.computeIfAbsent(player.getUuid(), id -> new MoveState());
		Vec3d pos = player.getPos();
		if (st.last != null) {
			double dx = pos.x - st.last.x;
			double dz = pos.z - st.last.z;
			if (Math.hypot(dx, dz) > 1.5) {
				dx = 0; // teleport / dimension change, ignore
				dz = 0;
			}
			st.sx = st.sx * 0.5 + dx * 0.5;
			st.sz = st.sz * 0.5 + dz * 0.5;
			boolean jumped = st.lastOnGround && !player.isOnGround() && pos.y - st.last.y > 0.05;
			if (jumped) {
				st.jumpTicks = 3;
			} else if (st.jumpTicks > 0) {
				st.jumpTicks--;
			}
		}
		st.last = pos;
		st.lastOnGround = player.isOnGround();
	}

	/** Called from a mixin every tick right after the mob's own AI ran. */
	public static void driveMob(MobEntity mob) {
		if (!enabled || !copyMovement || !mob.isAlive() || mob.hasVehicle()) {
			return;
		}
		if (!(mob.getWorld() instanceof ServerWorld world)) {
			return;
		}
		PlayerEntity near = world.getClosestPlayer(mob, -1.0);
		if (!(near instanceof ServerPlayerEntity player) || player.isSpectator()) {
			return;
		}
		if (range > 0 && mob.squaredDistanceTo(player) > (double) range * range) {
			return;
		}
		MoveState st = STATES.get(player.getUuid());
		if (st == null) {
			return;
		}

		faceLikePlayer(mob, player);
		mob.getNavigation().stop();

		float forward = 0.0f;
		float side = 0.0f;
		double speed = Math.hypot(st.sx, st.sz);
		if (speed > 0.03) {
			double yaw = Math.toRadians(player.getYaw());
			// Split the world-space movement into "forward" and "left" relative to where the player faces.
			double f = st.sx * -Math.sin(yaw) + st.sz * Math.cos(yaw);
			double l = st.sx * Math.cos(yaw) + st.sz * Math.sin(yaw);
			double m = speed / WALK_SPEED;
			double scale = m > 1.3 ? 1.3 / m : 1.0;
			forward = (float) (f / WALK_SPEED * scale);
			side = (float) (l / WALK_SPEED * scale);
		}

		boolean flyer = mob.getAttributes().hasAttribute(EntityAttributes.GENERIC_FLYING_SPEED);
		double base = mob.getAttributeValue(flyer ? EntityAttributes.GENERIC_FLYING_SPEED : EntityAttributes.GENERIC_MOVEMENT_SPEED);
		mob.setMovementSpeed((float) base);
		mob.setForwardSpeed(forward);
		mob.setSidewaysSpeed(side);

		boolean inFluid = mob.isTouchingWater() || mob.isInLava();
		mob.setJumping(st.jumpTicks > 0 || inFluid);
	}

	// ---------------------------------------------------------------- armor / items

	public static void syncEquipment(ServerWorld world) {
		if (!enabled) {
			return;
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			for (MobEntity mob : followers(world, player)) {
				mob.setCanPickUpLoot(false);
				for (EquipmentSlot slot : SLOTS) {
					ItemStack want = player.getEquippedStack(slot);
					ItemStack have = mob.getEquippedStack(slot);
					if (!ItemStack.areEqual(want, have)) {
						mob.equipStack(slot, want.copy());
					}
					// A chance above 1.0 means the item always drops, even if the mob
					// was not killed by a player, and it keeps its durability.
					mob.setEquipmentDropChance(slot, dropEquipment ? 2.0f : 0.0f);
				}
			}
		}
	}

	// ---------------------------------------------------------------- placing

	public static void onPlace(ServerWorld world, ServerPlayerEntity player, BlockPos placedPos, BlockState placed) {
		if (placed.isAir() || placed.contains(Properties.DOUBLE_BLOCK_HALF) || placed.contains(Properties.BED_PART)) {
			return; // skip two-block structures
		}
		BlockPos offset = placedPos.subtract(player.getBlockPos());
		for (MobEntity mob : followers(world, player)) {
			BlockPos target = mob.getBlockPos().add(offset);
			if (!world.isInBuildLimit(target) || !world.getWorldBorder().contains(target)) {
				continue;
			}
			if (!world.getBlockState(target).isReplaceable()) {
				continue;
			}
			if (!placed.canPlaceAt(world, target) || !world.canPlace(placed, target, ShapeContext.absent())) {
				continue;
			}
			faceLikePlayer(mob, player);
			mob.swingHand(Hand.MAIN_HAND, true);
			world.setBlockState(target, placed);
		}
	}

	// ---------------------------------------------------------------- breaking

	public static void onBreak(ServerWorld world, ServerPlayerEntity player, BlockPos brokenPos) {
		BlockPos offset = brokenPos.subtract(player.getBlockPos());
		for (MobEntity mob : followers(world, player)) {
			BlockPos target = mob.getBlockPos().add(offset);
			if (!world.isInBuildLimit(target) || !world.getWorldBorder().contains(target)) {
				continue;
			}
			BlockState state = world.getBlockState(target);
			if (state.isAir() || state.getHardness(world, target) < 0.0f) {
				continue; // air or unbreakable (bedrock, barrier...)
			}
			faceLikePlayer(mob, player);
			mob.swingHand(Hand.MAIN_HAND, true);
			world.breakBlock(target, true, mob);
		}
	}

	// ---------------------------------------------------------------- dropping (Q)

	public static void onDrop(ServerWorld world, ServerPlayerEntity player, boolean entireStack) {
		for (MobEntity mob : followers(world, player)) {
			ItemStack held = mob.getMainHandStack();
			if (held.isEmpty()) {
				continue;
			}
			ItemStack toDrop = held.copy();
			if (entireStack) {
				mob.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			} else {
				toDrop.setCount(1);
				held.decrement(1);
				if (held.isEmpty()) {
					mob.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				}
			}
			faceLikePlayer(mob, player);
			mob.swingHand(Hand.MAIN_HAND, true);

			ItemEntity drop = new ItemEntity(world, mob.getX(), mob.getEyeY() - 0.3, mob.getZ(), toDrop);
			drop.setPickupDelay(40);
			drop.setVelocity(mob.getRotationVec(1.0f).multiply(0.3).add(0.0, 0.1, 0.0));
			world.spawnEntity(drop);
		}
	}

	// ---------------------------------------------------------------- attacking

	public static void onAttack(ServerWorld world, ServerPlayerEntity player) {
		for (MobEntity mob : followers(world, player)) {
			faceLikePlayer(mob, player);
			mob.swingHand(Hand.MAIN_HAND, true);

			Vec3d eye = mob.getEyePos();
			Vec3d dir = mob.getRotationVec(1.0f);
			Vec3d end = eye.add(dir.multiply(ATTACK_REACH));
			Box box = mob.getBoundingBox().stretch(dir.multiply(ATTACK_REACH)).expand(1.0);
			EntityHitResult hit = ProjectileUtil.raycast(mob, eye, end, box,
				e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof PlayerEntity) && e != mob,
				ATTACK_REACH * ATTACK_REACH);
			if (hit == null) {
				continue;
			}
			hurt(world, mob, hit.getEntity());
		}
	}

	private static void hurt(ServerWorld world, MobEntity mob, Entity target) {
		boolean hasAttackStat = mob.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (hasAttackStat) {
			try {
				mob.tryAttack(target);
				return;
			} catch (RuntimeException ignored) {
				// fall through to the plain damage path
			}
		}
		float damage = 2.0f;
		if (mob.getMainHandStack().getItem() instanceof SwordItem sword) {
			damage += sword.getAttackDamage();
		}
		target.damage(world.getDamageSources().mobAttack(mob), damage);
	}
}
