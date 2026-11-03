package org.ctc.discord.incidents;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.IncidentDeliveryOperation;
import org.ctc.domain.service.IncidentNoticeService;
import org.ctc.integration.OutboundPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DiscordIncidentNoticeSenderTest {
    final RestClient.Builder builder = RestClient.builder().baseUrl("https://discord.com/api/v10").defaultHeader("Authorization","Bot test-incidents");
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final DiscordGlobalConfigRepository configs = mock(DiscordGlobalConfigRepository.class);
    final DiscordIncidentNoticeSender sender = new DiscordIncidentNoticeSender(new DiscordRestClient(builder.build()),configs,new OutboundPolicy(true,new MockEnvironment()));
    IncidentDeliveryClaim claim(IncidentDeliveryOperation operation) { return new IncidentDeliveryClaim(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,"test-runtime:0",operation,"100","200","test-stable-nonce"); }
    DiscordIncidentNoticeSenderTest() { var config=new DiscordGlobalConfig();config.setGuildId("100");when(configs.findFirstByOrderByIdAsc()).thenReturn(config); }
    void channel(String guild,String id,int type) { server.expect(requestTo("https://discord.com/api/v10/channels/200")).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("{\"id\":\""+id+"\",\"guild_id\":\""+guild+"\",\"type\":"+type+",\"name\":\"Test PRIVATE reporter facts\"}",MediaType.APPLICATION_JSON)); }

    @Test
    void givenConfirmedPrivateReport_whenSendingTheReceipt_thenPostOnlyFixedNeutralTextAndSuppressMentionsWithABoundedStableNonce() throws Exception {
        channel("100","200",0); var claim=claim(IncidentDeliveryOperation.RECEIPT);
        server.expect(requestTo("https://discord.com/api/v10/channels/200/messages")).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Bot test-incidents")).andExpect(request -> {
            String raw=((MockClientHttpRequest)request).getBodyAsString();var json=new ObjectMapper().readTree(raw);
            assertThat(json.size()).isEqualTo(4); assertThat(json.path("content").asText()).isEqualTo(IncidentNoticeService.neutralText());
            assertThat(json.path("allowed_mentions").path("parse").isArray()).isTrue();assertThat(json.path("allowed_mentions").path("parse").isEmpty()).isTrue();
            assertThat(json.path("nonce").asText()).isEqualTo(claim.nonce()).hasSizeLessThanOrEqualTo(25);assertThat(json.path("enforce_nonce").asBoolean()).isTrue();
            assertThat(raw).doesNotContain("PRIVATE","reporter","involved","00:00","example.invalid");
        }).andRespond(withSuccess("{\"id\":\"500\",\"channel_id\":\"200\",\"content\":\"ignored PRIVATE content\"}",MediaType.APPLICATION_JSON));
        var result=sender.send(claim);assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.SENT);assertThat(result.messageId()).isEqualTo("500");assertThat(result.toString()).doesNotContain("PRIVATE");server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings={"guild","channel","thread","configuration","disabled","disclosure"})
    void givenUnverifiedDestinationOrDisabledPolicy_whenSending_thenRejectBeforeAnyMessageCreation(String cause) {
        var operation=cause.equals("disclosure")?IncidentDeliveryOperation.DISCLOSURE:IncidentDeliveryOperation.RECEIPT;
        if(cause.equals("configuration")) when(configs.findFirstByOrderByIdAsc()).thenReturn(null);
        if(java.util.Set.of("guild","channel","thread").contains(cause)) channel(cause.equals("guild")?"999":"100",cause.equals("channel")?"999":"200",cause.equals("thread")?11:0);
        var selected=cause.equals("disabled")?new DiscordIncidentNoticeSender(new DiscordRestClient(builder.build()),configs,new OutboundPolicy(false,new MockEnvironment())):sender;
        var result=selected.send(claim(operation));assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED);assertThat(result.code()).isNotEqualTo("UNIMPLEMENTED");server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings={"403","404","500","timeout","malformed","channel","identity"})
    void givenRejectedOrUnconfirmedPost_whenSending_thenDistinguishRetryableRejectionFromUnknownRemoteCreation(String outcome) {
        channel("100","200",0);var expected=server.expect(requestTo("https://discord.com/api/v10/channels/200/messages")).andExpect(method(HttpMethod.POST));
        switch(outcome) {
            case "403" -> expected.andRespond(withStatus(HttpStatus.FORBIDDEN));
            case "404" -> expected.andRespond(withStatus(HttpStatus.NOT_FOUND));
            case "500" -> expected.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
            case "timeout" -> expected.andRespond(request -> {throw new java.io.IOException("Test PRIVATE uncertain send");});
            case "malformed" -> expected.andRespond(withSuccess("broken PRIVATE json",MediaType.APPLICATION_JSON));
            case "channel" -> expected.andRespond(withSuccess("{\"id\":\"500\",\"channel_id\":\"999\"}",MediaType.APPLICATION_JSON));
            default -> expected.andRespond(withSuccess("{\"channel_id\":\"200\"}",MediaType.APPLICATION_JSON));
        }
        var result=sender.send(claim(IncidentDeliveryOperation.RECEIPT));assertThat(result.disposition()).isEqualTo(java.util.Set.of("403","404").contains(outcome)?IncidentSendResult.Disposition.REJECTED:IncidentSendResult.Disposition.UNCERTAIN);assertThat(result.messageId()).isNull();assertThat(result.code()).isNotEqualTo("UNIMPLEMENTED");assertThat(result.toString()).doesNotContain("PRIVATE");server.verify();
    }

    @Test
    void givenReadFailureBeforeMessageCreation_whenSending_thenRetainASafeRetryableRejection() {
        server.expect(requestTo("https://discord.com/api/v10/channels/200")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var result=sender.send(claim(IncidentDeliveryOperation.RECEIPT));assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED);assertThat(result.code()).isNotEqualTo("UNIMPLEMENTED");server.verify();
    }
    @ParameterizedTest
    @ValueSource(strings={"rate-only","rate-after-unknown","transient-success","transient-deny"})
    void givenBoundedTransportRetries_whenTheLastResponseChanges_thenKeepTheNonceAndAnyEarlierUncertainty(String outcome) {
        var policy=new OutboundPolicy(true,new MockEnvironment());
        var http=RestClient.builder().baseUrl("https://discord.com/api/v10").requestInterceptor(new org.ctc.discord.DiscordRateLimitInterceptor(java.time.Clock.systemUTC(),"0","0",10000,policy));
        var remote=MockRestServiceServer.bindTo(http).build();
        var selected=new DiscordIncidentNoticeSender(new DiscordRestClient(http.build()),configs,policy);
        remote.expect(requestTo("https://discord.com/api/v10/channels/200")).andRespond(withSuccess("{\"id\":\"200\",\"guild_id\":\"100\",\"type\":0}",MediaType.APPLICATION_JSON));
        if(!outcome.equals("rate-only")) remote.expect(requestTo("https://discord.com/api/v10/channels/200/messages")).andExpect(request -> assertThat(((MockClientHttpRequest)request).getBodyAsString()).contains("test-stable-nonce","enforce_nonce")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        if(outcome.startsWith("rate-")) for(int i=0;i<4;i++) remote.expect(requestTo("https://discord.com/api/v10/channels/200/messages")).andExpect(request -> assertThat(((MockClientHttpRequest)request).getBodyAsString()).contains("test-stable-nonce","enforce_nonce")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        else if(outcome.equals("transient-success")) remote.expect(requestTo("https://discord.com/api/v10/channels/200/messages")).andRespond(withSuccess("{\"id\":\"500\",\"channel_id\":\"200\"}",MediaType.APPLICATION_JSON));
        else remote.expect(requestTo("https://discord.com/api/v10/channels/200/messages")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        var result=selected.send(claim(IncidentDeliveryOperation.RECEIPT));
        assertThat(result.disposition()).isEqualTo(outcome.equals("rate-only")?IncidentSendResult.Disposition.REJECTED:outcome.equals("transient-success")?IncidentSendResult.Disposition.SENT:IncidentSendResult.Disposition.UNCERTAIN);
        assertThat(result.code()).isNotEqualTo("UNIMPLEMENTED");remote.verify();
    }

}
