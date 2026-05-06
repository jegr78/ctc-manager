package org.ctc.sitegen.model;

/**
 * View record for one group-sub-tab row entry on the public site.
 * Prepared for Plan 1 consumption (Combined view + per-group tabs in GROUPS-layout phases).
 */
public record GroupSubTabView(String label, String href, boolean active, String ariaControlsId) {}
