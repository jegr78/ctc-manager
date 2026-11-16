package org.ctc.trailer;

import java.util.List;
import org.ctc.domain.exception.BusinessRuleException;

/** The trailer cannot start while checklist items are open. */
public class TrailerBlockedException extends BusinessRuleException {

    private final transient List<TrailerChecklistItem> items;

    public TrailerBlockedException(List<TrailerChecklistItem> items) {
        super("The trailer cannot start yet: " + items.getFirst().message());
        this.items = List.copyOf(items);
    }

    public List<TrailerChecklistItem> items() {
        return items;
    }
}
