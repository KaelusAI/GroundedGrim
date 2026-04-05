package ac.grim.grimac.checks.impl.inventory;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;

@CheckData(name = "AutoTotemA", configName = "AutoTotem", description = "Checks for inhuman totem re-equip speed", decay = 0.025)
public class AutoTotemA extends Check implements PacketCheck {

    private long totemPopTime = 0;
    private int totemPopTransaction = -1;
    private double buffer;
    private long threshold = 100;
    private boolean debug = false;

    public AutoTotemA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        threshold = config.getIntElse("AutoTotem.a.threshold", 100);
        debug = config.getBooleanElse("AutoTotem.a.debug", false);
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
        long adjustedDelta = Math.max(0, delta - (player.getTransactionPing() / 2));

        if (debug) {
            ac.grim.grimac.utils.anticheat.LogUtil.info("[AutoTotemA] " + player.user.getName()
                    + " delta=" + delta + "ms adjusted=" + adjustedDelta + "ms ping=" + player.getTransactionPing() + "ms");
        }

        if (adjustedDelta < threshold) {
            if (++buffer > 1) {
                if (flagAndAlert("delta=" + delta + "ms adjusted=" + adjustedDelta + "ms") && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        } else {
            buffer = Math.max(0, buffer - 0.25);
            reward();
        }

        totemPopTransaction = -1;
        totemPopTime = 0;
    }
}
