package ac.grim.grimac.utils.data;

import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.data.packetentity.PacketEntityShulker;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3i;

import java.util.Objects;

public class ShulkerData {
    public final int lastTransactionSent;
    public final boolean isClosing;

    public PacketEntity entity = null;
    public Vector3i blockPos = null;

    private int ticksSinceAction = 0;

    public ShulkerData(Vector3i position, int lastTransactionSent, boolean isClosing) {
        this.lastTransactionSent = lastTransactionSent;
        this.isClosing = isClosing;
        this.blockPos = position;
    }

    public ShulkerData(PacketEntityShulker entity, int lastTransactionSent, boolean isClosing) {
        this.lastTransactionSent = lastTransactionSent;
        this.isClosing = isClosing;
        this.entity = entity;
    }

    public void tick() {
        ticksSinceAction++;
    }

    public float getProgress() {
        if (isClosing) {
            return Math.max(0.0f, 1.0f - ticksSinceAction * 0.1f);
        }
        return Math.min(1.0f, ticksSinceAction * 0.1f);
    }

    public boolean tickIfGuaranteedFinished() {
        return isClosing && ticksSinceAction >= 25;
    }

    public SimpleCollisionBox getCollision() {
        if (blockPos != null) {
            return new SimpleCollisionBox(blockPos);
        }
        return entity.getPossibleCollisionBoxes();
    }

    public SimpleCollisionBox getDynamicCollision(BlockFace facing) {
        float progress = getProgress();
        double extension = 0.5 * progress;

        int bx, by, bz;
        if (blockPos != null) {
            bx = blockPos.getX();
            by = blockPos.getY();
            bz = blockPos.getZ();
        } else {
            SimpleCollisionBox entityBox = entity.getPossibleCollisionBoxes();
            bx = (int) Math.floor(entityBox.minX);
            by = (int) Math.floor(entityBox.minY);
            bz = (int) Math.floor(entityBox.minZ);
        }

        double minX = bx, minY = by, minZ = bz;
        double maxX = bx + 1, maxY = by + 1, maxZ = bz + 1;

        if (facing == null) facing = BlockFace.UP;

        switch (facing) {
            case UP -> maxY += extension;
            case DOWN -> minY -= extension;
            case NORTH -> minZ -= extension;
            case SOUTH -> maxZ += extension;
            case WEST -> minX -= extension;
            case EAST -> maxX += extension;
        }

        return new SimpleCollisionBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShulkerData that = (ShulkerData) o;
        return Objects.equals(entity, that.entity) && Objects.equals(blockPos, that.blockPos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entity, blockPos);
    }
}
