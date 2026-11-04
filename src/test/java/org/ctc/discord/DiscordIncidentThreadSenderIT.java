package org.ctc.discord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.ctc.discord.incidents.DiscordIncidentThreadSender;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.IncidentThreadStage;
import org.ctc.integration.OutboundPolicy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(classes={DiscordConfig.class,DiscordRateLimitInterceptor.class,DiscordHostValidator.class,DiscordRestClient.class,OutboundPolicy.class,DiscordIncidentThreadSender.class},
        webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"ctc.outbound.enabled=true","app.discord.bot-token=test-incident-thread-token","app.discord.allowed-hosts=discord.com,localhost,127.0.0.1","app.discord.rate-limit.jitter-ms=0","app.discord.rate-limit.fivexx-backoff-ms=0,0"})
@ActiveProfiles("dev")
@Tag("integration")
class DiscordIncidentThreadSenderIT {
    @RegisterExtension static WireMockExtension wm=WireMockExtension.newInstance().options(options().dynamicPort()).build();
    @DynamicPropertySource static void endpoint(DynamicPropertyRegistry properties){properties.add("app.discord.base-url",() -> wm.baseUrl()+"/api/v10");}
    @Autowired DiscordIncidentThreadSender sender;
    @MockitoBean DiscordGlobalConfigRepository configs;
    @MockitoBean java.time.Clock clock;
    static final java.time.Instant NOW=java.time.Instant.parse("2026-11-03T12:00:00Z");
    DiscordGlobalConfig config;
    static final String FACTS="Test party\nInvolved drivers: Test driver\nReplay: 01:20\nDescription: Test facts @everyone\nEvidence: https://example.invalid/test";
    String json(Object value) {return assertDoesNotThrow(() -> new ObjectMapper().writeValueAsString(value));}
    boolean privateSide(IncidentThreadStage stage) {return switch(stage){case PUBLIC_STARTER,PUBLIC_THREAD,PUBLIC_BODY -> false;default -> true;};}
    String content(boolean privateSide,int index) {return FACTS+"\n[CTC INC-TEST "+(privateSide?"PRIVATE":"PUBLIC")+":"+index+"]";}
    IncidentThreadClaim claim(IncidentThreadStage stage,int index) {
        boolean privateSide=privateSide(stage);String starter=privateSide?"601":"801";
        var work=new IncidentThreadClaim.Work(stage,index,"INC-TEST",stage==IncidentThreadStage.PRIVATE_REFERENCE?"[CTC INC-TEST REFERENCE]":"[CTC INC-TEST "+(privateSide?"PRIVATE":"PUBLIC")+":"+index+"]","Incident INC-TEST · Test party",stage==IncidentThreadStage.PRIVATE_REFERENCE?"Test match discussion https://discord.com/channels/100/801\n[CTC INC-TEST REFERENCE]":content(privateSide,index),content(privateSide,0),"test-stable-nonce",stage==IncidentThreadStage.PRIVATE_STARTER||stage==IncidentThreadStage.PUBLIC_STARTER?null:starter,stage==IncidentThreadStage.PRIVATE_BODY||stage==IncidentThreadStage.PUBLIC_BODY||stage==IncidentThreadStage.PRIVATE_REFERENCE?starter:null);
        return new IncidentThreadClaim(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,"test-generation",NOW.plusSeconds(600),new IncidentThreadClaim.Destination("100","200","300",List.of("500"),config.getStewardsForumTagIds().isEmpty()?List.of():List.of("900"),Set.of("400")),work);
    }
    String parent(int type,boolean broad) {
        return json(Map.of("id","300","guild_id","100","type",type,"flags",type==15?16:0,"permission_overwrites",List.of(Map.of("id","100","type",0,"allow",broad?"1024":"0","deny",broad?"0":"1024"),Map.of("id","500","type",0,"allow","1024","deny","0")),"available_tags",List.of(Map.of("id","900","name","Test Review","moderated",false))));
    }
    String thread(String id,String parent,boolean archived) {return json(Map.of("id",id,"guild_id","100","type",11,"parent_id",parent,"name","Incident INC-TEST · Test party","thread_metadata",Map.of("archived",archived,"locked",false,"archive_timestamp","2026-11-03T12:00:00Z")));}
    Map<String,Object> message(String id,String channel,String body) {return Map.of("id",id,"channel_id",channel,"content",body,"author",Map.of("id","700"),"nonce","test-stable-nonce");}
    @BeforeEach void setup() throws Exception {
        wm.resetAll();when(clock.instant()).thenReturn(NOW);
        config=new DiscordGlobalConfig();config.setGuildId("100");config.setStewardsChannelId("300");config.setIncidentStaffRoleIds("500");when(configs.findFirstByOrderByIdAsc()).thenReturn(config);
        wm.stubFor(get(urlEqualTo("/api/v10/users/@me")).willReturn(okJson("{\"id\":\"700\",\"username\":\"Test bot\"}")));
        wm.stubFor(get(urlEqualTo("/api/v10/guilds/100")).willReturn(okJson("{\"id\":\"100\",\"owner_id\":\"999\"}")));
        wm.stubFor(get(urlEqualTo("/api/v10/guilds/100/members/700")).willReturn(okJson("{\"user\":{\"id\":\"700\"},\"roles\":[\"701\"]}")));
        wm.stubFor(get(urlEqualTo("/api/v10/guilds/100/roles")).willReturn(okJson("[{\"id\":\"100\",\"permissions\":\"1024\"},{\"id\":\"400\",\"permissions\":\"0\"},{\"id\":\"500\",\"permissions\":\"0\"},{\"id\":\"701\",\"permissions\":\"8\",\"managed\":true,\"tags\":{\"bot_id\":\"700\"}}]")));
        wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(0,false))));
        wm.stubFor(get(urlEqualTo("/api/v10/channels/200")).willReturn(okJson("{\"id\":\"200\",\"guild_id\":\"100\",\"type\":0,\"permission_overwrites\":[]}")));
        wm.stubFor(get(urlEqualTo("/api/v10/channels/300/messages/601")).willReturn(okJson(json(message("601","300",content(true,0))))));
        wm.stubFor(get(urlEqualTo("/api/v10/channels/200/messages/801")).willReturn(okJson(json(message("801","200",content(false,0))))));
    }
    @Test void givenStaffOnlyTextParent_whenSendingDetails_thenCreateANewControlledStarterWithNoMentions() {
        wm.stubFor(post(urlEqualTo("/api/v10/channels/300/messages")).willReturn(okJson(json(message("601","300",content(true,0))))));
        var result=sender.send(claim(IncidentThreadStage.PRIVATE_STARTER,0));assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.SENT);assertThat(result.messageId()).isEqualTo("601");assertThat(result.threadId()).isNull();
        wm.verify(1,postRequestedFor(urlEqualTo("/api/v10/channels/300/messages")).withRequestBody(matchingJsonPath("$.allowed_mentions.parse",equalToJson("[]"))).withRequestBody(matchingJsonPath("$.nonce",equalTo("test-stable-nonce"))).withRequestBody(matchingJsonPath("$.content",equalTo(content(true,0)))));
        wm.verify(0,postRequestedFor(urlMatching(".*/messages/777/threads")));
    }
    @Test void capturedPairingAndConcreteRaceReachTheRealBotRequest() {
        var race = UUID.randomUUID();
        var payload = org.ctc.domain.service.IncidentThreadProjection.render("INC-TEST", "Test party",
                new org.ctc.domain.model.IncidentPayload("Test drivers", "Lap 2", "Test accepted facts", ""),
                "Test original home vs Test original away", "Leg 2 · Test original round · Race " + race);
        var original = claim(IncidentThreadStage.PUBLIC_STARTER, 0); var old = original.work();
        String body = payload.messages().getFirst() + "\n" + old.marker();
        var work = new IncidentThreadClaim.Work(old.stage(), old.chunkIndex(), old.caseReference(), old.marker(), payload.title(), body,
                body, old.nonce(), old.starterId(), old.threadId());
        var captured = new IncidentThreadClaim(original.id(), original.windowId(), original.token(), original.version(), original.generation(), original.validUntil(), original.destination(), work);
        wm.stubFor(post(urlEqualTo("/api/v10/channels/200/messages")).willReturn(okJson(json(message("801", "200", body)))));
        var result = sender.send(captured); assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.SENT);
        wm.verify(1, postRequestedFor(urlEqualTo("/api/v10/channels/200/messages"))
                .withRequestBody(matchingJsonPath("$.content", equalTo(body)))
                .withRequestBody(matchingJsonPath("$.allowed_mentions.parse", equalToJson("[]"))));
        assertThat(body).contains("Pairing: Test original home vs Test original away", "Race: Leg 2", race.toString());
    }

    @Test void givenAConfiguredPrivateForum_whenSending_thenUseItsActualTypeTagsAndNestedStarter() {
        config.setStewardsForumTagIds("900");wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(15,false))));
        var body=new ObjectMapper().createObjectNode();assertDoesNotThrow(() -> body.setAll((com.fasterxml.jackson.databind.node.ObjectNode)new ObjectMapper().readTree(thread("600","300",false))));body.set("message",new ObjectMapper().valueToTree(message("602","600",content(true,0))));
        wm.stubFor(post(urlEqualTo("/api/v10/channels/300/threads")).willReturn(okJson(body.toString())));
        var result=sender.send(claim(IncidentThreadStage.PRIVATE_STARTER,0));assertThat(result.disposition()).as("result=%s requests=%s",result,wm.getAllServeEvents().stream().map(event -> event.getRequest().getUrl()+" status="+event.getResponse().getStatus()+" timing="+event.getTiming()).toList()).isEqualTo(IncidentSendResult.Disposition.SENT);assertThat(result.messageId()).isEqualTo("602");assertThat(result.threadId()).isEqualTo("600");
        wm.verify(1,postRequestedFor(urlEqualTo("/api/v10/channels/300/threads")).withRequestBody(matchingJsonPath("$.applied_tags",equalToJson("[\"900\"]"))).withRequestBody(matchingJsonPath("$.message.allowed_mentions.parse",equalToJson("[]"))).withRequestBody(matchingJsonPath("$.message.content",equalTo(content(true,0)))));
    }
    @Test void givenKnownNewDetailedStarter_whenCreatingItsThread_thenBindOnlyThatMessage() {
        wm.stubFor(get(urlEqualTo("/api/v10/channels/601")).willReturn(notFound()));wm.stubFor(post(urlEqualTo("/api/v10/channels/300/messages/601/threads")).willReturn(okJson(thread("601","300",false))));
        var result=sender.send(claim(IncidentThreadStage.PRIVATE_THREAD,0));assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.SENT);assertThat(result.threadId()).isEqualTo("601");wm.verify(1,postRequestedFor(urlEqualTo("/api/v10/channels/300/messages/601/threads")));wm.verify(0,postRequestedFor(urlEqualTo("/api/v10/channels/300/messages")));
    }
    @Test void givenAnAlreadyCreatedArchivedMessageBoundThread_whenRetryingTheMissingAcknowledgement_thenAdoptItWithoutAnotherPost() {
        wm.stubFor(get(urlEqualTo("/api/v10/channels/601")).willReturn(okJson(thread("601","300",true))));
        var result=sender.send(claim(IncidentThreadStage.PRIVATE_THREAD,0));assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.SENT);assertThat(result.threadId()).isEqualTo("601");wm.verify(0,postRequestedFor(anyUrl()));
    }
    @Test void givenThePublicMatchStage_whenSending_thenKeepTheBodyControlledAndExcludePrivateLinksOrPersonalAttribution() {
        wm.stubFor(post(urlEqualTo("/api/v10/channels/200/messages")).willReturn(okJson(json(message("801","200",content(false,0))))));
        var result=sender.send(claim(IncidentThreadStage.PUBLIC_STARTER,0));assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.SENT);
        var request=wm.findAll(postRequestedFor(urlEqualTo("/api/v10/channels/200/messages"))).getFirst().getBodyAsString();assertThat(request).contains("Test party","Test driver","01:20","Test facts","example.invalid").doesNotContain("https://discord.com/channels/100/300","submitting user","900000000000000009");
    }
    @Test void givenPermissionChangesBetweenPosts_whenSendingTheNextDetailedPart_thenFetchCurrentPrivacyAndStopBeforeAnotherPost() {
        wm.stubFor(post(urlEqualTo("/api/v10/channels/300/messages")).willReturn(okJson(json(message("601","300",content(true,0))))));assertThat(sender.send(claim(IncidentThreadStage.PRIVATE_STARTER,0)).disposition()).isEqualTo(IncidentSendResult.Disposition.SENT);
        wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(0,true))));assertThat(sender.send(claim(IncidentThreadStage.PRIVATE_BODY,1)).disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED);wm.verify(1,postRequestedFor(anyUrl()));wm.verify(2,getRequestedFor(urlEqualTo("/api/v10/channels/300")));
    }
    @ParameterizedTest @ValueSource(strings={"everyone","guild","type","config","tag"})
    void givenAnUnverifiedDestination_whenSending_thenRejectBeforeAnyPost(String cause) {
        switch(cause){case "everyone" -> wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(0,true))));case "guild" -> wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(0,false).replace("\"100\"","\"101\""))));case "type" -> wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(12,false))));case "config" -> config.setStewardsChannelId("399");case "tag" -> wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(15,false))));default -> throw new AssertionError(cause);}
        var result=sender.send(claim(IncidentThreadStage.PRIVATE_STARTER,0));assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED);assertThat(result.code()).isIn("DESTINATION","CONFIGURATION");wm.verify(0,postRequestedFor(anyUrl()));
    }
    @Test void givenAnAmbiguousForumFailure_whenSending_thenKeepUncertainAndNeverFallBackToThePublicMatch() {
        config.setStewardsForumTagIds("900");wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(15,false))));wm.stubFor(post(urlEqualTo("/api/v10/channels/300/threads")).willReturn(serverError()));
        var result=sender.send(claim(IncidentThreadStage.PRIVATE_STARTER,0));assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.UNCERTAIN);wm.verify(1,postRequestedFor(urlEqualTo("/api/v10/channels/300/threads")));wm.verify(0,postRequestedFor(urlEqualTo("/api/v10/channels/200/messages")));
    }
    @Test void givenAKnownThreadWasDeleted_whenSendingItsNextPart_thenPreserveTheKnownPairAndNeverCreateAReplacement() {
        wm.stubFor(get(urlEqualTo("/api/v10/channels/601")).willReturn(notFound()));var result=sender.send(claim(IncidentThreadStage.PRIVATE_BODY,1));assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED);assertThat(result.code()).isEqualTo("DESTINATION");wm.verify(0,postRequestedFor(anyUrl()));
    }
    @Test void givenAnArchivedForumThreadWithAnExactBotMarker_whenReviewing_thenVerifyItsActualParentAndStarterWithoutPosting() {
        config.setStewardsForumTagIds("900");wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(15,false))));wm.stubFor(get(urlEqualTo("/api/v10/channels/600")).willReturn(okJson(thread("600","300",true))));wm.stubFor(get(urlPathEqualTo("/api/v10/channels/600/messages")).withQueryParam("limit",equalTo("100")).willReturn(okJson(json(List.of(message("602","600",content(true,0)))))));
        var result=sender.verify(claim(IncidentThreadStage.PRIVATE_STARTER,0),"600");assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.SENT);assertThat(result.threadId()).isEqualTo("600");assertThat(result.messageId()).isEqualTo("602");wm.verify(0,postRequestedFor(anyUrl()));
    }
    @Test void givenAnArchivedUnlockedThread_whenPostingItsNextPart_thenUnarchiveVerifyAndPostWithEmptyMentions() {
        wm.stubFor(get(urlEqualTo("/api/v10/channels/601")).inScenario("Test reopen").whenScenarioStateIs("Started").willReturn(okJson(thread("601","300",true))));
        wm.stubFor(patch(urlEqualTo("/api/v10/channels/601")).inScenario("Test reopen").whenScenarioStateIs("Started").willReturn(okJson(thread("601","300",false))).willSetStateTo("Open"));
        wm.stubFor(get(urlEqualTo("/api/v10/channels/601")).inScenario("Test reopen").whenScenarioStateIs("Open").willReturn(okJson(thread("601","300",false))));
        wm.stubFor(post(urlEqualTo("/api/v10/channels/601/messages")).willReturn(okJson(json(message("702","601",content(true,1))))));
        assertThat(sender.send(claim(IncidentThreadStage.PRIVATE_BODY,1)).disposition()).isEqualTo(IncidentSendResult.Disposition.SENT);
        wm.verify(1,patchRequestedFor(urlEqualTo("/api/v10/channels/601")).withRequestBody(equalToJson("{\"archived\":false}")));wm.verify(1,postRequestedFor(urlEqualTo("/api/v10/channels/601/messages")).withRequestBody(matchingJsonPath("$.allowed_mentions.parse",equalToJson("[]"))));
    }
    @Test void givenPrivacyChangesWhileReopeningAnArchivedThread_whenPostingItsNextPart_thenRecheckAndRejectBeforePosting() {
        wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).inScenario("Test privacy reopen").whenScenarioStateIs("Started").willReturn(okJson(parent(0,false))));
        wm.stubFor(get(urlEqualTo("/api/v10/channels/601")).willReturn(okJson(thread("601","300",true))));
        wm.stubFor(patch(urlEqualTo("/api/v10/channels/601")).inScenario("Test privacy reopen").whenScenarioStateIs("Started").willReturn(okJson(thread("601","300",false))).willSetStateTo("Exposed"));
        wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).inScenario("Test privacy reopen").whenScenarioStateIs("Exposed").willReturn(okJson(parent(0,true))));
        wm.stubFor(get(urlEqualTo("/api/v10/channels/601")).inScenario("Test privacy reopen").whenScenarioStateIs("Exposed").willReturn(okJson(thread("601","300",false))));
        wm.stubFor(post(urlEqualTo("/api/v10/channels/601/messages")).willReturn(okJson(json(message("702","601",content(true,1))))));
        assertThat(sender.send(claim(IncidentThreadStage.PRIVATE_BODY,1)).disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED);wm.verify(0,postRequestedFor(anyUrl()));
    }
    @Test void givenTheLeaseExpiresDuringPermissionReads_whenReachingThePost_thenRejectBeforeAnyDetailedMutation() {
        when(clock.instant()).thenAnswer(call -> wm.getAllServeEvents().stream().anyMatch(event -> event.getRequest().getUrl().equals("/api/v10/channels/300"))?NOW.plusSeconds(601):NOW);
        wm.stubFor(post(urlEqualTo("/api/v10/channels/300/messages")).willReturn(okJson(json(message("601","300",content(true,0))))));
        assertThat(sender.send(claim(IncidentThreadStage.PRIVATE_STARTER,0)).disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED);
        wm.verify(1,getRequestedFor(urlEqualTo("/api/v10/channels/300")));wm.verify(0,postRequestedFor(anyUrl()));
    }
    @Test void givenTheLeaseExpiresAfterARealRateLimitResponse_whenRetrying_thenStopBeforeAnotherHttpAttempt() {
        when(clock.instant()).thenAnswer(call -> wm.getAllServeEvents().stream().anyMatch(event -> event.getRequest().getMethod().getName().equals("POST"))?NOW.plusSeconds(601):NOW);
        wm.stubFor(post(urlEqualTo("/api/v10/channels/300/messages")).inScenario("Test expired retry").whenScenarioStateIs("Started").willReturn(aResponse().withStatus(429).withHeader("Retry-After","0")).willSetStateTo("Ready"));
        wm.stubFor(post(urlEqualTo("/api/v10/channels/300/messages")).inScenario("Test expired retry").whenScenarioStateIs("Ready").willReturn(okJson(json(message("601","300",content(true,0))))));
        assertThat(sender.send(claim(IncidentThreadStage.PRIVATE_STARTER,0)).disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED);wm.verify(1,postRequestedFor(urlEqualTo("/api/v10/channels/300/messages")));
    }
    void emptyInventory(String channel) {
        wm.stubFor(get(urlPathEqualTo("/api/v10/channels/200/messages")).withQueryParam("limit",equalTo("100")).willReturn(okJson("[]")));
        wm.stubFor(get(urlPathEqualTo("/api/v10/channels/200/threads/archived/public")).withQueryParam("limit",equalTo("100")).willReturn(okJson("{\"threads\":[],\"has_more\":false}")));
        wm.stubFor(get(urlEqualTo("/api/v10/guilds/100/threads/active")).willReturn(okJson("{\"threads\":[]}")));
        wm.stubFor(get(urlPathEqualTo("/api/v10/channels/"+channel+"/threads/archived/public")).withQueryParam("limit",equalTo("100")).willReturn(okJson("{\"threads\":[],\"has_more\":false}")));
    }
    @Test void givenACompleteEmptyPrivateForumInventory_whenReviewingAbsence_thenProveItWithoutCreatingAThread() {
        config.setStewardsForumTagIds("900");wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(15,false))));emptyInventory("300");
        var result=sender.verify(claim(IncidentThreadStage.PRIVATE_STARTER,0),"");assertThat(result.code()).isEqualTo("PROVEN_MISSING");wm.verify(0,postRequestedFor(anyUrl()));
    }
    @Test void givenAnUncertainForumCreationFoundInArchivedPages_whenReviewing_thenAdoptTheExactBotMarkerAndReturnedIds() {
        config.setStewardsForumTagIds("900");wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(15,false))));emptyInventory("300");
        wm.stubFor(get(urlPathEqualTo("/api/v10/channels/300/threads/archived/public")).withQueryParam("limit",equalTo("100")).willReturn(okJson("{\"threads\":["+thread("600","300",true)+"],\"has_more\":false}")));
        wm.stubFor(get(urlEqualTo("/api/v10/channels/600")).willReturn(okJson(thread("600","300",true))));wm.stubFor(get(urlPathEqualTo("/api/v10/channels/600/messages")).withQueryParam("limit",equalTo("100")).willReturn(okJson(json(List.of(message("602","600",content(true,0)))))));
        var result=sender.verify(claim(IncidentThreadStage.PRIVATE_STARTER,0),"");assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.SENT);assertThat(result.messageId()).isEqualTo("602");assertThat(result.threadId()).isEqualTo("600");wm.verify(0,postRequestedFor(anyUrl()));
    }
    @Test void givenAForeignRevisionWithTheSamePermanentBotStepMarker_whenReviewingAbsence_thenKeepItUnresolvedInsteadOfRepostingOldFacts() {
        wm.stubFor(get(urlPathEqualTo("/api/v10/channels/300/messages")).withQueryParam("limit",equalTo("100")).willReturn(okJson(json(List.of(message("601","300",content(true,0).replace("Test facts","Test later revised facts")))))));
        emptyInventory("300");var result=sender.verify(claim(IncidentThreadStage.PRIVATE_STARTER,0),"");assertThat(result.code()).isNotEqualTo("PROVEN_MISSING");assertThat(result.disposition()).isNotEqualTo(IncidentSendResult.Disposition.SENT);wm.verify(0,postRequestedFor(anyUrl()));
    }
    @Test void givenTheStarterWasDeletedButItsCaseThreadRemains_whenReviewingAbsence_thenDoNotCreateASecondCasePair() {
        emptyInventory("300");wm.stubFor(get(urlEqualTo("/api/v10/guilds/100/threads/active")).willReturn(okJson("{\"threads\":["+thread("601","300",false)+"]}")));
        wm.stubFor(get(urlPathEqualTo("/api/v10/channels/300/messages")).withQueryParam("limit",equalTo("100")).willReturn(okJson("[]")));
        wm.stubFor(get(urlEqualTo("/api/v10/channels/601")).willReturn(okJson(thread("601","300",false))));wm.stubFor(get(urlEqualTo("/api/v10/channels/300/messages/601")).willReturn(notFound()));
        var result=sender.verify(claim(IncidentThreadStage.PRIVATE_STARTER,0),"");assertThat(result.code()).isNotEqualTo("PROVEN_MISSING");wm.verify(0,postRequestedFor(anyUrl()));
    }
    @Test void givenArchivePaginationHasNoCompletenessProof_whenReviewingAnUnknownForumCreation_thenNeverTreatItAsMissing() {
        config.setStewardsForumTagIds("900");wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(15,false))));emptyInventory("300");
        wm.stubFor(get(urlPathEqualTo("/api/v10/channels/300/threads/archived/public")).withQueryParam("limit",equalTo("100")).willReturn(okJson("{\"threads\":[]}")));
        assertThat(sender.verify(claim(IncidentThreadStage.PRIVATE_STARTER,0),"").code()).isNotEqualTo("PROVEN_MISSING");wm.verify(0,postRequestedFor(anyUrl()));
    }
    @Test void givenAStandaloneLaterPublicStarterButNoPublicThread_whenReviewingPrivateAbsence_thenKeepTheCaseUnresolved() {
        emptyInventory("300");wm.stubFor(get(urlPathEqualTo("/api/v10/channels/300/messages")).withQueryParam("limit",equalTo("100")).willReturn(okJson("[]")));
        wm.stubFor(get(urlPathEqualTo("/api/v10/channels/200/messages")).withQueryParam("limit",equalTo("100")).willReturn(okJson(json(List.of(message("801","200",content(false,0)))))));
        assertThat(sender.verify(claim(IncidentThreadStage.PRIVATE_STARTER,0),"").code()).isNotEqualTo("PROVEN_MISSING");wm.verify(0,postRequestedFor(anyUrl()));
    }
    @Test void givenTwoIdenticalBotStartersInOneForumThread_whenDiscoveringAnUnknownResult_thenRejectItsAmbiguousIdentity() {
        config.setStewardsForumTagIds("900");wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(15,false))));emptyInventory("300");
        wm.stubFor(get(urlEqualTo("/api/v10/guilds/100/threads/active")).willReturn(okJson("{\"threads\":["+thread("600","300",false)+"]}")));
        wm.stubFor(get(urlEqualTo("/api/v10/channels/600")).willReturn(okJson(thread("600","300",false))));wm.stubFor(get(urlPathEqualTo("/api/v10/channels/600/messages")).withQueryParam("limit",equalTo("100")).willReturn(okJson(json(List.of(message("602","600",content(true,0)),message("603","600",content(true,0)))))));
        var result=sender.verify(claim(IncidentThreadStage.PRIVATE_STARTER,0),"");assertThat(result.disposition()).isNotEqualTo(IncidentSendResult.Disposition.SENT);assertThat(result.code()).isNotEqualTo("PROVEN_MISSING");wm.verify(0,postRequestedFor(anyUrl()));
    }
    @Test void givenAnArchivedCandidateOnAPaginatedInventory_whenReviewing_thenReadTheNextCursorPageBeforeAdopting() {
        config.setStewardsForumTagIds("900");wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(15,false))));emptyInventory("300");
        wm.stubFor(get(urlPathEqualTo("/api/v10/channels/300/threads/archived/public")).withQueryParam("limit",equalTo("100")).withQueryParam("before",absent()).willReturn(okJson("{\"threads\":["+thread("600","300",true)+"],\"has_more\":true}")));
        wm.stubFor(get(urlPathEqualTo("/api/v10/channels/300/threads/archived/public")).withQueryParam("limit",equalTo("100")).withQueryParam("before",equalTo("2026-11-03T12:00:00Z")).willReturn(okJson("{\"threads\":[],\"has_more\":false}")));
        wm.stubFor(get(urlEqualTo("/api/v10/channels/600")).willReturn(okJson(thread("600","300",true))));wm.stubFor(get(urlPathEqualTo("/api/v10/channels/600/messages")).withQueryParam("limit",equalTo("100")).willReturn(okJson(json(List.of(message("602","600",content(true,0)))))));
        assertThat(sender.verify(claim(IncidentThreadStage.PRIVATE_STARTER,0),"").threadId()).isEqualTo("600");wm.verify(1,getRequestedFor(urlPathEqualTo("/api/v10/channels/300/threads/archived/public")).withQueryParam("before",equalTo("2026-11-03T12:00:00Z")));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"forum","thread","unarchive"})
    void givenEveryDetailedMutationCrossesLeaseExpiryAfter429_whenRetrying_thenFenceTheExactHttpEndpoint(String mutation) {
        var stage=mutation.equals("thread")?IncidentThreadStage.PRIVATE_THREAD:mutation.equals("unarchive")?IncidentThreadStage.PRIVATE_BODY:IncidentThreadStage.PRIVATE_STARTER;
        String path=mutation.equals("forum")?"/api/v10/channels/300/threads":mutation.equals("thread")?"/api/v10/channels/300/messages/601/threads":"/api/v10/channels/601";
        if(mutation.equals("forum")){config.setStewardsForumTagIds("900");wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson(parent(15,false))));}
        else wm.stubFor(get(urlEqualTo("/api/v10/channels/601")).willReturn(mutation.equals("thread")?notFound():okJson(thread("601","300",true))));
        when(clock.instant()).thenAnswer(call -> wm.getAllServeEvents().stream().anyMatch(event -> java.util.Set.of("POST","PATCH").contains(event.getRequest().getMethod().getName()))?NOW.plusSeconds(601):NOW);
        if(mutation.equals("unarchive")) {
            wm.stubFor(patch(urlEqualTo(path)).inScenario("Test mutation expiry").whenScenarioStateIs("Started").willReturn(aResponse().withStatus(429).withHeader("Retry-After","0")).willSetStateTo("Ready"));wm.stubFor(patch(urlEqualTo(path)).inScenario("Test mutation expiry").whenScenarioStateIs("Ready").willReturn(okJson("{}")));
        } else {
            wm.stubFor(post(urlEqualTo(path)).inScenario("Test mutation expiry").whenScenarioStateIs("Started").willReturn(aResponse().withStatus(429).withHeader("Retry-After","0")).willSetStateTo("Ready"));wm.stubFor(post(urlEqualTo(path)).inScenario("Test mutation expiry").whenScenarioStateIs("Ready").willReturn(okJson("{}")));
        }
        assertThat(sender.send(claim(stage,mutation.equals("unarchive")?1:0)).disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED);
        if(mutation.equals("unarchive"))wm.verify(1,patchRequestedFor(urlEqualTo(path)));else wm.verify(1,postRequestedFor(urlEqualTo(path)));
    }
}
