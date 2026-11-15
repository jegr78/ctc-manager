package org.ctc.discord.picks;

import static org.ctc.discord.picks.PickGatewayInteraction.Kind.*;
import static org.ctc.discord.picks.PickInteractionViews.escape;
import static org.ctc.discord.picks.PickInteractionViews.optionLabel;
import static org.ctc.discord.picks.PickInteractionViews.send;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ctc.discord.DiscordTimestamps;
import org.ctc.discord.picks.LineupSessionStore.Action;
import org.ctc.discord.picks.LineupSessionStore.Session;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.lineups.LineupReceipt;
import org.ctc.domain.lineups.LineupTarget;
import org.ctc.domain.lineups.LineupView;
import org.ctc.domain.model.LineupPayload;
import org.ctc.domain.picks.ChannelPickActor;
import org.ctc.domain.service.LineupSubmissionService;
import org.ctc.domain.service.LineupTargetService;
import org.ctc.domain.service.PickAccessService;
import org.springframework.stereotype.Component;

/** The private /lineup dialog: choose a leg, pick the organisation's drivers, propose new ones, review and confirm. */
@Component
@RequiredArgsConstructor
public class LineupInteractionHandler implements PickGatewayInteractionHandler {
    static final int TARGETS_PER_PAGE = 5;
    static final int DRIVERS_PER_PAGE = 25;
    private final PickAccessService access;
    private final LineupTargetService targets;
    private final LineupSubmissionService submissions;
    private final LineupSessionStore sessions;

    @Override
    public void onInteraction(PickGatewayInteraction event, PickGatewayReply reply) {
        if (event.kind() == BUTTON) {
            try {
                boolean opened = sessions.withAction(event, (session, action) -> {
                    if (!action.name().equals("open-new")) return false;
                    session.clearActions();
                    reply.openModal(new PickGatewayModal(session.action("new", MODAL), "New driver", List.of(
                            new PickGatewayModal.Text("psn", "PSN ID", false, "", 3, 16, true),
                            new PickGatewayModal.Text("name", "Display name (optional)", false, "", 0, LineupPayload.MAX_NAME, false))));
                    return true;
                });
                if (opened) return;
            } catch (BusinessRuleException expired) {
                reply.replyPrivate("This dialog expired or was reset. Use /lineup to start again.");
                return;
            }
        }
        reply.deferPrivate(continuation -> {
            try {
                var channel = access.authorizeChannel(event.guildId(), event.channelId(), event.userId());
                if (event.kind() == COMMAND) {
                    var rows = targets.list(channel);
                    if (rows.isEmpty()) { continuation.replyPrivate("No legs accept lineups in this channel right now."); return; }
                    targetPage(sessions.create(event), rows, 0, continuation);
                } else {
                    sessions.withAction(event, (session, action) -> { handle(event, session, action, channel, continuation); return true; });
                }
            } catch (BusinessRuleException denied) {
                continuation.replyPrivate("Lineups changed or are unavailable. Use /lineup to reload the current submission.");
            }
        });
    }

    private void handle(PickGatewayInteraction event, Session session, Action action, ChannelPickActor channel, PickGatewayResponse.Continuation reply) {
        switch (action.name()) {
            case "targets" -> targetPage(session, targets.list(channel), action.page(), reply);
            case "target" -> {
                String value = one(event, "selection");
                var chosen = action.targets().stream().filter(row -> key(row).equals(value)).findFirst()
                        .orElseThrow(() -> new BusinessRuleException("Invalid target"));
                session.raceId = chosen.raceId();
                session.teamId = chosen.teamId();
                showStatus(session, targets.load(channel, session.raceId, session.teamId), reply);
            }
            case "view" -> showStatus(session, targets.load(channel, session.raceId, session.teamId), reply);
            case "edit" -> {
                var view = targets.load(channel, session.raceId, session.teamId);
                if (!view.target().editable()) { showStatus(session, view, reply); return; }
                start(session, view);
                session.selected.clear();
                session.proposed.clear();
                if (view.current() != null) {
                    for (var entry : view.current().entries()) {
                        var choice = entry.proposed() ? null : view.choices().stream().filter(c -> c.driverId().equals(entry.driverId())).findFirst().orElse(null);
                        if (choice != null) session.selected.put(choice.driverId(), choice);
                        else if (entry.proposed()) session.proposed.add(entry);
                    }
                }
                drivers(session, 0, "", reply);
            }
            case "page" -> drivers(session, action.page(), "", reply);
            case "drivers" -> {
                var page = action.choices();
                var chosen = new HashSet<>(event.values().getOrDefault("selection", List.of()));
                var previous = new java.util.LinkedHashMap<>(session.selected);
                page.forEach(choice -> session.selected.remove(choice.driverId()));
                page.stream().filter(choice -> chosen.contains(choice.driverId().toString())).forEach(choice -> session.selected.put(choice.driverId(), choice));
                String note = "";
                if (session.count() > session.size) {
                    session.selected.clear();
                    session.selected.putAll(previous);
                    note = "A lineup has at most " + session.size + " drivers. Remove a driver first.";
                }
                drivers(session, action.page(), note, reply);
            }
            case "new" -> {
                String psn = one(event, "psn").strip();
                String name = event.values().getOrDefault("name", List.of("")).getFirst();
                var known = session.choices.stream().filter(choice -> choice.psnId().equalsIgnoreCase(psn)).findFirst().orElse(null);
                String note;
                if (session.count() >= session.size) {
                    note = "A lineup has at most " + session.size + " drivers. Remove a driver first.";
                } else if (known != null) {
                    session.selected.putIfAbsent(known.driverId(), known);
                    note = escape(known.psnId()) + " is already registered and was selected.";
                } else if (session.proposed.stream().anyMatch(entry -> entry.psnId().equalsIgnoreCase(psn))) {
                    note = "This new driver is already in the lineup.";
                } else {
                    try {
                        session.proposed.add(LineupPayload.Entry.proposal(psn, name));
                        note = "New driver added. Management registers new drivers when applying the lineup.";
                    } catch (IllegalArgumentException invalid) {
                        note = escape(invalid.getMessage()) + ".";
                    }
                }
                drivers(session, 0, note, reply);
            }
            case "clear-new" -> { session.proposed.clear(); drivers(session, 0, "New drivers removed.", reply); }
            case "review" -> review(session, "", reply);
            case "confirm" -> confirm(session, channel, reply);
            case "next-leg" -> {
                var next = targets.nextLeg(channel, session.raceId, session.teamId).orElse(null);
                if (next == null) { reply.replyPrivate("The next leg does not accept lineups right now. Use /lineup to choose another leg."); return; }
                var view = targets.load(channel, next.raceId(), next.teamId());
                var carried = new ArrayList<>(session.selected.values());
                session.raceId = next.raceId();
                session.teamId = next.teamId();
                start(session, view);
                session.selected.clear();
                carried.stream().filter(session.choices::contains).forEach(choice -> session.selected.put(choice.driverId(), choice));
                review(session, "Prefilled with the lineup of the previous leg.", reply);
            }
            default -> throw new BusinessRuleException("Invalid dialog stage");
        }
    }

    private void start(Session session, LineupView view) {
        session.version = view.target().version();
        session.size = view.lineupSize();
        session.label = view.target().label();
        session.choices = view.choices();
        session.request = java.util.UUID.randomUUID();
    }

    private void confirm(Session session, ChannelPickActor channel, PickGatewayResponse.Continuation reply) {
        if (session.count() == 0) { review(session, "Choose at least one driver.", reply); return; }
        LineupReceipt receipt;
        try {
            var actor = access.authorizeLineupTarget(channel, session.raceId, session.teamId);
            if (actor.channel() == null || actor.channel().generation() != session.generation) throw new BusinessRuleException("This dialog was reset");
            receipt = submissions.submit(session.raceId, session.teamId, session.version, session.request, actor,
                    new LineupPayload(session.size, session.entries()), session.count() < session.size);
        } catch (BusinessRuleException | IllegalArgumentException rejected) {
            review(session, "Not saved: " + escape(rejected.getMessage()), reply);
            return;
        }
        session.clearActions();
        session.version = receipt.version();
        var text = new StringBuilder(escape(session.label)).append("\nSaved revision ").append(receipt.revisionNumber())
                .append(", ").append(DiscordTimestamps.fullDateTime(receipt.submittedAt())).append(" by ").append(author(receipt.actorId())).append('\n')
                .append(lines(session.entries()));
        receipt.warnings().forEach(warning -> text.append("\nNote: ").append(escape(warning)));
        text.append("\nUse /lineup to retrieve the current submission.");
        var buttons = new ArrayList<PickGatewayMessage.Button>();
        if (targets.nextLeg(channel, session.raceId, session.teamId).isPresent()) buttons.add(button(session, "next-leg", "Use this lineup for the next leg"));
        buttons.add(button(session, "targets", "Choose another leg"));
        send(reply, text.toString(), buttons, null);
    }

    private void showStatus(Session session, LineupView view, PickGatewayResponse.Continuation reply) {
        session.clearActions();
        session.label = view.target().label();
        var target = view.target();
        var text = new StringBuilder(escape(target.label())).append("\nStatus: ").append(target.status().getLabel())
                .append("\n").append(escape(target.reason())).append("\nDeadline: ")
                .append(target.deadline() == null ? "None" : DiscordTimestamps.fullDateTime(target.deadline()) + " · <t:" + target.deadline().getEpochSecond() + ":R>")
                .append("\nDrivers per team: ").append(view.lineupSize());
        if (view.current() == null) text.append("\nNo confirmed lineup.");
        else text.append("\n\nCurrent lineup").append(view.applied() ? " (applied by management)" : "").append(":\n").append(lines(view.current().entries()));
        view.warnings().forEach(warning -> text.append("\nNote: ").append(escape(warning)));
        var buttons = new ArrayList<PickGatewayMessage.Button>();
        if (target.editable()) buttons.add(button(session, "edit", view.current() == null ? "Enter lineup" : "Replace lineup"));
        buttons.add(button(session, "view", "Refresh"));
        buttons.add(button(session, "targets", "Choose another leg"));
        send(reply, text.toString(), buttons, null);
    }

    private void targetPage(Session session, List<LineupTarget> rows, int requested, PickGatewayResponse.Continuation reply) {
        session.clearActions();
        int pages = Math.max(1, (rows.size() + TARGETS_PER_PAGE - 1) / TARGETS_PER_PAGE);
        int page = Math.max(0, Math.min(requested, pages - 1));
        var shown = rows.subList(Math.min(rows.size(), page * TARGETS_PER_PAGE), Math.min(rows.size(), page * TARGETS_PER_PAGE + TARGETS_PER_PAGE));
        var text = new StringBuilder("Lineups, page " + (page + 1) + "/" + pages + "\n");
        shown.forEach(row -> text.append(escape(row.label())).append("\n").append(row.status().getLabel()).append(": ")
                .append(escape(row.reason())).append("\n\n"));
        var buttons = new ArrayList<PickGatewayMessage.Button>();
        if (page > 0) buttons.add(new PickGatewayMessage.Button(session.action(new Action("targets", BUTTON, page - 1, List.of(), List.of())), "Previous"));
        if (page + 1 < pages) buttons.add(new PickGatewayMessage.Button(session.action(new Action("targets", BUTTON, page + 1, List.of(), List.of())), "Next"));
        var selection = shown.isEmpty() ? null : new PickGatewayMessage.Selection(session.action(new Action("target", SELECT, page, shown, List.of())),
                "Choose a leg", shown.stream().map(row -> new PickGatewayModal.Option(optionLabel(row.label()), key(row))).toList());
        send(reply, text.toString(), buttons, selection);
    }

    private void drivers(Session session, int requested, String note, PickGatewayResponse.Continuation reply) {
        session.clearActions();
        int pages = Math.max(1, (session.choices.size() + DRIVERS_PER_PAGE - 1) / DRIVERS_PER_PAGE);
        int page = Math.max(0, Math.min(requested, pages - 1));
        var shown = session.choices.subList(Math.min(session.choices.size(), page * DRIVERS_PER_PAGE),
                Math.min(session.choices.size(), page * DRIVERS_PER_PAGE + DRIVERS_PER_PAGE));
        var text = new StringBuilder(escape(session.label)).append("\nChoose ").append(session.size)
                .append(" drivers. Selected ").append(session.count()).append("/").append(session.size).append(".");
        if (pages > 1) text.append(" Drivers page ").append(page + 1).append("/").append(pages).append(".");
        if (!note.isEmpty()) text.append('\n').append(note);
        if (session.count() > 0) text.append('\n').append(lines(session.entries()));
        if (session.choices.isEmpty()) text.append("\nNo drivers are registered for this organisation. Add new drivers instead.");
        var buttons = new ArrayList<PickGatewayMessage.Button>();
        if (page > 0) buttons.add(new PickGatewayMessage.Button(session.action(new Action("page", BUTTON, page - 1, List.of(), List.of())), "Previous drivers"));
        if (page + 1 < pages) buttons.add(new PickGatewayMessage.Button(session.action(new Action("page", BUTTON, page + 1, List.of(), List.of())), "Next drivers"));
        buttons.add(button(session, "open-new", "New driver"));
        if (!session.proposed.isEmpty()) buttons.add(button(session, "clear-new", "Remove new drivers"));
        buttons.add(button(session, "review", "Review lineup"));
        var selection = shown.isEmpty() ? null : new PickGatewayMessage.Selection(session.action(new Action("drivers", SELECT, page, List.of(), shown)),
                "Select drivers", shown.stream().map(choice -> new PickGatewayModal.Option(optionLabel(choice.label()), choice.driverId().toString(),
                        session.selected.containsKey(choice.driverId()))).toList(), 0, shown.size());
        send(reply, text.toString(), buttons, selection);
    }

    private void review(Session session, String note, PickGatewayResponse.Continuation reply) {
        session.clearActions();
        var text = new StringBuilder(escape(session.label)).append("\nReview the lineup before confirming. Nothing has been saved.");
        if (!note.isEmpty()) text.append('\n').append(note);
        text.append('\n').append(session.count() == 0 ? "No drivers selected." : lines(session.entries()));
        var buttons = new ArrayList<PickGatewayMessage.Button>();
        if (session.count() == session.size) buttons.add(button(session, "confirm", "Confirm lineup"));
        else if (session.count() > 0 && session.count() < session.size) {
            text.append("\nThis lineup has ").append(session.count()).append(" of ").append(session.size).append(" drivers.");
            buttons.add(button(session, "confirm", "Confirm with " + session.count() + " of " + session.size + " drivers"));
        }
        buttons.add(button(session, "page", "Change drivers"));
        send(reply, text.toString(), buttons, null);
    }

    static String lines(List<LineupPayload.Entry> entries) {
        var text = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            text.append(i + 1).append(". ").append(escape(entry.label())).append(entry.proposed() ? " (new driver)" : "").append('\n');
        }
        return text.toString().stripTrailing();
    }

    private static String author(String user) {
        return user != null && user.matches("[0-9]{1,20}") && !user.chars().allMatch(c -> c == '0') ? "<@" + user + ">" : "an unknown team member";
    }

    private static String key(LineupTarget target) { return target.raceId() + ":" + target.teamId(); }

    private static String one(PickGatewayInteraction event, String field) {
        var values = event.values().get(field);
        if (values == null || values.size() != 1) throw new BusinessRuleException("Invalid dialog input");
        return values.getFirst();
    }

    private static PickGatewayMessage.Button button(Session session, String action, String label) {
        return new PickGatewayMessage.Button(session.action(action, BUTTON), label);
    }
}
