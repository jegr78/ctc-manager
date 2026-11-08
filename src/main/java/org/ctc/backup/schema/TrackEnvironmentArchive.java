package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import org.ctc.domain.model.TrackEnvironmentValues;
import org.ctc.domain.model.TrackRainCapability;
import org.ctc.domain.model.Track;
import org.ctc.domain.model.TrackEnvironmentProvenance;

public final class TrackEnvironmentArchive {
    private TrackEnvironmentArchive() { }
    public static JsonNode normalize(int version, String table, JsonNode row) {
        if (!"tracks".equals(table)) return row;
        if (!(row instanceof ObjectNode object)) throw new IllegalArgumentException("Track row must be an object");
        var copy = object.deepCopy();
        if (version < 19) {
            var times = copy.get("timeOfDayOptions"); var rain = copy.get("rainCapability"); var token = copy.get("environmentVersion");
            if (times != null && !times.isNull() && (!times.isArray() || !times.isEmpty())
                    || rain != null && !rain.isNull() && (!rain.isTextual() || !rain.textValue().equals("UNKNOWN"))
                    || token != null && !token.isNull() && (!token.isIntegralNumber() || !token.canConvertToLong() || token.longValue() != 0)) {
                throw new IllegalArgumentException("Track environment data requires backup schema 19");
            }
            copy.putArray("timeOfDayOptions"); copy.put("rainCapability", "UNKNOWN"); copy.put("environmentVersion", 0L);
        }
        var times = copy.path("timeOfDayOptions"); var rain = copy.path("rainCapability"); var token = copy.path("environmentVersion");
        if (!times.isArray() || times.size() > 25 || !rain.isTextual() || !token.isIntegralNumber()
                || !token.canConvertToLong() || token.longValue() < 0) throw new IllegalArgumentException("Invalid track environment fields");
        var labels = new ArrayList<String>();
        for (var time : times) {
            if (!time.isTextual()) throw new IllegalArgumentException("Invalid track time of day label");
            labels.add(time.textValue());
        }
        try {
            new TrackEnvironmentValues(labels, TrackRainCapability.valueOf(rain.textValue()));
            boolean manual=token.longValue()>0 || !labels.isEmpty() || !rain.textValue().equals("UNKNOWN");
            if(version<20) {
                if(copy.hasNonNull("environmentSource") || copy.hasNonNull("environmentImportHash")
                        || copy.hasNonNull("environmentOverride") && (!copy.path("environmentOverride").isBoolean() || copy.path("environmentOverride").booleanValue()!=manual))
                    throw new IllegalArgumentException("Track environment source data requires backup schema 20");
                copy.putNull("environmentSource");copy.putNull("environmentImportHash");copy.put("environmentOverride",manual);
            }
            if(!copy.path("environmentOverride").isBoolean())throw new IllegalArgumentException("Invalid manual layout override");
            for(String field:java.util.List.of("environmentSource","environmentImportHash"))
                if(copy.hasNonNull(field) && !copy.path(field).isTextual())throw new IllegalArgumentException("Invalid layout source field");
            var track=new Track();track.setTimeOfDayOptions(labels);track.setRainCapability(rain.textValue());track.setEnvironmentVersion(token.longValue());
            track.setEnvironmentSource(copy.hasNonNull("environmentSource")?copy.path("environmentSource").textValue():null);
            track.setEnvironmentImportHash(copy.hasNonNull("environmentImportHash")?copy.path("environmentImportHash").textValue():null);
            track.setEnvironmentOverride(copy.path("environmentOverride").booleanValue());
            TrackEnvironmentProvenance.requireConsistent(track);
        }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid track environment metadata", invalid); }
        return copy;
    }
}
