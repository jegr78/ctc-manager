package org.ctc.discord;

import java.time.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.integration.OutboundPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiscordIncidentSendAuthorityTest {
    static final Instant NOW=Instant.parse("2026-11-03T12:00:00Z");
    final AtomicReference<Instant> now=new AtomicReference<>(NOW);
    final Clock clock=new Clock() {
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now.get();}
    };
    final DiscordRateLimitInterceptor interceptor=new DiscordRateLimitInterceptor(clock,"0","0,0",10000,new OutboundPolicy(true,new MockEnvironment()));
    HttpRequest request(Instant deadline) {
        var request=mock(HttpRequest.class);when(request.getMethod()).thenReturn(HttpMethod.POST);when(request.getAttributes()).thenReturn(deadline==null?Map.of():Map.of(DiscordRateLimitInterceptor.SEND_DEADLINE,deadline));return request;
    }
    ClientHttpResponse response(HttpStatus status) throws Exception {
        var response=mock(ClientHttpResponse.class);when(response.getStatusCode()).thenReturn(status);var headers=new HttpHeaders();headers.set("Retry-After","0");when(response.getHeaders()).thenReturn(headers);return response;
    }
    @Test void givenAnExpiredDetailedSendAuthority_whenIntercepting_thenRejectWithoutExecutingTheRequest() throws Exception {
        var execution=mock(ClientHttpRequestExecution.class);var response=response(HttpStatus.OK);when(execution.execute(any(),any())).thenReturn(response);
        assertThatThrownBy(() -> interceptor.intercept(request(NOW),new byte[0],execution)).isInstanceOfSatisfying(DiscordTransientException.class,failure -> assertThat(failure.isConfirmedRejection()).isTrue());verify(execution,never()).execute(any(),any());
    }
    @Test void givenAValidOrLegacyAuthority_whenIntercepting_thenPreserveTheExistingSuccessfulRequest() throws Exception {
        for(Instant deadline:new Instant[]{NOW.plusSeconds(600),null}) {var execution=mock(ClientHttpRequestExecution.class);var response=response(HttpStatus.OK);when(execution.execute(any(),any())).thenReturn(response);assertThat(interceptor.intercept(request(deadline),new byte[0],execution)).isSameAs(response);verify(execution,times(1)).execute(any(),any());}
    }
    @Test void givenAuthorityExpiresAfterAConfirmedRateLimitRejection_whenRetrying_thenMakeNoSecondAttempt() throws Exception {
        var execution=mock(ClientHttpRequestExecution.class);var first=response(HttpStatus.TOO_MANY_REQUESTS);var count=new AtomicInteger();
        when(execution.execute(any(),any())).thenAnswer(call -> {if(count.getAndIncrement()==0){now.set(NOW.plusSeconds(601));return first;}return response(HttpStatus.OK);});
        assertThatThrownBy(() -> interceptor.intercept(request(NOW.plusSeconds(600)),new byte[0],execution)).isInstanceOfSatisfying(DiscordTransientException.class,failure -> assertThat(failure.isConfirmedRejection()).isTrue());verify(execution,times(1)).execute(any(),any());verify(first).close();
    }
    @Test void givenAuthorityExpiresAfterAnAmbiguousServerFailure_whenRetrying_thenPreserveTheUnknownRemoteOutcome() throws Exception {
        var execution=mock(ClientHttpRequestExecution.class);var first=response(HttpStatus.INTERNAL_SERVER_ERROR);var count=new AtomicInteger();
        when(execution.execute(any(),any())).thenAnswer(call -> {if(count.getAndIncrement()==0){now.set(NOW.plusSeconds(601));return first;}return response(HttpStatus.OK);});
        assertThatThrownBy(() -> interceptor.intercept(request(NOW.plusSeconds(600)),new byte[0],execution)).isInstanceOfSatisfying(DiscordTransientException.class,failure -> assertThat(failure.isConfirmedRejection()).isFalse());verify(execution,times(1)).execute(any(),any());verify(first).close();
    }
}
