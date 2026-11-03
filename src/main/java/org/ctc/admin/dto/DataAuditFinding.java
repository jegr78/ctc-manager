package org.ctc.admin.dto;

public record DataAuditFinding(Category category, Resolution resolution, String subject, String evidence,
		String correction) {

	public enum Category {
		STALE_AGGREGATE("Stale match and matchup aggregates", "#191"),
		PAIRING("Duplicate and reversed pairings", "#199"),
		PHASE_GROUP("Phase and group inconsistencies", "#190"),
		SUCCESSION("Succession collisions and cycles", "#190"),
		PLAYOFF("Playoff winners and advancement", "#195, #201"),
		PUBLIC_URL("Ambiguous public URLs", "#210"),
        RACE_SETTINGS("Inconsistent race settings", "#280"),
        CAR_METADATA("Car metadata without valid review evidence", "#274"),
        CAR_ELIGIBILITY("Car eligibility information", "#278"),
        CAR_POOL("Inconsistent seasonal car pools", "#275, #276, #278"),
        PRIVATE_PICKS("Inconsistent confidential pick history", "#281"),
        INCIDENT_REPORTS("Inconsistent incident report history", "#288");

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
