package org.ctc.sitegen.model;

/** View record for one phase-tab row entry on the public site (REGULAR / PLAYOFF / PLACEMENT tabs). */
public record PhaseTabView(String label, String href, boolean active, String ariaControlsId) {}
