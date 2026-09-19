package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.math.Vector3dm;
import ac.grim.grimac.utils.nmsutil.Collisions;
import ac.grim.grimac.utils.nmsutil.GetBoundingBox;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "AirStuck", description = "Checks for players freezing in air without position packets", experimental = true)
public class AirStuck extends Check implements PacketReceiveListener {

    private static final double MIN_REAL_DISTANCE_SQ = 0.01 * 0.01;

    private long lastPositionTime = System.currentTimeMillis();
    private long lastSimTime = 0;
    private long lastFlagTime = 0;
    private double anchorX = Double.NaN;
    private double anchorY = Double.NaN;
    private double anchorZ = Double.NaN;
    private boolean enabled = true;
    private int maxMs = 2000;
    private int flagCooldownMs = 3000;
    private boolean debug = false;

    public AirStuck(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        enabled = config.getBooleanElse("AirStuck.enabled", true);
        maxMs = config.getIntElse("AirStuck.max-ticks", 40) * 50;
        flagCooldownMs = config.getIntElse("AirStuck.flag-cooldown-ticks", 60) * 50;
        debug = config.getBooleanElse("AirStuck.debug", false);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!enabled) return;

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            if (player.packetStateData.lastPacketWasTeleport) {
                lastPositionTime = System.currentTimeMillis();
                anchorX = Double.NaN;
                return;
            }
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            if (flying.hasPositionChanged()) {
                double x = flying.getLocation().getX();
                double y = flying.getLocation().getY();
                double z = flying.getLocation().getZ();
                if (Double.isNaN(anchorX)) {
                    anchorX = x;
                    anchorY = y;
                    anchorZ = z;
                }
                double dx = x - anchorX, dy = y - anchorY, dz = z - anchorZ;
                if (dx * dx + dy * dy + dz * dz > MIN_REAL_DISTANCE_SQ) {
                    lastPositionTime = System.currentTimeMillis();
                    anchorX = x;
                    anchorY = y;
                    anchorZ = z;
                }
            }
        }

        checkTimeout();
    }

    private void checkTimeout() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastPositionTime;

        if (elapsed <= maxMs || player.inVehicle()
                || player.isFlying || player.isClimbing
                || player.wasTouchingWater || player.wasTouchingLava
                || player.compensatedEntities.self.isDead
                || player.compensatedEntities.getPotionLevelForPlayer(PotionTypes.LEVITATION).isPresent()
                || player.compensatedEntities.getPotionLevelForPlayer(PotionTypes.SLOW_FALLING).isPresent()
                || !player.getSetbackTeleportUtil().hasAcceptedSpawnTeleport
                || System.currentTimeMillis() - player.joinTime < 5000) {
            return;
        }

        if (now - lastSimTime < 50) return;
        lastSimTime = now;

        if (now - lastFlagTime < flagCooldownMs) return;

        final int minY = player.compensatedWorld.getMinHeight();

        double startX, startY, startZ;
        if (player.platformPlayer != null) {
            var pos = player.platformPlayer.getPosition();
            startX = pos.getX();
            startY = pos.getY();
            startZ = pos.getZ();
        } else {
            startX = player.x;
            startY = player.y;
            startZ = player.z;
        }

        if (startY < minY + 2) return;

        double simX = startX;
        double simZ = startZ;
        double simY = startY;
        double simVelY = 0;
        int simTicks = (int) Math.min(elapsed / 50L, 200L);

        var oldBox = player.boundingBox;
        double oldPlayerY = player.y;
        boolean oldLastOnGround = player.lastOnGround;
        boolean oldIsStepMovement = player.uncertaintyHandler.isStepMovement;
        Vector3dm oldActualMovement = player.actualMovement;
        player.lastOnGround = false;
        player.actualMovement = new Vector3dm();

        try {
            for (int i = 0; i < simTicks; i++) {
                simVelY = (simVelY - 0.08) * 0.98;
                player.y = simY;
                player.boundingBox = GetBoundingBox.getBoundingBoxFromPosAndSize(
                        player, simX, simY, simZ, 0.6f, 1.8f);
                Vector3dm collided = Collisions.collide(player, 0, simVelY, 0);
                simY += collided.getY();
                if (collided.getY() > simVelY + 1e-7 && simVelY < 0) break;
                if (simY < minY) break;
            }
        } finally {
            player.y = oldPlayerY;
            player.boundingBox = oldBox;
            player.lastOnGround = oldLastOnGround;
            player.actualMovement = oldActualMovement;
            player.uncertaintyHandler.isStepMovement = oldIsStepMovement;
        }

        boolean canTeleport = simY >= minY + 2
                && simY < startY - 0.01
                && (startY - simY) <= 256.0;

        if (debug) {
            LogUtil.info(String.format("[AirStuck] %s startY=%.2f simY=%.2f iters=%d canTp=%b",
                    player.getName(), startY, simY, simTicks, canTeleport));
        }

        // Sim couldn't move player down = already on real ground, no flag
        if (!canTeleport || player.platformPlayer == null) return;

        boolean flagged = flagAndAlert("ms=" + elapsed + " y=" + String.format("%.1f", simY));
        lastFlagTime = now;
        lastPositionTime = now;

        // flag() is what knows about the experimental gate, disableGrim and the exempt permission. Teleporting
        // on a flag that never fired means a check nobody switched on still moves players around.
        if (!flagged || !shouldModifyPackets()) return;

        var world = player.platformPlayer.getWorld();
        float yaw = player.yaw;
        float pitch = player.pitch;
        double finalX = simX;
        double finalY = simY;
        double finalZ = simZ;

        GrimAPI.INSTANCE.getScheduler().getEntityScheduler().execute(
                player.platformPlayer,
                GrimAPI.INSTANCE.getGrimPlugin(),
                () -> {
                    player.platformPlayer.resetFallDistance();
                    player.platformPlayer.teleportAsync(
                            new ac.grim.grimac.utils.math.Location(
                                    world, finalX, finalY, finalZ, yaw, pitch));
                },
                null, 0);
    }
}
