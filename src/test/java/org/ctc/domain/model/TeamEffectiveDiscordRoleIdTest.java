package org.ctc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeamEffectiveDiscordRoleIdTest {

	@Test
	void givenOwnDiscordRoleId_whenGetEffective_thenReturnsOwn() {
		// given
		Team team = new Team("VRX", "VRX", null);
		team.setDiscordRoleId("100");

		// when
		String effective = team.getEffectiveDiscordRoleId();

		// then
		assertThat(effective).isEqualTo("100");
	}

	@Test
	void givenSubTeamWithoutOwnRoleAndParentHasRole_whenGetEffective_thenReturnsParentRole() {
		// given
		Team parent = new Team("VRX", "VRX", null);
		parent.setDiscordRoleId("100");
		Team sub = new Team("VRX A", "VRX_A", parent);

		// when
		String effective = sub.getEffectiveDiscordRoleId();

		// then
		assertThat(effective).isEqualTo("100");
	}

	@Test
	void givenSubTeamWithOwnRoleAndParentHasRole_whenGetEffective_thenReturnsOwnRole() {
		// given
		Team parent = new Team("VRX", "VRX", null);
		parent.setDiscordRoleId("100");
		Team sub = new Team("VRX A", "VRX_A", parent);
		sub.setDiscordRoleId("999");

		// when
		String effective = sub.getEffectiveDiscordRoleId();

		// then
		assertThat(effective).isEqualTo("999");
	}

	@Test
	void givenSubTeamWithoutOwnRoleAndParentWithoutRole_whenGetEffective_thenReturnsNull() {
		// given
		Team parent = new Team("VRX", "VRX", null);
		Team sub = new Team("VRX A", "VRX_A", parent);

		// when
		String effective = sub.getEffectiveDiscordRoleId();

		// then
		assertThat(effective).isNull();
	}

	@Test
	void givenParentTeamWithoutRole_whenGetEffective_thenReturnsNull() {
		// given
		Team team = new Team("VRX", "VRX", null);

		// when
		String effective = team.getEffectiveDiscordRoleId();

		// then
		assertThat(effective).isNull();
	}

	@Test
	void givenSubTeamWithBlankOwnRoleAndParentHasRole_whenGetEffective_thenReturnsParentRole() {
		// given — blank string must be treated as absent (94 WR-05 closure)
		Team parent = new Team("VRX", "VRX", null);
		parent.setDiscordRoleId("100");
		Team sub = new Team("VRX A", "VRX_A", parent);
		sub.setDiscordRoleId("");

		// when
		String effective = sub.getEffectiveDiscordRoleId();

		// then
		assertThat(effective).isEqualTo("100");
	}

	@Test
	void givenSubTeamWithBlankOwnRoleAndParentHasBlankRole_whenGetEffective_thenReturnsNull() {
		// given
		Team parent = new Team("VRX", "VRX", null);
		parent.setDiscordRoleId("");
		Team sub = new Team("VRX A", "VRX_A", parent);
		sub.setDiscordRoleId("");

		// when
		String effective = sub.getEffectiveDiscordRoleId();

		// then
		assertThat(effective).isNull();
	}
}
