package org.ctc.discord.util;

import java.time.Instant;

public record BucketState(int remaining, Instant resetAt) {
}
