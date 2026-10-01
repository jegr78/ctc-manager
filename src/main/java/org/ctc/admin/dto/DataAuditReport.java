package org.ctc.admin.dto;

import java.time.Instant;
import java.util.List;

/** Read-only audit result: one section per category, in category order, each possibly empty. */
public record DataAuditReport(Instant generatedAt, List<Section> sections) {

	public record Section(DataAuditFinding.Category category, List<DataAuditFinding> findings) {
	}

	public int total() {
		return sections.stream().mapToInt(section -> section.findings().size()).sum();
	}

	public long count(DataAuditFinding.Resolution resolution) {
		return sections.stream().flatMap(section -> section.findings().stream())
				.filter(finding -> finding.resolution() == resolution).count();
	}

	public List<DataAuditFinding> findings(DataAuditFinding.Category category) {
		return sections.stream().filter(section -> section.category() == category)
				.flatMap(section -> section.findings().stream()).toList();
	}
}
