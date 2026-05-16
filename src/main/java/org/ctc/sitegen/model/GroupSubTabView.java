package org.ctc.sitegen.model;

/** View record for one group-sub-tab row entry (combined view + per-group tabs in GROUPS-layout phases). */
public record GroupSubTabView(String label, String href, boolean active, String ariaControlsId) {}
