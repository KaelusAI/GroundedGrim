package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.impl.misc.PacketLogger;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.command.CommandUtils;
import ac.grim.grimac.platform.api.command.PlayerSelector;
import ac.grim.grimac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

public class GrimLogger implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        var base = commandManager.commandBuilder("grim", "grimac").literal("logger").permission("grim.logger");

        commandManager.command(base
                .literal("filter")
                .required("value", StringParser.stringParser(), CommandUtils.fromStrings("movement", "combat", "abilities", "all"))
                .handler(this::handleFilter));

        commandManager.command(base
                .literal("side")
                .required("value", StringParser.stringParser(), CommandUtils.fromStrings("c2s", "s2c", "both"))
                .handler(this::handleSide));

        commandManager.command(base
                .literal("time")
                .required("seconds", IntegerParser.integerParser(0))
                .handler(this::handleTime));

        commandManager.command(base
                .literal("start")
                .required("target", adapter.singlePlayerSelectorParser())
                .handler(this::handleStart));

        commandManager.command(base
                .literal("stop")
                .required("target", adapter.singlePlayerSelectorParser())
                .handler(this::handleStop));

        commandManager.command(base
                .literal("status")
                .required("target", adapter.singlePlayerSelectorParser())
                .handler(this::handleStatus));
    }

    private void handleFilter(@NotNull CommandContext<Sender> ctx) {
        Sender sender = ctx.sender();
        String value = ctx.<String>get("value").toUpperCase();
        try {
            PacketLogger.Filter filter = PacketLogger.Filter.valueOf(value);
            sender.sendMessage(Component.text()
                    .append(Component.text("[Logger] ", NamedTextColor.GOLD))
                    .append(Component.text("Filter set to ", NamedTextColor.GRAY))
                    .append(Component.text(filter.name(), NamedTextColor.WHITE))
                    .build());
            storeFilterForSender(sender, filter);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text()
                    .append(Component.text("[Logger] ", NamedTextColor.GOLD))
                    .append(Component.text("Invalid filter. Use: movement, combat, abilities, all", NamedTextColor.RED))
                    .build());
        }
    }

    private void handleSide(@NotNull CommandContext<Sender> ctx) {
        Sender sender = ctx.sender();
        String value = ctx.<String>get("value").toUpperCase();
        try {
            PacketLogger.Side side = PacketLogger.Side.valueOf(value);
            sender.sendMessage(Component.text()
                    .append(Component.text("[Logger] ", NamedTextColor.GOLD))
                    .append(Component.text("Side set to ", NamedTextColor.GRAY))
                    .append(Component.text(side.name(), NamedTextColor.WHITE))
                    .build());
            storeSideForSender(sender, side);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text()
                    .append(Component.text("[Logger] ", NamedTextColor.GOLD))
                    .append(Component.text("Invalid side. Use: c2s, s2c, both", NamedTextColor.RED))
                    .build());
        }
    }

    private void handleTime(@NotNull CommandContext<Sender> ctx) {
        Sender sender = ctx.sender();
        int seconds = ctx.get("seconds");
        sender.sendMessage(Component.text()
                .append(Component.text("[Logger] ", NamedTextColor.GOLD))
                .append(Component.text("Timeout set to ", NamedTextColor.GRAY))
                .append(Component.text(seconds == 0 ? "infinite" : seconds + "s", NamedTextColor.WHITE))
                .build());
        storeTimeoutForSender(sender, seconds);
    }

    private void handleStart(@NotNull CommandContext<Sender> ctx) {
        Sender sender = ctx.sender();
        GrimPlayer target = resolveTarget(sender, ctx.get("target"));
        if (target == null) return;

        PacketLogger logger = target.checkManager.getPacketCheck(PacketLogger.class);
        if (logger == null) {
            sender.sendMessage(Component.text("[Logger] PacketLogger not found.", NamedTextColor.RED));
            return;
        }

        if (logger.isActive()) {
            sender.sendMessage(Component.text()
                    .append(Component.text("[Logger] ", NamedTextColor.GOLD))
                    .append(Component.text("Logger is already active for ", NamedTextColor.RED))
                    .append(Component.text(target.user.getProfile().getName(), NamedTextColor.WHITE))
                    .build());
            return;
        }

        LoggerSettings settings = getSettings(sender);
        logger.setFilter(settings.filter);
        logger.setSide(settings.side);
        logger.setTimeoutSeconds(settings.timeout);

        if (logger.start()) {
            sender.sendMessage(Component.text()
                    .append(Component.text("[Logger] ", NamedTextColor.GOLD))
                    .append(Component.text("Started logging for ", NamedTextColor.GREEN))
                    .append(Component.text(target.user.getProfile().getName(), NamedTextColor.WHITE))
                    .append(Component.text(" [filter=" + settings.filter + " side=" + settings.side
                            + " timeout=" + (settings.timeout == 0 ? "infinite" : settings.timeout + "s") + "]", NamedTextColor.GRAY))
                    .build());
        } else {
            sender.sendMessage(Component.text()
                    .append(Component.text("[Logger] ", NamedTextColor.GOLD))
                    .append(Component.text("Failed to start logger. Check console for errors.", NamedTextColor.RED))
                    .build());
        }
    }

    private void handleStop(@NotNull CommandContext<Sender> ctx) {
        Sender sender = ctx.sender();
        GrimPlayer target = resolveTarget(sender, ctx.get("target"));
        if (target == null) return;

        PacketLogger logger = target.checkManager.getPacketCheck(PacketLogger.class);
        if (logger == null || !logger.isActive()) {
            sender.sendMessage(Component.text()
                    .append(Component.text("[Logger] ", NamedTextColor.GOLD))
                    .append(Component.text("Logger is not active for ", NamedTextColor.RED))
                    .append(Component.text(target.user.getProfile().getName(), NamedTextColor.WHITE))
                    .build());
            return;
        }

        logger.stop();
        sender.sendMessage(Component.text()
                .append(Component.text("[Logger] ", NamedTextColor.GOLD))
                .append(Component.text("Stopped logging for ", NamedTextColor.GREEN))
                .append(Component.text(target.user.getProfile().getName(), NamedTextColor.WHITE))
                .build());
    }

    private void handleStatus(@NotNull CommandContext<Sender> ctx) {
        Sender sender = ctx.sender();
        GrimPlayer target = resolveTarget(sender, ctx.get("target"));
        if (target == null) return;

        PacketLogger logger = target.checkManager.getPacketCheck(PacketLogger.class);
        if (logger == null) {
            sender.sendMessage(Component.text("[Logger] PacketLogger not found.", NamedTextColor.RED));
            return;
        }

        Component status = Component.text()
                .append(Component.text("[Logger] ", NamedTextColor.GOLD))
                .append(Component.text(target.user.getProfile().getName(), NamedTextColor.WHITE))
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text(logger.isActive() ? "ACTIVE" : "INACTIVE",
                        logger.isActive() ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(" [filter=" + logger.getFilter() + " side=" + logger.getSide()
                        + " timeout=" + (logger.getTimeoutSeconds() == 0 ? "infinite" : logger.getTimeoutSeconds() + "s") + "]", NamedTextColor.GRAY))
                .build();
        sender.sendMessage(status);
    }

    private GrimPlayer resolveTarget(Sender sender, PlayerSelector selector) {
        Sender targetSender = selector.getSinglePlayer();
        if (targetSender == null) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "player-not-found", "%prefix% &cPlayer not found!"));
            return null;
        }
        GrimPlayer grimPlayer = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(targetSender.getUniqueId());
        if (grimPlayer == null) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "player-not-found", "%prefix% &cPlayer is exempt or offline!"));
        }
        return grimPlayer;
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, LoggerSettings> PENDING_SETTINGS = new java.util.concurrent.ConcurrentHashMap<>();

    private static String senderKey(Sender sender) {
        return sender.isPlayer() ? sender.getUniqueId().toString() : "console";
    }

    private static LoggerSettings getSettings(Sender sender) {
        return PENDING_SETTINGS.getOrDefault(senderKey(sender), new LoggerSettings());
    }

    private static void storeFilterForSender(Sender sender, PacketLogger.Filter filter) {
        PENDING_SETTINGS.computeIfAbsent(senderKey(sender), k -> new LoggerSettings()).filter = filter;
    }

    private static void storeSideForSender(Sender sender, PacketLogger.Side side) {
        PENDING_SETTINGS.computeIfAbsent(senderKey(sender), k -> new LoggerSettings()).side = side;
    }

    private static void storeTimeoutForSender(Sender sender, int timeout) {
        PENDING_SETTINGS.computeIfAbsent(senderKey(sender), k -> new LoggerSettings()).timeout = timeout;
    }

    private static class LoggerSettings {
        PacketLogger.Filter filter = PacketLogger.Filter.ALL;
        PacketLogger.Side side = PacketLogger.Side.BOTH;
        int timeout = 0;
    }
}
