package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class GrimAlerts implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("grim", "grimac")
                        .literal("alerts", Description.of("Toggle alerts for the sender"))
                        .permission("grim.alerts")
                        .optional("player", StringParser.stringParser(), adapter.onlinePlayerSuggestions())
                        .handler(this::handleAlerts)
        );
    }

    private void handleAlerts(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        String targetFilter = context.getOrDefault("player", null);

        if (sender.isPlayer()) {
            GrimAPI.INSTANCE.getAlertManager().toggleAlerts(Objects.requireNonNull(sender.getPlatformPlayer()), false);

            if (GrimAPI.INSTANCE.getAlertManager().hasAlertsEnabled(sender.getPlatformPlayer())) {
                GrimAPI.INSTANCE.getAlertManager().setPlayerFilter(sender.getPlatformPlayer(), targetFilter);
                if (targetFilter != null) {
                    String msg = GrimAPI.INSTANCE.getConfigManager().getConfig()
                            .getStringElse("alerts-filter", "%prefix% &fFiltering alerts for: &b%player%")
                            .replace("%player%", targetFilter);
                    sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, msg)));
                }
            } else {
                GrimAPI.INSTANCE.getAlertManager().setPlayerFilter(sender.getPlatformPlayer(), null);
            }
        } else if (sender.isConsole()) {
            GrimAPI.INSTANCE.getAlertManager().toggleConsoleAlerts();
        }
    }
}
