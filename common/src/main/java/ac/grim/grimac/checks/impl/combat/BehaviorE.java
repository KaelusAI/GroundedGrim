package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

// Based on ideas from DarknessAC
// https://github.com/1hendex/DarknessAC
@CheckData(name = "BehaviorE", configName = "Behavior", description = "Checks for perfect fall distance when attack")
public class BehaviorE extends Check implements PacketCheck {

    private double lastJumpY = 0;
    private double buffer = 0;
    private long lastAttackTime = 0;
    private boolean playersOnly = true;
    private boolean debug = false;
    private int bufferThreshold = 15;

    public BehaviorE(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        playersOnly = config.getBooleanElse("Behavior.e.players-only", true);
        debug = config.getBooleanElse("Behavior.e.debug", false);
        bufferThreshold = config.getIntElse("Behavior.e.buffer-threshold", 15);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity action = new WrapperPlayClientInteractEntity(event);
        if (action.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        if (playersOnly) {
            PacketEntity entity = player.compensatedEntities.entityMap.get(action.getEntityId());
            if (entity == null || entity.type != EntityTypes.PLAYER) return;
        }

        // Only check when player is in air (crits require being airborne)
        if (!player.onGround && !player.packetStateData.packetPlayerOnGround) {
            long now = System.currentTimeMillis();
            long interval = now - lastAttackTime;

            // CPS < 4 means interval > 250ms - triggerbot waits for optimal crit timing
            if (interval > 250 || lastAttackTime == 0) {
                // Exempt conditions where Y position is unreliable
                if (player.verticalCollision
                        || player.wasTouchingWater
                        || player.isSwimming
                        || player.isClimbing
                        || player.gamemode != GameMode.SURVIVAL
                        || player.isFlying
                        || player.inVehicle()
                        || System.currentTimeMillis() - player.joinTime < 5000) {
                    buffer = 0;
                    lastAttackTime = now;
                    return;
                }

                double jumpY = player.y % 1;
                if (Math.abs(jumpY - lastJumpY) < 1.0E-7) {
                    buffer++;
                    if (debug) {
                        LogUtil.info("[BehaviorE DEBUG] " + player.getName() + " " + String.format("buffer=%.0f jumpY=%.7f delta=%.2E", buffer, jumpY, Math.abs(jumpY - lastJumpY)));
                    } else if (buffer > bufferThreshold) {
                        flagAndAlert(String.format("delta=%.7f", jumpY));
                        buffer = bufferThreshold - 3; // Reset partially to keep flagging
                    }
                } else {
                    if (debug && buffer > 0) {
                        LogUtil.info("[BehaviorE DEBUG] " + player.getName() + " " + String.format("decay buffer=%.0f jumpY=%.7f lastY=%.7f", buffer, jumpY, lastJumpY));
                    }
                    buffer = buffer / 2;
                }
                lastJumpY = jumpY;
            }

            lastAttackTime = now;
        }
    }
}
