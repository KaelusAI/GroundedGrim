package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.math.Vector3dm;
import ac.grim.grimac.utils.nmsutil.Collisions;
import ac.grim.grimac.utils.nmsutil.GetBoundingBox;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "AirStuck", description = "Checks for players freezing in air without position packets", experimental = true)
public class AirStuck extends Check implements PacketCheck {

    private long lastPositionTime = System.currentTimeMillis();
    private boolean enabled = true;
    private int maxMs = 2000;
    private double simX, simY, simZ;
    private double simVelY = 0;
    private boolean simulating = false;

    public AirStuck(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        enabled = config.getBooleanElse("AirStuck.enabled", true);
        maxMs = config.getIntElse("AirStuck.max-ticks", 40) * 50;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!enabled) return;

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            if (flying.hasPositionChanged()) {
                lastPositionTime = System.currentTimeMillis();
                simulating = false;
                simVelY = 0;
                return;
            }

            long elapsed = System.currentTimeMillis() - lastPositionTime;

            if (elapsed > maxMs && !player.onGround && !player.inVehicle()
                    && !player.isFlying && !player.compensatedEntities.self.isDead) {

                if (!simulating) {
                    simX = player.x;
                    simY = player.y;
                    simZ = player.z;
                    simVelY = 0;
                    simulating = true;
                }

                int simTicks = maxMs / 50;
                for (int i = 0; i < simTicks; i++) {
                    simVelY = (simVelY - 0.08) * 0.98;
                    double oldSimY = simY;
                    double oldPlayerY = player.y;
                    player.y = simY;
                    player.boundingBox = GetBoundingBox.getBoundingBoxFromPosAndSize(
                            player, simX, simY, simZ, 0.6f, 1.8f);
                    Vector3dm collided = Collisions.collide(player, 0, simVelY, 0);
                    player.y = oldPlayerY;
                    simY += collided.getY();
                    if (Math.abs(collided.getY()) < 0.001 && simVelY < 0) {
                        simVelY = 0;
                    }
                }

                if (flagAndAlert("ms=" + elapsed + " y=" + String.format("%.1f", simY))) {
                    if (player.platformPlayer != null) {
                        var world = player.platformPlayer.getWorld();
                        float yaw = player.yaw;
                        float pitch = player.pitch;
                        double finalY = simY;
                        double finalX = simX;
                        double finalZ = simZ;

                        GrimAPI.INSTANCE.getScheduler().getEntityScheduler().execute(
                                player.platformPlayer,
                                GrimAPI.INSTANCE.getGrimPlugin(),
                                () -> player.platformPlayer.teleportAsync(
                                        new ac.grim.grimac.utils.math.Location(
                                                world, finalX, finalY, finalZ, yaw, pitch)),
                                null, 0);
                    }
                    lastPositionTime = System.currentTimeMillis();
                }
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) {
            lastPositionTime = System.currentTimeMillis();
        }
    }
}
