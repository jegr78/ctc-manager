package org.ctc.discord.picks;

import java.util.List;
import java.util.Locale;
import org.ctc.domain.picks.PickTeamView.Choice;

public final class PickCatalogSelector {
    private PickCatalogSelector() { }
    static int pageContaining(List<Choice> catalog, String query, Choice selected) {
        if (selected == null) return 0;
        var filtered = filtered(catalog, query);
        int index = filtered.indexOf(selected);
        return index < 0 ? 0 : index / 25;
    }
    static Page page(List<Choice> catalog, String query, int requested) {
        if (requested < 0) throw new IllegalArgumentException("Page must be non-negative");
        var filtered = filtered(catalog, query);
        int pages = Math.max(1, (filtered.size() + 24) / 25);
        int page = Math.min(requested, pages - 1);
        return new Page(filtered.subList(page * 25, Math.min(filtered.size(), (page + 1) * 25)), page, pages, filtered.size());
    }
    private static List<Choice> filtered(List<Choice> catalog, String query) {
        var normalized = query.strip().toLowerCase(Locale.ROOT);
        return catalog.stream().filter(choice -> choice.label().toLowerCase(Locale.ROOT).contains(normalized)).toList();
    }
    record Page(List<Choice> choices, int page, int pages, int count) { Page { choices = List.copyOf(choices); } }
}
