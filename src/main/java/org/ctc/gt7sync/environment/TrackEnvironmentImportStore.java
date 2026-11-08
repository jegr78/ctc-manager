package org.ctc.gt7sync.environment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

@Component
public class TrackEnvironmentImportStore {
    private final Clock clock;
    private final Duration lifetime;
    private final int maximum;
    private final Map<UUID,Held> previews=new LinkedHashMap<>();
    @org.springframework.beans.factory.annotation.Autowired
    public TrackEnvironmentImportStore(Clock clock) { this(clock,Duration.ofMinutes(20),64); }
    TrackEnvironmentImportStore(Clock clock,Duration lifetime,int maximum) {
        if(lifetime.isNegative()||lifetime.isZero()||maximum<1)throw new IllegalArgumentException("Invalid preview limits");
        this.clock=clock;this.lifetime=lifetime;this.maximum=maximum;
    }
    public synchronized void capture(TrackEnvironmentImportService.Preview preview,String actor,String generation) {
        previews.values().removeIf(held -> !held.expires.isAfter(clock.instant()));
        while(previews.size()>=maximum)previews.remove(previews.keySet().iterator().next());
        previews.put(preview.id(),new Held(preview,actor,generation,clock.instant().plus(lifetime)));
    }
    public <T>T apply(UUID id,String actor,String generation,Function<TrackEnvironmentImportService.Preview,T> operation) {
        Held held; synchronized(this){held=previews.get(id);}
        if(held==null)throw expired();
        if(!held.lock.tryLock())throw new BusinessRuleException("This preview is busy; reload after the current import");
        try {
            synchronized(this){if(previews.get(id)!=held)throw expired();}
            if(!held.actor.equals(actor)||!held.generation.equals(generation)||!held.expires.isAfter(clock.instant()))throw expired();
            T result=operation.apply(held.preview);
            synchronized(this){previews.remove(id,held);}
            return result;
        } finally {held.lock.unlock();}
    }
    private static BusinessRuleException expired(){return new BusinessRuleException("Layout preview expired or changed; load and review it again");}
    private static final class Held {
        final TrackEnvironmentImportService.Preview preview;final String actor;final String generation;final Instant expires;
        final ReentrantLock lock=new ReentrantLock();
        Held(TrackEnvironmentImportService.Preview preview,String actor,String generation,Instant expires){this.preview=preview;this.actor=actor;this.generation=generation;this.expires=expires;}
    }
}
