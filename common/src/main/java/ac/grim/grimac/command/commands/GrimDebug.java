package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.debug.HitboxDebugHandler;
import ac.grim.grimac.checks.impl.prediction.DebugHandler;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.command.PlayerSelector;
import ac.grim.grimac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GrimDebug implements BuildableCommand {

    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        var grim = commandManager.commandBuilder("grim", "grimac");

        commandManager.command(grim
                .literal("debug")
                .literal("start", Description.of("Start debug output"))
                .permission("grim.debug")
                .required("target", adapter.singlePlayerSelectorParser())
                .handler(this::handleStart));

        commandManager.command(grim
                .literal("debug")
                .literal("stop", Description.of("Stop debug output"))
                .permission("grim.debug")
                .required("target", adapter.singlePlayerSelectorParser())
                .handler(this::handleStop));

        commandManager.command(grim
                .literal("debug")
                .literal("filter")
                .literal("add", Description.of("Add a filter"))
                .permission("grim.debug")
                .required("filterType", EnumParser.enumParser(DebugHandler.Filter.class))
                .handler(this::handleFilterAdd));

        commandManager.command(grim
                .literal("debug")
                .literal("filter")
                .literal("remove", Description.of("Remove a filter"))
                .permission("grim.debug")
                .required("filterType", EnumParser.enumParser(DebugHandler.Filter.class))
                .handler(this::handleFilterRemove));

        commandManager.command(grim
                .literal("debug")
                .literal("filter")
                .literal("reset", Description.of("Reset filters to show all"))
                .permission("grim.debug")
                .handler(this::handleFilterReset));

        commandManager.command(grim
                .literal("debug")
                .literal("threshold", Description.of("Set offset threshold for THRESHOLD filter"))
                .permission("grim.debug")
                .required("value", DoubleParser.doubleParser(0.0))
                .handler(this::handleThreshold));

        commandManager.command(grim
                .literal("debug")
                .literal("time", Description.of("Set auto-stop timeout (0 = infinite)"))
                .permission("grim.debug")
                .required("seconds", IntegerParser.integerParser(0))
                .handler(this::handleTime));

        commandManager.command(grim
                .literal("debug")
                .literal("buffer", Description.of("Set paste buffer size in ticks"))
                .permission("grim.debug")
                .required("size", IntegerParser.integerParser(50, 5000))
                .handler(this::handleBuffer));

        commandManager.command(grim
                .literal("debug")
                .literal("paste", Description.of("Upload debug buffer to paste.grim.ac"))
                .permission("grim.debug")
                .required("target", adapter.singlePlayerSelectorParser())
                .handler(this::handlePaste));

        commandManager.command(grim
                .literal("debug")
                .literal("status", Description.of("Show debug status"))
                .permission("grim.debug")
                .required("target", adapter.singlePlayerSelectorParser())
                .handler(this::handleStatus));

        // Legacy toggle: /grim debug [player]
        commandManager.command(grim
                .literal("debug", Description.of("Toggle debug output"))
                .permission("grim.debug")
                .optional("target", adapter.singlePlayerSelectorParser())
                .handler(this::handleToggle));

        commandManager.command(grim
                .literal("consoledebug", Description.of("Toggle console debug output"))
                .permission("grim.consoledebug")
                .required("target", adapter.singlePlayerSelectorParser())
                .handler(this::handleConsoleDebug));

        commandManager.command(grim
                .literal("hitboxdebug", Description.of("Toggle hitbox debug"))
                .permission("grim.hitboxdebug")
                .optional("target", adapter.singlePlayerSelectorParser(), Description.of("Player to debug"))
                .handler(this::handleHitboxDebug));
    }

    private void handleStart(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        GrimPlayer target = parseTarget(sender, context.<PlayerSelector>get("target").getSinglePlayer());
        if (target == null) return;
        GrimPlayer senderPlayer = resolveSenderPlayer(sender);
        if (senderPlayer == null) return;

        DebugHandler handler = target.checkManager.getDebugHandler();
        if (handler.isListening(senderPlayer)) {
            sender.sendMessage(Component.text("Already active. Use /grim debug stop first.", NamedTextColor.RED));
            return;
        }

        DebugHandler.DebugSettings settings = DebugHandler.getOrCreatePendingSettings(sender.getUniqueId().toString());
        handler.startListening(senderPlayer, settings);

        sender.sendMessage(Component.text()
                .append(Component.text("Debug started for ", NamedTextColor.GREEN))
                .append(Component.text(target.getName(), NamedTextColor.WHITE))
                .append(Component.text(" [" + settings.filtersString()
                        + (settings.hasFilter(DebugHandler.Filter.THRESHOLD) ? " >=" + settings.threshold() : "")
                        + (settings.timeoutSeconds() > 0 ? " timeout=" + settings.timeoutSeconds() + "s" : "")
                        + " buf=" + settings.bufferSize()
                        + "]", NamedTextColor.AQUA))
                .build());
    }

    private void handleStop(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        GrimPlayer target = parseTarget(sender, context.<PlayerSelector>get("target").getSinglePlayer());
        if (target == null) return;
        GrimPlayer senderPlayer = resolveSenderPlayer(sender);
        if (senderPlayer == null) return;

        if (target.checkManager.getDebugHandler().stopListening(senderPlayer)) {
            sender.sendMessage(Component.text("Debug stopped for " + target.getName() + ".", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Debug was not active for " + target.getName() + ".", NamedTextColor.RED));
        }
    }

    private void handleToggle(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        PlayerSelector selector = context.getOrDefault("target", null);
        GrimPlayer target = parseTarget(sender, selector == null ? sender : selector.getSinglePlayer());
        if (target == null) return;

        DebugHandler handler = target.checkManager.getDebugHandler();

        if (sender.isConsole()) {
            boolean enabled = handler.toggleConsoleOutput();
            sender.sendMessage(Component.text("Console debug for " + target.getName() + (enabled ? " enabled." : " disabled."), NamedTextColor.GRAY));
            return;
        }

        GrimPlayer senderPlayer = resolveSenderPlayer(sender);
        if (senderPlayer == null) return;

        if (handler.isListening(senderPlayer)) {
            handler.stopListening(senderPlayer);
            sender.sendMessage(Component.text("Debug stopped for " + target.getName() + ".", NamedTextColor.GRAY));
        } else {
            DebugHandler.DebugSettings settings = DebugHandler.getOrCreatePendingSettings(sender.getUniqueId().toString());
            handler.startListening(senderPlayer, settings);
            sender.sendMessage(Component.text()
                    .append(Component.text("Debug started for ", NamedTextColor.GREEN))
                    .append(Component.text(target.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" [" + settings.filtersString() + "]", NamedTextColor.AQUA))
                    .build());
        }
    }

    private void handleFilterAdd(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        DebugHandler.Filter filterType = context.get("filterType");
        DebugHandler.DebugSettings settings = DebugHandler.getOrCreatePendingSettings(senderKey(sender));
        settings.addFilter(filterType);
        sender.sendMessage(Component.text("Added filter: " + filterType + ". Active: " + settings.filtersString(), NamedTextColor.GREEN));
    }

    private void handleFilterRemove(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        DebugHandler.Filter filterType = context.get("filterType");
        DebugHandler.DebugSettings settings = DebugHandler.getOrCreatePendingSettings(senderKey(sender));
        settings.removeFilter(filterType);
        sender.sendMessage(Component.text("Removed filter: " + filterType + ". Active: " + settings.filtersString(), NamedTextColor.GREEN));
    }

    private void handleFilterReset(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        DebugHandler.DebugSettings settings = DebugHandler.getOrCreatePendingSettings(senderKey(sender));
        settings.resetFilters();
        sender.sendMessage(Component.text("Filters reset. Showing ALL ticks.", NamedTextColor.GREEN));
    }

    private void handleThreshold(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        double value = context.get("value");
        DebugHandler.DebugSettings settings = DebugHandler.getOrCreatePendingSettings(senderKey(sender));
        settings.setThreshold(value);
        sender.sendMessage(Component.text("Threshold set to: " + value, NamedTextColor.GREEN));
    }

    private void handleTime(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        int seconds = context.get("seconds");
        DebugHandler.DebugSettings settings = DebugHandler.getOrCreatePendingSettings(senderKey(sender));
        settings.setTimeoutSeconds(seconds);
        sender.sendMessage(Component.text("Timeout set to: " + (seconds == 0 ? "infinite" : seconds + "s"), NamedTextColor.GREEN));
    }

    private void handleBuffer(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        int size = context.get("size");
        DebugHandler.DebugSettings settings = DebugHandler.getOrCreatePendingSettings(senderKey(sender));
        settings.setBufferSize(size);
        sender.sendMessage(Component.text("Buffer size set to: " + size + " ticks", NamedTextColor.GREEN));
    }

    private void handlePaste(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        GrimPlayer target = parseTarget(sender, context.<PlayerSelector>get("target").getSinglePlayer());
        if (target == null) return;
        target.checkManager.getDebugHandler().pasteBuffer(sender);
    }

    private void handleStatus(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        GrimPlayer target = parseTarget(sender, context.<PlayerSelector>get("target").getSinglePlayer());
        if (target == null) return;
        GrimPlayer senderPlayer = resolveSenderPlayer(sender);
        if (senderPlayer == null) return;

        DebugHandler handler = target.checkManager.getDebugHandler();
        boolean active = handler.isListening(senderPlayer);
        DebugHandler.DebugSettings activeSettings = handler.getListenerSettings(senderPlayer);
        DebugHandler.DebugSettings pending = DebugHandler.getOrCreatePendingSettings(senderKey(sender));

        sender.sendMessage(Component.text()
                .append(Component.text("Debug for ", NamedTextColor.GRAY))
                .append(Component.text(target.getName() + ": ", NamedTextColor.WHITE))
                .append(Component.text(active ? "ACTIVE" : "INACTIVE", active ? NamedTextColor.GREEN : NamedTextColor.RED))
                .build());
        if (active) {
            sender.sendMessage(Component.text("  Active: filter=" + activeSettings.filtersString()
                    + " threshold=" + activeSettings.threshold()
                    + " timeout=" + (activeSettings.timeoutSeconds() == 0 ? "none" : activeSettings.timeoutSeconds() + "s")
                    + " buffer=" + activeSettings.bufferSize(), NamedTextColor.AQUA));
        }
        sender.sendMessage(Component.text("  Pending: filter=" + pending.filtersString()
                + " threshold=" + pending.threshold()
                + " timeout=" + (pending.timeoutSeconds() == 0 ? "none" : pending.timeoutSeconds() + "s")
                + " buffer=" + pending.bufferSize(), NamedTextColor.GRAY));
    }

    private void handleConsoleDebug(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        PlayerSelector targetName = context.getOrDefault("target", null);
        GrimPlayer grimPlayer = parseTarget(sender, targetName.getSinglePlayer());
        if (grimPlayer == null) return;

        boolean isOutput = grimPlayer.checkManager.getDebugHandler().toggleConsoleOutput();
        sender.sendMessage(Component.text()
                .append(Component.text("Console output for ", NamedTextColor.GRAY))
                .append(Component.text(grimPlayer.user.getProfile().getName(), NamedTextColor.WHITE))
                .append(Component.text(isOutput ? " enabled." : " disabled.", NamedTextColor.GRAY))
                .build());
    }

    private void handleHitboxDebug(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        PlayerSelector selector = context.getOrDefault("target", null);

        if (!sender.isPlayer()) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "hitboxdebug-player-only", "%prefix% &cHitbox debug can only be toggled by players."));
            return;
        }

        GrimPlayer target = parseTarget(sender, selector == null ? sender : selector.getSinglePlayer());
        if (target == null) return;
        GrimPlayer senderPlayer = resolveSenderPlayer(sender);
        if (senderPlayer == null) return;

        HitboxDebugHandler hitboxHandler = target.checkManager.getCheck(HitboxDebugHandler.class);
        if (hitboxHandler == null) {
            sender.sendMessage(Component.text("HitboxDebugHandler not found.", NamedTextColor.RED));
            return;
        }

        boolean enabled = hitboxHandler.toggleListener(senderPlayer);
        sender.sendMessage(Component.text()
                .append(Component.text("Hitbox debug for ", NamedTextColor.GRAY))
                .append(Component.text(target.getName(), NamedTextColor.WHITE))
                .append(Component.text(enabled ? " enabled." : " disabled.", NamedTextColor.GRAY))
                .build());
    }

    private String senderKey(Sender sender) {
        return sender.isPlayer() ? sender.getUniqueId().toString() : "console";
    }

    private @Nullable GrimPlayer resolveSenderPlayer(Sender sender) {
        if (!sender.isPlayer()) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "run-as-player-or-console", "%prefix% &cThis command can only be used by players!"));
            return null;
        }
        GrimPlayer p = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(sender.getUniqueId());
        if (p == null) sender.sendMessage(MessageUtil.getParsedComponent(sender, "sender-not-found", "%prefix% &cYou cannot be exempt to use this command!"));
        return p;
    }

    private @Nullable GrimPlayer parseTarget(@NotNull Sender sender, @Nullable Sender t) {
        if (sender.isConsole() && t == null) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "console-specify-target", "%prefix% &cYou must specify a target as the console!"));
            return null;
        }
        Sender target = t == null ? sender : t;
        GrimPlayer p = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(target.getUniqueId());
        if (p == null) sender.sendMessage(MessageUtil.getParsedComponent(sender, "player-not-found", "%prefix% &cPlayer is exempt or offline!"));
        return p;
    }
}
