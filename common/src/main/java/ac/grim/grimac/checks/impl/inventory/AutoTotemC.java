package ac.grim.grimac.checks.impl.inventory;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "AutoTotemC", configName = "AutoTotem", description = "Checks for offhand swap", decay = 0.05)
public class AutoTotemC extends Check implements PacketCheck {

    private int clickCountThisSession = 0;
    private int pendingOffhandSwapSlot = -1;
    private int flyingTicksInSession = 0;
    private long lastTickTimestamp = 0;
    private double buffer;
    private boolean debug = false;

    public AutoTotemC(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        debug = config.getBooleanElse("AutoTotem.c.debug", false);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {}

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())
                || event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END) {
            lastTickTimestamp = System.currentTimeMillis();
            if (clickCountThisSession > 0) {
                flyingTicksInSession++;
            } else if (buffer > 0) {
                buffer = Math.max(0, buffer - 0.025);
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
            clickCountThisSession++;

            if (click.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.SWAP
                    && click.getButton() == 40
                    && click.getWindowId() == 0) {

                if (clickCountThisSession == 1) {
                    pendingOffhandSwapSlot = click.getSlot();

                    if (violations > 0 && shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                        player.inventory.needResend = true;
                    }
                } else {
                    pendingOffhandSwapSlot = -1;
                }
            } else {
                pendingOffhandSwapSlot = -1;
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            if (pendingOffhandSwapSlot != -1 && clickCountThisSession == 1 && flyingTicksInSession == 0) {
                // 1.9-1.21.1 clients can skip flying packets when standing still.
                // 1.21.2+ always sends CLIENT_TICK_END so this doesn't apply to them.
                boolean clientWasSkippingTicks = player.canSkipTicks()
                        && (System.currentTimeMillis() - lastTickTimestamp) > 80;

                buffer += clientWasSkippingTicks ? 0.5 : 1.0;

                if (buffer >= 2.0) {
                    if (debug) {
                        ac.grim.grimac.utils.anticheat.LogUtil.info("[AutoTotemC] " + player.user.getName()
                                + " isolated SWAP+CLOSE pattern, slot=" + pendingOffhandSwapSlot
                                + " buffer=" + buffer + " skipping=" + clientWasSkippingTicks);
                    }

                    flagAndAlert("slot=" + pendingOffhandSwapSlot);
                }
            } else {
                buffer = Math.max(0, buffer - 0.5);
                reward();
            }

            clickCountThisSession = 0;
            pendingOffhandSwapSlot = -1;
            flyingTicksInSession = 0;
        }
    }
}
