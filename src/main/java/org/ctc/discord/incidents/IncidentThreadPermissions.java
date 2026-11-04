package org.ctc.discord.incidents;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.ctc.discord.DiscordRestClient.*;
import org.ctc.domain.exception.BusinessRuleException;

public final class IncidentThreadPermissions {
    private static final BigInteger ADMIN = BigInteger.ONE.shiftLeft(3);
    private static final BigInteger VIEW = BigInteger.ONE.shiftLeft(10);
    private static final BigInteger CREATE = BigInteger.ONE.shiftLeft(35);
    private static final BigInteger MANAGE = BigInteger.ONE.shiftLeft(34);
    private static final BigInteger SEND = VIEW.setBit(11).setBit(16).setBit(38);

    private IncidentThreadPermissions() { }

    public static void requirePrivate(AccessGuild guild, IncidentChannel channel, AccessMember member,
            List<IncidentRole> roles, String botId, String guildId, String channelId,
            Set<String> staff, Set<String> participants, List<String> tags) {
        var inventory = validate(guild, channel, member, roles, botId, guildId, channelId);
        require(channel.type() == 0 || channel.type() == 15);
        require(staff != null && !staff.isEmpty() && participants != null);
        for (var roleId : staff) {
            require(inventory.containsKey(roleId) && !guildId.equals(roleId) && !participants.contains(roleId));
        }
        for (var role : roles) {
            boolean botOnly = Boolean.TRUE.equals(role.managed()) && role.tags() != null
                    && botId.equals(role.tags().botId()) && member.roles().contains(role.id());
            if (!staff.contains(role.id()) && !botOnly) {
                require(!has(effective(guildId, null, List.of(role.id()), inventory, channel), VIEW));
            }
        }
        for (var overwrite : channel.permissionOverwrites()) {
            if (overwrite.type() == 1 && !botId.equals(overwrite.id()) && !guild.ownerId().equals(overwrite.id())) {
                require(!has(bits(overwrite.allow()), VIEW));
            }
        }
        var permissions = requireBotRights(guild, channel, member, inventory, botId, guildId);
        require(tags != null && tags.size() <= 5 && new HashSet<>(tags).size() == tags.size());
        if (channel.type() == 0) {
            require(tags.isEmpty());
            return;
        }
        require(channel.flags() != null);
        require((channel.flags() & 16) == 0 || !tags.isEmpty());
        if (!tags.isEmpty()) {
            require(channel.availableTags() != null);
            var available = new HashMap<String, IncidentForumTag>();
            for (var tag : channel.availableTags()) {
                require(tag != null && id(tag.id()) && available.putIfAbsent(tag.id(), tag) == null);
            }
            for (var tagId : tags) {
                require(id(tagId));
                var tag = available.get(tagId);
                require(tag != null && tag.moderated() != null);
                require(!tag.moderated() || has(permissions, ADMIN) || has(permissions, MANAGE));
            }
        }
    }

    public static void requirePublic(AccessGuild guild, IncidentChannel channel, AccessMember member,
            List<IncidentRole> roles, String botId, String guildId, String channelId) {
        var inventory = validate(guild, channel, member, roles, botId, guildId, channelId);
        require(channel.type() == 0);
        requireBotRights(guild, channel, member, inventory, botId, guildId);
    }

    private static Map<String, BigInteger> validate(AccessGuild guild, IncidentChannel channel,
            AccessMember member, List<IncidentRole> roles, String botId, String guildId, String channelId) {
        require(id(botId) && id(guildId) && id(channelId));
        require(guild != null && guildId.equals(guild.id()) && id(guild.ownerId()));
        require(channel != null && channelId.equals(channel.id()) && guildId.equals(channel.guildId())
                && channel.type() != null && channel.permissionOverwrites() != null);
        require(member != null && member.user() != null && botId.equals(member.user().id()) && member.roles() != null);
        require(roles != null);
        var inventory = new HashMap<String, BigInteger>();
        for (var role : roles) {
            require(role != null && id(role.id()));
            require(inventory.putIfAbsent(role.id(), bits(role.permissions())) == null);
        }
        require(inventory.containsKey(guildId));
        for (var roleId : member.roles()) {
            require(inventory.containsKey(roleId));
        }
        var seen = new HashSet<String>();
        for (var overwrite : channel.permissionOverwrites()) {
            require(overwrite != null && id(overwrite.id()) && (overwrite.type() == 0 || overwrite.type() == 1));
            require(seen.add(overwrite.type() + ":" + overwrite.id()));
            require(overwrite.type() != 0 || inventory.containsKey(overwrite.id()));
            bits(overwrite.allow());
            bits(overwrite.deny());
        }
        return inventory;
    }

    private static BigInteger requireBotRights(AccessGuild guild, IncidentChannel channel,
            AccessMember member, Map<String, BigInteger> inventory, String botId, String guildId) {
        var permissions = effective(guildId, botId, member.roles(), inventory, channel);
        if (botId.equals(guild.ownerId()) || has(permissions, ADMIN)) {
            return permissions.or(ADMIN).or(MANAGE);
        }
        var required = channel.type() == 0 ? SEND.or(CREATE) : SEND;
        require(permissions.and(required).equals(required));
        return permissions;
    }

    private static BigInteger effective(String guildId, String memberId, List<String> roleIds,
            Map<String, BigInteger> inventory, IncidentChannel channel) {
        var permissions = inventory.get(guildId);
        for (var roleId : roleIds) {
            permissions = permissions.or(inventory.get(roleId));
        }
        if (has(permissions, ADMIN)) {
            return permissions.or(VIEW);
        }
        BigInteger deny = BigInteger.ZERO, allow = BigInteger.ZERO;
        for (var overwrite : channel.permissionOverwrites()) {
            if (overwrite.type() == 0 && guildId.equals(overwrite.id())) {
                permissions = permissions.andNot(bits(overwrite.deny())).or(bits(overwrite.allow()));
            } else if (overwrite.type() == 0 && roleIds.contains(overwrite.id())) {
                deny = deny.or(bits(overwrite.deny()));
                allow = allow.or(bits(overwrite.allow()));
            }
        }
        permissions = permissions.andNot(deny).or(allow);
        for (var overwrite : channel.permissionOverwrites()) {
            if (overwrite.type() == 1 && overwrite.id().equals(memberId)) {
                permissions = permissions.andNot(bits(overwrite.deny())).or(bits(overwrite.allow()));
            }
        }
        return permissions;
    }

    private static boolean id(String value) {
        return value != null && value.matches("[0-9]{1,20}") && !value.chars().allMatch(character -> character == '0');
    }

    private static BigInteger bits(String value) {
        require(value != null && value.matches("[0-9]{1,128}"));
        return new BigInteger(value);
    }

    private static boolean has(BigInteger permissions, BigInteger required) {
        return permissions.and(required).equals(required);
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new BusinessRuleException("Incident destination permissions could not be verified");
        }
    }
}
