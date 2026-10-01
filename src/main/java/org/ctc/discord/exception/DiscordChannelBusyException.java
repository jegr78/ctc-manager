package org.ctc.discord.exception;

import org.ctc.domain.exception.BusinessRuleException;

public class DiscordChannelBusyException extends BusinessRuleException {

	public DiscordChannelBusyException() {
		super("Another request is changing this match's Discord channel. Reload the page and try again.");
	}
}
