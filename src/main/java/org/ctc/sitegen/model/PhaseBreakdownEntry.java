package org.ctc.sitegen.model;

/**
 * View record for one row in the team-/driver-profile phase-breakdown section.
 * Prepared for Plan 4 consumption (e.g. "Regular: 5th, 47pts" or "Playoff: SF exit").
 */
public record PhaseBreakdownEntry(String label, String summary) {}
