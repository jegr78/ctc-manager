package org.ctc.discord.picks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.ctc.domain.exception.BusinessRuleException;
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
        String user = receipt.submitterId();
        String author = user != null && user.matches("[0-9]{1,20}") && !user.chars().allMatch(c -> c == '0')
                ? "<@" + user + ">" : "an unknown team member";
        return "revision " + receipt.revisionNumber() + ", version " + receipt.aggregateVersion() + ", saved "
                + receipt.confirmedAt() + " by " + author;
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

    static PickGatewayModal modal(String id, String stage, LobbyRulesProfile profile, boolean oneMake) {
        return modal(id, stage, profile, oneMake, Map.of());
    }

    static PickGatewayModal modal(String id, String stage, LobbyRulesProfile profile, boolean oneMake,
                                  Map<String, List<String>> defaults) {
        var family = family(profile, oneMake, defaults);
        List<PickGatewayModal.Field> fields = switch (stage) {
            case "rules" -> {
                var result = new ArrayList<PickGatewayModal.Field>(List.of(text(defaults, "laps", "Laps, positive integer", 10),
                        text(defaults, "wear", "Tyre wear, 1 to 7", 1), text(defaults, "consumption", "Fuel consumption, 1 to 6", 1),
                        text(defaults, "refuel", "Refuelling litres/second, 1 to 10", 2)));
                if (sportsAllowed(profile, oneMake)) {
                    result.add(select(defaults, "family", "Tyre family", List.of(new PickGatewayModal.Option("Racing", "RACING"),
                            new PickGatewayModal.Option("Sports", "SPORTS")), 1));
                }
                yield result;
            }
            case "fuel" -> List.of(select(defaults, "initial", "Initial fuel", Arrays.stream(PickInitialFuel.values())
                            .map(value -> new PickGatewayModal.Option(value.settingsValue(), value.name())).toList(), 1),
                    select(defaults, "stops", "Required pit stops", options("0", "1", "2"), 1),
                    select(defaults, "available", "Available dry tyres", tyreOptions(family), 3));
            case "mandatory" -> {
                var options = new ArrayList<PickGatewayModal.Option>();
                options.add(new PickGatewayModal.Option("None", "None"));
                var selected = defaults.getOrDefault("available", List.of());
                tyreOptions(family).stream().filter(option -> selected.contains(option.value())).forEach(options::add);
                yield List.of(select(defaults, "mandatory", "Mandatory dry tyres", options, Math.max(1, options.size() - 1)));
            }
            case "time" -> {
                var progression = new ArrayList<PickGatewayModal.Option>();
                progression.add(new PickGatewayModal.Option("Equal Conditions", "equal"));
                for (int i = 0; i <= 6; i++) progression.add(new PickGatewayModal.Option(i + "x", Integer.toString(i)));
                var result = new ArrayList<PickGatewayModal.Field>(List.of(select(defaults, "progression", "Time progression", progression, 1),
                        new PickGatewayModal.Text("weather", "Weather, keep slot order", true, defaultText(defaults, "weather"), 1, 255, true),
                        text(defaults, "daytime", "Time of day", 255)));
                if (profile == LobbyRulesProfile.CTC_2027) {
                    result.add(select(defaults, "start", "Start type", profile.getStartTypes().stream()
                            .map(value -> new PickGatewayModal.Option(value.getLabel(), value.name())).toList(), 1));
                    result.add(select(defaults, "slipstream", "Race slipstream", profile.getSlipstreams().stream()
                            .map(value -> new PickGatewayModal.Option(value.getLabel(), value.name())).toList(), 1));
                }
                yield result;
            }
            default -> List.of(new PickGatewayModal.Text("query", "Search local catalog", false, null, 0, 100, false));
        };
        return new PickGatewayModal(id, stage.startsWith("search-") ? "Search private picks" : "Private picks: " + stage, fields);
    }

    static PickGatewayModal modal(String id, String stage, LobbyRulesProfile profile) {
        return modal(id, stage, profile, false);
    }

    private static List<PickGatewayModal.Option> tyreOptions(PickTyreFamily family) {
        return Arrays.stream(PickTyre.values()).filter(tyre -> tyre.getFamily() == family && !tyre.isWet())
                .map(tyre -> new PickGatewayModal.Option(tyre.getLabel(), tyre.name())).toList();
    }
    static boolean sportsAllowed(LobbyRulesProfile profile, boolean oneMake) {
        return profile == LobbyRulesProfile.CTC_2027 && oneMake;
    }
    static PickTyreFamily family(LobbyRulesProfile profile, boolean oneMake, Map<String, List<String>> fields) {
        if (!sportsAllowed(profile, oneMake)) return PickTyreFamily.RACING;
        return PickTyreFamily.valueOf(fields.containsKey("family") ? one(fields, "family") : "RACING");
    }
    private static String defaultText(Map<String, List<String>> defaults, String id) {
        var values = defaults.get(id);
        return values != null && values.size() == 1 ? values.getFirst() : null;
    }
    private static PickGatewayModal.Text text(Map<String, List<String>> defaults, String id, String label, int max) {
        return new PickGatewayModal.Text(id, label, false, defaultText(defaults, id), 1, max, true);
    }
    private static PickGatewayModal.Selection select(Map<String, List<String>> defaults, String id, String label,
                                                     List<PickGatewayModal.Option> options, int max) {
        var requested = defaults.getOrDefault(id, id.equals("family") ? List.of("RACING") : List.of());
        var values = id.equals("mandatory") && options.stream().noneMatch(option -> requested.contains(option.value()))
                ? List.of("None") : requested;
        var choices = options.stream().map(option -> new PickGatewayModal.Option(option.label(), option.value(),
                values.contains(option.value()))).toList();
        return new PickGatewayModal.Selection(id, label, choices, 1, Math.min(max, choices.size()));
    }
    private static List<PickGatewayModal.Option> options(String... values) {
        return Arrays.stream(values).map(value -> new PickGatewayModal.Option(value, value)).toList();
    }

    static void validateStage(String stage, Map<String, List<String>> values) { validateStage(stage, values, LobbyRulesProfile.LEGACY); }

    static void validateStage(String stage, Map<String, List<String>> values, LobbyRulesProfile profile, boolean oneMake) {
        validateStage(stage, values, profile, oneMake, Map.of());
    }

    static void validateStage(String stage, Map<String, List<String>> values, LobbyRulesProfile profile, boolean oneMake,
                               Map<String, List<String>> context) {
        Set<String> expected = switch (stage) {
            case "rules" -> sportsAllowed(profile, oneMake) ? Set.of("laps", "wear", "consumption", "refuel", "family")
                    : Set.of("laps", "wear", "consumption", "refuel");
            case "fuel" -> Set.of("initial", "stops", "available");
            case "mandatory" -> Set.of("mandatory");
            case "time" -> profile == LobbyRulesProfile.CTC_2027 ? Set.of("progression", "weather", "daytime", "start", "slipstream")
                    : Set.of("progression", "weather", "daytime");
            default -> Set.of("query");
        };
        if (!values.keySet().equals(expected) && !(stage.startsWith("search-") && values.isEmpty())) {
            throw new IllegalArgumentException("Unexpected form fields");
        }
        switch (stage) {
            case "rules" -> { number(values, "laps", 1, Integer.MAX_VALUE); number(values, "wear", 1, 7);
                number(values, "consumption", 1, 6); number(values, "refuel", 1, 10);
                if (sportsAllowed(profile, oneMake)) family(profile, true, values); }
            case "fuel" -> {
                PickInitialFuel.valueOf(one(values, "initial")); number(values, "stops", 0, 2);
                availableInput(values, profile, oneMake, context);
            }
            case "mandatory" -> {
                var available = availableInput(context, profile, oneMake, context);
                var mandatory = tyreSet(values, "mandatory", true);
                try {
                    requireTyres(profile, oneMake, available, mandatory);
                } catch (BusinessRuleException invalid) {
                    throw new IllegalArgumentException(invalid.getMessage(), invalid);
                }
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

    static void validateStage(String stage, Map<String, List<String>> values, LobbyRulesProfile profile) {
        validateStage(stage, values, profile, false);
    }

    private static void requireTyres(LobbyRulesProfile profile, boolean oneMake, Set<PickTyre> available, Set<PickTyre> mandatory) {
        var family = PickTyreSelection.require(profile, available, mandatory);
        if (family == PickTyreFamily.SPORTS && !oneMake) {
            throw new BusinessRuleException("Sports tyres require an explicit One-Make entry");
        }
    }
    private static Set<PickTyre> availableInput(Map<String, List<String>> values, LobbyRulesProfile profile, boolean oneMake,
                                               Map<String, List<String>> context) {
        var selected = tyreSet(values, "available", false);
        var family = family(profile, oneMake, context);
        if (selected.stream().anyMatch(tyre -> tyre.isWet() || tyre.getFamily() != family)) {
            throw new IllegalArgumentException("Choose dry compounds from the selected tyre family");
        }
        try {
            var available = PickTyreSelection.withAutomaticWetTyres(profile, selected);
            requireTyres(profile, oneMake, available, Set.of());
            return available;
        } catch (BusinessRuleException invalid) {
            throw new IllegalArgumentException(invalid.getMessage(), invalid);
        }
    }

    static Map<String, List<String>> draftFields(PickPayload payload) {
        if (payload == null) return Map.of();
        var fields = new HashMap<String, List<String>>();
        fields.put("laps", List.of(payload.laps().toString()));
        fields.put("wear", List.of(payload.tyreWear().toString()));
        fields.put("consumption", List.of(payload.fuelConsumption().toString()));
        fields.put("refuel", List.of(payload.refuelingSpeed().toString()));
        fields.put("initial", List.of(payload.initialFuel().name()));
        fields.put("stops", List.of(payload.requiredPitStops().toString()));
        fields.put("family", List.of(payload.availableTyres().iterator().next().getFamily().name()));
        fields.put("available", payload.availableTyres().stream().filter(tyre -> !tyre.isWet()).sorted().map(Enum::name).toList());
        fields.put("mandatory", payload.mandatoryTyres().isEmpty() ? List.of("None")
                : payload.mandatoryTyres().stream().sorted().map(Enum::name).toList());
        fields.put("progression", List.of(payload.timeProgressionMode() == TimeProgressionMode.EQUAL_CONDITIONS
                ? "equal" : payload.timeProgressionMultiplier().toString()));
        fields.put("weather", List.of(payload.weather()));
        fields.put("daytime", List.of(payload.timeOfDay()));
        fields.put("start", List.of(payload.startType().name()));
        fields.put("slipstream", List.of(payload.slipstream().name()));
        return Map.copyOf(fields);
    }
    static PickPayload payload(PickDraftSessionStore.Session session) {
        var fields = session.fields;
        String progression = one(fields, "progression");
        var available = PickTyreSelection.withAutomaticWetTyres(session.lobbyRulesProfile, tyreSet(fields, "available", false));
        var mandatory = tyreSet(fields, "mandatory", true);
        requireTyres(session.lobbyRulesProfile, session.car.oneMake(), available, mandatory);
        if (available.iterator().next().getFamily() != family(session.lobbyRulesProfile, session.car.oneMake(), fields)) {
            throw new BusinessRuleException("Tyre family changed; reload the current choices");
        }
        return new PickPayload(session.track.id(), session.car.id(), session.car.entryId(), number(fields, "laps", 1, Integer.MAX_VALUE),
                number(fields, "wear", 1, 7), number(fields, "consumption", 1, 6), number(fields, "refuel", 1, 10),
                PickInitialFuel.valueOf(one(fields, "initial")), number(fields, "stops", 0, 2),
                "equal".equals(progression) ? TimeProgressionMode.EQUAL_CONDITIONS : TimeProgressionMode.MULTIPLIER,
                "equal".equals(progression) ? null : number(fields, "progression", 0, 6), one(fields, "weather"), one(fields, "daytime"),
                available, mandatory, session.lobbyRulesProfile,
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
        if (values == null || values.isEmpty() || values.size() > 3 || values.stream().anyMatch(java.util.Objects::isNull)
                || values.stream().distinct().count() != values.size()) {
            throw new IllegalArgumentException("Choose valid tyres");
        }
        if (allowNone && values.equals(List.of("None"))) return Set.of();
        return values.stream().map(PickTyre::valueOf).collect(Collectors.toUnmodifiableSet());
    }
}
