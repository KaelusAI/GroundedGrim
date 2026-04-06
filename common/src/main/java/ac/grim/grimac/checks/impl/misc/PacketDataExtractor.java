package ac.grim.grimac.checks.impl.misc;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import io.netty.buffer.ByteBuf;

public final class PacketDataExtractor {

    private PacketDataExtractor() {}

    public static String extractC2S(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();
        try {
            // Flying packets may have their buffer consumed by CheckManagerListener before PacketLogger runs.
            // Reset readerIndex to re-read the data, then restore it.
            if (WrapperPlayClientPlayerFlying.isFlying(type)) {
                return extractFlying(event, type);
            }
            if (type == PacketType.Play.Client.INTERACT_ENTITY) {
                var p = new WrapperPlayClientInteractEntity(event);
                return "entityId=" + p.getEntityId() + " action=" + p.getAction();
            }
            if (type == PacketType.Play.Client.ATTACK)
                return "entityId=" + new WrapperPlayClientAttack(event).getEntityId();
            if (type == PacketType.Play.Client.ANIMATION)
                return "hand=" + new WrapperPlayClientAnimation(event).getHand();
            if (type == PacketType.Play.Client.PLAYER_ABILITIES)
                return "isFlying=" + new WrapperPlayClientPlayerAbilities(event).isFlying();
            if (type == PacketType.Play.Client.ENTITY_ACTION)
                return "action=" + new WrapperPlayClientEntityAction(event).getAction();
            if (type == PacketType.Play.Client.PLAYER_INPUT) {
                var p = new WrapperPlayClientPlayerInput(event);
                return "fwd=" + p.isForward() + " back=" + p.isBackward() + " left=" + p.isLeft() + " right=" + p.isRight() + " jump=" + p.isJump() + " shift=" + p.isShift() + " sprint=" + p.isSprint();
            }
            if (type == PacketType.Play.Client.CLICK_WINDOW) {
                var p = new WrapperPlayClientClickWindow(event);
                return "windowId=" + p.getWindowId() + " slot=" + p.getSlot() + " button=" + p.getButton() + " mode=" + p.getWindowClickType();
            }
            if (type == PacketType.Play.Client.CLOSE_WINDOW)
                return "windowId=" + new WrapperPlayClientCloseWindow(event).getWindowId();
            if (type == PacketType.Play.Client.HELD_ITEM_CHANGE)
                return "slot=" + new WrapperPlayClientHeldItemChange(event).getSlot();
            if (type == PacketType.Play.Client.STEER_VEHICLE) {
                var p = new WrapperPlayClientSteerVehicle(event);
                return "sideways=" + p.getSideways() + " forward=" + p.getForward() + " jump=" + p.isJump() + " unmount=" + p.isUnmount();
            }
            if (type == PacketType.Play.Client.PLAYER_DIGGING) {
                var p = new WrapperPlayClientPlayerDigging(event);
                return "action=" + p.getAction() + " pos=" + p.getBlockPosition() + " face=" + p.getBlockFace();
            }
            if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
                var p = new WrapperPlayClientPlayerBlockPlacement(event);
                return "hand=" + p.getHand() + " pos=" + p.getBlockPosition() + " face=" + p.getFace();
            }
            if (type == PacketType.Play.Client.USE_ITEM)
                return "hand=" + new WrapperPlayClientUseItem(event).getHand();
        } catch (Exception e) {
            return "error=" + e.getClass().getSimpleName();
        }
        return "";
    }

    public static String extractS2C(PacketSendEvent event) {
        PacketTypeCommon type = event.getPacketType();
        try {
            if (type == PacketType.Play.Server.PLAYER_ABILITIES) {
                var p = new WrapperPlayServerPlayerAbilities(event);
                return "flySpeed=" + p.getFlySpeed() + " isFlying=" + p.isFlying() + " canFly=" + p.isFlightAllowed();
            }
            if (type == PacketType.Play.Server.ENTITY_VELOCITY) {
                var p = new WrapperPlayServerEntityVelocity(event);
                return "eid=" + p.getEntityId() + " vx=" + fmt(p.getVelocity().getX()) + " vy=" + fmt(p.getVelocity().getY()) + " vz=" + fmt(p.getVelocity().getZ());
            }
            if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
                var p = new WrapperPlayServerEntityRelativeMove(event);
                return "eid=" + p.getEntityId() + " dx=" + fmt(p.getDeltaX()) + " dy=" + fmt(p.getDeltaY()) + " dz=" + fmt(p.getDeltaZ()) + " ground=" + p.isOnGround();
            }
            if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
                var p = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
                return "eid=" + p.getEntityId() + " dx=" + fmt(p.getDeltaX()) + " dy=" + fmt(p.getDeltaY()) + " dz=" + fmt(p.getDeltaZ()) + " yaw=" + String.format("%.1f", p.getYaw()) + " pitch=" + String.format("%.1f", p.getPitch());
            }
            if (type == PacketType.Play.Server.ENTITY_ROTATION) {
                var p = new WrapperPlayServerEntityRotation(event);
                return "eid=" + p.getEntityId() + " yaw=" + String.format("%.1f", p.getYaw()) + " pitch=" + String.format("%.1f", p.getPitch());
            }
            if (type == PacketType.Play.Server.ENTITY_HEAD_LOOK) {
                var p = new WrapperPlayServerEntityHeadLook(event);
                return "eid=" + p.getEntityId() + " headYaw=" + String.format("%.1f", p.getHeadYaw());
            }
            if (type == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
                var p = new WrapperPlayServerEntityPositionSync(event);
                var values = p.getValues();
                var pos = values.getPosition();
                return "eid=" + p.getId() + " x=" + fmt(pos.getX()) + " y=" + fmt(pos.getY()) + " z=" + fmt(pos.getZ()) + " ground=" + p.isOnGround();
            }
            if (type == PacketType.Play.Server.SPAWN_ENTITY) {
                var p = new WrapperPlayServerSpawnEntity(event);
                return "eid=" + p.getEntityId() + " type=" + p.getEntityType().getName();
            }
            if (type == PacketType.Play.Server.DESTROY_ENTITIES) {
                var p = new WrapperPlayServerDestroyEntities(event);
                return "count=" + p.getEntityIds().length;
            }
            if (type == PacketType.Play.Server.UPDATE_HEALTH) {
                var p = new WrapperPlayServerUpdateHealth(event);
                return "health=" + fmt(p.getHealth()) + " food=" + p.getFood() + " sat=" + fmt(p.getFoodSaturation());
            }
            if (type == PacketType.Play.Server.ENTITY_METADATA) {
                var p = new WrapperPlayServerEntityMetadata(event);
                return "eid=" + p.getEntityId() + " entries=" + p.getEntityMetadata().size();
            }
            if (type == PacketType.Play.Server.UPDATE_ATTRIBUTES) {
                var p = new WrapperPlayServerUpdateAttributes(event);
                return "eid=" + p.getEntityId() + " attrs=" + p.getProperties().size();
            }
            if (type == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) {
                var p = new WrapperPlayServerPlayerPositionAndLook(event);
                return "x=" + fmt(p.getX()) + " y=" + fmt(p.getY()) + " z=" + fmt(p.getZ()) + " yaw=" + String.format("%.1f", p.getYaw()) + " pitch=" + String.format("%.1f", p.getPitch());
            }
            if (type == PacketType.Play.Server.EXPLOSION) {
                var p = new WrapperPlayServerExplosion(event);
                return "x=" + fmt(p.getPosition().getX()) + " y=" + fmt(p.getPosition().getY()) + " z=" + fmt(p.getPosition().getZ()) + " strength=" + p.getStrength();
            }
            if (type == PacketType.Play.Server.ENTITY_EQUIPMENT) {
                var p = new WrapperPlayServerEntityEquipment(event);
                return "eid=" + p.getEntityId() + " slots=" + p.getEquipment().size();
            }
            if (type == PacketType.Play.Server.ENTITY_STATUS) {
                var p = new WrapperPlayServerEntityStatus(event);
                return "eid=" + p.getEntityId() + " status=" + p.getStatus();
            }
        } catch (Exception e) {
            return "error=" + e.getClass().getSimpleName();
        }
        return "";
    }

    private static String extractFlying(PacketReceiveEvent event, PacketTypeCommon type) {
        ByteBuf buf = (ByteBuf) event.getByteBuf();
        int savedIndex = buf.readerIndex();
        buf.readerIndex(0);
        try {
            if (type == PacketType.Play.Client.PLAYER_POSITION) {
                var p = new WrapperPlayClientPlayerPosition(event);
                return pos(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(), Float.NaN, Float.NaN, p.isOnGround());
            }
            if (type == PacketType.Play.Client.PLAYER_ROTATION) {
                var p = new WrapperPlayClientPlayerRotation(event);
                return pos(Double.NaN, Double.NaN, Double.NaN, p.getYaw(), p.getPitch(), p.isOnGround());
            }
            if (type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
                var p = new WrapperPlayClientPlayerPositionAndRotation(event);
                return pos(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(), p.getLocation().getYaw(), p.getLocation().getPitch(), p.isOnGround());
            }
            if (type == PacketType.Play.Client.PLAYER_FLYING) {
                return "onGround=" + new WrapperPlayClientPlayerFlying(event).isOnGround();
            }
            return "";
        } catch (Exception e) {
            return "error=" + e.getClass().getSimpleName();
        } finally {
            buf.readerIndex(savedIndex);
        }
    }

    private static String pos(double x, double y, double z, float yaw, float pitch, boolean ground) {
        StringBuilder sb = new StringBuilder();
        if (!Double.isNaN(x)) sb.append("x=").append(String.format("%.4f", x)).append(' ');
        if (!Double.isNaN(y)) sb.append("y=").append(String.format("%.4f", y)).append(' ');
        if (!Double.isNaN(z)) sb.append("z=").append(String.format("%.4f", z)).append(' ');
        if (!Float.isNaN(yaw)) sb.append("yaw=").append(String.format("%.2f", yaw)).append(' ');
        if (!Float.isNaN(pitch)) sb.append("pitch=").append(String.format("%.2f", pitch)).append(' ');
        sb.append("ground=").append(ground);
        return sb.toString();
    }

    private static String fmt(double v) {
        return String.format("%.4f", v);
    }
}
