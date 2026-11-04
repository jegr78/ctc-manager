package org.ctc.domain.service;

import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class IncidentThreadPublicationTest {
    final IncidentUnitFixture f=new IncidentUnitFixture();
    final String author="900000000000000009";
    final IncidentPayload facts=new IncidentPayload("Test involved-driver facts","Lap 2 at 00:12","Test accepted incident description","https://example.invalid/test-clip");
    IncidentReport report;
    IncidentDelivery frozen(IncidentPayload payload,String party) {
        f.openWindow();report=IncidentUnitFixture.id(new IncidentReport(f.window,f.race,author,new IncidentParty(IncidentPartyType.TEAM,f.home.getId(),party,f.home.getDiscordRoleId())));report.appendRevision();
        var revision=IncidentUnitFixture.id(new IncidentReportRevision(report,1,1,payload,author,IncidentUnitFixture.NOW,UUID.randomUUID()));report.freeze();f.window.close(IncidentUnitFixture.NOW.plusSeconds(3600));
        return IncidentUnitFixture.id(IncidentDelivery.capture(report,revision,IncidentDeliveryOperation.DISCLOSURE,f.window.getClosedAt()));
    }
    @Test
    void givenFrozenActiveDisclosure_whenProjecting_thenIncludeTheOriginalPartyAndAllFactsWithoutPersonalReporterMetadata() {
        var operation=frozen(facts,"Test CLR");
        ReflectionTestUtils.setField(report,"party",new IncidentParty(IncidentPartyType.TEAM,f.away.getId(),"Test changed party",f.away.getDiscordRoleId()));report.appendRevision();
        var projected=assertDoesNotThrow(() -> IncidentThreadProjection.capture(operation));
        assertThat(projected.title()).contains(operation.getCaseReference(),"Test CLR").doesNotContain(author,"Test changed party");
        String body=String.join("",projected.messages());assertThat(body).contains(operation.getCaseReference(),"Test CLR",facts.getInvolvedDrivers(),facts.getReplayTimestamp(),facts.getDescription(),facts.getEvidenceLinks())
                .doesNotContain(author,"Test changed party","Reporter:","Withdrawal history","https://discord.com/channels/");
        assertThat(projected.messages()).isNotEmpty().allSatisfy(message -> assertThat(message).hasSizeLessThanOrEqualTo(2000));
        assertThatThrownBy(() -> projected.messages().add("Test mutation")).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test
    void givenLongUnicodeFactsAndParty_whenProjecting_thenKeepEveryFactInOrderedBoundedMessagesWithoutBrokenSurrogates() {
        String description="Test complete emoji facts "+"🚗𝄞 abc ".repeat(1600);
        String evidence="https://example.invalid/test-evidence/"+"x".repeat(6000);
        var operation=frozen(new IncidentPayload("Test involved " + "D".repeat(1800),"00:12",description,evidence),"Test CLR "+"🚗".repeat(100));
        var projected=assertDoesNotThrow(() -> IncidentThreadProjection.capture(operation));
        assertThat(projected.title()).hasSizeLessThanOrEqualTo(100).contains(operation.getCaseReference());assertThat(projected.messages()).hasSizeGreaterThan(1);
        String joined=String.join("",projected.messages());assertThat(joined).contains(description,evidence,operation.getPartyLabel());
        for(String message:projected.messages()) {assertThat(message.length()).isLessThanOrEqualTo(2000);assertThat(Character.isLowSurrogate(message.charAt(0))).isFalse();assertThat(Character.isHighSurrogate(message.charAt(message.length()-1))).isFalse();}
        assertThat(Character.isHighSurrogate(projected.title().charAt(projected.title().length()-1))).isFalse();
    }
    @Test
    void givenMarkupInThePartyAndMalformedUnicode_whenProjecting_thenRenderAnEscapedLabelAndReadableValidText() {
        var input=new IncidentPayload("Test driver","00:12","Test broken \uD83D value","https://example.invalid/clip");
        var operation=frozen(input,"Test *CLR*\nsecond line");
        var projected=assertDoesNotThrow(() -> IncidentThreadProjection.capture(operation));
        assertThat(projected.title()).doesNotContain("\n","\r");
        String body=String.join("",projected.messages());assertThat(body).contains("Test \\*CLR\\* second line","Test broken \uFFFD value");
    }
    @ParameterizedTest
    @ValueSource(strings={"open","withdrawn","unfrozen","foreign-frozen","receipt","foreign-owner"})
    void givenAnUnpublishableOperation_whenProjecting_thenRejectBeforeAnyDetailedPayloadCanBeSent(String cause) {
        var operation=frozen(facts,"Test CLR");
        if(cause.equals("open"))ReflectionTestUtils.setField(f.window,"state",IncidentWindowState.OPEN);
        if(cause.equals("withdrawn"))report.withdraw();
        if(cause.equals("unfrozen"))ReflectionTestUtils.setField(report,"frozenRevisionNumber",null);
        if(cause.equals("foreign-frozen"))ReflectionTestUtils.setField(report,"frozenRevisionNumber",2);
        if(cause.equals("receipt"))operation=IncidentDelivery.capture(report,operation.getRevision(),IncidentDeliveryOperation.RECEIPT,IncidentUnitFixture.NOW);
        if(cause.equals("foreign-owner")) {
            var foreign=IncidentUnitFixture.id(new IncidentReport(f.window,f.race,"901",f.party()));
            var revision=IncidentUnitFixture.id(new IncidentReportRevision(foreign,1,1,facts,"901",IncidentUnitFixture.NOW,UUID.randomUUID()));
            ReflectionTestUtils.setField(operation,"revision",revision);
        }
        var candidate=operation;
        assertThatThrownBy(() -> IncidentThreadProjection.capture(candidate)).isInstanceOf(BusinessRuleException.class).hasMessageNotContaining(facts.getDescription()).hasMessageNotContaining(author);
    }
}
