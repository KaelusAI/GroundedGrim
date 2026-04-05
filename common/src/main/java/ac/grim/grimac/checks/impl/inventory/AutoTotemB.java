package ac.grim.grimac.checks.impl.inventory;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.lists.EvictingQueue;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;

@CheckData(name = "AutoTotemB", configName = "AutoTotem", description = "Checks for inhuman totem re-equip consistency", decay = 0.025)
public class AutoTotemB extends Check implements PacketCheck {

    private long totemPopTime = 0;
    private int totemPopTransaction = -1;
    private final EvictingQueue<Long> intervals = new EvictingQueue<>(10);
    private double buffer;
    private int minSamples = 3;
    private double maxSD = 50;
    private long maxMean = 500;
    private boolean debug = false;

    public AutoTotemB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minSamples = config.getIntElse("AutoTotem.b.min-samples", 3);
        maxSD = config.getDoubleElse("AutoTotem.b.max-sd", 50.0);
        maxMean = config.getIntElse("AutoTotem.b.max-mean", 500);
        debug = config.getBooleanElse("AutoTotem.b.debug", false);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_STATUS) {
            WrapperPlayServerEntityStatus status = new WrapperPlayServerEntityStatus(event);
            if (status.getStatus() == 35 && status.getEntityId() == player.entityID) {
                player.sendTransaction();
                totemPopTransaction = player.lastTransactionSent.get();
                totemPopTime = System.currentTimeMillis();
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (totemPopTransaction == -1) return;

        boolean isOffhandSwap = false;

        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
            if (click.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.SWAP && click.getButton() == 40) {
                isOffhandSwap = true;
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            if (dig.getAction() == DiggingAction.SWAP_ITEM_WITH_OFFHAND) {
                isOffhandSwap = true;
            }
        }

        if (!isOffhandSwap) return;
        if (totemPopTransaction > player.lastTransactionReceived.get()) return;

        long delta = System.currentTimeMillis() - totemPopTime;
        totemPopTransaction = -1;
        totemPopTime = 0;

        if (delta > 5000) {
            intervals.clear();
            return;
        }

        intervals.add(delta);

        if (intervals.size() < minSamples) return;

        double mean = 0;
        for (long v : intervals) mean += v;
        mean /= intervals.size();

        double variance = 0;
        for (long v : intervals) variance += (v - mean) * (v - mean);
        double sd = Math.sqrt(variance / intervals.size());

        if (debug) {
            ac.grim.grimac.utils.anticheat.LogUtil.info("[AutoTotemB] " + player.user.getName()
                    + " sd=" + String.format("%.1f", sd) + "ms mean=" + String.format("%.0f", mean)
                    + "ms samples=" + intervals.size());
        }

        if (sd < maxSD && mean < maxMean) {
            if (++buffer > 2) {
                if (flagAndAlert("sd=" + String.format("%.1f", sd) + "ms mean=" + String.format("%.0f", mean) + "ms") && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        } else {
            buffer = Math.max(0, buffer - 0.5);
            reward();
        }
    }
}
