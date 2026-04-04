package ac.grim.grimac.checks.impl.elytra;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

// Original check by DarknessAC
// https://github.com/1hendex/DarknessAC
@CheckData(name = "ElytraJ", description = "Checks for invalid elytra accelerations")
public class ElytraJ extends Check implements PacketCheck {

    private double buffer = 0;
    private double deltaY = 0;
    private double deltaXZ = 0;
    private double lastDeltaY = 0;
    private double lastDeltaXZ = 0;

    public ElytraJ(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (player.wasTouchingWater
                || player.wasSwimming
                || isInWeb()
                || player.onGround
                || player.lastOnGround
                || player.packetStateData.lastPacketWasOnePointSeventeenDuplicate
                || player.packetStateData.lastPacketWasTeleport) {
            return;
        }

        if (player.getTransactionPing() > 500 || GrimAPI.INSTANCE.getPlatformServer().getTPS() < 18.5) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            WrapperPlayClientEntityAction action = new WrapperPlayClientEntityAction(event);
            if (action.getAction() == WrapperPlayClientEntityAction.Action.START_FLYING_WITH_ELYTRA) {
                double totalSpeed = Math.hypot(deltaXZ, deltaY);
                if (totalSpeed < 0.1) {
                    buffer = Math.max(0, buffer - 0.5);
                    return;
                }

                double accelY = Math.abs(deltaY - lastDeltaY);
                double accelXZ = Math.abs(deltaXZ - lastDeltaXZ);

                if (accelY <= 0.0 && accelXZ <= 0.0) {
                    if (buffer++ > 5) {
                        flagAndAlert("accel=" + String.format("%.4f", accelXZ) + " | " + String.format("%.4f", accelY));
                    }
                } else {
                    buffer = Math.max(0, buffer - 0.075);
                    reward();
                }
            }
        }

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            lastDeltaY = deltaY;
            lastDeltaXZ = deltaXZ;

            double dx = player.x - player.lastX;
            double dz = player.z - player.lastZ;
            deltaY = Math.abs(player.y - player.lastY);
            deltaXZ = Math.hypot(dx, dz);
        }
    }

    private boolean isInWeb() {
        int blockX = (int) Math.floor(player.x);
        int blockY = (int) Math.floor(player.y);
        int blockZ = (int) Math.floor(player.z);
        return player.compensatedWorld.getBlock(blockX, blockY, blockZ).getType() == StateTypes.COBWEB;
    }
}
