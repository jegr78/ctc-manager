package org.ctc.admin.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SeedForm {

	private UUID playoffId;
	private List<SeedEntry> seeds = new ArrayList<>();

	@Getter
	@Setter
	@NoArgsConstructor
	public static class SeedEntry {
		private UUID matchupId;
		private int slot;
		private UUID teamId;
		private Integer seedNumber;
	}
}
