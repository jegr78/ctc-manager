package org.ctc.discord.incidents;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.ctc.discord.picks.*;
import org.ctc.testsupport.IncidentTestClock;

import static org.assertj.core.api.Assertions.*;

final class IncidentGatewayTestDriver {
    final PickGatewayDispatcher dispatcher;
    final IncidentTestClock clock;
    final String guild, channel, user;
    Object current;

    IncidentGatewayTestDriver(PickGatewayDispatcher dispatcher, IncidentTestClock clock, String guild, String channel, String user) {
        this.dispatcher = dispatcher; this.clock = clock; this.guild = guild; this.channel = channel; this.user = user;
    }
    IncidentGatewayTestDriver command() { return send(PickGatewayInteraction.Kind.COMMAND, "incidents", Map.of()); }
    IncidentGatewayTestDriver click(String label) { return send(PickGatewayInteraction.Kind.BUTTON, button(label), Map.of()); }
    IncidentGatewayTestDriver select(UUID value) {
        var control = message().selection(); assertThat(control).isNotNull();
        return send(PickGatewayInteraction.Kind.SELECT, control.id(), Map.of("selection", List.of(value.toString())));
    }
    IncidentGatewayTestDriver fields(String replay, String description, String links) {
        assertThat(current).isInstanceOf(PickGatewayModal.class); var modal = (PickGatewayModal) current;
        var values = defaults(modal);
        for (var field : modal.fields()) {
            if (field instanceof PickGatewayModal.Selection menu && values.get(menu.id()).isEmpty()) {
                values.put(menu.id(), menu.options().stream().map(PickGatewayModal.Option::value).toList());
            }
        }
        values.put("replay", List.of(replay)); values.put("description", List.of(description)); values.put("links", List.of(links));
        return send(PickGatewayInteraction.Kind.MODAL, modal.id(), values);
    }
    static Map<String, List<String>> defaults(PickGatewayModal modal) {
        var values = new HashMap<String, List<String>>();
        for (var field : modal.fields()) {
            if (field instanceof PickGatewayModal.Text text) values.put(text.id(), List.of(text.value() == null ? "" : text.value()));
            else if (field instanceof PickGatewayModal.Selection menu) values.put(menu.id(), menu.options().stream()
                    .filter(PickGatewayModal.Option::defaultSelected).map(PickGatewayModal.Option::value).toList());
        }
        return values;
    }
    IncidentGatewayTestDriver draft(UUID race, String description) {
        command().click("New report"); if (message().selection() != null) select(race);
        click("Enter report");
        assertThat(((PickGatewayModal) current).fields()).allMatch(field -> field instanceof PickGatewayModal.Text text
                ? text.value() == null : ((PickGatewayModal.Selection) field).options().stream().noneMatch(PickGatewayModal.Option::defaultSelected));
        return fields("00:00", description, "https://example.invalid/video");
    }
    IncidentGatewayTestDriver edit(UUID report, String description) {
        command().click("My reports").select(report).click("Edit report").click("Enter report");
        assertThat(((PickGatewayModal) current).fields()).allMatch(field -> field instanceof PickGatewayModal.Text text
                ? text.value() != null : ((PickGatewayModal.Selection) field).options().stream().allMatch(PickGatewayModal.Option::defaultSelected));
        return fields("00:00", description, "");
    }
    String button(String label) {
        var button = message().buttons().stream().filter(item -> item.label().equals(label)).findFirst().orElse(null);
        assertThat(button).as(label).isNotNull(); return button.id();
    }
    PickGatewayMessage message() { assertThat(current).isInstanceOf(PickGatewayMessage.class); return (PickGatewayMessage) current; }
    String text() { assertThat(current).isInstanceOf(String.class); return (String) current; }
    IncidentGatewayTestDriver send(PickGatewayInteraction.Kind kind, String action, Map<String, List<String>> values) {
        var response = new Response();
        dispatcher.accept(guild, new PickGatewayInteraction(UUID.randomUUID().toString(), guild, channel, user, kind, action, values, clock.instant(), response));
        current = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> response.output.get(10, TimeUnit.SECONDS));
        assertThat(response.privateAcknowledgement).isTrue(); return this;
    }
    static final class Response implements PickGatewayResponse {
        final CompletableFuture<Object> output = new CompletableFuture<>();
        volatile boolean privateAcknowledgement;
        @Override public java.util.concurrent.CompletionStage<Continuation> deferPrivate() {
            privateAcknowledgement = true;
            return CompletableFuture.completedFuture(new Continuation() {
                @Override public void replyPrivate(String value) { output.complete(value); }
                @Override public void replyPrivate(PickGatewayMessage value) { output.complete(value); }
            });
        }
        @Override public void replyPrivate(String value) { privateAcknowledgement = true; output.complete(value); }
        @Override public void openModal(PickGatewayModal value) { privateAcknowledgement = true; output.complete(value); }
    }
}
