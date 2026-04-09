package ac.grim.grimac.checks.impl.elytra;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.utils.nmsutil.BlockUtil;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

@CheckData(name = "ElytraM", description = "Checks for velocity discontinuities during elytra flight", experimental = true, decay = 0.05, setback = 5)
public class ElytraM extends Check implements PostPredictionCheck {

    private double lastDeltaX, lastDeltaY, lastDeltaZ;
    private double lastDeltaXZ;
    private int glidingTicks = 0;
    private double buffer = 0;

    private static final double MAX_ACCEL_FACTOR = 0.35;
    private static final double FIREWORK_DELTA_PER_ROCKET = 0.85;
    private static final double BASE_EPSILON = 0.05;
    private static final int MIN_GLIDE_TICKS = 5;

    public ElytraM(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;

        if (!player.isGliding && !player.wasGliding) {
            glidingTicks = 0;
            resetDeltas();
            return;
        }

        if (player.packetStateData.lastPacketWasTeleport
                || player.wasTouchingWater || player.wasSwimming
                || player.onGround || player.lastOnGround
                || player.isFlying
                || player.verticalCollision || player.horizontalCollision
                || player.packetStateData.tryingToRiptide
                || player.compensatedEntities.getSlowFallingAmplifier().isPresent()
                || BlockUtil.isPlayerInBlockType(player, StateTypes.COBWEB)
                || player.predictedVelocity.isKnockback()
                || player.predictedVelocity.isExplosion()
                || player.getTransactionPing() > 500) {
            glidingTicks = 0;
            updateDeltas();
            return;
        }

        glidingTicks++;

        if (glidingTicks < MIN_GLIDE_TICKS) {
            updateDeltas();
            return;
        }

        double deltaX = player.x - player.lastX;
        double deltaY = player.y - player.lastY;
        double deltaZ = player.z - player.lastZ;
        double deltaXZ = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        double expectedDeltaXZ = lastDeltaXZ * 0.99;
        double expectedDeltaY = lastDeltaY * 0.98;

        int fireworks = player.fireworks.getMaxFireworksAppliedPossible();
        double fireworkAllowance = fireworks * FIREWORK_DELTA_PER_ROCKET;

        double speedFactor = Math.max(deltaXZ, lastDeltaXZ);
        double maxAccelXZ = MAX_ACCEL_FACTOR * Math.max(0.1, speedFactor) + fireworkAllowance + BASE_EPSILON;
        double maxAccelY = 0.08 + MAX_ACCEL_FACTOR * Math.max(0.1, speedFactor) + fireworkAllowance + BASE_EPSILON;

        double discXZ = Math.abs(deltaXZ - expectedDeltaXZ) - maxAccelXZ;
        double discY = Math.abs(deltaY - expectedDeltaY) - maxAccelY;
        double discontinuity = Math.max(0, Math.max(discXZ, discY));

        if (discontinuity > 0) {
            buffer += discontinuity;
            if (buffer > 3.0) {
                if (flagAndAlert(String.format("disc=%.4f dXZ=%.3f dY=%.3f eXZ=%.3f eY=%.3f fw=%d",
                        discontinuity, deltaXZ, deltaY, expectedDeltaXZ, expectedDeltaY, fireworks))) {
                    setbackIfAboveSetbackVL();
                }
            }
        } else {
            buffer = Math.max(0, buffer - 0.05);
            reward();
        }

        updateDeltas();
    }

    private void updateDeltas() {
        lastDeltaX = player.x - player.lastX;
        lastDeltaY = player.y - player.lastY;
        lastDeltaZ = player.z - player.lastZ;
        lastDeltaXZ = Math.sqrt(lastDeltaX * lastDeltaX + lastDeltaZ * lastDeltaZ);
    }

    private void resetDeltas() {
        lastDeltaX = lastDeltaY = lastDeltaZ = lastDeltaXZ = 0;
        buffer = 0;
    }

}
