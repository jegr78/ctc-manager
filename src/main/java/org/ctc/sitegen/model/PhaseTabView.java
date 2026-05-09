package org.ctc.sitegen.model;

/**
 * View record for one phase-tab row entry on the public site.
 * Prepared for Plan 1 consumption (REGULAR / PLAYOFF / PLACEMENT tabs).
 */
public record PhaseTabView(String label, String href, boolean active, String ariaControlsId) {}
