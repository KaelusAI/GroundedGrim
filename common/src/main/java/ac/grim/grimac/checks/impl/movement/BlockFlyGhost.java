package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import org.jetbrains.annotations.NotNull;

// Delayed acknowledgments can leave predicted blocks solid in the player's world cache.
// Track unconfirmed placements to detect support over server-side air.
@CheckData(name = "BlockFlyGhost", stableKey = "grim.movement.block_fly_ghost",
        description = "Stood on placements the server never confirmed", experimental = true, decay = 0.05)
public class BlockFlyGhost extends Check implements PostPredictionListener {
    private static final Verbose V = Verbose.of("ticks={sint}, ping={sint}ms");

    private int sustained;
    private int minTicks = 6;
    private int minPing = 200;

    public BlockFlyGhost(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked() || !player.onlySupportedByUnconfirmedPlacement) {
            sustained = 0;
            return;
        }

        sustained++;

        // Normal bridging also uses unconfirmed placements.
        // Require sustained support and delayed transactions.
        if (sustained >= minTicks && player.getTransactionPing() > minPing) {
            flag(V.write(verbose()).sint(sustained).sint(player.getTransactionPing()));
        }
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minTicks = config.getIntElse("BlockFlyGhost.min-ticks", 6);
        minPing = config.getIntElse("BlockFlyGhost.min-transaction-ping", 200);
    }
}
