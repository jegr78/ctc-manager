package org.ctc.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ctc.discord.DiscordPermissions.ADD_REACTIONS;
import static org.ctc.discord.DiscordPermissions.ATTACH_FILES;
import static org.ctc.discord.DiscordPermissions.CONNECT;
import static org.ctc.discord.DiscordPermissions.CREATE_INSTANT_INVITE;
import static org.ctc.discord.DiscordPermissions.EMBED_LINKS;
import static org.ctc.discord.DiscordPermissions.EVERYONE_DENY_VIEW_MASK;
import static org.ctc.discord.DiscordPermissions.MANAGE_CHANNELS;
import static org.ctc.discord.DiscordPermissions.MANAGE_MESSAGES;
import static org.ctc.discord.DiscordPermissions.MANAGE_THREADS;
import static org.ctc.discord.DiscordPermissions.MANAGE_WEBHOOKS;
import static org.ctc.discord.DiscordPermissions.MENTION_EVERYONE;
import static org.ctc.discord.DiscordPermissions.OVERWRITE_TYPE_ROLE;
import static org.ctc.discord.DiscordPermissions.READ_MESSAGE_HISTORY;
import static org.ctc.discord.DiscordPermissions.SEND_MESSAGES;
import static org.ctc.discord.DiscordPermissions.SPEAK;
import static org.ctc.discord.DiscordPermissions.TEAM_MEMBER_ALLOW_MASK;
import static org.ctc.discord.DiscordPermissions.TEAM_MEMBER_DENY_MASK;
import static org.ctc.discord.DiscordPermissions.USE_EXTERNAL_EMOJIS;
import static org.ctc.discord.DiscordPermissions.USE_EXTERNAL_STICKERS;
import static org.ctc.discord.DiscordPermissions.VIEW_CHANNEL;

import org.junit.jupiter.api.Test;

class DiscordPermissionsTest {

	@Test
	void givenEveryoneDenyViewMask_whenComputed_thenEqualsViewChannelBit() {
		assertThat(EVERYONE_DENY_VIEW_MASK).isEqualTo(VIEW_CHANNEL);
	}

	@Test
	void givenTeamMemberAllowMask_whenComputed_thenEqualsExpectedComposite() {
		long expected = VIEW_CHANNEL
				| SEND_MESSAGES
				| ADD_REACTIONS
				| ATTACH_FILES
				| EMBED_LINKS
				| READ_MESSAGE_HISTORY
				| USE_EXTERNAL_EMOJIS
				| USE_EXTERNAL_STICKERS;
		assertThat(TEAM_MEMBER_ALLOW_MASK).isEqualTo(expected);
	}

	@Test
	void givenTeamMemberDenyMask_whenComputed_thenEqualsExpectedComposite() {
		long expected = CONNECT
				| SPEAK
				| MANAGE_CHANNELS
				| MANAGE_MESSAGES
				| MANAGE_THREADS
				| MANAGE_WEBHOOKS
				| CREATE_INSTANT_INVITE
				| MENTION_EVERYONE;
		assertThat(TEAM_MEMBER_DENY_MASK).isEqualTo(expected);
	}

	@Test
	void givenOverwriteTypeRole_whenRead_thenEqualsZero() {
		assertThat(OVERWRITE_TYPE_ROLE).isZero();
	}
}
