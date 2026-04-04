package ac.grim.grimac.utils.latency;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import ac.grim.grimac.utils.common.arguments.CommonGrimArguments;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

public class LatencyUtils {
    private final ArrayDeque<TransactionTask> transactionMap = new ArrayDeque<>();
    private final GrimPlayer player;

    private final ArrayList<Runnable> tasksToRun = new ArrayList<>();

    public LatencyUtils(GrimPlayer player) {
        this.player = player;
    }

    public void addRealTimeTask(int transaction, Runnable runnable) {
        addRealTimeTask(transaction, false, runnable);
    }

    public void addRealTimeTaskAsync(int transaction, Runnable runnable) {
        addRealTimeTask(transaction, true, runnable);
    }

    public void addRealTimeTask(int transaction, boolean async, Runnable runnable) {
        if (player.lastTransactionReceived.get() >= transaction) {
            if (async) {
                player.runSafely(runnable);
            } else {
                runnable.run();
            }
            return;
        }
        synchronized (transactionMap) {
            transactionMap.add(new TransactionTask(transaction, runnable));
        }
    }

    public void handleNettySyncTransaction(int transaction) {
        synchronized (transactionMap) {
            tasksToRun.clear();

            Iterator<TransactionTask> iterator = transactionMap.iterator();
            while (iterator.hasNext()) {
                TransactionTask task = iterator.next();

                if (transaction + 1 < task.transactionId)
                    break;

                if (transaction == task.transactionId - 1)
                    continue;

                tasksToRun.add(task.runnable);
                iterator.remove();
            }

            for (Runnable runnable : tasksToRun) {
                try {
                    runnable.run();
                } catch (Exception e) {
                    LogUtil.error("An error has occurred when running transactions for player: " + player.user.getName(), e);
                    if (CommonGrimArguments.KICK_ON_TRANSACTION_ERRORS.value()) {
                        player.disconnect(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(player, GrimAPI.INSTANCE.getConfigManager().getDisconnectPacketError())));
                    }
                }
            }
        }
    }

    private record TransactionTask(int transactionId, Runnable runnable) {}
}
