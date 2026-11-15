package org.ctc.discord.picks;

import java.util.function.Consumer;
import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.ctc.integration.ExternalIntegrationDisabledException;
import org.ctc.integration.OutboundPolicy;

@RequiredArgsConstructor
public class JdaPickGatewayTransport implements PickGatewayTransport {
    private final OutboundPolicy outbound;
    @Override
    public Session connect(String botToken, String guildId, Consumer<PickGatewayInteraction> receiver) {
        try {
            outbound.requireEnabled();
        } catch (ExternalIntegrationDisabledException disabled) {
            throw new IllegalStateException("External integrations are disabled");
        }
        var client = JDABuilder.createLight(botToken, EnumSet.noneOf(GatewayIntent.class))
                .setAutoReconnect(true).setEnableShutdownHook(false)
                .addEventListeners(new Interactions(receiver)).build();
        return new Session() {
            private boolean closed;

            @Override
            public ConnectionState state() {
                return switch (client.getStatus()) {
                    case CONNECTED -> ConnectionState.CONNECTED;
                    case DISCONNECTED, RECONNECT_QUEUED, WAITING_TO_RECONNECT, ATTEMPTING_TO_RECONNECT -> ConnectionState.RECONNECTING;
                    case SHUTTING_DOWN, SHUTDOWN, FAILED_TO_LOGIN -> ConnectionState.STOPPED;
                    default -> ConnectionState.CONNECTING;
                };
            }

            @Override
            public synchronized void close() {
                if (closed) return;
                client.shutdown();
                try {
                    if (!client.awaitShutdown(Duration.ofSeconds(5))) client.shutdownNow();
                } catch (InterruptedException interrupted) {
                    client.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                closed = true;
            }
        };
    }

    @RequiredArgsConstructor
    private static final class Interactions extends ListenerAdapter {
        private final Consumer<PickGatewayInteraction> receiver;

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            forward(event, PickGatewayInteraction.Kind.COMMAND, event.getName(), options(event.getOptions()), event.getSubcommandName(), null,
                    new JdaPickGatewayResponse(event, PickGatewayDispatcher.reusesPrivateMessage(event.getName())));
        }

        @Override
        public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
            try {
                var guild = event.getGuild();
                PickGatewayResponse response = new PickGatewayResponse() {
                    @Override public java.util.concurrent.CompletionStage<Continuation> deferPrivate() { throw new UnsupportedOperationException(); }
                    @Override public void replyPrivate(String message) { throw new UnsupportedOperationException(); }
                    @Override public void openModal(PickGatewayModal modal) { throw new UnsupportedOperationException(); }
                    @Override public void replyChoices(List<PickGatewayModal.Option> choices) {
                        event.replyChoices(choices.stream().map(choice -> new Command.Choice(choice.label(), choice.value())).toList())
                                .queue(sent -> { }, failure -> { });
                    }
                };
                receiver.accept(new PickGatewayInteraction(event.getId(), guild == null ? null : guild.getId(), event.getChannelId(),
                        event.getUser().getId(), PickGatewayInteraction.Kind.AUTOCOMPLETE, event.getName(), options(event.getOptions()),
                        event.getTimeCreated().toInstant(), response, event.getSubcommandName(), event.getFocusedOption().getName(), null));
            } catch (RuntimeException unavailable) {
                // Do not pass SDK exceptions containing callback tokens or private input to its event logger.
            }
        }

        private static Map<String, List<String>> options(List<OptionMapping> options) {
            var values = new LinkedHashMap<String, List<String>>();
            for (var option : options) values.put(option.getName(), List.of(option.getAsString()));
            return values;
        }

        @Override
        public void onButtonInteraction(ButtonInteractionEvent event) {
            forward(event, PickGatewayInteraction.Kind.BUTTON, event.getComponentId(), Map.of());
        }

        @Override
        public void onStringSelectInteraction(StringSelectInteractionEvent event) {
            forward(event, PickGatewayInteraction.Kind.SELECT, event.getComponentId(), Map.of("selection", event.getValues()));
        }

        @Override
        public void onModalInteraction(ModalInteractionEvent event) {
            var values = new LinkedHashMap<String, List<String>>();
            for (var field : event.getValues()) {
                switch (field.getType()) {
                    case TEXT_INPUT -> values.put(field.getCustomId(), List.of(field.getAsString()));
                    case STRING_SELECT -> values.put(field.getCustomId(), field.getAsStringList());
                    default -> { return; }
                }
            }
            forward(event, PickGatewayInteraction.Kind.MODAL, event.getModalId(), values);
        }

        private void forward(IReplyCallback event, PickGatewayInteraction.Kind kind, String action, Map<String, List<String>> values) {
            forward(event, kind, action, values, null, null, new JdaPickGatewayResponse(event, PickGatewayDispatcher.reusesPrivateMessage(action)));
        }

        private void forward(IReplyCallback event, PickGatewayInteraction.Kind kind, String action, Map<String, List<String>> values,
                             String subcommand, String focused, PickGatewayResponse response) {
            try {
                var guild = event.getGuild();
                var member = event.getMember();
                receiver.accept(new PickGatewayInteraction(event.getId(), guild == null ? null : guild.getId(),
                        event.getChannelId(), event.getUser().getId(), kind, action, values,
                        event.getTimeCreated().toInstant(), response, subcommand, focused,
                        member != null ? member.getEffectiveName() : event.getUser().getEffectiveName()));
            } catch (RuntimeException unavailable) {
                // Do not pass SDK exceptions containing callback tokens or private input to its event logger.
            }
        }
    }
}
