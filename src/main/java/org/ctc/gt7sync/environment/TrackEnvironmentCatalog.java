package org.ctc.gt7sync.environment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class TrackEnvironmentCatalog {
    private final Map<String,String> aliases;
    public TrackEnvironmentCatalog() throws IOException { this(load()); }
    TrackEnvironmentCatalog(List<Mapping> mappings) {
        if (mappings.isEmpty() || mappings.size()>256) throw new IllegalArgumentException("Invalid reviewed layout mappings");
        var result=new LinkedHashMap<String,String>();
        for (var mapping:mappings) {
            if (mapping.trackName()==null || mapping.trackName().isBlank() || mapping.trackName().length()>300
                    || mapping.profileKey()==null || mapping.profileKey().isBlank() || mapping.profileKey().length()>300
                    || mapping.trackName().chars().anyMatch(Character::isISOControl) || mapping.profileKey().chars().anyMatch(Character::isISOControl)
                    || result.putIfAbsent(mapping.trackName().strip(),mapping.profileKey())!=null) throw new IllegalArgumentException("Ambiguous or invalid reviewed layout alias");
        }
        aliases=Map.copyOf(result);
    }
    private static List<Mapping> load() throws IOException {
        try (var input=new ClassPathResource("gt7/track-environment-mappings.json").getInputStream()) {
            byte[] bytes=input.readNBytes(131_072); if(bytes.length>=131_072) throw new IOException("Layout mapping exceeds its size limit");
            return new ObjectMapper().readValue(bytes,new TypeReference<>(){});
        }
    }
    public GtPlanetEnvironmentSource.Profile find(String name,GtPlanetEnvironmentSource.Snapshot snapshot) {
        String key=aliases.get(name.strip());
        return key==null?null:snapshot.profiles().stream().filter(p -> p.key().equals(key)).findFirst().orElse(null);
    }
    public Map<String,String> mappings() { return aliases; }
    public record Mapping(String trackName,String profileKey) { }
}
