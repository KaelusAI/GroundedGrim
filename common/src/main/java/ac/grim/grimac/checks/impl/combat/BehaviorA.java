package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.RotationCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import com.github.retrooper.packetevents.protocol.player.GameMode;

// Original check by DarknessAC
// https://github.com/1hendex/DarknessAC
@CheckData(name = "BehaviorA", configName = "Behavior", description = "Checks for zero pitch rotations")
public class BehaviorA extends Check implements RotationCheck {

    public BehaviorA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        if (!player.hasRotatedSinceSpawn
                || player.packetStateData.lastPacketWasTeleport
                || player.gamemode == GameMode.SPECTATOR
                || player.inVehicle()
                || player.compensatedEntities.self.isDead
                || System.currentTimeMillis() - player.joinTime < 5000
                || System.currentTimeMillis() - player.lastAttackTime > 500) {
            return;
        }

        final float pitch = rotationUpdate.getTo().pitch();

        if (pitch == 0) {
            flagAndAlert("");
        }
    }
}
