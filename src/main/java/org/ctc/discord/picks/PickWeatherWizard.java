package org.ctc.discord.picks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PickWeatherSelection;
import org.ctc.domain.model.PickWeatherSelection.Mode;
import org.ctc.domain.model.WeatherPreset;
import org.ctc.domain.picks.PickTeamView.Choice;
import org.ctc.discord.picks.PickDraftSessionStore.Action;
import org.ctc.discord.picks.PickDraftSessionStore.Session;
import static org.ctc.discord.picks.PickGatewayInteraction.Kind.*;
import static org.ctc.discord.picks.PickInteractionViews.*;

/** Each ordered slot has its own single-choice menu, including repeated codes. */
final class PickWeatherWizard {
    private PickWeatherWizard() { }

    static Map<String, List<String>> defaults(String raw) {
        var result = new HashMap<String, List<String>>();
        PickWeatherSelection.readDefaults(raw).ifPresent(selection -> {
            result.put("weather-mode", List.of(selection.mode().name()));
            result.put("weather-count", List.of(Integer.toString(selection.slots().size())));
            for (int i = 0; i < selection.slots().size(); i++) result.put(key(i), List.of(selection.slots().get(i)));
        });
        return result;
    }

    static void start(Session session, Choice layout, PickGatewayResponse.Continuation reply) {
        session.payload = null;
        session.clearActions();
        var buttons = new ArrayList<PickGatewayMessage.Button>();
        try {
            selection(session).requireAvailable(layout.rainCapability());
            buttons.add(new PickGatewayMessage.Button(route(session, "weather-keep", BUTTON, List.of(), 0, 0, false), "Keep selected weather"));
        } catch (BusinessRuleException | IllegalArgumentException incomplete) {
            // Unsupported historic text remains visible in the committed receipt, without invented slots.
        }
        var options = List.of(new PickGatewayModal.Option("Preset: one weather code", "PRESET", selected(session, "weather-mode", "PRESET")),
                new PickGatewayModal.Option("Custom: 6 to 8 ordered slots", "CUSTOM", selected(session, "weather-mode", "CUSTOM")));
        send(reply, escape(session.label) + "\nChoose weather mode. Custom slots may repeat; ? means Random."
                        + (layout.rainCapability() == org.ctc.domain.model.TrackRainCapability.UNKNOWN
                        ? "\nRain availability is unverified for this layout. Ask management to configure it before choosing rain codes." : ""),
                buttons, new PickGatewayMessage.Selection(route(session, "weather-mode", SELECT, List.of("PRESET", "CUSTOM"), 0, 0, false), "Choose Preset or Custom", options));
    }

    static boolean handle(Session session, Action action, Map<String, List<String>> values, Choice layout,
                          PickGatewayResponse.Continuation reply) {
        switch (action.name()) {
            case "weather-change" -> start(session, layout, reply);
            case "weather-keep" -> { finish(session, layout); return true; }
            case "weather-mode" -> {
                String mode = chosen(action, values);
                session.fields.put("weather-mode", List.of(mode)); session.fields.remove("weather");
                session.payload = null;
                if (mode.equals("PRESET")) slot(session, layout, 0, -1, false, reply);
                else count(session, reply);
            }
            case "weather-count" -> {
                session.fields.put("weather-count", List.of(chosen(action, values)));
                slot(session, layout, 0, -1, false, reply);
            }
            case "weather-page" -> slot(session, layout, action.weatherSlot(), action.page(), action.weatherEdit(), reply);
            case "weather-slot" -> {
                String code = action.kind() == BUTTON ? action.values().getFirst() : chosen(action, values);
                if (!PickWeatherSelection.catalog(layout.rainCapability(), mode(session)).contains(code)) throw invalid();
                session.fields.put(key(action.weatherSlot()), List.of(code));
                if (action.weatherEdit() || mode(session) == Mode.PRESET || action.weatherSlot() + 1 == count(session)) {
                    finish(session, layout); return true;
                }
                slot(session, layout, action.weatherSlot() + 1, -1, false, reply);
            }
            case "weather-edit" -> slot(session, layout, Integer.parseInt(chosen(action, values)), -1, true, reply);
            default -> throw invalid();
        }
        return false;
    }

    static PickGatewayMessage.Selection editSlots(Session session) {
        var selection = selection(session);
        if (selection.mode() != Mode.CUSTOM) return null;
        var positions = IntStream.range(0, selection.slots().size()).mapToObj(Integer::toString).toList();
        var menu = new PickGatewayMessage.Selection(route(session, "weather-edit", SELECT, positions, 0, 0, true), "Change one custom weather slot",
                IntStream.range(0, selection.slots().size()).mapToObj(i -> new PickGatewayModal.Option(
                        "Slot " + (i + 1) + ": " + selection.slots().get(i), Integer.toString(i))).toList());
        return menu;
    }

    private static void count(Session session, PickGatewayResponse.Continuation reply) {
        session.clearActions();
        var counts = List.of("6", "7", "8");
        send(reply, escape(session.label) + "\nChoose the number of custom weather slots.", List.of(),
                new PickGatewayMessage.Selection(route(session, "weather-count", SELECT, counts, 0, 0, false), "Choose 6, 7 or 8 slots",
                        counts.stream().map(n -> new PickGatewayModal.Option(n + " slots", n, selected(session, "weather-count", n))).toList()));
    }

    private static void slot(Session session, Choice layout, int position, int requested, boolean edit,
                             PickGatewayResponse.Continuation reply) {
        session.clearActions(); session.payload = null;
        var choices = PickWeatherSelection.catalog(layout.rainCapability(), mode(session));
        String current = session.fields.getOrDefault(key(position), List.of("")).getFirst();
        int pages = (choices.size() + 24) / 25;
        int page = requested < 0 ? Math.max(0, choices.indexOf(current)) / 25 : Math.clamp(requested, 0, pages - 1);
        var codes = choices.subList(page * 25, Math.min(choices.size(), page * 25 + 25));
        var buttons = new ArrayList<PickGatewayMessage.Button>();
        if (page > 0) buttons.add(new PickGatewayMessage.Button(route(session, "weather-page", BUTTON, List.of(), page - 1, position, edit), "Previous"));
        if (page + 1 < pages) buttons.add(new PickGatewayMessage.Button(route(session, "weather-page", BUTTON, List.of(), page + 1, position, edit), "Next"));
        if (choices.contains(current)) buttons.add(new PickGatewayMessage.Button(route(session, "weather-slot", BUTTON, List.of(current), page, position, edit), "Keep selected slot"));
        send(reply, escape(session.label) + "\n" + (mode(session) == Mode.PRESET ? "Preset weather" : "Slot " + (position + 1) + " of " + count(session))
                        + ". Page " + (page + 1) + "/" + pages + "\nCustom slots may repeat; ? means Random.", buttons,
                new PickGatewayMessage.Selection(route(session, "weather-slot", SELECT, codes, page, position, edit), "Choose weather code",
                        codes.stream().map(code -> new PickGatewayModal.Option(optionLabel(code.equals("?") ? "? · Random"
                                : code + " · " + WeatherPreset.valueOf(code).getDescription()), code, code.equals(current))).toList()));
    }

    private static String chosen(Action action, Map<String, List<String>> values) {
        try {
            if (!values.keySet().equals(java.util.Set.of("selection"))) throw invalid();
            String value = one(values, "selection");
            if (!action.values().contains(value)) throw invalid();
            return value;
        } catch (IllegalArgumentException malformed) { throw invalid(); }
    }
    private static Mode mode(Session session) { return Mode.valueOf(one(session.fields, "weather-mode")); }
    private static int count(Session session) {
        if (mode(session) == Mode.PRESET) return 1;
        String count = one(session.fields, "weather-count");
        if (!List.of("6", "7", "8").contains(count)) throw invalid();
        return Integer.parseInt(count);
    }
    private static PickWeatherSelection selection(Session session) {
        return new PickWeatherSelection(mode(session), IntStream.range(0, count(session)).mapToObj(i -> one(session.fields, key(i))).toList());
    }
    private static void finish(Session session, Choice layout) {
        var selection = selection(session); selection.requireAvailable(layout.rainCapability());
        session.fields.put("weather", List.of(selection.encode()));
    }
    private static String route(Session session, String name, PickGatewayInteraction.Kind kind, List<String> values, int page, int slot, boolean edit) {
        return session.action(new Action(name, kind, List.of(), List.of(), page, "", values, slot, edit));
    }
    private static boolean selected(Session session, String key, String value) { return session.fields.getOrDefault(key, List.of()).equals(List.of(value)); }
    private static String key(int i) { return "weather-slot-" + i; }
    private static BusinessRuleException invalid() { return new BusinessRuleException("Weather choices changed or are invalid; reload the current weather choices"); }
}
