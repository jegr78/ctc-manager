package org.ctc.gt7sync.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.PickSessionEpoch;
import org.ctc.domain.service.PickWriteExecutor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class TrackEnvironmentImportService {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final TrackRepository tracks;
    private final EntityManager entities;
    private final TrackEnvironmentCatalog catalog;
    private final GtPlanetEnvironmentSource source;
    private final TrackEnvironmentImportStore previews;
    private final PickSessionEpoch epoch;
    private final PickWriteExecutor writes;
    private final Environment environment;

    public Preview bundled(Principal principal)throws IOException {
        String actor=actor(principal),generation=epoch.managementGeneration();
        return prepare(GtPlanetEnvironmentSource.bundled(),"Reviewed bundled reference",actor,generation);
    }
    public Preview refresh(Principal principal)throws IOException {
        String actor=actor(principal),generation=epoch.managementGeneration();
        return prepare(source.fetch(),"Fetched GTPlanet reference",actor,generation);
    }
    Preview prepare(GtPlanetEnvironmentSource.Snapshot snapshot,String label,String actor,String generation) {
        var layouts=tracks.findAllByOrderByNameAsc();
        var names=new HashMap<String,Integer>();layouts.forEach(t -> names.merge(t.getName().strip(),1,Integer::sum));
        var entries=layouts.stream().map(t -> entry(t,snapshot,names.get(t.getName().strip())>1)).toList();
        var preview=new Preview(UUID.randomUUID(),label,snapshot,entries);
        previews.capture(preview,actor,generation);return preview;
    }
    public int apply(UUID previewId,List<UUID> selected,Principal principal) {
        String actor=actor(principal),generation=epoch.managementGeneration();
        if(selected==null||selected.isEmpty()||selected.size()>256||selected.stream().anyMatch(Objects::isNull)||new HashSet<>(selected).size()!=selected.size())throw new BusinessRuleException("Select distinct eligible layout updates");
        return previews.apply(previewId,actor,generation,preview -> {
            var offered=new HashMap<UUID,Entry>();preview.entries().stream().filter(Entry::selectable).forEach(e -> offered.put(e.id(),e));
            if(!offered.keySet().containsAll(selected))throw new BusinessRuleException("Only eligible updates from this preview may be imported");
            return writes.execute(() -> {
                if(!generation.equals(epoch.managementGeneration()))throw new BusinessRuleException("Reload the layout preview after a restart or restore");
                var locked=new LinkedHashMap<UUID,Track>();
                for(var id:selected.stream().sorted().toList()) {
                    var track=tracks.findByIdForEnvironmentUpdate(id).orElseThrow(() -> new BusinessRuleException("A previewed layout was removed; reload the preview"));
                    entities.refresh(track,LockModeType.PESSIMISTIC_WRITE);
                    if(!fingerprint(track).equals(offered.get(id).fingerprint()))throw new BusinessRuleException("Layout settings changed; reload and review the preview");
                    var current=entry(track,preview.source(),false);
                    if(!current.selectable()||!current.proposed().equals(offered.get(id).proposed()))throw new BusinessRuleException("Layout source or manual settings changed; reload the preview");
                    locked.put(id,track);
                }
                for(var row:locked.entrySet()) {
                    var track=row.getValue();var proposed=offered.get(row.getKey()).proposed();var values=proposed.values();
                    var provenance=new TrackEnvironmentProvenance(proposed.key(),preview.source().sourceEditedAt(),preview.source().retrievedAt(),preview.source().sourceHash());
                    track.setTimeOfDayOptions(values.timeOfDayOptions());track.setRainCapability(values.rainCapability().name());
                    track.setEnvironmentSource(provenance.encode());track.setEnvironmentImportHash(TrackEnvironmentProvenance.valuesHash(values));
                    track.setEnvironmentOverride(false);track.setEnvironmentVersion(track.getEnvironmentVersion()+1);
                }
                return locked.size();
            });
        });
    }
    private Entry entry(Track track,GtPlanetEnvironmentSource.Snapshot snapshot,boolean ambiguous) {
        var proposed=catalog.find(track.getName(),snapshot);
        try {
            var values=TrackEnvironmentValues.from(track);TrackEnvironmentProvenance.requireConsistent(track);
            var prior=TrackEnvironmentProvenance.decode(track.getEnvironmentSource());
            Status status;
            if(ambiguous)status=Status.AMBIGUOUS;
            else if(proposed==null)status=Status.UNMAPPED;
            else if(track.getEnvironmentVersion()==Long.MAX_VALUE)status=Status.EXHAUSTED;
            else if(track.isEnvironmentOverride()||prior==null&&(track.getEnvironmentVersion()>0||!values.timeOfDayOptions().isEmpty()||values.rainCapability()!=TrackRainCapability.UNKNOWN))status=Status.MANUAL;
            else if(prior!=null&&values.equals(proposed.values())&&prior.profile().equals(proposed.key())&&prior.sourceHash().equals(snapshot.sourceHash()))status=Status.CURRENT;
            else status=prior==null?Status.IMPORT:Status.UPDATE;
            return new Entry(track.getId(),track.getName(),values,proposed,status,fingerprint(track));
        } catch(BusinessRuleException invalid) {return new Entry(track.getId(),track.getName(),null,proposed,Status.INVALID,"");}
    }
    static String fingerprint(Track track) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(Arrays.asList(track.getId(),track.getName(),track.getTimeOfDayOptions(),track.getRainCapability(),track.getEnvironmentVersion(),track.getEnvironmentSource(),track.getEnvironmentImportHash(),track.isEnvironmentOverride()))));
        } catch(IOException|NoSuchAlgorithmException impossible){throw new IllegalStateException("Cannot fingerprint a layout preview",impossible);}
    }
    private String actor(Principal principal) {
        String name=principal==null?null:principal.getName();
        if(name!=null&&!name.isBlank()&&name.length()<=128)return name;
        if(principal==null&&environment.matchesProfiles("dev","local"))return "development-manager";
        throw new BusinessRuleException("An authenticated management actor is required");
    }
    public enum Status {
        IMPORT("Import available"),UPDATE("Source update"),CURRENT("Already current"),MANUAL("Manual settings protected"),UNMAPPED("No reviewed source mapping"),AMBIGUOUS("Ambiguous layout name"),INVALID("Invalid saved metadata; review layout"),EXHAUSTED("Layout version limit reached");
        private final String label;Status(String label){this.label=label;}public String getLabel(){return label;}
    }
    public record Entry(UUID id,String name,TrackEnvironmentValues current,GtPlanetEnvironmentSource.Profile proposed,Status status,String fingerprint) {
        public boolean selectable(){return status==Status.IMPORT||status==Status.UPDATE;}
    }
    public record Preview(UUID id,String label,GtPlanetEnvironmentSource.Snapshot source,List<Entry> entries) {
        public Preview{entries=List.copyOf(entries);}
        public long readyCount(){return entries.stream().filter(Entry::selectable).count();}
        public long manualCount(){return entries.stream().filter(e -> e.status()==Status.MANUAL).count();}
        public long unmappedCount(){return entries.stream().filter(e -> e.status()==Status.UNMAPPED||e.status()==Status.AMBIGUOUS).count();}
    }
}
