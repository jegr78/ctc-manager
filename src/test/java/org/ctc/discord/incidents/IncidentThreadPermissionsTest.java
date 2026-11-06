package org.ctc.discord.incidents;

import java.util.*;
import org.ctc.discord.DiscordRestClient.*;
import org.ctc.discord.dto.PermissionOverwrite;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class IncidentThreadPermissionsTest {
    static final long VIEW=1L<<10, THREAD_SEND=1L<<38, CREATE=1L<<35;
    static final long BOT=VIEW|(1L<<11)|(1L<<16)|THREAD_SEND|CREATE;
    AccessGuild guild;
    AccessMember member;
    List<IncidentRole> roles;
    List<PermissionOverwrite> overwrites;
    Set<String> staff;
    List<String> tags;
    List<IncidentForumTag> available;
    String channelId,channelGuild;
    Integer type,flags;
    @BeforeEach void data() {
        guild=new AccessGuild("100","999");member=new AccessMember(new BotUser("700","Test bot","0000"),List.of("701"));
        roles=new ArrayList<>(List.of(new IncidentRole("100",Long.toString(VIEW),false,null),new IncidentRole("300",Long.toString(VIEW),false,null),
                new IncidentRole("400","0",false,null),new IncidentRole("500",Long.toString((1L<<11)|(1L<<16)|THREAD_SEND),false,null),
                new IncidentRole("600","0",false,null),new IncidentRole("701","8",true,new IncidentRoleTags("700"))));
        overwrites=new ArrayList<>(List.of(new PermissionOverwrite("100",0,"0",Long.toString(VIEW)),new PermissionOverwrite("500",0,Long.toString(VIEW),"0"),new PermissionOverwrite("700",1,Long.toString(BOT),"0")));
        staff=new HashSet<>(Set.of("500"));tags=new ArrayList<>();available=new ArrayList<>();channelId="300";channelGuild="100";type=0;flags=0;
    }
    IncidentChannel target() {return new IncidentChannel(channelId,channelGuild,type,null,"Test PRIVATE stewards",overwrites,flags,available,null,null);}
    void privateCheck() {IncidentThreadPermissions.requirePrivate(guild,target(),member,roles,"700","100","300",staff,Set.of("300","400"),tags);}
    void role(String id,String permissions,Boolean managed,IncidentRoleTags marker) {roles.replaceAll(value -> value.id().equals(id)?new IncidentRole(id,permissions,managed,marker):value);}
    void botRights(long mask) {role("701","0",true,new IncidentRoleTags("700"));overwrites.removeIf(value -> value.type()==1 && value.id().equals("700"));overwrites.add(new PermissionOverwrite("700",1,Long.toString(mask),"0"));}
    @Test void givenStaffOnlyTextParentAndTheBotsManagedAdministratorRole_whenPreflighting_thenPermitThePrivateDestination() {assertDoesNotThrow(this::privateCheck);}
    @Test void givenABotWithoutAdministratorButWithExplicitRequiredRights_whenPreflighting_thenApplyItsMemberOverwrite() {botRights(BOT);assertDoesNotThrow(this::privateCheck);}
    @Test void givenAStaffOnlyForumWithRequiredTags_whenPreflighting_thenRequireSendRightsWithoutRequiringPublicThreadCreation() {
        type=15;flags=16;tags.add("900");available.add(new IncidentForumTag("900","Test Review",false));botRights(BOT&~CREATE);assertDoesNotThrow(this::privateCheck);
    }
    @ParameterizedTest
    @ValueSource(strings={"everyone","everyone-admin","team","outsider","administrator","shared-bot-admin","member","guild","guild-identity","channel","type","staff-empty","staff-unknown","staff-everyone","staff-team","bot-identity","bot-role","bot-view","bot-send","bot-thread-send","bot-create","bot-history","malformed-role","duplicate-role","malformed-overwrite","duplicate-overwrite","foreign-bot-role"})
    void givenBroadVisibilityOrUnverifiedCurrentMetadata_whenPreflighting_thenDenyWithoutExposingPrivateDestinationData(String cause) {
        switch(cause) {
            case "everyone" -> overwrites.removeIf(value -> value.id().equals("100"));
            case "everyone-admin" -> role("100","8",false,null);
            case "team" -> overwrites.add(new PermissionOverwrite("300",0,Long.toString(VIEW),"0"));
            case "outsider" -> overwrites.add(new PermissionOverwrite("600",0,Long.toString(VIEW),"0"));
            case "administrator" -> role("600","8",false,null);
            case "shared-bot-admin" -> {role("600","8",false,null);member=new AccessMember(member.user(),List.of("701","600"));}
            case "member" -> overwrites.add(new PermissionOverwrite("800",1,Long.toString(VIEW),"0"));
            case "guild" -> channelGuild="101";
            case "guild-identity" -> guild=new AccessGuild("101","999");
            case "channel" -> channelId="301";
            case "type" -> type=12;
            case "staff-empty" -> staff.clear();
            case "staff-unknown" -> staff.add("599");
            case "staff-everyone" -> staff.add("100");
            case "staff-team" -> staff.add("300");
            case "bot-identity" -> member=new AccessMember(new BotUser("799","Test foreign bot","0000"),member.roles());
            case "bot-role" -> member=new AccessMember(member.user(),List.of("799"));
            case "bot-view" -> botRights(BOT&~VIEW);
            case "bot-send" -> botRights(BOT&~(1L<<11));
            case "bot-thread-send" -> botRights(BOT&~THREAD_SEND);
            case "bot-create" -> botRights(BOT&~CREATE);
            case "bot-history" -> botRights(BOT&~(1L<<16));
            case "malformed-role" -> role("600","not-bits",false,null);
            case "duplicate-role" -> roles.add(roles.getFirst());
            case "malformed-overwrite" -> overwrites.add(new PermissionOverwrite("600",0,"not-bits","0"));
            case "duplicate-overwrite" -> overwrites.add(overwrites.getFirst());
            case "foreign-bot-role" -> role("701","8",true,new IncidentRoleTags("799"));
            default -> throw new AssertionError(cause);
        }
        assertThatThrownBy(this::privateCheck).isInstanceOf(BusinessRuleException.class).hasMessageNotContaining("Test PRIVATE");
    }
    @ParameterizedTest
    @ValueSource(strings={"required","unknown","moderated","duplicates","too-many","text-tags"})
    void givenUnverifiedForumTags_whenPreflighting_thenRejectBeforeCreatingAStarter(String cause) {
        type=15;flags=16;available.add(new IncidentForumTag("900","Test Review",false));tags.add("900");
        switch(cause) {
            case "required" -> tags.clear();
            case "unknown" -> tags.set(0,"901");
            case "moderated" -> {available.set(0,new IncidentForumTag("900","Test Review",true));botRights(BOT);}
            case "duplicates" -> tags.add("900");
            case "too-many" -> tags.addAll(List.of("901","902","903","904","905"));
            case "text-tags" -> {type=0;flags=0;}
            default -> throw new AssertionError(cause);
        }
        assertThatThrownBy(this::privateCheck).isInstanceOf(BusinessRuleException.class);
    }
    @Test void givenTheExactMatchTextChannel_whenPreflightingPublicDetails_thenPermitParticipantVisibility() {
        channelId="200";overwrites.add(new PermissionOverwrite("300",0,Long.toString(VIEW),"0"));
        assertDoesNotThrow(() -> IncidentThreadPermissions.requirePublic(guild,target(),member,roles,"700","100","200"));
    }
    @ParameterizedTest
    @ValueSource(strings={"guild","channel","forum","bot","rights"})
    void givenAnUnverifiedMatchDestination_whenPreflighting_thenNeverFallBackToAnotherChannel(String cause) {
        channelId="200";
        switch(cause) {case "guild" -> channelGuild="101";case "channel" -> channelId="201";case "forum" -> type=15;case "bot" -> member=new AccessMember(new BotUser("799","Test foreign","0000"),member.roles());case "rights" -> botRights(BOT&~THREAD_SEND);default -> throw new AssertionError(cause);}
        assertThatThrownBy(() -> IncidentThreadPermissions.requirePublic(guild,target(),member,roles,"700","100","200")).isInstanceOf(BusinessRuleException.class);
    }
    List<PermissionOverwrite> updates() {
        return IncidentThreadPermissions.staffUpdates(guild, target(), member, roles, "700", "100", channelId, List.of("500"), Set.of("300", "400"));
    }
    @Test void givenEffectiveInheritedStaffAccess_whenPreparingGrant_thenNeedNoPermissionManagement() {
        botRights(BOT);assertThat(updates()).isEmpty();
    }
    @Test void givenAnAbsentStaffOverwrite_whenPreparingGrant_thenGrantOnlyReadAndThreadParticipation() {
        overwrites.removeIf(row -> row.id().equals("500"));role("500", "0", false, null);
        long required = VIEW | (1L << 16) | THREAD_SEND;
        assertThat(updates()).containsExactly(new PermissionOverwrite("500", 0, Long.toString(required), "0"));
    }
    @Test void givenExistingStaffDenyAndUnrelatedBits_whenPreparingGrant_thenMergeOnlyRequiredBits() {
        role("500", "0", false, null);overwrites.removeIf(row -> row.id().equals("500"));
        var unrelated = java.math.BigInteger.ONE.shiftLeft(90);
        var deny = unrelated.setBit(11).setBit(10).setBit(16).setBit(38);
        overwrites.add(new PermissionOverwrite("500", 0, unrelated.setBit(14).toString(), deny.toString()));
        var before = List.copyOf(overwrites);
        var result = updates().getFirst();
        assertThat(result.allow()).isEqualTo(unrelated.setBit(14).setBit(10).setBit(16).setBit(38).toString());
        assertThat(result.deny()).isEqualTo(unrelated.setBit(11).toString());
        assertThat(overwrites).containsExactlyElementsOf(before);
    }
    @Test void givenMissingAccessAndNoBotPermissionManagement_whenPreparingGrant_thenReject() {
        overwrites.removeIf(row -> row.id().equals("500"));botRights(BOT);
        assertThatThrownBy(this::updates).isInstanceOf(BusinessRuleException.class);
        botRights(BOT | (1L << 28));assertThat(updates()).hasSize(1);
    }
    @ParameterizedTest @ValueSource(strings = {"100", "300", "999"})
    void givenAnEveryoneParticipantOrMissingStaffRole_whenPreparingGrant_thenRejectTheWholePlan(String role) {
        assertThatThrownBy(() -> IncidentThreadPermissions.staffUpdates(guild, target(), member, roles,
                "700", "100", channelId, List.of("500", role), Set.of("300", "400"))).isInstanceOf(BusinessRuleException.class);
    }
    @Test void givenDuplicateStaffRoles_whenPreparingGrant_thenReject() {
        assertThatThrownBy(() -> IncidentThreadPermissions.staffUpdates(guild, target(), member, roles,
                "700", "100", channelId, List.of("500", "500"), Set.of("300", "400"))).isInstanceOf(BusinessRuleException.class);
    }

}
