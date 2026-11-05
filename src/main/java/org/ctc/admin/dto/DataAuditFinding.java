package org.ctc.admin.dto;

public record DataAuditFinding(Category category, Resolution resolution, String subject, String evidence,
		String correction) {

	public enum Category {
		STALE_AGGREGATE("Stale match and matchup aggregates", "#191"),
        RESULT_POINTS("Race points inconsistent with their scoring scheme", ""),
        TEAM_PENALTIES("Team penalty history and official scores", "#342"),
		PAIRING("Pairing and walkover inconsistencies", "#199"),
		PHASE_GROUP("Phase and group inconsistencies", "#190"),
        SEASON_CONFIGURATION("Ambiguous active season", ""),
		SUCCESSION("Succession collisions and cycles", "#190"),
		PLAYOFF("Playoff winners and advancement", "#195, #201"),
		PUBLIC_URL("Ambiguous public URLs", "#210"),
        RACE_SETTINGS("Inconsistent race settings", "#280"),
        LOBBY_RULES("Inconsistent season lobby rules", "#343"),
        CAR_METADATA("Car metadata without valid review evidence", "#274"),
        CAR_ELIGIBILITY("Car eligibility information", "#278"),
        CAR_POOL("Inconsistent seasonal car pools", "#275, #276, #278"),
        PRIVATE_PICKS("Inconsistent confidential pick history", "#281"),
        INCIDENT_REPORTS("Incident history and delivery review", "#288, #293");

		private final String label;
		private final String relatedIssues;

		Category(String label, String relatedIssues) {
			this.label = label;
			this.relatedIssues = relatedIssues;
		}

		public String getLabel() {
			return label;
		}

		public String getRelatedIssues() {
			return relatedIssues;
		}
	}

	public enum Resolution {
		RECONSTRUCTIBLE("Reconstructible from authoritative data", "badge-active"),
		AMBIGUOUS("Needs an individual decision", "badge-warning"),
		UNDETERMINABLE("Cannot be inferred; do not invent a value", "badge-inactive");

		private final String label;
		private final String badgeClass;

		Resolution(String label, String badgeClass) {
			this.label = label;
			this.badgeClass = badgeClass;
		}

		public String getLabel() {
			return label;
		}

		public String getBadgeClass() {
			return badgeClass;
		}
	}
}
