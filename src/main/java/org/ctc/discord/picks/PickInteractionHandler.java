package org.ctc.discord.picks;

import java.util.ArrayList;
import java.util.List;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.picks.ChannelPickActor;
import org.ctc.domain.picks.HomeRaceTarget;
import org.ctc.domain.picks.PickTeamView;
import org.ctc.domain.picks.PickTeamView.Choice;
import org.ctc.domain.service.PickAccessService;
import org.ctc.domain.service.PickSubmissionService;
import org.ctc.domain.service.PickTargetService;
import org.ctc.domain.service.PickTeamReadService;
import org.ctc.discord.picks.PickDraftSessionStore.Action;
import org.ctc.discord.picks.PickDraftSessionStore.Session;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import static org.ctc.discord.picks.PickGatewayInteraction.Kind.*;
import static org.ctc.discord.picks.PickInteractionViews.*;

@Component
@RequiredArgsConstructor
public class PickInteractionHandler implements PickGatewayInteractionHandler {
    private final PickAccessService access;
    private final PickTargetService targets;
    private final PickTeamReadService reads;
    private final PickSubmissionService submissions;
    private final PickDraftSessionStore sessions;

    @Override
    public void onInteraction(PickGatewayInteraction event, PickGatewayReply reply) {
        if (event.kind() == BUTTON) {
            try {
                boolean opened = sessions.withAction(event, (session, action) -> {
                    if (!action.name().startsWith("open-")) return false;
                    String stage = action.name().substring(5);
                    session.clearActions();
                    reply.openModal(modal(session.action(stage, MODAL), stage, session.lobbyRulesProfile,
                            session.car != null && session.car.oneMake(), session.fields,
                            session.track == null ? List.of() : session.track.timeOfDayOptions()));
                    return true;
                });
                if (opened) return;
            } catch (BusinessRuleException expired) {
                reply.replyPrivate("This dialog expired or was reset. Use /picks to start again."); return;
            }
        }
        reply.deferPrivate(continuation -> {
            try {
                var channel = access.authorizeChannel(event.guildId(), event.channelId(), event.userId());
                if (event.kind() == COMMAND) {
                    var rows = targets.listHomeRaces(channel);
                    if (rows.isEmpty()) { continuation.replyPrivate("No eligible home races are available in this channel."); return; }
                    var session = sessions.create(event);
                    continuation.replyPrivate("Choose your home race to view or edit its picks.");
                    targetPage(session, rows, 0, continuation);
                } else {
                    sessions.withAction(event, (session, action) -> {
                        handle(event, session, action, channel, continuation); return true;
                    });
                }
            } catch (BusinessRuleException denied) {
                continuation.replyPrivate("Picks changed or are unavailable. Use /picks to reload the current submission.");
            }
        });
    }

    private void handle(PickGatewayInteraction event, Session session, Action action, ChannelPickActor channel,
                        PickGatewayResponse.Continuation reply) {
        String name = action.name();
        if (name.equals("targets")) { targetPage(session, targets.listHomeRaces(channel), action.page(), reply); return; }
        if (name.equals("target")) {
            String value = one(event.values(), "selection");
            var chosen = action.targets().stream().filter(row -> row.raceId().toString().equals(value)).findFirst()
                    .orElseThrow(() -> new BusinessRuleException("Invalid target"));
            session.raceId = chosen.raceId();
        }
        var view = reads.load(channel, session.raceId);
        session.label = view.target().label();
        if (name.equals("target") || name.equals("view")) { showStatus(session, view, reply); return; }
        if (name.equals("confirm")) {
            if (session.payload == null) throw new BusinessRuleException("A complete draft is required");
            var actor = access.authorizeTarget(channel, session.raceId);
            if (actor.channel() == null || actor.channel().generation() != session.generation) {
                throw new BusinessRuleException("This dialog was reset");
            }
            var receipt = submissions.submit(session.raceId, session.version, session.request, actor, session.payload);
            send(reply, escape(session.label) + "\nSaved " + receipt(receipt) + "\nUse /picks to retrieve the authoritative current submission.", List.of(), null);
            return;
        }
        if (!view.target().editable()) { showStatus(session, view, reply); return; }
        if (name.equals("entries")) {
            catalog(session, "entry", entries(view.cars()), "", -1, reply); return;
        }
        if (name.equals("edit")) {
            session.version = view.target().version();
            session.lobbyRulesProfile = view.lobbyRulesProfile();
            var current = view.payload();
            session.entryId = current == null ? null : current.carPoolEntryId();
            session.fields.clear(); session.fields.putAll(draftFields(current));
            session.track = current == null ? null : view.tracks().stream()
                    .filter(choice -> choice.id().equals(current.trackId())).findFirst().orElse(null);
            session.car = current == null ? null : view.cars().stream()
                    .filter(choice -> choice.id().equals(current.carId())
                            && java.util.Objects.equals(choice.entryId(), current.carPoolEntryId()))
                    .findFirst().orElse(null);
            session.payload = null;
            catalog(session, "track", view.tracks(), "", -1, reply); return;
        }
        if (name.startsWith("page-")) {
            String type = name.substring(5);
            catalog(session, type, catalogChoices(session, type, view), action.query(), action.page(), reply); return;
        }
        if (name.equals("track") || name.equals("entry") || name.equals("car")) {
            String value = event.kind() == BUTTON && action.choices().size() == 1
                    ? action.choices().getFirst().id().toString() : one(event.values(), "selection");
            Choice chosen = action.choices().stream().filter(choice -> choice.id().toString().equals(value)).findFirst()
                    .orElseThrow(() -> new BusinessRuleException("Invalid choice"));
            var current = catalogChoices(session, name, view);
            if (!current.contains(chosen)) throw new BusinessRuleException("Catalog changed");
            if (name.equals("track")) {
                session.track = chosen;
                String next = entries(view.cars()).isEmpty() ? "car" : "entry";
                catalog(session, next, catalogChoices(session, next, view), "", -1, reply);
            } else if (name.equals("entry")) {
                session.entryId = chosen.id();
                if (session.car != null && !chosen.id().equals(session.car.entryId())) session.car = null;
                var cars = catalogChoices(session, "car", view);
                if (chosen.oneMake()) {
                    if (cars.size() != 1 || !cars.getFirst().oneMake()) throw new BusinessRuleException("One-Make catalog changed");
                    selectCar(session, cars.getFirst(), reply);
                } else {
                    catalog(session, "car", cars, "", -1, reply);
                }
            } else {
                selectCar(session, chosen, reply);
            }
            return;
        }
        boolean oneMake = session.car != null && session.car.oneMake();
        try {
            validateStage(name, event.values(), session.lobbyRulesProfile, oneMake, session.fields);
            if (name.equals("time") && session.track != null && !session.track.timeOfDayOptions().isEmpty()
                    && !session.track.timeOfDayOptions().contains(one(event.values(), "daytime"))) throw new IllegalArgumentException("Invalid layout time");
        } catch (IllegalArgumentException invalid) {
            next(session, name, "Check all required values and allowed ranges. Mandatory tyres must be available; choose None on its own.", reply); return;
        }
        if (name.startsWith("search-")) {
            String type = name.substring(7);
            String query = event.values().getOrDefault("query", List.of("")).getFirst();
            catalog(session, type, catalogChoices(session, type, view), query, 0, reply); return;
        }
        if (name.equals("rules") && family(session.lobbyRulesProfile, oneMake, session.fields)
                != family(session.lobbyRulesProfile, oneMake, event.values())) {
            session.fields.remove("available"); session.fields.put("mandatory", List.of("None"));
        }
        session.fields.putAll(event.values());
        if (name.equals("fuel")) {
            var mandatory = session.fields.getOrDefault("mandatory", List.of("None"));
            if (!mandatory.equals(List.of("None")) && !session.fields.get("available").containsAll(mandatory)) {
                session.fields.put("mandatory", List.of("None"));
            }
        }
        switch (name) {
            case "rules" -> next(session, "fuel", "Choose initial fuel, stops and available dry tyres. Racing always includes Intermediate and Wet.", reply);
            case "fuel" -> next(session, "mandatory", "Choose None or mandatory compounds from your available dry tyres.", reply);
            case "mandatory" -> next(session, "time", "Choose time progression and enter weather and time of day.", reply);
            case "time" -> {
                session.payload = payload(session);
                session.clearActions();
                send(reply, escape(session.label) + "\nReview all values before confirming. Nothing has been saved.\n"
                                + payload(session.payload, session.track.label(), session.car.label()),
                        List.of(button(session, "confirm", "Confirm submission")), null);
            }
            default -> throw new BusinessRuleException("Invalid dialog stage");
        }
    }

    private void selectCar(Session session, Choice chosen, PickGatewayResponse.Continuation reply) {
        session.car = chosen;
        if (!sportsAllowed(session.lobbyRulesProfile, chosen.oneMake())
                && session.fields.getOrDefault("family", List.of("RACING")).equals(List.of("SPORTS"))) {
            session.fields.put("family", List.of("RACING"));
            session.fields.remove("available"); session.fields.put("mandatory", List.of("None"));
        }
        next(session, "rules", "Car: " + escape(chosen.label()) + "\nEnter race values and choose the tyre family where available.", reply);
    }

    private static List<Choice> catalogChoices(Session session, String type, PickTeamView view) {
        return switch (type) {
            case "track" -> view.tracks();
            case "entry" -> entries(view.cars());
            case "car" -> view.cars().stream().filter(choice -> session.entryId == null
                    ? choice.entryId() == null : session.entryId.equals(choice.entryId())).toList();
            default -> throw new BusinessRuleException("Invalid catalog");
        };
    }

    private static List<Choice> entries(List<Choice> cars) {
        var entries = new java.util.LinkedHashMap<java.util.UUID, Choice>();
        for (var car : cars) {
            if (car.entryId() == null) continue;
            if (car.entryLabel() == null || car.entryLabel().isBlank()) throw new BusinessRuleException("Pool entry label is unavailable");
            var entry = new Choice(car.entryId(), car.entryId(), (car.oneMake() ? "One-Make: " : "Category: ") + car.entryLabel(),
                    car.oneMake(), car.entryLabel());
            var previous = entries.putIfAbsent(entry.id(), entry);
            if (previous != null && !previous.equals(entry)) throw new BusinessRuleException("Pool entry catalog changed");
        }
        return entries.values().stream().sorted(java.util.Comparator.comparing(Choice::label).thenComparing(Choice::id)).toList();
    }

    private void showStatus(Session session, PickTeamView view, PickGatewayResponse.Continuation reply) {
        session.clearActions();
        var buttons = new ArrayList<PickGatewayMessage.Button>();
        if (view.target().editable()) buttons.add(button(session, "edit", view.payload() == null ? "Enter picks" : "Replace picks"));
        buttons.add(button(session, "view", "Refresh current submission"));
        send(reply, status(view), buttons, null);
    }

    private void targetPage(Session session, List<HomeRaceTarget> rows, int requested, PickGatewayResponse.Continuation reply) {
        session.clearActions();
        int pages = Math.max(1, (rows.size() + 4) / 5); int page = Math.min(requested, pages - 1);
        var choices = rows.subList(page * 5, Math.min(rows.size(), page * 5 + 5));
        var text = new StringBuilder("Home races, page " + (page + 1) + "/" + pages + "\n");
        choices.forEach(row -> text.append(escape(row.label())).append("\n").append(row.state()).append(": ").append(escape(row.reason())).append("\n\n"));
        var buttons = new ArrayList<PickGatewayMessage.Button>();
        if (page > 0) buttons.add(new PickGatewayMessage.Button(session.action(new Action("targets", BUTTON, List.of(), List.of(), page - 1, "")), "Previous"));
        if (page + 1 < pages) buttons.add(new PickGatewayMessage.Button(session.action(new Action("targets", BUTTON, List.of(), List.of(), page + 1, "")), "Next"));
        var selection = choices.isEmpty() ? null : new PickGatewayMessage.Selection(
                session.action(new Action("target", SELECT, List.of(), choices, 0, "")), "Choose home race",
                choices.stream().map(row -> new PickGatewayModal.Option(optionLabel(row.label()), row.raceId().toString())).toList());
        send(reply, text.toString(), buttons, selection);
    }

    private void catalog(Session session, String type, List<Choice> choices, String query, int requested, PickGatewayResponse.Continuation reply) {
        session.clearActions();
        var selected = switch (type) {
            case "track" -> session.track;
            case "car" -> session.car;
            case "entry" -> choices.stream().filter(choice -> choice.id().equals(session.entryId)).findFirst().orElse(null);
            default -> throw new BusinessRuleException("Invalid catalog");
        };
        var page = PickCatalogSelector.page(choices, query, requested < 0 ? PickCatalogSelector.pageContaining(choices, query, selected) : requested);
        String caption = type.equals("entry") ? "category or One-Make" : type;
        var buttons = new ArrayList<PickGatewayMessage.Button>();
        if (page.page() > 0) buttons.add(new PickGatewayMessage.Button(session.action(new Action("page-" + type, BUTTON, List.of(), List.of(), page.page() - 1, query)), "Previous"));
        if (page.page() + 1 < page.pages()) buttons.add(new PickGatewayMessage.Button(session.action(new Action("page-" + type, BUTTON, List.of(), List.of(), page.page() + 1, query)), "Next"));
        buttons.add(button(session, "open-search-" + type, type.equals("entry") ? "Search categories and One-Makes" : "Search " + type + " catalog"));
        if (selected != null && choices.contains(selected)) {
            buttons.add(new PickGatewayMessage.Button(session.action(new Action(type, BUTTON, List.of(selected), List.of(), 0, "")),
                    "Keep selected " + caption));
        }
        if (type.equals("car") && session.entryId != null) {
            buttons.add(button(session, "entries", "Change category or One-Make"));
        }
        var selection = page.choices().isEmpty() ? null : new PickGatewayMessage.Selection(
                session.action(new Action(type, SELECT, page.choices(), List.of(), page.page(), query)), "Choose " + caption,
                page.choices().stream().map(choice -> new PickGatewayModal.Option(optionLabel(choice.label()), choice.id().toString(),
                        choice.equals(selected))).toList());
        send(reply, escape(session.label) + "\nChoose " + caption + ". Page " + (page.page() + 1) + "/" + page.pages()
                + ", " + page.count() + " matching choices.\nSearch: " + escape(query)
                + (type.equals("track") ? "\nSearch covers available tracks in this season track pool."
                        + (choices.isEmpty() ? "\nNo tracks are available. Ask management to check the season track pool and tracks already used or reserved by your team."
                        : page.count() == 0 ? "\nNo available tracks match your search. Clear the search to show all available tracks." : "") : "")
                + (selected != null && choices.contains(selected) ? "\nSelected: " + escape(selected.label()) : ""), buttons, selection);
    }

    private void next(Session session, String stage, String message, PickGatewayResponse.Continuation reply) {
        session.clearActions();
        send(reply, escape(session.label) + "\n" + message, List.of(button(session, "open-" + stage, "Open " + stageLabel(stage) + " form")), null);
    }
    private static PickGatewayMessage.Button button(Session session, String action, String label) {
        return new PickGatewayMessage.Button(session.action(action, BUTTON), label);
    }
}
