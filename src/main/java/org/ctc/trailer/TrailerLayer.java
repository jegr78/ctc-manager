package org.ctc.trailer;

import java.nio.file.Path;
import java.time.Duration;

public record TrailerLayer(String scene, int sceneIndex, String name, Path png, Duration sceneLength) {
}
