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
    void readableDisclosuresKeepTheCompleteReferenceAndFactsInCompactContextualHeaders() {
        var operation = frozen(facts, "Test CLR");
        ReflectionTestUtils.setField(operation, "caseReference", "INC-6E37B1BE-20ED-48EC-9C95-9D07812AF45E");
        ReflectionTestUtils.setField(operation, "pairingLabel", "T-DLT vs T-CHR");
        ReflectionTestUtils.setField(operation, "raceLabel", "Leg 1 · Test season 2027 · MD 1");
        ReflectionTestUtils.setField(operation, "presentationVersion", 1);
        var projected = IncidentThreadProjection.capture(operation);
        assertThat(projected.title()).hasSizeLessThanOrEqualTo(100)
                .startsWith("T-DLT vs T-CHR").endsWith("INC:bjexviDtSOyclZ0HgSr0Xg");
        assertThat(String.join("", projected.messages())).contains("**Incident INC:bjexviDtSOyclZ0HgSr0Xg**",
                "**Pairing:** T-DLT vs T-CHR", "**Race:** Leg 1 · Test season 2027 · MD 1",
                "**Involved drivers**", "**Replay timestamp**", "**Description**", "**Evidence references**",
                facts.getReplayTimestamp(), facts.getDescription(), facts.getEvidenceLinks())
                .doesNotContain(operation.getCaseReference(), report.getRace().getId().toString(), author);
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
    void legacyDisclosuresKeepTheirExactPublishedFormatAfterTeamsAndRaceLabelsChange() {
        var operation = frozen(facts, "Test original party");
        var original = IncidentThreadProjection.capture(operation);
        assertThat(original.title()).isEqualTo("Incident " + operation.getCaseReference() + " · Test original party");
        assertThat(original.messages()).containsExactly("Incident " + operation.getCaseReference()
                + "\nReporting party: Test original party\n\nInvolved drivers\n" + facts.getInvolvedDrivers()
                + "\n\nReplay timestamp\n" + facts.getReplayTimestamp() + "\n\nDescription\n" + facts.getDescription()
                + "\n\nEvidence references\n" + facts.getEvidenceLinks());
        f.home.setShortName("T-CHANGED"); f.day.setLabel("Test changed day");
        var reread = IncidentThreadProjection.capture(operation);
        assertThat(reread).isEqualTo(original);
        assertThat(String.join("", reread.messages())).doesNotContain("Pairing:", "Race:", "T-CHANGED", "Test changed day");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void givenLongUnicodeFactsAndParty_whenProjecting_thenKeepEveryFactInOrderedBoundedMessagesWithoutBrokenSurrogates(boolean readable) {
        String description="Test complete emoji facts "+"🚗𝄞 abc ".repeat(1600);
        String evidence="https://example.invalid/test-evidence/"+"x".repeat(6000);
        var operation=frozen(new IncidentPayload("Test involved " + "D".repeat(1800),"00:12",description,evidence),"Test CLR "+"🚗".repeat(100));
        if (readable) {
            ReflectionTestUtils.setField(operation, "pairingLabel", "Test home vs Test away");
            ReflectionTestUtils.setField(operation, "raceLabel", "Leg 2 · Test season · Test day");
            ReflectionTestUtils.setField(operation, "presentationVersion", 1);
        }
        var projected=assertDoesNotThrow(() -> IncidentThreadProjection.capture(operation));
        assertThat(projected.title()).hasSizeLessThanOrEqualTo(100).contains(readable ? org.ctc.domain.incidents.IncidentDisplay.reference(operation.getCaseReference()) : operation.getCaseReference());assertThat(projected.messages()).hasSizeGreaterThan(1);
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
    @ParameterizedTest @ValueSource(strings = {"stored-legacy", "duplicate", "receipt", "half"})
    void frozenIdentityCannotUpgradeLegacyRowsOrBeReplacedOrAttachedToAReceipt(String cause) {
        var original = frozen(facts, "Test original party");
        var fresh = cause.equals("stored-legacy") ? original : IncidentDelivery.capture(report, original.getRevision(),
                cause.equals("receipt") ? IncidentDeliveryOperation.RECEIPT : IncidentDeliveryOperation.DISCLOSURE, original.getCapturedAt());
        String race = "Leg 1 · Test original day · Race " + report.getRace().getId();
        if (cause.equals("duplicate")) fresh.freezePublicIdentity("Test original pairing", race);
        assertThatThrownBy(() -> fresh.freezePublicIdentity("Test changed pairing", cause.equals("half") ? null : race)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> fresh.freezeReadableIdentity("Test changed pairing", cause.equals("half") ? null : race)).isInstanceOf(BusinessRuleException.class);
        assertThat(fresh.getPresentationVersion()).isNull();
        if (cause.equals("duplicate")) assertThat(fresh.getPairingLabel()).isEqualTo("Test original pairing");
        else assertThat(fresh.getPairingLabel()).isNull();
    }

    @ParameterizedTest @ValueSource(ints = {0, 2, -1})
    void givenUnsupportedFrozenPresentation_whenProjecting_thenRejectInsteadOfChangingThePublishedRepresentation(int version) {
        var operation = frozen(facts, "Test party");
        ReflectionTestUtils.setField(operation, "pairingLabel", "Test home vs Test away");
        ReflectionTestUtils.setField(operation, "raceLabel", "Leg 1 · Test season · Test day");
        ReflectionTestUtils.setField(operation, "presentationVersion", version);
        assertThatThrownBy(() -> IncidentThreadProjection.capture(operation)).isInstanceOf(BusinessRuleException.class);
    }

    @Test void givenReadableVersionWithoutFrozenCaptions_whenProjecting_thenReject() {
        var operation = frozen(facts, "Test party");
        ReflectionTestUtils.setField(operation, "presentationVersion", 1);
        assertThatThrownBy(() -> IncidentThreadProjection.capture(operation)).isInstanceOf(BusinessRuleException.class);
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
