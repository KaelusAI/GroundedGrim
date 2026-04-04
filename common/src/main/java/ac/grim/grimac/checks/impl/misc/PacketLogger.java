package ac.grim.grimac.checks.impl.misc;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@CheckData(name = "PacketLogger", configName = "PacketLogger", decay = 0, setback = -1)
public class PacketLogger extends Check implements PacketCheck {

    public enum Filter { MOVEMENT, COMBAT, ABILITIES, ALL }
    public enum Side { C2S, S2C, BOTH }

    private static final Set<PacketTypeCommon> MOVEMENT_PACKETS = Set.of(
            PacketType.Play.Client.PLAYER_POSITION,
            PacketType.Play.Client.PLAYER_ROTATION,
            PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION,
            PacketType.Play.Client.PLAYER_FLYING
    );
    private static final Set<PacketTypeCommon> COMBAT_PACKETS = Set.of(
            PacketType.Play.Client.INTERACT_ENTITY,
            PacketType.Play.Client.ANIMATION
    );
    private static final Set<PacketTypeCommon> ABILITIES_C2S = Set.of(
            PacketType.Play.Client.PLAYER_ABILITIES,
            PacketType.Play.Client.ENTITY_ACTION
    );
    private static final Set<PacketTypeCommon> ABILITIES_S2C = Set.of(
            PacketType.Play.Server.PLAYER_ABILITIES
    );
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private volatile boolean active;
    private Filter filter = Filter.ALL;
    private Side side = Side.BOTH;
    private int timeoutSeconds;
    private long startTime;
    private int tickCounter;
    private BufferedWriter writer;

    public PacketLogger(GrimPlayer player) {
        super(player);
    }

    public boolean isActive() { return active; }
    public Filter getFilter() { return filter; }
    public Side getSide() { return side; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setFilter(Filter f) { this.filter = f; }
    public void setSide(Side s) { this.side = s; }
    public void setTimeoutSeconds(int s) { this.timeoutSeconds = s; }

    public synchronized boolean start() {
        if (active) return false;
        try {
            File dir = new File(GrimAPI.INSTANCE.getGrimPlugin().getDataFolder(), "logs" + File.separator + "packets");
            dir.mkdirs();
            String name = player.user.getProfile().getName() + "_" + LocalDateTime.now().format(FILE_FMT) + ".csv";
            writer = new BufferedWriter(new FileWriter(new File(dir, name)));
            writer.write("tick,timestamp,direction,packet_type,data");
            writer.newLine();
            tickCounter = 0;
            startTime = System.currentTimeMillis();
            active = true;
            return true;
        } catch (IOException e) {
            LogUtil.error("Failed to start packet logger for " + player.user.getProfile().getName(), e);
            return false;
        }
    }

    public synchronized void stop() {
        if (!active) return;
        active = false;
        if (writer != null) {
            try { writer.flush(); writer.close(); } catch (IOException ignored) {}
            writer = null;
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!active || side == Side.S2C) return;
        checkTimeout();

        PacketTypeCommon type = event.getPacketType();
        if (!matches(type, true)) return;
        if (WrapperPlayClientPlayerFlying.isFlying(type)) tickCounter++;

        write("C2S", type.getName(), extractC2S(event));
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!active || side == Side.C2S) return;
        checkTimeout();

        PacketTypeCommon type = event.getPacketType();
        if (!matches(type, false)) return;

        write("S2C", type.getName(), extractS2C(event));
    }

    private synchronized void write(String dir, String packet, String data) {
        if (writer == null) return;
        try {
            long ts = System.currentTimeMillis() - startTime;
            writer.write(tickCounter + "," + ts + "," + dir + "," + packet + ",\"" + data.replace("\"", "\"\"") + "\"");
            writer.newLine();
        } catch (IOException e) {
            LogUtil.error("PacketLogger write error", e);
            stop();
        }
    }

    private boolean matches(PacketTypeCommon type, boolean c2s) {
        if (filter == Filter.ALL) return true;
        return switch (filter) {
            case MOVEMENT -> MOVEMENT_PACKETS.contains(type);
            case COMBAT -> COMBAT_PACKETS.contains(type);
            case ABILITIES -> c2s ? ABILITIES_C2S.contains(type) : ABILITIES_S2C.contains(type);
            default -> true;
        };
    }

    private String extractC2S(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Client.PLAYER_POSITION) {
            var p = new WrapperPlayClientPlayerPosition(event);
            return pos(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(), Float.NaN, Float.NaN, p.isOnGround());
        }
        if (type == PacketType.Play.Client.PLAYER_ROTATION) {
            var p = new WrapperPlayClientPlayerRotation(event);
            return pos(Double.NaN, Double.NaN, Double.NaN, p.getYaw(), p.getPitch(), p.isOnGround());
        }
        if (type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            var p = new WrapperPlayClientPlayerPositionAndRotation(event);
            return pos(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(), p.getLocation().getYaw(), p.getLocation().getPitch(), p.isOnGround());
        }
        if (type == PacketType.Play.Client.PLAYER_FLYING)
            return "onGround=" + new WrapperPlayClientPlayerFlying(event).isOnGround();
        if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            var p = new WrapperPlayClientInteractEntity(event);
            return "entityId=" + p.getEntityId() + " action=" + p.getAction();
        }
        if (type == PacketType.Play.Client.ANIMATION)
            return "hand=" + new WrapperPlayClientAnimation(event).getHand();
        if (type == PacketType.Play.Client.PLAYER_ABILITIES)
            return "isFlying=" + new WrapperPlayClientPlayerAbilities(event).isFlying();
        if (type == PacketType.Play.Client.ENTITY_ACTION)
            return "action=" + new WrapperPlayClientEntityAction(event).getAction();
        return "";
    }

    private String extractS2C(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_ABILITIES) {
            var p = new WrapperPlayServerPlayerAbilities(event);
            return "flySpeed=" + p.getFlySpeed() + " isFlying=" + p.isFlying() + " canFly=" + p.isFlightAllowed();
        }
        return "";
    }

    private String pos(double x, double y, double z, float yaw, float pitch, boolean ground) {
        StringBuilder sb = new StringBuilder();
        if (!Double.isNaN(x)) sb.append("x=").append(String.format("%.4f", x)).append(' ');
        if (!Double.isNaN(y)) sb.append("y=").append(String.format("%.4f", y)).append(' ');
        if (!Double.isNaN(z)) sb.append("z=").append(String.format("%.4f", z)).append(' ');
        if (!Float.isNaN(yaw)) sb.append("yaw=").append(String.format("%.2f", yaw)).append(' ');
        if (!Float.isNaN(pitch)) sb.append("pitch=").append(String.format("%.2f", pitch)).append(' ');
        sb.append("onGround=").append(ground);
        return sb.toString();
    }

    private void checkTimeout() {
        if (timeoutSeconds > 0 && System.currentTimeMillis() - startTime > timeoutSeconds * 1000L) stop();
    }
}
