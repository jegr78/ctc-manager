package org.ctc.discord;

/**
 * Discord permission bitmask constants encoded per
 * <a href="https://discord.com/developers/docs/topics/permissions">Discord Permissions docs</a>.
 *
 * <p>Composite masks are pre-computed at the bit level (no run-time OR). Used by
 * {@code DiscordChannelService} to build per-overwrite allow/deny pairs.
 */
public final class DiscordPermissions {

	public static final long CREATE_INSTANT_INVITE = 1L << 0;
	public static final long MANAGE_CHANNELS = 1L << 4;
	public static final long ADD_REACTIONS = 1L << 6;
	public static final long VIEW_CHANNEL = 1L << 10;
	public static final long SEND_MESSAGES = 1L << 11;
	public static final long MANAGE_MESSAGES = 1L << 13;
	public static final long EMBED_LINKS = 1L << 14;
	public static final long ATTACH_FILES = 1L << 15;
	public static final long READ_MESSAGE_HISTORY = 1L << 16;
	public static final long MENTION_EVERYONE = 1L << 17;
	public static final long USE_EXTERNAL_EMOJIS = 1L << 18;
	public static final long CONNECT = 1L << 20;
	public static final long SPEAK = 1L << 21;
	public static final long MANAGE_WEBHOOKS = 1L << 29;
	public static final long MANAGE_THREADS = 1L << 34;
	public static final long USE_EXTERNAL_STICKERS = 1L << 37;

	public static final long EVERYONE_DENY_VIEW_MASK = VIEW_CHANNEL;

	public static final long TEAM_MEMBER_ALLOW_MASK =
			VIEW_CHANNEL
					| SEND_MESSAGES
					| ADD_REACTIONS
					| ATTACH_FILES
					| EMBED_LINKS
					| READ_MESSAGE_HISTORY
					| USE_EXTERNAL_EMOJIS
					| USE_EXTERNAL_STICKERS;

	public static final long TEAM_MEMBER_DENY_MASK =
			CONNECT
					| SPEAK
					| MANAGE_CHANNELS
					| MANAGE_MESSAGES
					| MANAGE_THREADS
					| MANAGE_WEBHOOKS
					| CREATE_INSTANT_INVITE
					| MENTION_EVERYONE;

	public static final long BOT_ALLOW_MASK =
			VIEW_CHANNEL
					| MANAGE_CHANNELS
					| MANAGE_WEBHOOKS
					| SEND_MESSAGES
					| EMBED_LINKS
					| ATTACH_FILES
					| READ_MESSAGE_HISTORY;

	public static final int OVERWRITE_TYPE_ROLE = 0;
	public static final int OVERWRITE_TYPE_MEMBER = 1;

	private DiscordPermissions() {
		throw new UnsupportedOperationException("Utility class");
	}
}
