package ac.grim.grimac.checks.impl.prediction;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.debug.AbstractDebugHandler;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.command.commands.GrimLog;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.utils.lists.EvictingQueue;
import ac.grim.grimac.utils.math.Vector3dm;
import com.github.retrooper.packetevents.PacketEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public class DebugHandler extends AbstractDebugHandler implements PostPredictionCheck {
    private static final Component GRAY_ARROW = MiniMessage.miniMessage().deserialize("<gray>→0.03→</gray>");
    private static final Component P_PREFIX = MiniMessage.miniMessage().deserialize("<reset>P: </reset>");
    private static final Component A_PREFIX = MiniMessage.miniMessage().deserialize("<reset>A: </reset>");
    private static final Component O_PREFIX = MiniMessage.miniMessage().deserialize("<reset>O: </reset>");
    private static final int DEFAULT_BUFFER_SIZE = 500;

    public enum Filter { FLAGS_ONLY, ELYTRA, THRESHOLD }

    public static final class DebugSettings {
        private final EnumSet<Filter> filters;
        private double threshold;
        private int timeoutSeconds;
        private int bufferSize;

        public DebugSettings() {
            this.filters = EnumSet.noneOf(Filter.class);
            this.threshold = 0.001;
            this.timeoutSeconds = 0;
            this.bufferSize = DEFAULT_BUFFER_SIZE;
        }

        public DebugSettings(DebugSettings other) {
            this.filters = EnumSet.copyOf(other.filters.isEmpty() ? EnumSet.noneOf(Filter.class) : other.filters);
            this.threshold = other.threshold;
            this.timeoutSeconds = other.timeoutSeconds;
            this.bufferSize = other.bufferSize;
        }

        public Set<Filter> filters() { return filters; }
        public boolean hasFilter(Filter f) { return filters.contains(f); }
        public void addFilter(Filter f) { filters.add(f); }
        public void removeFilter(Filter f) { filters.remove(f); }
        public void resetFilters() { filters.clear(); }
        public boolean isFiltered() { return !filters.isEmpty(); }

        public double threshold() { return threshold; }
        public void setThreshold(double t) { this.threshold = t; }
        public int timeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int t) { this.timeoutSeconds = t; }
        public int bufferSize() { return bufferSize; }
        public void setBufferSize(int s) { this.bufferSize = s; }

        public String filtersString() {
            if (filters.isEmpty()) return "ALL";
            return filters.stream().map(Enum::name).collect(Collectors.joining(", "));
        }
    }

    private final CopyOnWriteArraySet<GrimPlayer> listeners = new CopyOnWriteArraySet<>();
    private final Map<GrimPlayer, DebugSettings> listenerSettings = new ConcurrentHashMap<>();
    private final Map<GrimPlayer, Long> listenerStartTime = new ConcurrentHashMap<>();
    private boolean outputToConsole = false;

    private EvictingQueue<String> pasteBuffer = new EvictingQueue<>(DEFAULT_BUFFER_SIZE);

    private static final ConcurrentHashMap<String, DebugSettings> PENDING_SETTINGS = new ConcurrentHashMap<>();

    public DebugHandler(GrimPlayer player) {
        super(player);
    }

    public static DebugSettings getOrCreatePendingSettings(String senderKey) {
        return PENDING_SETTINGS.computeIfAbsent(senderKey, k -> new DebugSettings());
    }

    public void resizeBuffer(int newSize) {
        EvictingQueue<String> newBuffer = new EvictingQueue<>(newSize);
        newBuffer.addAll(pasteBuffer);
        pasteBuffer = newBuffer;
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;

        double offset = predictionComplete.getOffset();

        if (listeners.isEmpty() && !outputToConsole) return;
        if (player.predictedVelocity.vector.lengthSquared() == 0 && offset == 0) return;

        checkTimeouts();

        String color = pickColor(offset, offset);
        boolean isFlag = !color.equals("gray") && !color.equals("green");

        Vector3dm predicted = player.predictedVelocity.vector;
        Vector3dm actually = player.actualMovement;

        String xColor = pickColor(Math.abs(predicted.getX() - actually.getX()), offset);
        String yColor = pickColor(Math.abs(predicted.getY() - actually.getY()), offset);
        String zColor = pickColor(Math.abs(predicted.getZ() - actually.getZ()), offset);

        Component p = Component.empty()
                .append(P_PREFIX.color(NamedTextColor.NAMES.value(color)))
                .append(Component.text(predicted.getX()).color(NamedTextColor.NAMES.value(xColor)))
                .append(Component.space())
                .append(Component.text(predicted.getY()).color(NamedTextColor.NAMES.value(yColor)))
                .append(Component.space())
                .append(Component.text(predicted.getZ()).color(NamedTextColor.NAMES.value(zColor)));

        Component a = Component.empty()
                .append(A_PREFIX.color(NamedTextColor.NAMES.value(color)))
                .append(Component.text(actually.getX()).color(NamedTextColor.NAMES.value(xColor)))
                .append(Component.space())
                .append(Component.text(actually.getY()).color(NamedTextColor.NAMES.value(yColor)))
                .append(Component.space())
                .append(Component.text(actually.getZ()).color(NamedTextColor.NAMES.value(zColor)));

        String canSkipTick = (player.couldSkipTick + " ").substring(0, 1);
        String actualMovementSkip = (player.skippedTickInActualMovement + "").charAt(0) + " ";
        Component o = Component.empty()
                .append(Component.text(canSkipTick).color(NamedTextColor.GRAY))
                .append(GRAY_ARROW)
                .append(Component.text(actualMovementSkip).color(NamedTextColor.GRAY))
                .append(O_PREFIX.color(NamedTextColor.NAMES.value(color)))
                .append(Component.text(offset));

        if (player.isGliding || player.wasGliding) {
            String elytraState = " G:" + (player.isGliding ? "1" : "0") + (player.wasGliding ? "1" : "0")
                    + " gnd:" + (player.onGround ? "1" : "0") + (player.lastOnGround ? "1" : "0")
                    + " vCol:" + (player.verticalCollision ? "1" : "0")
                    + " v:" + String.format("%.2f", player.clientVelocity.length())
                    + " fw:" + player.fireworks.getMaxFireworksAppliedPossible();
            o = o.append(Component.text(elytraState).color(NamedTextColor.AQUA));
        }

        String plainLine = "P: " + predicted.getX() + " " + predicted.getY() + " " + predicted.getZ()
                + " | A: " + actually.getX() + " " + actually.getY() + " " + actually.getZ()
                + " | O: " + offset
                + (player.isGliding || player.wasGliding
                    ? " | G:" + (player.isGliding ? "1" : "0") + (player.wasGliding ? "1" : "0")
                      + " gnd:" + (player.onGround ? "1" : "0") + (player.lastOnGround ? "1" : "0")
                      + " vCol:" + (player.verticalCollision ? "1" : "0")
                      + " v:" + String.format("%.2f", player.clientVelocity.length())
                      + " fw:" + player.fireworks.getMaxFireworksAppliedPossible()
                    : "");
        pasteBuffer.add(plainLine);

        String prefix = player.platformPlayer == null ? "null" : player.platformPlayer.getName() + " ";
        Component prefixComponent = Component.text(prefix);

        for (GrimPlayer listener : listeners) {
            DebugSettings settings = listenerSettings.getOrDefault(listener, new DebugSettings());
            if (!shouldSend(settings, offset, isFlag)) continue;

            Component listenerPrefix = listener == getPlayer() ? Component.empty() : prefixComponent;
            listener.sendMessage(listenerPrefix.append(p));
            listener.sendMessage(listenerPrefix.append(a));
            listener.sendMessage(listenerPrefix.append(o));
        }

        listeners.removeIf(l -> l.platformPlayer != null && !l.platformPlayer.isOnline());

        if (outputToConsole) {
            Sender consoleSender = GrimAPI.INSTANCE.getPlatformServer().getConsoleSender();
            consoleSender.sendMessage(p);
            consoleSender.sendMessage(a);
            consoleSender.sendMessage(o);
        }
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        listeners.removeIf(listener -> {
            Long startTime = listenerStartTime.get(listener);
            if (startTime == null) return false;
            DebugSettings settings = listenerSettings.get(listener);
            if (settings == null || settings.timeoutSeconds() <= 0) return false;
            if (now - startTime >= settings.timeoutSeconds() * 1000L) {
                listenerSettings.remove(listener);
                listenerStartTime.remove(listener);
                listener.sendMessage(Component.text("Debug for " + player.getName() + " timed out.", NamedTextColor.GRAY));
                return true;
            }
            return false;
        });
    }

    private boolean shouldSend(DebugSettings settings, double offset, boolean isFlag) {
        if (!settings.isFiltered()) return true;
        for (Filter f : settings.filters()) {
            boolean match = switch (f) {
                case FLAGS_ONLY -> isFlag;
                case ELYTRA -> player.isGliding || player.wasGliding;
                case THRESHOLD -> offset >= settings.threshold();
            };
            if (match) return true;
        }
        return false;
    }

    private String pickColor(double offset, double totalOffset) {
        if (player.getSetbackTeleportUtil().blockOffsets) return "gray";
        if (offset <= 0 || totalOffset <= 0) {
            return "gray";
        } else if (offset < 0.0001) {
            return "green";
        } else if (offset < 0.01) {
            return "yellow";
        } else {
            return "red";
        }
    }

    public boolean startListening(GrimPlayer listener, DebugSettings settings) {
        if (listeners.contains(listener)) return false;
        listeners.add(listener);
        listenerSettings.put(listener, new DebugSettings(settings));
        listenerStartTime.put(listener, System.currentTimeMillis());
        if (settings.bufferSize() != pasteBuffer.getMaxSize()) {
            resizeBuffer(settings.bufferSize());
        }
        return true;
    }

    public boolean stopListening(GrimPlayer listener) {
        if (!listeners.remove(listener)) return false;
        listenerSettings.remove(listener);
        listenerStartTime.remove(listener);
        return true;
    }

    @Override
    public boolean toggleListener(GrimPlayer listener) {
        if (listeners.remove(listener)) {
            listenerSettings.remove(listener);
            listenerStartTime.remove(listener);
            return false;
        }
        listeners.add(listener);
        listenerStartTime.put(listener, System.currentTimeMillis());
        return true;
    }

    public boolean isListening(GrimPlayer listener) {
        return listeners.contains(listener);
    }

    public DebugSettings getListenerSettings(GrimPlayer listener) {
        return listenerSettings.getOrDefault(listener, new DebugSettings());
    }

    @Override
    public boolean toggleConsoleOutput() {
        this.outputToConsole = !outputToConsole;
        return this.outputToConsole;
    }

    public void pasteBuffer(Sender sender) {
        if (pasteBuffer.isEmpty()) {
            sender.sendMessage(Component.text("Debug buffer is empty.", NamedTextColor.RED));
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Player: ").append(player.user.getName());
        sb.append("\nClient: ").append(player.getClientVersion().getReleaseName());
        sb.append("\nBrand: ").append(player.getBrand());
        sb.append("\nServer: ").append(PacketEvents.getAPI().getServerManager().getVersion().getReleaseName());
        sb.append("\nPing: ").append(player.getTransactionPing()).append("ms");
        sb.append("\nTicks buffered: ").append(pasteBuffer.size());
        sb.append("\n\n");

        for (String line : pasteBuffer) {
            sb.append(line).append("\n");
        }

        GrimLog.sendLogAsync(sender, sb.toString(), string -> {}, "text/yaml");
    }
}
