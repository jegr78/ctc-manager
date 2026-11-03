package org.ctc.discord;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.ctc.discord.dto.PermissionOverwrite;
import org.ctc.discord.exception.DiscordApiException;

public final class DiscordChannelMemberAccess {
    private DiscordChannelMemberAccess() { }
    public static Snapshot current(DiscordRestClient discord, String guildId, String channelId, String userId) {
        if (!snowflake(guildId) || !snowflake(channelId) || !snowflake(userId)) return null;
        try {
            var guild = discord.fetchAccessGuild(guildId);
            var channel = discord.fetchAccessChannel(channelId);
            var member = discord.fetchAccessMember(guildId, userId);
            var roles = discord.fetchPermissionRoles(guildId);
            if (guild == null || !guildId.equals(guild.id()) || !snowflake(guild.ownerId())
                    || channel == null || !channelId.equals(channel.id()) || !guildId.equals(channel.guildId())
                    || channel.type() == null || channel.type() != 0 || channel.permissionOverwrites() == null
                    || member == null || member.user() == null || !userId.equals(member.user().id())
                    || member.roles() == null || roles == null) return null;
            var roleBits = new HashMap<String, BigInteger>();
            for (var role : roles) {
                if (role == null || !snowflake(role.id()) || roleBits.putIfAbsent(role.id(), bits(role.permissions())) != null) return null;
            }
            var everyone = roleBits.get(guildId);
            if (everyone == null) return null;
            BigInteger base = everyone;
            for (String id : member.roles()) {
                var permissions = roleBits.get(id);
                if (permissions == null) return null;
                base = base.or(permissions);
            }
            var overwrites = parse(channel.permissionOverwrites());
            boolean privateChannel = !everyone.testBit(3) && !applyEveryone(everyone, guildId, overwrites).testBit(10);
            if (guild.ownerId().equals(userId) || base.testBit(3)) return new Snapshot(Set.copyOf(member.roles()), privateChannel);
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
            return effective.testBit(10) ? new Snapshot(Set.copyOf(member.roles()), privateChannel) : null;
        } catch (DiscordApiException | RuntimeException unavailable) {
            return null;
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
    public record Snapshot(Set<String> roles, boolean privateChannel) {
        public Snapshot { roles = Set.copyOf(roles); }
        @Override public String toString() { return "DiscordChannelMember[redacted]"; }
    }
}
