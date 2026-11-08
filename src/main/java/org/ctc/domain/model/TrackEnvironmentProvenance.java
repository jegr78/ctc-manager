package org.ctc.domain.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.ctc.domain.exception.BusinessRuleException;

public record TrackEnvironmentProvenance(String profile,String sourceEditedAt,String retrievedAt,String sourceHash) {
    public static final String SOURCE_URL="https://www.gtplanet.net/forum/threads/time-and-weather-settings-for-all-tracks-and-layouts-in-gran-turismo-7.420661/";
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public TrackEnvironmentProvenance {
        if(profile==null || profile.isBlank() || !profile.equals(profile.strip()) || profile.length()>300 || profile.chars().anyMatch(Character::isISOControl)
                || sourceEditedAt==null || retrievedAt==null || sourceHash==null || !sourceHash.matches("[a-f0-9]{64}")) throw invalid();
        try { Instant.parse(sourceEditedAt);Instant.parse(retrievedAt); } catch (java.time.format.DateTimeParseException bad) { throw invalid(); }
    }
    public String encode() {
        try { return JSON.writeValueAsString(this); } catch(IOException failure) { throw new IllegalStateException("Cannot encode layout source",failure); }
    }
    public static TrackEnvironmentProvenance decode(String text) {
        if(text==null)return null;
        if(text.length()>1500)throw invalid();
        try {
            var node=JSON.readTree(text);
            if(node==null || !node.isObject() || node.size()!=4
                    || !node.path("profile").isTextual() || !node.path("sourceEditedAt").isTextual()
                    || !node.path("retrievedAt").isTextual() || !node.path("sourceHash").isTextual())throw invalid();
            return JSON.treeToValue(node,TrackEnvironmentProvenance.class);
        }
        catch(IOException | IllegalArgumentException bad) { throw invalid(); }
    }
    public static String valuesHash(TrackEnvironmentValues values) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(List.of(values.timeOfDayOptions(),values.rainCapability().name())))); }
        catch(IOException | NoSuchAlgorithmException impossible) { throw new IllegalStateException("Cannot fingerprint layout values",impossible); }
    }
    public static void requireConsistent(Track track) {
        if(track.getEnvironmentVersion()<0)throw invalid();
        var source=decode(track.getEnvironmentSource());String hash=track.getEnvironmentImportHash();
        if(source==null ? hash!=null : hash==null || !hash.matches("[a-f0-9]{64}"))throw invalid();
        if(source!=null && !track.isEnvironmentOverride() && !MessageDigest.isEqual(valuesHash(TrackEnvironmentValues.from(track)).getBytes(StandardCharsets.US_ASCII),hash.getBytes(StandardCharsets.US_ASCII)))throw invalid();
    }
    private static BusinessRuleException invalid() { return new BusinessRuleException("Track environment source is invalid; review this layout"); }
}
