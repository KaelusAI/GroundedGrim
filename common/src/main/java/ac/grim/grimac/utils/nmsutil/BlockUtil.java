package ac.grim.grimac.utils.nmsutil;

import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;

public final class BlockUtil {

    private BlockUtil() {
    }

    public static boolean isPlayerInBlockType(GrimPlayer player, StateType type) {
        int blockX = (int) Math.floor(player.x);
        int blockY = (int) Math.floor(player.y);
        int blockZ = (int) Math.floor(player.z);
        return player.compensatedWorld.getBlock(blockX, blockY, blockZ).getType() == type;
    }
}
