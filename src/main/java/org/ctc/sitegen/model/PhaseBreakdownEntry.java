package org.ctc.sitegen.model;

/** View record for one row in the team-/driver-profile phase-breakdown section (e.g. "Regular: 5th, 47pts"). */
public record PhaseBreakdownEntry(String label, String summary) {}
