// This file was designed and is an original check for GrimAC
// Copyright (C) 2021 DefineOutside
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.BlockHitData;
import ac.grim.grimac.utils.data.EntityHitData;
import ac.grim.grimac.utils.data.HitData;
import ac.grim.grimac.utils.data.Pair;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.data.packetentity.PacketEntitySizeable;
import ac.grim.grimac.utils.data.packetentity.dragon.PacketEntityEnderDragonPart;
import ac.grim.grimac.utils.math.GrimMath;
import ac.grim.grimac.utils.math.Vector3dm;
import ac.grim.grimac.utils.nmsutil.ReachUtils;
import ac.grim.grimac.utils.nmsutil.WorldRayTrace;
import ac.grim.grimac.utils.viaversion.ViaVersionUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemAttackRange;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.viaversion.viaversion.api.Via;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// You may not copy the check unless you are licensed under GPL
@CheckData(name = "Reach", setback = 10)
public class Reach extends Check implements PacketCheck {

    private static final List<EntityType> blacklisted = Arrays.asList(
            EntityTypes.BOAT,
            EntityTypes.CHEST_BOAT,
            EntityTypes.SHULKER);
    private static final CheckResult NONE = new CheckResult(ResultType.NONE, "");
    public static final double extraSearchDistance = 3;
    // Only one flag per reach attack, per entity, per tick.
    // We store position because lastX isn't reliable on teleports.
    private final Int2ObjectMap<InteractionData> playerAttackQueue = new Int2ObjectOpenHashMap<>();
    // Temporarily used to prevent falses in the wall hit check
    private final Set<Vector3i> blocksChangedThisTick = new HashSet<>();
    private boolean cancelImpossibleHits;
    private boolean enableWallHit;
    private boolean enableEntityPierce;
    private boolean wallHitOnlyPlayers;
    private boolean entityPierceOnlyPlayers;
    public double threshold;
    private double hitboxExtraExpansion;
    private double reachExtraExpansion;
    private Set<String> wallHitIgnoredBlocks = new HashSet<>();
    private final java.util.HashMap<Integer, Double> cancelBuffers = new java.util.HashMap<>();

    public Reach(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (!player.disableGrim && event.getPacketType() == PacketType.Play.Client.ATTACK) {
            WrapperPlayClientAttack packet = new WrapperPlayClientAttack(event);
            onInteract(event, packet.getEntityId(), InteractionHand.MAIN_HAND);
        }

        if (!player.disableGrim && event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            onInteract(event, packet.getEntityId(), packet.getHand());
        }

        // If the player set their look, or we know they have a new tick
        final boolean isFlying = WrapperPlayClientPlayerFlying.isFlying(event.getPacketType());
        if (isUpdate(event.getPacketType())) {
            tickBetterReachCheckWithAngle(isFlying);
        }
    }

    private void onInteract(PacketReceiveEvent event, int entityId, InteractionHand hand) {
        // Don't let the player teleport to bypass reach
        if (player.getSetbackTeleportUtil().shouldBlockMovement()) {
            event.setCancelled(true);
            player.onPacketCancel();
            return;
        }

        PacketEntity entity = player.compensatedEntities.entityMap.get(entityId);
        // Stop people from freezing transactions before an entity spawns to bypass reach
        // TODO: implement dragon parts?
        if (entity == null || entity instanceof PacketEntityEnderDragonPart) {
            // Only cancel if and only if we are tracking this entity
            // This is because we don't track paintings.
            if (shouldModifyPackets() && player.compensatedEntities.serverPositionsMap.containsKey(entityId)) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
            return;
        }

        // Dead or unhittable entities cause false flags
        if (!entity.canHit()) return;

        // TODO: Remove when in front of via
        if (entity.type == EntityTypes.ARMOR_STAND && player.getClientVersion().isOlderThan(ClientVersion.V_1_8))
            return;
        // Prevents Happy Ghast Reach false on 1.21.6+ servers with ViaBackwards set up
        if (entity.type == EntityTypes.HAPPY_GHAST && player.getClientVersion().isOlderThan(ClientVersion.V_1_21_6))
            return;
        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR)
            return;
        if (player.inVehicle()) return;
        if (entity.riding != null) return;

        ItemStack currentStack = player.inventory.getItemInHand(hand);
        ItemStack startStack = player.inventory.getStartOfTickStack();

        boolean hasRange = false;
        float maxReach = 0f;
        float hitboxMargin = 0f;

        boolean clientAttackRangeExists = player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_11);
        boolean clientAndServerAgrees = clientAttackRangeExists && ATTACK_RANGE_COMPONENT_EXISTS;

        boolean viaVersionAvailable = false;
        if (USE_1_8_HITBOX_MARGIN && ViaVersionUtil.isAvailable) {
            viaVersionAvailable = Via.getConfig().getValues().containsKey("use-1_8-hitbox-margin") && Via.getConfig().use1_8HitboxMargin();
        }

        boolean clientAndViaVersion = clientAttackRangeExists && viaVersionAvailable;
        if (clientAndServerAgrees || clientAndViaVersion) {
            ItemAttackRange startRange = startStack.getComponentOr(ComponentTypes.ATTACK_RANGE, null);
            ItemAttackRange currentRange = currentStack.getComponentOr(ComponentTypes.ATTACK_RANGE, null);

            if (clientAndViaVersion) {
                if (startStack != ItemStack.EMPTY) {
                    startRange = new ItemAttackRange(0F, 3F, 0F, 4F, 0.1F, 1F);
                }

                if (currentStack != ItemStack.EMPTY) {
                    currentRange = new ItemAttackRange(0F, 3F, 0F, 4F, 0.1F, 1F);
                }
            }

            // If the start stack has no range component, the client defaults to vanilla reach behavior,
            // regardless of what the current stack is (No Range -> X = No Range used).
            if (startRange != null) {
                hasRange = true;
                if (currentRange == null) {
                    // Range (Start) -> No Range (Current)
                    // Client logic uses Start Range
                    maxReach = startRange.getMaxRange();
                    hitboxMargin = startRange.getHitboxMargin();
                } else {
                    // Range (Start) -> Range (Current)
                    // Client logic requires satisfying BOTH constraints
                    maxReach = Math.min(startRange.getMaxRange(), currentRange.getMaxRange());
                    hitboxMargin = Math.min(startRange.getHitboxMargin(), currentRange.getHitboxMargin());
                }
            }
        }

        boolean tooManyAttacks = playerAttackQueue.size() > 10;
        if (!tooManyAttacks) {
            playerAttackQueue.put(entityId, new InteractionData(
                    player.x, player.y, player.z,
                    hasRange, maxReach, hitboxMargin
            )); // Queue for next tick for very precise check
        }

        boolean knownInvalid = isKnownInvalid(entityId, entity, hasRange, maxReach, hitboxMargin);

        if ((shouldModifyPackets() && cancelImpossibleHits && knownInvalid) || tooManyAttacks) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }

    // This method finds the most optimal point at which the user should be aiming at
    // and then measures the distance between the player's eyes and this target point
    //
    // It will not cancel every invalid attack but should cancel 3.05+ or so in real-time
    // Let the post look check measure the distance, as it will always return equal or higher
    // than this method.  If this method flags, the other method WILL flag.
    //
    // Meaning that the other check should be the only one that flags.
    private boolean isKnownInvalid(int entityId, PacketEntity reachEntity, boolean hasAttackRange, float itemMaxReach, float itemHitboxMargin) {
        // If the entity doesn't exist, or if it is exempt, or if it is dead
        if ((blacklisted.contains(reachEntity.type) || !reachEntity.isLivingEntity) && reachEntity.type != EntityTypes.END_CRYSTAL)
            return false; // exempt

        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR)
            return false;
        if (player.inVehicle()) return false;

        double buffer = cancelBuffers.getOrDefault(entityId, 0.0);
        // Filter out what we assume to be cheats
        if (buffer != 0) {
            CheckResult result = checkReach(entityId, reachEntity, player.x, player.y, player.z, hasAttackRange, itemMaxReach, itemHitboxMargin, false);
            return result.isFlag(); // If they flagged
        } else {
            SimpleCollisionBox targetBox = getTargetBox(reachEntity);

            double maxReach = applyReachModifiers(targetBox, hasAttackRange, itemMaxReach, itemHitboxMargin, !player.packetStateData.didLastMovementIncludePosition);
            return ReachUtils.getMinReachToBox(player, targetBox) > maxReach;
        }
    }

    private void tickBetterReachCheckWithAngle(boolean isFlying) {
        for (Int2ObjectMap.Entry<InteractionData> attack : playerAttackQueue.int2ObjectEntrySet()) {
            PacketEntity reachEntity = player.compensatedEntities.entityMap.get(attack.getIntKey());
            if (reachEntity == null) continue;

            int entityId = attack.getIntKey();
            InteractionData interactionData = attack.getValue();
            CheckResult result = checkReach(entityId, reachEntity, interactionData.x, interactionData.y, interactionData.z, interactionData.hasAttackRange, interactionData.maxReach, interactionData.hitboxMargin, false);
            switch (result.type()) {
                case REACH -> {
                    String added = ", type=" + reachEntity.type.getName().getKey();
                    if (reachEntity instanceof PacketEntitySizeable sizeable) {
                        added += ", size=" + sizeable.size;
                    }
                    flagAndAlert(result.verbose() + added);
                }
                case HITBOX -> {
                    String added = ", type=" + reachEntity.type.getName().getKey();
                    if (reachEntity instanceof PacketEntitySizeable sizeable) {
                        added += ", size=" + sizeable.size;
                    }
                    player.checkManager.getCheck(Hitboxes.class).flagAndAlert(result.verbose() + added);
                }
                case WALL_HIT -> {
                    String added = reachEntity.type == EntityTypes.PLAYER ? "" : ", type=" + reachEntity.type.getName().getKey();
                    player.checkManager.getCheck(WallHit.class).flagAndAlert(result.verbose() + added);
                }
                case ENTITY_PIERCE -> {
                    String added = reachEntity.type == EntityTypes.PLAYER ? "" : ", type=" + reachEntity.type.getName().getKey();
                    player.checkManager.getCheck(EntityPierce.class).flagAndAlert(result.verbose() + added);
                }
            }
        }

        playerAttackQueue.clear();
        cancelBuffers.keySet().removeIf(id -> !player.compensatedEntities.entityMap.containsKey(id));
        // We can't use transactions for this because of timing issues with block changes
        if (isFlying) blocksChangedThisTick.clear();
    }

    @NotNull
    private CheckResult checkReach(int entityId, PacketEntity reachEntity, double x, double y, double z, boolean hasAttackRange, float itemMaxReach, float itemHitboxMargin, boolean isPrediction) {
        SimpleCollisionBox targetBox = getTargetBox(reachEntity);

        double maxReach = applyReachModifiers(targetBox, hasAttackRange, itemMaxReach, itemHitboxMargin, !player.packetStateData.didLastLastMovementIncludePosition);
        if (hitboxExtraExpansion > 0) targetBox.expand(hitboxExtraExpansion);
        double minDistance = Double.MAX_VALUE;

        // Stores look vectors and eye heights that successfully hit the target entity
        // Used later for WallHit/EntityPierce ray trace obstruction checks
        List<Pair<Vector3dm, Double>> lookVecsAndEyeHeights = new ArrayList<>();

        // +3 would be 3 + 3 = 6, which is the pre-1.20.5 behaviour, preventing "Missed Hitbox"
        final double distance = maxReach + extraSearchDistance;

        final double[] possibleEyeHeights = player.getPossibleEyeHeights();
        // https://bugs.mojang.com/browse/MC-67665
        final Vector3dm[] possibleLookDirs = player.getPossibleLookVectors(isPrediction);
        final Vector3dm eyePos = new Vector3dm(x, 0, z);
        for (Vector3dm lookVec : possibleLookDirs) {
            for (double eye : possibleEyeHeights) {
                eyePos.setY(y + eye);
                Vector3dm endReachPos = eyePos.clone().add(lookVec.getX() * distance, lookVec.getY() * distance, lookVec.getZ() * distance);

                Vector3dm intercept = ReachUtils.calculateIntercept(targetBox, eyePos, endReachPos).first();

                if (ReachUtils.isVecInside(targetBox, eyePos)) {
                    minDistance = 0;
                    break;
                }

                if (intercept != null) {
                    minDistance = Math.min(eyePos.distance(intercept), minDistance);
                    lookVecsAndEyeHeights.add(new Pair<>(lookVec, eye));
                }
            }
        }

        // Check for obstructions (blocks/entities) between player and target
        HitData foundHitData = null;
        boolean isTargetPlayer = reachEntity.type == EntityTypes.PLAYER;
        boolean checkWallHit = enableWallHit && (!wallHitOnlyPlayers || isTargetPlayer);
        boolean checkEntityPierce = enableEntityPierce && (!entityPierceOnlyPlayers || isTargetPlayer);
        // Only check if at least one obstruction check is enabled, entity is within reach,
        // and not near glitchy entities (shulkers, pistons)
        if ((checkWallHit || checkEntityPierce)
                && minDistance <= distance - extraSearchDistance
                && !player.compensatedWorld.isNearHardEntity(player.boundingBox.copy().expand(4))) {
            final @Nullable Pair<Double, HitData> hitResult = WorldRayTrace.didRayTraceHit(player, reachEntity, lookVecsAndEyeHeights, x, y, z);
            HitData hitData = hitResult.second();
            // Hit a different entity than the target (EntityPierce)
            if (checkEntityPierce && hitData instanceof EntityHitData &&
                    player.compensatedEntities.getPacketEntityID(((EntityHitData) hitData).getEntity()) != player.compensatedEntities.getPacketEntityID(reachEntity)) {
                minDistance = Double.MIN_VALUE;
                foundHitData = hitData;
            // Hit a block that wasn't changed this tick (WallHit)
            } else if (checkWallHit && hitData instanceof BlockHitData
                    && !blocksChangedThisTick.contains(((BlockHitData) hitData).position())
                    && !wallHitIgnoredBlocks.contains(((BlockHitData) hitData).state().getType().getName().toUpperCase())) {
                minDistance = Double.MIN_VALUE;
                foundHitData = hitData;
            }
        }

        // if the entity is not exempt and the entity is alive
        if ((!blacklisted.contains(reachEntity.type) && reachEntity.isLivingEntity) || reachEntity.type == EntityTypes.END_CRYSTAL) {
            // Obstruction detected - player hit through a block or another entity
            if (minDistance == Double.MIN_VALUE && foundHitData != null) {
                cancelBuffers.put(entityId, 1.0);
                if (foundHitData instanceof BlockHitData) {
                    return new CheckResult(ResultType.WALL_HIT, "block=" + ((BlockHitData) foundHitData).state().getType().getName() + " ");
                } else {
                    return new CheckResult(ResultType.ENTITY_PIERCE, "entity=" + ((EntityHitData) foundHitData).getEntity().type.getName() + " ");
                }
            } else if (minDistance == Double.MAX_VALUE) {
                cancelBuffers.put(entityId, 1.0);
                return new CheckResult(ResultType.HITBOX, "");
            } else if (minDistance > maxReach) {
                cancelBuffers.put(entityId, 1.0);
                return new CheckResult(ResultType.REACH, String.format("%.5f", minDistance) + " blocks");
            } else {
                double buf = cancelBuffers.getOrDefault(entityId, 0.0);
                if (buf > 0) {
                    buf = Math.max(0, buf - 0.25);
                    if (buf == 0) cancelBuffers.remove(entityId);
                    else cancelBuffers.put(entityId, buf);
                }
            }
        }

        return NONE;
    }

    private SimpleCollisionBox getTargetBox(PacketEntity reachEntity) {
        if (reachEntity.type == EntityTypes.END_CRYSTAL) { // Hardcode end crystal box
            return new SimpleCollisionBox(reachEntity.trackedServerPosition.getPos().subtract(1, 0, 1), reachEntity.trackedServerPosition.getPos().add(1, 2, 1));
        }
        return reachEntity.getPossibleCollisionBoxes();
    }

    private static final boolean ATTACK_RANGE_COMPONENT_EXISTS = PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_21_11);
    private static final boolean USE_1_8_HITBOX_MARGIN = PacketEvents.getAPI().getServerManager().getVersion().isOlderThanOrEquals(ServerVersion.V_1_8_8);

    private double applyReachModifiers(SimpleCollisionBox targetBox, boolean hasAttackRange, float itemMaxReach, float itemHitboxMargin, boolean giveMovementThreshold) {
        double maxReach;
        double hitboxMargin = threshold;

        if (hasAttackRange) {
            maxReach = itemMaxReach;
            hitboxMargin += itemHitboxMargin;
        } else {
            maxReach = player.compensatedEntities.self.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            // 1.7 and 1.8 players get a bit of extra hitbox (this is why you should use 1.8 on cross version servers)
            // Yes, this is vanilla and not uncertainty.  All reach checks have this or they are wrong.
            if (player.getClientVersion().isOlderThan(ClientVersion.V_1_9)) {
                hitboxMargin += 0.1f;
            }
        }

        // This is better than adding to the reach, as 0.03 can cause a player to miss their target
        // Adds some more than 0.03 uncertainty in some cases, but a good trade off for simplicity
        //
        // Just give the uncertainty on 1.9+ clients as we have no way of knowing whether they had 0.03 movement
        // However, on 1.21.2+ we do know if they had 0.03 movement
        if (giveMovementThreshold || player.canSkipTicks()) {
            hitboxMargin += player.getMovementThreshold();
        }

        targetBox.expand(hitboxMargin);

        return maxReach + reachExtraExpansion;
    }

    @Override
    public void onReload(ConfigManager config) {
        this.cancelImpossibleHits = config.getBooleanElse("Reach.block-impossible-hits", true);
        this.enableWallHit = config.getBooleanElse("WallHit.enabled", true);
        this.enableEntityPierce = config.getBooleanElse("EntityPierce.enabled", true);
        this.wallHitOnlyPlayers = config.getBooleanElse("WallHit.only-players", false);
        this.entityPierceOnlyPlayers = config.getBooleanElse("EntityPierce.only-players", false);
        this.wallHitIgnoredBlocks = new HashSet<>(config.getStringListElse("WallHit.ignored-blocks", new ArrayList<>()));
        this.threshold = config.getDoubleElse("Reach.threshold", 0.0005);
        this.reachExtraExpansion = config.getDoubleElse("Reach.extra-expansion", 0.0);
        this.hitboxExtraExpansion = config.getDoubleElse("Hitboxes.extra-expansion", 0.0);
    }

    // Track block changes to prevent WallHit false positives from dynamic blocks (doors, pistons etc.)
    public void handleBlockChange(Vector3i vector3i, WrappedBlockState state) {
        if (blocksChangedThisTick.size() >= 40) return; // Don't let players freeze movement packets to grow this
        // Only track nearby blocks
        if (GrimMath.distanceSquared(vector3i.x, vector3i.y, vector3i.z, player.x, player.y, player.z) > 36) return;
        // Only track if the state actually changed
        if (state.equals(player.compensatedWorld.getBlock(vector3i))) return;
        blocksChangedThisTick.add(vector3i);
    }

    private enum ResultType {
        REACH, HITBOX, WALL_HIT, ENTITY_PIERCE, NONE
    }

    private record CheckResult(ResultType type, String verbose) {
        public boolean isFlag() {
            return type != ResultType.NONE;
        }
    }

    private record InteractionData(double x, double y, double z, boolean hasAttackRange, float maxReach, float hitboxMargin) {
    }
}
