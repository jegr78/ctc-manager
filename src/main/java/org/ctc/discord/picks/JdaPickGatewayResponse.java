package org.ctc.discord.picks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.modals.Modal;

@RequiredArgsConstructor
public class JdaPickGatewayResponse implements PickGatewayResponse {
    private final IReplyCallback callback;
    @Override
    public CompletionStage<Continuation> deferPrivate() {
        var result = new CompletableFuture<Continuation>();
        callback.deferReply(true).queue(hook -> {
            hook.setEphemeral(true);
            result.complete(message -> hook.sendMessage(message).setEphemeral(true).setAllowedMentions(List.of())
                    .queue(sent -> { }, failure -> { }));
        }, failure -> result.completeExceptionally(new IllegalStateException("Private acknowledgement failed")));
        return result;
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
                    for (var option : selection.options()) input.addOption(option.label(), option.value());
                    builder.addComponents(Label.of(selection.label(), input.build()));
                }
            }
        }
        supported.replyModal(builder.build()).queue(sent -> { }, failure -> { });
    }
}
