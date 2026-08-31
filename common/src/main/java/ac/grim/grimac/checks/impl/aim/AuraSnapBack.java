package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import com.github.retrooper.packetevents.protocol.player.GameMode;

@CheckData(name = "AuraSnapBack", configName = "AuraSnapBack", description = "Detects yaw snap-back around attacks", experimental = true)
public class AuraSnapBack extends Check implements RotationListener {
    private static final int HISTORY_SIZE = 8;

    private final float[] yawHistory = new float[HISTORY_SIZE];
    private int historyHead = 0;
    private int historyFilled = 0;

    private float snapThreshold = 30.0f;
    private float returnTolerance = 3.0f;
    private int returnWindow = 3;

    private long lastProcessedAttackTime = 0;
    private int pendingAttackTicks = -1;
    private float preSpikeYaw = 0;
    private boolean sawSpike = false;

    public AuraSnapBack(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        snapThreshold = (float) config.getDoubleElse("AuraSnapBack.snap-threshold", 30.0);
        returnTolerance = (float) config.getDoubleElse("AuraSnapBack.return-tolerance", 3.0);
        returnWindow = config.getIntElse("AuraSnapBack.return-window", 3);
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        float currentYaw = rotationUpdate.newYaw();

        if (player.lastAttackTime > lastProcessedAttackTime
                && player.gamemode != GameMode.CREATIVE
                && player.gamemode != GameMode.SPECTATOR
                && !player.inVehicle() && !player.isGliding
                && System.currentTimeMillis() - player.joinTime >= 5000
                && !player.packetStateData.lastPacketWasTeleport
                && historyFilled >= 2) {
            int idx = (historyHead - 2 + HISTORY_SIZE) % HISTORY_SIZE;
            preSpikeYaw = yawHistory[idx];
            pendingAttackTicks = 0;
            sawSpike = false;
            lastProcessedAttackTime = player.lastAttackTime;
        }

        if (pendingAttackTicks >= 0) {
            pendingAttackTicks++;

            float spikeAbs = Math.abs(rotationUpdate.deltaYaw());
            if (!sawSpike && spikeAbs > snapThreshold) sawSpike = true;

            if (sawSpike) {
                float diff = Math.abs(wrapDegrees(currentYaw - preSpikeYaw));
                if (diff < returnTolerance) {
                    flagAndAlert(String.format("spike=%.1f return=%.2f window=%d", spikeAbs, diff, pendingAttackTicks));
                    resetPending();
                } else if (pendingAttackTicks >= returnWindow) {
                    resetPending();
                }
            } else if (pendingAttackTicks >= returnWindow) {
                resetPending();
            }
        }

        yawHistory[historyHead] = currentYaw;
        historyHead = (historyHead + 1) % HISTORY_SIZE;
        if (historyFilled < HISTORY_SIZE) historyFilled++;
    }

    private void resetPending() {
        pendingAttackTicks = -1;
        sawSpike = false;
    }

    private static float wrapDegrees(float v) {
        v %= 360.0f;
        if (v >= 180.0f) v -= 360.0f;
        if (v < -180.0f) v += 360.0f;
        return v;
    }
}
