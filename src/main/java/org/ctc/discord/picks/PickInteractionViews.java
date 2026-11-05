package org.ctc.discord.picks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.PickTeamView;
import org.ctc.domain.picks.SubmissionReceipt;

final class PickInteractionViews {
    private PickInteractionViews() { }

    static String escape(String value) {
        if (value == null) return "None";
        return value.replace("\\", "\\\\").replace("@", "@\u200b").replace("*", "\\*").replace("_", "\\_")
                .replace("`", "\\`").replace("~", "\\~").replace("|", "\\|").replace("[", "\\[").replace("]", "\\]")
                .replace("<", "\\<").replace(">", "\\>").replace("#", "\\#");
    }
    static String optionLabel(String text) {
        String clean = text.replaceAll("[\\p{Cntrl}]", " ").strip();
        return clean.length() <= 100 ? clean : clean.substring(0, 97) + "...";
    }

    static void send(PickGatewayResponse.Continuation reply, String content, List<PickGatewayMessage.Button> buttons,
                     PickGatewayMessage.Selection selection) {
        while (content.length() > 1900) {
            int cut = content.lastIndexOf('\n', 1900);
            if (cut < 1) cut = 1900;
            reply.replyPrivate(content.substring(0, cut)); content = content.substring(cut).stripLeading();
        }
        reply.replyPrivate(new PickGatewayMessage(content.isEmpty() ? "Private picks" : content, buttons, selection));
    }

    static String status(PickTeamView view) {
        var text = new StringBuilder(escape(view.target().label())).append("\nState: ").append(view.target().state())
                .append("\nSeason rules: ").append(view.lobbyRulesProfile().getLabel())
                .append("\n").append(escape(view.target().reason())).append("\nDeadline: ").append(escape(view.deadline()))
                .append("\nManagement opening: ").append(view.manualOpen() ? "Yes, no new deadline" : "No")
                .append("\nManually closed: ").append(view.manualClosed() ? "Yes" : "No");
        if (view.returnReason() != null) text.append("\nReturn reason: ").append(escape(view.returnReason()));
        if (view.payload() != null) text.append("\n\nCurrent ").append(receipt(view.receipt())).append("\n")
                .append(payload(view.payload(), view.trackLabel(), view.carLabel()));
        else text.append("\nNo confirmed submission.");
        return text.toString();
    }

    static String receipt(SubmissionReceipt receipt) {
        return "revision " + receipt.revisionNumber() + ", version " + receipt.aggregateVersion() + ", saved "
                + receipt.confirmedAt() + " by Discord user " + receipt.submitterId();
    }

    static String payload(PickPayload payload, String track, String car) {
        return "Track: " + escape(track) + "\nCar: " + escape(car) + "\nLaps: " + payload.laps()
                + "\nTyre wear: " + payload.tyreWear() + "\nFuel consumption: " + payload.fuelConsumption()
                + "\nRefuelling speed: " + payload.refuelingSpeed() + " litres/second\nInitial fuel: " + payload.initialFuel().settingsValue()
                + "\nRequired pit stops: " + payload.requiredPitStops() + "\nTime progression: "
                + (payload.timeProgressionMode() == TimeProgressionMode.EQUAL_CONDITIONS ? "Equal Conditions" : payload.timeProgressionMultiplier() + "x")
                + "\nWeather: " + escape(payload.weather()) + "\nTime of day: " + escape(payload.timeOfDay())
                + "\nAvailable tyres: " + tyres(payload.availableTyres()) + "\nMandatory tyres: " + tyres(payload.mandatoryTyres())
                + "\nSeason rules: " + payload.lobbyRulesProfile().getLabel() + "\nStart type: " + payload.startType().getLabel()
                + "\nRace slipstream: " + payload.slipstream().getLabel();
    }
    private static String tyres(Set<PickTyre> tyres) {
        return tyres.isEmpty() ? "None" : tyres.stream().sorted().map(Enum::name).collect(Collectors.joining(", "));
    }

    static PickGatewayModal modal(String id, String stage) { return modal(id, stage, LobbyRulesProfile.LEGACY); }

    static PickGatewayModal modal(String id, String stage, LobbyRulesProfile profile) {
        List<PickGatewayModal.Field> fields = switch (stage) {
            case "rules" -> List.of(text("laps", "Laps, positive integer", 10), text("wear", "Tyre wear, 1 to 7", 1),
                    text("consumption", "Fuel consumption, 1 to 6", 1), text("refuel", "Refuelling litres/second, 1 to 10", 2));
            case "fuel" -> List.of(select("initial", "Initial fuel", Arrays.stream(PickInitialFuel.values())
                            .map(value -> new PickGatewayModal.Option(value.settingsValue(), value.name())).toList(), 1),
                    select("stops", "Required pit stops", options("0", "1", "2"), 1),
                    select("available", "Available tyres", options("RS", "RM", "RH"), 3),
                    select("mandatory", "Mandatory tyres, None or a subset", options("None", "RS", "RM", "RH"), 3));
            case "time" -> {
                var progression = new ArrayList<PickGatewayModal.Option>();
                progression.add(new PickGatewayModal.Option("Equal Conditions", "equal"));
                for (int i = 0; i <= 6; i++) progression.add(new PickGatewayModal.Option(i + "x", Integer.toString(i)));
                var result = new ArrayList<PickGatewayModal.Field>(List.of(select("progression", "Time progression", progression, 1),
                        new PickGatewayModal.Text("weather", "Weather, keep slot order", true, null, 1, 255, true),
                        text("daytime", "Time of day", 255)));
                if (profile == LobbyRulesProfile.CTC_2027) {
                    result.add(select("start", "Start type", profile.getStartTypes().stream()
                            .map(value -> new PickGatewayModal.Option(value.getLabel(), value.name())).toList(), 1));
                    result.add(select("slipstream", "Race slipstream", profile.getSlipstreams().stream()
                            .map(value -> new PickGatewayModal.Option(value.getLabel(), value.name())).toList(), 1));
                }
                yield result;
            }
            default -> List.of(new PickGatewayModal.Text("query", "Search local catalog", false, null, 0, 100, false));
        };
        return new PickGatewayModal(id, stage.startsWith("search-") ? "Search private picks" : "Private picks: " + stage, fields);
    }
    private static PickGatewayModal.Text text(String id, String label, int max) {
        return new PickGatewayModal.Text(id, label, false, null, 1, max, true);
    }
    private static PickGatewayModal.Selection select(String id, String label, List<PickGatewayModal.Option> options, int max) {
        return new PickGatewayModal.Selection(id, label, options, 1, max);
    }
    private static List<PickGatewayModal.Option> options(String... values) {
        return Arrays.stream(values).map(value -> new PickGatewayModal.Option(value, value)).toList();
    }

    static void validateStage(String stage, Map<String, List<String>> values) { validateStage(stage, values, LobbyRulesProfile.LEGACY); }

    static void validateStage(String stage, Map<String, List<String>> values, LobbyRulesProfile profile) {
        Set<String> expected = switch (stage) {
            case "rules" -> Set.of("laps", "wear", "consumption", "refuel");
            case "fuel" -> Set.of("initial", "stops", "available", "mandatory");
            case "time" -> profile == LobbyRulesProfile.CTC_2027 ? Set.of("progression", "weather", "daytime", "start", "slipstream")
                    : Set.of("progression", "weather", "daytime");
            default -> Set.of("query");
        };
        if (!values.keySet().equals(expected) && !(stage.startsWith("search-") && values.isEmpty())) {
            throw new IllegalArgumentException("Unexpected form fields");
        }
        switch (stage) {
            case "rules" -> { number(values, "laps", 1, Integer.MAX_VALUE); number(values, "wear", 1, 7);
                number(values, "consumption", 1, 6); number(values, "refuel", 1, 10); }
            case "fuel" -> {
                PickInitialFuel.valueOf(one(values, "initial")); number(values, "stops", 0, 2);
                var available = tyreSet(values, "available", false); var mandatory = tyreSet(values, "mandatory", true);
                if (!available.containsAll(mandatory)) throw new IllegalArgumentException("Mandatory tyres must be available");
            }
            case "time" -> {
                String progression = one(values, "progression");
                if (!"equal".equals(progression)) number(values, "progression", 0, 6);
                boundedText(values, "weather", 255); boundedText(values, "daytime", 255);
                if (profile == LobbyRulesProfile.CTC_2027) {
                    profile.requireChoices(RaceStartType.valueOf(one(values, "start")), RaceSlipstream.valueOf(one(values, "slipstream")));
                }
            }
            default -> {
                var query = values.getOrDefault("query", List.of(""));
                if (query.size() != 1 || query.getFirst().length() > 100) throw new IllegalArgumentException("Invalid query");
            }
        }
    }
    static PickPayload payload(PickDraftSessionStore.Session session) {
        var fields = session.fields;
        String progression = one(fields, "progression");
        return new PickPayload(session.track.id(), session.car.id(), session.car.entryId(), number(fields, "laps", 1, Integer.MAX_VALUE),
                number(fields, "wear", 1, 7), number(fields, "consumption", 1, 6), number(fields, "refuel", 1, 10),
                PickInitialFuel.valueOf(one(fields, "initial")), number(fields, "stops", 0, 2),
                "equal".equals(progression) ? TimeProgressionMode.EQUAL_CONDITIONS : TimeProgressionMode.MULTIPLIER,
                "equal".equals(progression) ? null : number(fields, "progression", 0, 6), one(fields, "weather"), one(fields, "daytime"),
                tyreSet(fields, "available", false), tyreSet(fields, "mandatory", true), session.lobbyRulesProfile,
                session.lobbyRulesProfile == LobbyRulesProfile.CTC_2027 ? RaceStartType.valueOf(one(fields, "start")) : RaceStartType.GRID_FALSE_START,
                session.lobbyRulesProfile == LobbyRulesProfile.CTC_2027 ? RaceSlipstream.valueOf(one(fields, "slipstream")) : RaceSlipstream.REAL);
    }
    static String one(Map<String, List<String>> fields, String key) {
        var values = fields.get(key);
        if (values == null || values.size() != 1 || values.getFirst() == null) throw new IllegalArgumentException("One value required");
        return values.getFirst();
    }
    private static int number(Map<String, List<String>> fields, String key, int min, int max) {
        String raw = one(fields, key);
        if (!raw.matches("[0-9]{1,10}")) throw new IllegalArgumentException("Integer required");
        int value = Integer.parseInt(raw);
        if (value < min || value > max) throw new IllegalArgumentException("Outside range");
        return value;
    }
    private static void boundedText(Map<String, List<String>> fields, String key, int max) {
        String value = one(fields, key);
        if (value.isBlank() || value.length() > max) throw new IllegalArgumentException("Text required");
    }
    private static Set<PickTyre> tyreSet(Map<String, List<String>> fields, String key, boolean allowNone) {
        var values = fields.get(key);
        if (values == null || values.isEmpty() || values.size() > 3 || values.stream().distinct().count() != values.size()) {
            throw new IllegalArgumentException("Choose valid tyres");
        }
        if (allowNone && values.equals(List.of("None"))) return Set.of();
        return values.stream().map(PickTyre::valueOf).collect(Collectors.toUnmodifiableSet());
    }
}
