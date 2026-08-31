package ac.grim.grimac.utils.nmsutil;

import ac.grim.grimac.checks.impl.combat.Reach;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.CollisionData;
import ac.grim.grimac.utils.collisions.HitboxData;
import ac.grim.grimac.utils.collisions.datatypes.CollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.ComplexCollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.NoCollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.BlockHitData;
import ac.grim.grimac.utils.data.EntityHitData;
import ac.grim.grimac.utils.data.HitData;
import ac.grim.grimac.utils.data.Pair;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.math.GrimMath;
import ac.grim.grimac.utils.math.Vector3dm;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

@UtilityClass
public class WorldRayTrace {
    public static BlockHitData getNearestBlockHitResult(GrimPlayer player, StateType heldItem, boolean sourcesHaveHitbox, boolean fluidPlacement, boolean itemUsePlacement) {
        Vector3d startingPos = new Vector3d(player.x, player.y + player.getEyeHeight(), player.z);
        Vector3dm startingVec = new Vector3dm(startingPos.getX(), startingPos.getY(), startingPos.getZ());
        Ray trace = new Ray(player, startingPos.getX(), startingPos.getY(), startingPos.getZ(), player.yaw, player.pitch);
        final double distance = itemUsePlacement && player.getClientVersion().isOlderThan(ClientVersion.V_1_20_5) ? 5 : player.compensatedEntities.self.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        Vector3d endVec = trace.getPointAtDistance(distance);
        Vector3d endPos = new Vector3d(endVec.getX(), endVec.getY(), endVec.getZ());

        return traverseBlocks(player, startingPos, endPos, (block, vector3i) -> {
            if (fluidPlacement && player.getClientVersion().isOlderThan(ClientVersion.V_1_13) && CollisionData.getData(block.getType())
                    .getMovementCollisionBox(player, player.getClientVersion(), block, vector3i.getX(), vector3i.getY(), vector3i.getZ()).isNull()) {
                return null;
            }

            CollisionBox data = HitboxData.getBlockHitbox(player, heldItem, player.getClientVersion(), block, false, vector3i.getX(), vector3i.getY(), vector3i.getZ());
            List<SimpleCollisionBox> boxes = new ArrayList<>();
            data.downCast(boxes);

            double bestHitResult = Double.MAX_VALUE;
            Vector3dm bestHitLoc = null;
            BlockFace bestFace = null;

            for (SimpleCollisionBox box : boxes) {
                Pair<Vector3d, BlockFace> intercept = ReachUtils.calculateIntercept(box, trace.origin(), trace.getPointAtDistance(distance));
                if (intercept.first() == null) continue; // No intercept

                Vector3dm hitLoc = Vector3dm.from(intercept.first());

                if (hitLoc.distanceSquared(startingVec) < bestHitResult) {
                    bestHitResult = hitLoc.distanceSquared(startingVec);
                    bestHitLoc = hitLoc;
                    bestFace = intercept.second();
                }
            }
            if (bestHitLoc != null) {
                return new BlockHitData(vector3i, bestHitLoc, bestFace, block);
            }

            if (sourcesHaveHitbox &&
                    (player.compensatedWorld.isWaterSourceBlock(vector3i.getX(), vector3i.getY(), vector3i.getZ())
                            || player.compensatedWorld.getLavaFluidLevelAt(vector3i.getX(), vector3i.getY(), vector3i.getZ()) == (8 / 9f))) {
                double waterHeight = player.getClientVersion().isOlderThan(ClientVersion.V_1_13) ? 1
                        : player.compensatedWorld.getFluidLevelAt(vector3i.getX(), vector3i.getY(), vector3i.getZ());
                SimpleCollisionBox box = new SimpleCollisionBox(vector3i.getX(), vector3i.getY(), vector3i.getZ(), vector3i.getX() + 1, vector3i.getY() + waterHeight, vector3i.getZ() + 1);

                Pair<Vector3d, BlockFace> intercept = ReachUtils.calculateIntercept(box, trace.origin(), trace.getPointAtDistance(distance));

                if (intercept.first() != null) {
                    return new BlockHitData(vector3i, Vector3dm.from(intercept.first()), intercept.second(), block);
                }
            }

            return null;
        });
    }

    // Copied from MCP...
    // Returns null if there isn't anything.
    //
    // I do have to admit that I'm starting to like bifunctions/new java 8 things more than I originally did.
    // although I still don't understand Mojang's obsession with streams in some of the hottest methods... that kills performance
    public static BlockHitData traverseBlocks(GrimPlayer player, Vector3d start, Vector3d end, BiFunction<WrappedBlockState, Vector3i, BlockHitData> predicate) {
        // I guess go back by the collision epsilon?
        double endX = GrimMath.lerp(-1.0E-7D, end.x, start.x);
        double endY = GrimMath.lerp(-1.0E-7D, end.y, start.y);
        double endZ = GrimMath.lerp(-1.0E-7D, end.z, start.z);
        double startX = GrimMath.lerp(-1.0E-7D, start.x, end.x);
        double startY = GrimMath.lerp(-1.0E-7D, start.y, end.y);
        double startZ = GrimMath.lerp(-1.0E-7D, start.z, end.z);
        int floorStartX = GrimMath.floor(startX);
        int floorStartY = GrimMath.floor(startY);
        int floorStartZ = GrimMath.floor(startZ);

        if (start.equals(end)) return null;

        WrappedBlockState state = player.compensatedWorld.getBlock(floorStartX, floorStartY, floorStartZ);
        BlockHitData apply = predicate.apply(state, new Vector3i(floorStartX, floorStartY, floorStartZ));

        if (apply != null) {
            return apply;
        }

        double xDiff = endX - startX;
        double yDiff = endY - startY;
        double zDiff = endZ - startZ;
        double xSign = Math.signum(xDiff);
        double ySign = Math.signum(yDiff);
        double zSign = Math.signum(zDiff);

        double posXInverse = xSign == 0 ? Double.MAX_VALUE : xSign / xDiff;
        double posYInverse = ySign == 0 ? Double.MAX_VALUE : ySign / yDiff;
        double posZInverse = zSign == 0 ? Double.MAX_VALUE : zSign / zDiff;

        double d12 = posXInverse * (xSign > 0 ? 1.0D - GrimMath.frac(startX) : GrimMath.frac(startX));
        double d13 = posYInverse * (ySign > 0 ? 1.0D - GrimMath.frac(startY) : GrimMath.frac(startY));
        double d14 = posZInverse * (zSign > 0 ? 1.0D - GrimMath.frac(startZ) : GrimMath.frac(startZ));

        // Can't figure out what this code does currently
        while (d12 <= 1.0D || d13 <= 1.0D || d14 <= 1.0D) {
            if (d12 < d13) {
                if (d12 < d14) {
                    floorStartX += xSign;
                    d12 += posXInverse;
                } else {
                    floorStartZ += zSign;
                    d14 += posZInverse;
                }
            } else if (d13 < d14) {
                floorStartY += ySign;
                d13 += posYInverse;
            } else {
                floorStartZ += zSign;
                d14 += posZInverse;
            }

            state = player.compensatedWorld.getBlock(floorStartX, floorStartY, floorStartZ);
            apply = predicate.apply(state, new Vector3i(floorStartX, floorStartY, floorStartZ));

            if (apply != null) {
                return apply;
            }
        }

        return null;
    }

    @Nullable
    public static HitData getNearestHitResult(GrimPlayer player, PacketEntity targetEntity, Vector3dm eyePos, Vector3dm lookVec,
                                              @Nullable Set<String> ignoredBlocks, @Nullable Set<Vector3i> ignoredPositions) {

        double maxAttackDistance = player.compensatedEntities.self.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        double maxBlockDistance = player.compensatedEntities.self.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);

        Vector3d startingPos = new Vector3d(eyePos.getX(), eyePos.getY(), eyePos.getZ());
        Vector3dm startingVec = new Vector3dm(startingPos.getX(), startingPos.getY(), startingPos.getZ());
        Ray trace = new Ray(startingPos, new Vector3d(lookVec.getX(), lookVec.getY(), lookVec.getZ()));
        Vector3d endPos = trace.getPointAtDistance(maxBlockDistance);

        // Get block hit
        BlockHitData blockHitData = getTraverseResult(player, null, startingPos, startingVec, trace, endPos, false, true, maxBlockDistance, true, ignoredBlocks, ignoredPositions);
        Vector3dm closestHitVec = null;
        PacketEntity closestEntity = null;
        double closestDistanceSquared = blockHitData != null ? blockHitData.getBlockHitLocation().distanceSquared(startingVec) : maxAttackDistance * maxAttackDistance;

        for (PacketEntity entity : player.compensatedEntities.entityMap.values().stream().filter(PacketEntity::canHit).toList()) {
            SimpleCollisionBox box = null;
            // 1.7 and 1.8 players get a bit of extra hitbox (this is why you should use 1.8 on cross version servers)
            // Yes, this is vanilla and not uncertainty.  All reach checks have this or they are wrong.

            if (entity.equals(targetEntity)) {
                box = entity.getPossibleCollisionBoxes();
                box.expand(player.checkManager.get(Reach.class).threshold);
                // This is better than adding to the reach, as 0.03 can cause a player to miss their target
                // Adds some more than 0.03 uncertainty in some cases, but a good trade off for simplicity
                //
                // Just give the uncertainty on 1.9+ clients as we have no way of knowing whether they had 0.03 movement
                if (!player.packetStateData.didLastLastMovementIncludePosition || player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9))
                    box.expand(player.getMovementThreshold());
                if (ReachUtils.isVecInside(box, startingPos)) {
                    return new EntityHitData(entity, eyePos);
                }
            } else {
                CollisionBox b = entity.getMinimumPossibleCollisionBoxes();
                if (b instanceof NoCollisionBox) {
                    continue;
                }
                box = (SimpleCollisionBox) b;
                box.expand(-player.checkManager.get(Reach.class).threshold);
                // todo, shrink by reachThreshold as well for non-target entities?
                if (!player.packetStateData.didLastLastMovementIncludePosition || player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9))
                    box.expand(-player.getMovementThreshold());
            }
            if (player.getClientVersion().isOlderThan(ClientVersion.V_1_9)) {
                box.expand(0.1f);
            }


            Pair<Vector3d, BlockFace> intercept = ReachUtils.calculateIntercept(box, trace.origin(), trace.getPointAtDistance(Math.sqrt(closestDistanceSquared)));

            if (intercept.first() != null) {
                Vector3dm hitVec = Vector3dm.from(intercept.first());
                double distSquared = hitVec.distanceSquared(startingVec);
                if (distSquared < closestDistanceSquared) {
                    closestDistanceSquared = distSquared;
                    closestHitVec = hitVec;
                    closestEntity = entity;
                }
            }
        }

        return closestEntity == null ? blockHitData : new EntityHitData(closestEntity, closestHitVec);
    }

    // Checks if it was possible to hit a target entity
    // TODO refactor to return list of rays and why each of them didn't hit instead of closest obstruction
    // NOTE: It should be impossible for the returned Pair to be null
    // because all of the possibleLookVecsAndEyeHeights passed in should be ones that hit the target entity
    // in previous parts of this check when we didn't check for any obstructions like blocks/entities
    public static @NotNull Pair<@NotNull Double, @NotNull HitData> didRayTraceHit(GrimPlayer player, PacketEntity targetEntity,
                                                                                   List<Pair<Vector3dm, Double>> possibleLookVecsAndEyeHeights,
                                                                                   double x, double y, double z,
                                                                                   @Nullable Set<String> ignoredBlocks,
                                                                                   @Nullable Set<Vector3i> ignoredPositions) {
        HitData firstObstruction = null;
        double firstObstructionDistanceSq = 0;

        // Check every possible look direction and every possible eye height
        for (Pair<Vector3dm, Double> vectorDoublePair : possibleLookVecsAndEyeHeights) {
            Vector3dm lookVec = vectorDoublePair.first();
            double eye = vectorDoublePair.second();

            Vector3dm eyes = new Vector3dm(x, y + eye, z);
            // this function is completely 0.03 aware
            final HitData hitResult = WorldRayTrace.getNearestHitResult(player, targetEntity, eyes, lookVec, ignoredBlocks, ignoredPositions);

            // If we hit the target entity, it's a valid hit
            if (hitResult instanceof EntityHitData && ((EntityHitData) hitResult).getEntity().equals(targetEntity)) {
                double distanceSquared = eyes.distanceSquared(hitResult.getBlockHitLocation());
                return new Pair<>(distanceSquared, hitResult); // Legitimate hit
            } else if (hitResult != null && firstObstruction == null) {
                // Store the first obstruction only
                firstObstruction = hitResult;
                firstObstructionDistanceSq = eyes.distanceSquared(hitResult.getBlockHitLocation());
            }
        }

        // Return the first obstruction if no valid hit found
        // Since we sort eye heights by likeniness, we should in effect return the most likely (first) obstruction
        assert firstObstruction != null;
        return new Pair<>(firstObstructionDistanceSq, firstObstruction);
    }

    // The 0.03 shrink stands in for not knowing the eye position exactly, but shrinking each block on its
    // own invents a 0.06 slit at every seam between two flush blocks. A ray held level with a seam then
    // threads solid stone. Faces that another full block sits against keep their full size.
    // Answers depend on the cell, not on which sub-box of it we are shrinking, so they are worked out once.
    private static boolean[] sealedFaces(GrimPlayer player, Vector3i pos) {
        final int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return new boolean[]{
                neighbourSeals(player, x, y, z, -1, 0, 0),
                neighbourSeals(player, x, y, z, 1, 0, 0),
                neighbourSeals(player, x, y, z, 0, -1, 0),
                neighbourSeals(player, x, y, z, 0, 1, 0),
                neighbourSeals(player, x, y, z, 0, 0, -1),
                neighbourSeals(player, x, y, z, 0, 0, 1)};
    }

    private static void shrinkExposedFaces(GrimPlayer player, SimpleCollisionBox box, Vector3i pos, boolean[] sealed) {
        final double threshold = player.getMovementThreshold();
        final int x = pos.getX(), y = pos.getY(), z = pos.getZ();

        if (box.minX > x || !sealed[0]) box.minX += threshold;
        if (box.maxX < x + 1 || !sealed[1]) box.maxX -= threshold;
        if (box.minY > y || !sealed[2]) box.minY += threshold;
        if (box.maxY < y + 1 || !sealed[3]) box.maxY -= threshold;
        if (box.minZ > z || !sealed[4]) box.minZ += threshold;
        if (box.maxZ < z + 1 || !sealed[5]) box.maxZ -= threshold;
    }

    // A slab sitting on stone is flush with it even though neither is a full cube, so the test is whether
    // the neighbour reaches the shared plane and covers the whole face, not whether it fills its own cell.
    private static boolean neighbourSeals(GrimPlayer player, int x, int y, int z, int dx, int dy, int dz) {
        final int nx = x + dx, ny = y + dy, nz = z + dz;
        WrappedBlockState state = player.compensatedWorld.getBlock(nx, ny, nz);
        CollisionBox data = HitboxData.getBlockHitbox(player, null, player.getClientVersion(), state, false, nx, ny, nz);

        SimpleCollisionBox[] boxes = new SimpleCollisionBox[ComplexCollisionBox.DEFAULT_MAX_COLLISION_BOX_SIZE];
        int size = data.downCast(boxes);

        for (int i = 0; i < size; i++) {
            SimpleCollisionBox box = boxes[i];
            if (dx != 0) {
                boolean touches = dx > 0 ? box.minX <= x + 1 : box.maxX >= x;
                if (touches && box.minY <= y && box.maxY >= y + 1 && box.minZ <= z && box.maxZ >= z + 1) return true;
            } else if (dy != 0) {
                boolean touches = dy > 0 ? box.minY <= y + 1 : box.maxY >= y;
                if (touches && box.minX <= x && box.maxX >= x + 1 && box.minZ <= z && box.maxZ >= z + 1) return true;
            } else {
                boolean touches = dz > 0 ? box.minZ <= z + 1 : box.maxZ >= z;
                if (touches && box.minX <= x && box.maxX >= x + 1 && box.minY <= y && box.maxY >= y + 1) return true;
            }
        }
        return false;
    }

    // TODO replace shrinkBlocks boolean with a data structure/better way to represent
    // 1. We have a target block. Shrink everything by movementThreshold except expand target block (we are checking to see if it matches the target block)
    // 2. We do not have a target block. Shrink everything by movementThreshold()
    // 3. Do not expand or shrink everything, we do not expect 0.03/0.002 or we legacy example where we want to keep old behaviour
    private static BlockHitData getTraverseResult(GrimPlayer player, @Nullable StateType heldItem, Vector3d startingPos, Vector3dm startingVec, Ray trace, Vector3d endPos, boolean sourcesHaveHitbox, boolean checkInside, double knownDistance, boolean shrinkBlocks,
                                                  @Nullable Set<String> ignoredBlocks, @Nullable Set<Vector3i> ignoredPositions) {
        return traverseBlocks(player, startingPos, endPos, (block, vector3i) -> {
            // Skipped rather than reported: treating an exempt block as the answer ends the trace and
            // leaves whatever is behind it unexamined, which turns a slab held at eye level into a visor.
            if (ignoredPositions != null && ignoredPositions.contains(vector3i)) return null;
            if (ignoredBlocks != null && ignoredBlocks.contains(block.getType().getName().toUpperCase())) return null;
            // even though sometimes we are raytracing against a block that is the target block, we pass false to this function because it only applies a change for brewing stands in 1.8
            CollisionBox data = HitboxData.getBlockHitbox(player, heldItem, player.getClientVersion(), block, false, vector3i.getX(), vector3i.getY(), vector3i.getZ());
            SimpleCollisionBox[] boxes = new SimpleCollisionBox[ComplexCollisionBox.DEFAULT_MAX_COLLISION_BOX_SIZE];
            int size = data.downCast(boxes);

            double bestHitResult = Double.MAX_VALUE;
            Vector3dm bestHitLoc = null;
            BlockFace bestFace = null;
            boolean[] sealed = shrinkBlocks && size > 0 ? sealedFaces(player, vector3i) : null;

            for (int i = 0; i < size; i++) {
                if (shrinkBlocks) shrinkExposedFaces(player, boxes[i], vector3i, sealed);
                Pair<Vector3d, BlockFace> intercept = ReachUtils.calculateIntercept(boxes[i], trace.origin(), trace.getPointAtDistance(knownDistance));
                if (intercept.first() == null) continue; // No intercept

                Vector3dm hitLoc = Vector3dm.from(intercept.first());

                // If inside a block, return empty result for reach check (don't bother checking this?)
                if (checkInside && ReachUtils.isVecInside(boxes[i], trace.origin())) {
                    return null;
                }

                if (hitLoc.distanceSquared(startingVec) < bestHitResult) {
                    bestHitResult = hitLoc.distanceSquared(startingVec);
                    bestHitLoc = hitLoc;
                    bestFace = intercept.second();
                }
            }

            if (bestHitLoc != null) {
                return new BlockHitData(vector3i, bestHitLoc, bestFace, block);
            }

            if (sourcesHaveHitbox &&
                    (player.compensatedWorld.isWaterSourceBlock(vector3i.getX(), vector3i.getY(), vector3i.getZ())
                            || player.compensatedWorld.getLavaFluidLevelAt(vector3i.getX(), vector3i.getY(), vector3i.getZ()) == (8 / 9f))) {
                double waterHeight = player.compensatedWorld.getFluidLevelAt(vector3i.getX(), vector3i.getY(), vector3i.getZ());
                SimpleCollisionBox box = new SimpleCollisionBox(vector3i.getX(), vector3i.getY(), vector3i.getZ(), vector3i.getX() + 1, vector3i.getY() + waterHeight, vector3i.getZ() + 1);

                Pair<Vector3d, BlockFace> intercept = ReachUtils.calculateIntercept(box, trace.origin(), trace.getPointAtDistance(knownDistance));

                if (intercept.first() != null) {
                    return new BlockHitData(vector3i, Vector3dm.from(intercept.first()), intercept.second(), block);
                }
            }

            return null;
        });
    }
}
