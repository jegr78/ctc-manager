package org.ctc.domain.model;

public enum CarCategory {
    GR_1("Gr.1"), GR_2("Gr.2"), GR_3("Gr.3"), GR_4("Gr.4"), GR_B("Gr.B"), GR_N("Gr.N"), GR_X("Gr.X");
    private final String label;
    CarCategory(String label) { this.label = label; }
    public String getLabel() { return label; }
    public static CarCategory fromSource(String value) {
        for (var category : values()) if (category.label.equals(value)) return category;
        return null;
    }
}
