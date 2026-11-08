package org.ctc.discord.picks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.modals.Modal;

@RequiredArgsConstructor
public class JdaPickGatewayResponse implements PickGatewayResponse {
    private final IReplyCallback callback;
    private final boolean reusePrivateMessage;

    public JdaPickGatewayResponse(IReplyCallback callback) {
        this(callback, false);
    }

    @Override
    public CompletionStage<Continuation> deferPrivate() {
        var result = new CompletableFuture<Continuation>();
        Consumer<InteractionHook> accepted = hook -> {
            hook.setEphemeral(true);
            var first = new AtomicBoolean(true);
            result.complete(new Continuation() {
                @Override public void replyPrivate(String message) {
                    if (reusePrivateMessage && first.getAndSet(false)) {
                        hook.editOriginal(message).setComponents(List.of()).setAllowedMentions(List.of()).queue(sent -> { }, failure -> { });
                    } else {
                        hook.sendMessage(message).setEphemeral(true).setAllowedMentions(List.of()).queue(sent -> { }, failure -> { });
                    }
                }
                @Override public void replyPrivate(PickGatewayMessage message) {
                    sendInteractive(hook, message, reusePrivateMessage && first.getAndSet(false));
                }
            });
        };
        Consumer<Throwable> rejected = failure -> result.completeExceptionally(new IllegalStateException("Private acknowledgement failed"));
        if (reusePrivateMessage && privateSource() && callback instanceof IMessageEditCallback editable) {
            editable.deferEdit().queue(accepted, rejected);
        } else {
            callback.deferReply(true).queue(accepted, rejected);
        }
        return result;
    }

    private boolean privateSource() {
        Message source = switch (callback) {
            case GenericComponentInteractionCreateEvent event -> event.getMessage();
            case ModalInteractionEvent event -> event.getMessage();
            default -> null;
        };
        return source != null && source.isEphemeral();
    }

    private static void sendInteractive(InteractionHook hook, PickGatewayMessage message, boolean edit) {
        var rows = new java.util.ArrayList<ActionRow>();
        if (message.selection() != null) {
            var select = StringSelectMenu.create(message.selection().id()).setPlaceholder(message.selection().placeholder()).setRequiredRange(1, 1);
            for (var option : message.selection().options()) {
                select.addOptions(SelectOption.of(option.label(), option.value()).withDefault(option.defaultSelected()));
            }
            rows.add(ActionRow.of(select.build()));
        }
        if (!message.buttons().isEmpty()) rows.add(ActionRow.of(message.buttons().stream().map(button -> Button.secondary(button.id(), button.label())).toList()));
        if (edit) {
            hook.editOriginal(message.content()).setComponents(rows).setAllowedMentions(List.of()).queue(sent -> { }, failure -> { });
        } else {
            hook.sendMessage(message.content()).setEphemeral(true).setAllowedMentions(List.of()).setComponents(rows).queue(sent -> { }, failure -> { });
        }
    }

    @Override
    public void replyPrivate(String message) {
        callback.reply(message).setEphemeral(true).setAllowedMentions(List.of()).queue(sent -> { }, failure -> { });
    }

    @Override
    public void openModal(PickGatewayModal modal) {
        if (!(callback instanceof IModalCallback supported)) {
            throw new IllegalStateException("This interaction cannot open a modal");
        }
        var builder = Modal.create(modal.id(), modal.title());
        for (var field : modal.fields()) {
            switch (field) {
                case PickGatewayModal.Text text -> {
                    var input = TextInput.create(text.id(), text.paragraph() ? TextInputStyle.PARAGRAPH : TextInputStyle.SHORT)
                            .setRequired(text.required()).setRequiredRange(text.minLength(), text.maxLength());
                    if (text.value() != null && !text.value().isEmpty()) input.setValue(text.value());
                    builder.addComponents(Label.of(text.label(), input.build()));
                }
                case PickGatewayModal.Selection selection -> {
                    var input = StringSelectMenu.create(selection.id()).setRequiredRange(selection.minValues(), selection.maxValues());
                    for (var option : selection.options()) {
                        input.addOptions(SelectOption.of(option.label(), option.value()).withDefault(option.defaultSelected()));
                    }
                    builder.addComponents(Label.of(selection.label(), input.build()));
                }
            }
        }
        supported.replyModal(builder.build()).queue(sent -> { }, failure -> { });
    }
}
