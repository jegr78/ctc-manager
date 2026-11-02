package org.ctc.discord.picks;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.dto.PermissionOverwrite;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.domain.picks.PickChannelPermissionSource;

@Component
@RequiredArgsConstructor
public class DiscordPickChannelPermissionSource implements PickChannelPermissionSource {
    private final DiscordRestClient discord;

    @Override
    public boolean hasCurrentPrivateAccess(String guildId, String channelId, String userId) {
        try {
            var guild = discord.fetchAccessGuild(guildId);
            var channel = discord.fetchAccessChannel(channelId);
            var member = discord.fetchAccessMember(guildId, userId);
            var roles = discord.fetchPermissionRoles(guildId);
            if (guild == null || !guildId.equals(guild.id()) || !snowflake(guild.ownerId())
                    || channel == null || !channelId.equals(channel.id()) || !guildId.equals(channel.guildId())
                    || channel.type() == null || channel.type() != 0 || channel.permissionOverwrites() == null
                    || member == null || member.user() == null || !userId.equals(member.user().id())
                    || member.roles() == null || roles == null) return false;
            var roleBits = new HashMap<String, BigInteger>();
            for (var role : roles) {
                if (role == null || !snowflake(role.id()) || roleBits.putIfAbsent(role.id(), bits(role.permissions())) != null) return false;
            }
            var everyone = roleBits.get(guildId);
            if (everyone == null) return false;
            BigInteger base = everyone;
            for (String id : member.roles()) {
                var permissions = roleBits.get(id);
                if (permissions == null) return false;
                base = base.or(permissions);
            }
            var overwrites = parse(channel.permissionOverwrites());
            if (everyone.testBit(3) || applyEveryone(everyone, guildId, overwrites).testBit(10)) return false;
            if (guild.ownerId().equals(userId) || base.testBit(3)) return true;
            var effective = applyEveryone(base, guildId, overwrites);
            BigInteger allow = BigInteger.ZERO, deny = BigInteger.ZERO;
            for (var overwrite : overwrites) {
                if (overwrite.type() == 0 && !guildId.equals(overwrite.id()) && member.roles().contains(overwrite.id())) {
                    allow = allow.or(overwrite.allow());
                    deny = deny.or(overwrite.deny());
                }
            }
            effective = effective.andNot(deny).or(allow);
            for (var overwrite : overwrites) {
                if (overwrite.type() == 1 && userId.equals(overwrite.id())) {
                    effective = effective.andNot(overwrite.deny()).or(overwrite.allow());
                }
            }
            return effective.testBit(10);
        } catch (DiscordApiException | RuntimeException unavailable) {
            return false;
        }
    }

    private static BigInteger applyEveryone(BigInteger permissions, String guildId, List<Overwrite> overwrites) {
        for (var overwrite : overwrites) {
            if (overwrite.type() == 0 && guildId.equals(overwrite.id())) {
                return permissions.andNot(overwrite.deny()).or(overwrite.allow());
            }
        }
        return permissions;
    }

    private static List<Overwrite> parse(List<PermissionOverwrite> rows) {
        var identities = new HashSet<String>();
        return rows.stream().map(row -> {
            if (row == null || !snowflake(row.id()) || row.type() != 0 && row.type() != 1
                    || !identities.add(row.type() + ":" + row.id())) throw new IllegalArgumentException("Invalid overwrite identity");
            return new Overwrite(row.id(), row.type(), bits(row.allow()), bits(row.deny()));
        }).toList();
    }

    private static BigInteger bits(String value) {
        if (value == null || !value.matches("[0-9]{1,128}")) throw new IllegalArgumentException("Invalid permission value");
        return new BigInteger(value);
    }

    private static boolean snowflake(String value) { return value != null && value.matches("[0-9]{1,32}"); }
    private record Overwrite(String id, int type, BigInteger allow, BigInteger deny) { }
}
