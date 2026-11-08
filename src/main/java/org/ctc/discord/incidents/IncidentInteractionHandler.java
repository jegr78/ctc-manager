package org.ctc.discord.incidents;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.discord.picks.*;
import org.ctc.discord.incidents.IncidentDraftSessionStore.Action;
import org.ctc.discord.incidents.IncidentDraftSessionStore.Session;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.IncidentEventKind;
import org.ctc.domain.service.*;
import org.springframework.stereotype.Component;

import static org.ctc.discord.picks.PickGatewayInteraction.Kind.*;
import static org.ctc.discord.incidents.IncidentInteractionViews.*;

@Component
@RequiredArgsConstructor
public class IncidentInteractionHandler implements PickGatewayInteractionHandler {
    private static final String UNAVAILABLE = "Incident report changed or is unavailable. Use /incidents to reload.";
    private final IncidentAccessService access;
    private final IncidentPrivateReadService reads;
    private final IncidentReportService reports;
    private final IncidentDraftSessionStore sessions;
    private final ImportLockService imports;
    private final PickSessionEpoch epoch;
    private final IncidentLineupService lineups;

    @Override
    public void onInteraction(PickGatewayInteraction event, PickGatewayReply reply) {
        if (event.kind() == BUTTON) {
            try {
                var modal = sessions.withAction(event, (session, action) -> {
                    if (!action.name().equals("open-report")) return null;
                    session.clearActions(); return form(session.action("fields", MODAL), session.payload, session.driverChoices, session.selectedDrivers);
                });
                if (modal != null) { reply.openModal(modal); return; }
            } catch (IncidentLineupException unavailable) { reply.replyPrivate(unavailable.getMessage()); return; }
            catch (BusinessRuleException unavailable) { reply.replyPrivate(UNAVAILABLE); return; }
        }
        long generation = epoch.getAsLong();
        reply.deferPrivate(continuation -> {
            if (!imports.tryEnterWriter()) { continuation.replyPrivate("A backup restore is in progress. Try again later."); return; }
            try {
                if (generation != epoch.getAsLong()) throw unavailable();
                var actor = access.authorizeChannel(event.guildId(), event.channelId(), event.userId()); var view = reads.home(actor);
                if (event.kind() == COMMAND) { menu(sessions.create(event, view), view, continuation); return; }
                var prepared = new PreparedReply();
                sessions.withAction(event, (session, action) -> {
                    if (!session.contextId.equals(view.contextId()) || !session.windowId.equals(view.windowId())) throw unavailable();
                    handle(event, session, action, actor, view, prepared); return true;
                });
                prepared.forwardTo(continuation);
            } catch (IncidentLineupException unavailable) { continuation.replyPrivate(unavailable.getMessage()); }
            catch (IncidentAccessDeniedException denied) { continuation.replyPrivate(denied.getMessage()); }
            catch (BusinessRuleException | IllegalArgumentException unavailable) { continuation.replyPrivate(UNAVAILABLE); }
            finally { imports.exitWriter(); }
        });
    }

    // Publish the next action only after its preceding session operation has released the lock.
    private static final class PreparedReply implements PickGatewayResponse.Continuation {
        private java.util.function.Consumer<PickGatewayResponse.Continuation> delivery = ignored -> { };
        @Override public void replyPrivate(String message) { delivery = target -> target.replyPrivate(message); }
        @Override public void replyPrivate(PickGatewayMessage message) { delivery = target -> target.replyPrivate(message); }
        void forwardTo(PickGatewayResponse.Continuation target) { delivery.accept(target); }
    }

    private void handle(PickGatewayInteraction event, Session session, Action action, AuthorizedIncidentActor actor,
                        IncidentReporterView view, PickGatewayResponse.Continuation reply) {
        switch (action.name()) {
            case "menu" -> menu(session, view, reply);
            case "cases" -> cases(session, view, action.page(), reply);
            case "new" -> {
                if (!view.editable() || view.races().isEmpty()) throw unavailable();
                if (view.races().size() == 1) begin(session, actor, view, view.races().getFirst().id(), null, reply);
                else races(session, view, action.page(), reply);
            }
            case "races" -> { if (!view.editable()) throw unavailable(); races(session, view, action.page(), reply); }
            case "race" -> {
                if (!view.editable()) throw unavailable();
                UUID id = selected(event, action);
                if (view.races().stream().noneMatch(race -> race.id().equals(id))) throw unavailable();
                begin(session, actor, view, id, null, reply);
            }
            case "case", "case-page" -> {
                UUID id = action.name().equals("case") ? selected(event, action) : action.ids().getFirst();
                casePage(session, actor, view, id, action.page(), reply);
            }
            case "edit", "withdraw" -> {
                var saved = reads.load(actor, action.ids().getFirst());
                if (!view.editable() || !saved.summary().editable()) throw unavailable();
                prepare(session, actor, view, saved.summary().raceId(), saved.summary());
                if (action.name().equals("edit")) {
                    session.payload = saved.payload();session.driverChoices = lineups.load(actor, session.raceId);
                    session.selectedDrivers = session.driverChoices.savedSelection(session.payload.getInvolvedDrivers());nextForm(session, reply);
                }
                else {
                    session.clearActions(); send(reply, "Confirm withdrawal of " + escape(saved.summary().reference()) + ". Earlier revisions remain stored.",
                            List.of(button(session, "confirm-withdraw", "Confirm withdrawal"), button(session, "menu", "Cancel")), null);
                }
            }
            case "clear-footage" -> {
                requireCandidate(session, actor);
                var saved = session.payload;
                if (!view.editable() || session.reportId == null || saved == null || !hasLongFootage(saved)) throw unavailable();
                session.payload = new org.ctc.domain.model.IncidentPayload(saved.getInvolvedDrivers(), saved.getReplayTimestamp(), saved.getDescription(), "");
                nextForm(session, reply);
            }
            case "fields" -> {
                requireCandidate(session, actor);
                if (!view.editable()) throw unavailable();
                try {
                    var submitted = submitted(event.values(), session.payload, session.driverChoices);
                    session.payload = submitted.payload();session.selectedDrivers = new java.util.HashSet<>(submitted.selected());session.lineupNotice = null;review(session, 0, reply);
                } catch (IncidentLineupException missingSelection) {
                    session.payload = retainDraft(event.values(), session.payload);session.selectedDrivers = Set.of();
                    session.lineupNotice = missingSelection.getMessage();nextForm(session, reply);
                }
            }
            case "review" -> { requireCandidate(session, actor); review(session, action.page(), reply); }
            case "confirm-submit", "confirm-edit", "confirm-withdraw" -> confirm(session, action.name(), actor, reply);
            default -> throw unavailable();
        }
    }

    private void menu(Session session, IncidentReporterView view, PickGatewayResponse.Continuation reply) {
        session.clearActions(); session.payload = null; var buttons = new ArrayList<PickGatewayMessage.Button>();
        if (view.editable()) buttons.add(button(session, "new", "New report"));
        buttons.add(button(session, "cases", "My reports")); buttons.add(button(session, "menu", "Refresh"));
        send(reply, escape(view.label()) + "\nPrivate incident reporting. Review and confirm before saving."
                + (view.editable() ? "" : "\nReporting is closed; your saved history remains available."), buttons, null);
    }

    private void races(Session session, IncidentReporterView view, int requested, PickGatewayResponse.Continuation reply) {
        session.clearActions(); int page = page(requested, view.races().size()); int start = page * 10;
        var rows = view.races().subList(start, Math.min(start + 10, view.races().size()));
        var buttons = navigation(session, "races", view.races().size(), page); buttons.add(button(session, "menu", "Back"));
        var selection = new PickGatewayMessage.Selection(session.action(new Action("race", SELECT, rows.stream().map(IncidentReporterView.RaceChoice::id).toList(), 0)),
                "Choose concrete race", rows.stream().map(row -> new PickGatewayModal.Option(option(row.label()), row.id().toString())).toList());
        send(reply, escape(view.label()) + "\nChoose the concrete race or leg for this report.", buttons, selection);
    }

    private void cases(Session session, IncidentReporterView view, int requested, PickGatewayResponse.Continuation reply) {
        session.clearActions(); session.payload = null;
        if (view.cases().isEmpty()) { send(reply, "You have no accessible saved incident reports for this pairing.", List.of(button(session, "menu", "Back")), null); return; }
        int page = page(requested, view.cases().size()); int start = page * 10;
        var rows = view.cases().subList(start, Math.min(start + 10, view.cases().size()));
        var buttons = navigation(session, "cases", view.cases().size(), page); buttons.add(button(session, "menu", "Back"));
        var selection = new PickGatewayMessage.Selection(session.action(new Action("case", SELECT, rows.stream().map(IncidentReporterView.CaseChoice::id).toList(), 0)),
                "My reports", rows.stream().map(row -> new PickGatewayModal.Option(option(row.state() + " | " + row.reference() + " | Revision " + row.revision()), row.id().toString())).toList());
        send(reply, escape(view.label()) + "\nYour saved reports, page " + (page + 1) + "."
                + (view.cases().stream().anyMatch(row -> row.state().equals("WITHDRAWN"))
                    ? "\nWithdrawn reports remain in your history and will not be disclosed after the deadline." : ""), buttons, selection);
    }

    private void casePage(Session session, AuthorizedIncidentActor actor, IncidentReporterView view, UUID id, int requested, PickGatewayResponse.Continuation reply) {
        var saved = reads.load(actor, id); var item = saved.summary();
        var pages = pages(item.reference() + " | " + item.state() + " | Revision " + item.revision() + "\n" + raceLabel(view, item.raceId())
                + "\nReporting party: " + item.party()
                + (item.state().equals("WITHDRAWN") ? "\nThis report was withdrawn. It remains in your history and will not be disclosed after the deadline." : "")
                + "\n" + body(saved.payload()));
        int page = Math.min(Math.max(requested, 0), pages.size() - 1); session.clearActions(); var buttons = new ArrayList<PickGatewayMessage.Button>();
        if (page > 0) buttons.add(actionButton(session, "case-page", "Previous", List.of(id), page - 1));
        if (page + 1 < pages.size()) buttons.add(actionButton(session, "case-page", "Next", List.of(id), page + 1));
        else if (view.editable() && item.editable()) {
            buttons.add(actionButton(session, "edit", "Edit report", List.of(id), 0)); buttons.add(actionButton(session, "withdraw", "Withdraw report", List.of(id), 0));
        }
        buttons.add(button(session, "cases", "Back"));
        send(reply, "Your incident report, page " + (page + 1) + "/" + pages.size() + "\n" + pages.get(page), buttons, null);
    }

    private void begin(Session session, AuthorizedIncidentActor actor, IncidentReporterView view, UUID race, IncidentReporterView.CaseChoice saved, PickGatewayResponse.Continuation reply) {
        prepare(session, actor, view, race, saved);session.driverChoices = lineups.load(actor, race);nextForm(session, reply);
    }
    private void prepare(Session session, AuthorizedIncidentActor actor, IncidentReporterView view, UUID race, IncidentReporterView.CaseChoice saved) {
        session.raceId = race; session.reportId = saved == null ? null : saved.id(); session.reportVersion = saved == null ? 0 : saved.version();
        session.windowVersion = view.windowVersion(); session.request = UUID.randomUUID(); session.party = actor.party(); session.payload = null;
        session.driverChoices = null;session.selectedDrivers = java.util.Set.of();session.lineupNotice = null;
        session.label = raceLabel(view, race); session.reportingParty = saved == null ? actor.party().getLabel() : saved.party();
    }
    private void nextForm(Session session, PickGatewayResponse.Continuation reply) {
        session.clearActions();
        var buttons = new ArrayList<PickGatewayMessage.Button>();
        buttons.add(button(session, "open-report", "Enter report"));
        if (hasLongFootage(session.payload)) buttons.add(button(session, "clear-footage", "Clear footage in draft"));
        String guidance = session.reportId == null ? "Choose the involved drivers from this race lineup and enter the report fields, then review before confirming."
                : hasLongText(session.payload)
                    ? "Saved fields within Discord's limits are prefilled. Longer description or footage fields are blank: leave them blank to keep the saved text, or enter a replacement. Use Clear footage in draft to remove longer saved footage."
                    : "Your saved values are prefilled. Review your changes before confirming.";
        if (session.lineupNotice != null) guidance = session.lineupNotice + " " + guidance;
        if (session.reportId != null && session.payload != null && session.selectedDrivers.isEmpty()) guidance += " Previously saved driver text could not be matched to the current lineup. Select the involved drivers again; the stored report remains unchanged until confirmation.";
        send(reply, escape(session.label) + "\n" + guidance + "\nNothing is saved until confirmation. Earlier revisions remain stored.", buttons, null);
    }
    private void review(Session session, int requested, PickGatewayResponse.Continuation reply) {
        if (session.payload == null) throw unavailable();
        var pages = pages(session.label + "\nReporting party: " + session.reportingParty + "\n" + body(session.payload));
        int page = Math.min(Math.max(requested, 0), pages.size() - 1); session.clearActions(); var buttons = new ArrayList<PickGatewayMessage.Button>();
        if (page > 0) buttons.add(actionButton(session, "review", "Previous", List.of(), page - 1));
        if (page + 1 < pages.size()) buttons.add(actionButton(session, "review", "Next", List.of(), page + 1));
        else buttons.add(button(session, session.reportId == null ? "confirm-submit" : "confirm-edit", session.reportId == null ? "Confirm report" : "Confirm changes"));
        buttons.add(button(session, "menu", "Cancel"));
        send(reply, "Review incident report, page " + (page + 1) + "/" + pages.size() + "\n" + pages.get(page), buttons, null);
    }
    private void confirm(Session session, String operation, AuthorizedIncidentActor actor, PickGatewayResponse.Continuation reply) {
        requireCandidate(session, actor); IncidentReportReceipt receipt;
        if (operation.equals("confirm-withdraw")) receipt = reports.withdraw(session.reportId, session.windowVersion, session.reportVersion, session.request, actor);
        else {
            if (session.payload == null) throw unavailable();
            var selection = new IncidentDriverSelection(session.raceId, session.driverChoices.fingerprint(), List.copyOf(session.selectedDrivers));
            try {
                receipt = operation.equals("confirm-submit") ? reports.submitSelected(session.windowId, session.raceId, session.windowVersion, session.request, actor, session.payload, selection)
                        : reports.editSelected(session.reportId, session.windowVersion, session.reportVersion, session.request, actor, session.payload, selection);
            } catch (IncidentLineupException changed) {
                session.lineupNotice = changed.getMessage();
                session.driverChoices = lineups.load(actor, session.raceId);
                session.selectedDrivers = session.selectedDrivers.stream().filter(id -> session.driverChoices.drivers().stream().anyMatch(driver -> driver.id().equals(id)))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                nextForm(session, reply);return;
            }
        }
        String message = receipt.action() == IncidentEventKind.SUBMITTED ? "Stored " + receipt.caseReference() + " revision " + receipt.revisionNumber() + ". Neutral receipt delivery is pending."
                : receipt.action() == IncidentEventKind.WITHDRAWN ? "Stored withdrawal of " + receipt.caseReference() + ". Its history remains available; it will not be disclosed after the deadline. The original neutral receipt may remain in the channel."
                : "Stored changes to " + receipt.caseReference() + " revision " + receipt.revisionNumber() + ".";
        send(reply, escape(message), List.of(button(session, "cases", "My reports")), null);
    }
    private static void requireCandidate(Session session, AuthorizedIncidentActor actor) { if (!Objects.equals(session.party, actor.party())) throw unavailable(); }
    private static String raceLabel(IncidentReporterView view, UUID race) { return view.races().stream().filter(row -> row.id().equals(race)).findFirst().orElseThrow(IncidentInteractionHandler::unavailable).label(); }
    private static UUID selected(PickGatewayInteraction event, Action action) {
        var values = event.values().get("selection"); if (values == null || values.size() != 1) throw unavailable();
        UUID id = UUID.fromString(values.getFirst()); if (!action.ids().contains(id)) throw unavailable(); return id;
    }
    private static int page(int requested, int size) { return Math.min(Math.max(requested, 0), Math.max(0, (size - 1) / 10)); }
    private static ArrayList<PickGatewayMessage.Button> navigation(Session session, String name, int size, int page) {
        var buttons = new ArrayList<PickGatewayMessage.Button>();
        if (page > 0) buttons.add(actionButton(session, name, "Previous", List.of(), page - 1));
        if ((page + 1) * 10 < size) buttons.add(actionButton(session, name, "Next", List.of(), page + 1)); return buttons;
    }
    private static PickGatewayMessage.Button button(Session session, String name, String label) { return new PickGatewayMessage.Button(session.action(name, BUTTON), label); }
    private static PickGatewayMessage.Button actionButton(Session session, String name, String label, List<UUID> ids, int page) { return new PickGatewayMessage.Button(session.action(new Action(name, BUTTON, ids, page)), label); }
    private static void send(PickGatewayResponse.Continuation reply, String text, List<PickGatewayMessage.Button> buttons, PickGatewayMessage.Selection selection) { reply.replyPrivate(new PickGatewayMessage(text, buttons, selection)); }
    private static BusinessRuleException unavailable() { return new BusinessRuleException(UNAVAILABLE); }
}
