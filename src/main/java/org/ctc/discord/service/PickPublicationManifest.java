package org.ctc.discord.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.ctc.discord.dto.NamedAttachment;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PickPublicationAttempt;

/** Describes the exact logical content and files of one physical webhook output. */
final class PickPublicationManifest {
    static final int MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024;
    private PickPublicationManifest() { }

    static String capture(String content, List<NamedAttachment> attachments, ObjectMapper mapper) {
        var root = mapper.createObjectNode().put("format", 1)
                .put("contentHash", PickPublicationAttempt.manifestHash(content == null ? "" : content));
        var files = root.putArray("attachments");
        for (var attachment : attachments) files.addObject().put("filename", attachment.filename())
                .put("bytes", attachment.bytes().length).put("sha256", hash(attachment.bytes()));
        String manifest = root.toString();
        read(manifest, mapper);
        return manifest;
    }

    static Contents read(String manifest, ObjectMapper mapper) {
        try {
            var root = mapper.readTree(manifest);
            if (root == null || !root.isObject() || root.size() != 3 || !root.path("format").isIntegralNumber()
                    || !root.path("format").canConvertToInt() || root.path("format").intValue() != 1 || !isHash(root.get("contentHash"))) throw invalid();
            var files = root.get("attachments");
            if (files == null || !files.isArray() || files.isEmpty() || files.size() > 10) throw invalid();
            var names = new HashSet<String>(); var parsed = new ArrayList<FileProof>();
            for (var file : files) {
                if (!file.isObject() || file.size() != 3 || !file.path("filename").isTextual()
                        || !file.path("filename").textValue().matches("(?:settings-race-|lobby-settings-race-|settings-md|lineups-md)(?:[1-9]|10)\\.png")
                        || !names.add(file.get("filename").textValue()) || !file.path("bytes").isIntegralNumber()
                        || !file.path("bytes").canConvertToInt() || file.path("bytes").intValue() < 1
                        || file.path("bytes").intValue() > MAX_ATTACHMENT_BYTES || !isHash(file.get("sha256"))) throw invalid();
                parsed.add(new FileProof(file.get("filename").textValue(), file.get("bytes").intValue(), file.get("sha256").textValue()));
            }
            return new Contents(root.get("contentHash").textValue(), parsed);
        } catch (JsonProcessingException | IllegalArgumentException malformed) { throw invalid(); }
    }

    static String hash(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("Required SHA-256 support is unavailable", impossible); }
    }
    private static boolean isHash(JsonNode value) { return value != null && value.isTextual() && value.textValue().matches("[0-9a-f]{64}"); }
    private static BusinessRuleException invalid() { return new BusinessRuleException("Captured publication evidence is unverifiable; retain the pending state for management review"); }
    record Contents(String contentHash, List<FileProof> attachments) { Contents { attachments = List.copyOf(attachments); } }
    record FileProof(String filename, int bytes, String sha256) { }
}
