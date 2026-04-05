package ac.grim.grimac.manager.init.start;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;

// Intended for future events we inject all platforms at the end of a tick
public abstract class AbstractTickEndEvent implements StartableInitable {

    @Override
    public void start() {

    }

    protected void onEndOfTick(GrimPlayer player) {
        player.checkManager.getPacketEntityReplication().onEndOfTickEvent();
    }

    protected boolean shouldInjectEndTick() {
        boolean forceEnable = GrimAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse("Reach.force-enable-post-packet", false);
        if (forceEnable) return true;

        boolean enabled = GrimAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse("Reach.enable-post-packet", true);
        if (!enabled) return false;

        // Auto-disable on 1.8 servers unless force-enabled
        return !PacketEvents.getAPI().getServerManager().getVersion().isOlderThanOrEquals(ServerVersion.V_1_8_8);
    }
}
