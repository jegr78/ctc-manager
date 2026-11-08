package org.ctc.gt7sync.environment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TrackEnvironmentImportStoreTest {
    @Test void givenBoundedPreview_whenExpiredEvictedOrConsumed_thenNeverRunItsOperation() throws Exception {
        var now=new AtomicReference<>(Instant.parse("2026-11-08T12:00:00Z"));
        var clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;} public Instant instant(){return now.get();}};
        var store=new TrackEnvironmentImportStore(clock,Duration.ofSeconds(10),1);
        var first=preview();var second=preview();
        store.capture(first,"owner","generation");store.capture(second,"owner","generation");
        assertThatThrownBy(() -> store.apply(first.id(),"owner","generation",p -> {throw new AssertionError("Evicted operation ran");})).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> store.apply(second.id(),"intruder","generation",p -> 1)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> store.apply(second.id(),"owner","changed",p -> 1)).isInstanceOf(BusinessRuleException.class);
        now.set(now.get().plusSeconds(10));
        assertThatThrownBy(() -> store.apply(second.id(),"owner","generation",p -> {throw new AssertionError("Expired operation ran");})).isInstanceOf(BusinessRuleException.class);
        store.capture(first,"owner","generation");
        assertThatThrownBy(() -> store.apply(first.id(),"owner","generation",p -> {throw new BusinessRuleException("Retryable conflict");})).hasMessage("Retryable conflict");
        int applied=store.apply(first.id(),"owner","generation",p -> 7);
        assertThat(applied).isEqualTo(7);
        assertThatThrownBy(() -> store.apply(first.id(),"owner","generation",p -> 1)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void givenConcurrentApply_whenSamePreviewIsBusy_thenOnlyOneOperationRuns() throws Exception {
        var store=new TrackEnvironmentImportStore(Clock.systemUTC());var preview=preview();store.capture(preview,"owner","generation");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newSingleThreadExecutor()) {
            var running=executor.submit(() -> store.apply(preview.id(),"owner","generation",p -> {
                entered.countDown();try {if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Fixture timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return 1;
            }));
            try {
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> store.apply(preview.id(),"owner","generation",p -> {throw new AssertionError("Concurrent operation ran");})).isInstanceOf(BusinessRuleException.class).hasMessageContaining("busy");
            }finally{release.countDown();}
            assertThat(running.get(5,TimeUnit.SECONDS)).isEqualTo(1);
        }
    }
    private TrackEnvironmentImportService.Preview preview() throws Exception {
        return new TrackEnvironmentImportService.Preview(java.util.UUID.randomUUID(),"Test preview",GtPlanetEnvironmentSource.bundled(),List.of());
    }
}
