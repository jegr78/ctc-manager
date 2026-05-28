package org.ctc.discord;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.discord.util.BucketState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DiscordRateLimitInterceptor implements ClientHttpRequestInterceptor {

	private static final int MAX_429_RETRIES = 3;
	private static final long[] DEFAULT_FIVE_XX_BACKOFF_MS = {200L, 1000L, 5000L};

	private final Clock clock;
	private final long jitterMinMs;
	private final long jitterMaxMs;
	private final long[] fiveXxBackoffMs;
	private final ConcurrentMap<String, BucketState> buckets = new ConcurrentHashMap<>();
	private final Random random = new Random();

	public DiscordRateLimitInterceptor(
			Clock clock,
			@Value("${app.discord.rate-limit.jitter-ms:100-500}") String jitterRange,
			@Value("${app.discord.rate-limit.fivexx-backoff-ms:}") String fiveXxBackoffCsv) {
		this.clock = clock;
		long[] jitter = parseJitter(jitterRange);
		this.jitterMinMs = jitter[0];
		this.jitterMaxMs = jitter[1];
		this.fiveXxBackoffMs = parseBackoff(fiveXxBackoffCsv);
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		int four29Attempts = 0;
		int fiveXxAttempts = 0;
		while (true) {
			ClientHttpResponse response = execution.execute(request, body);
			int status = response.getStatusCode().value();
			updateBucket(response.getHeaders());
			if (status == 429) {
				if (four29Attempts >= MAX_429_RETRIES) {
					response.close();
					throw new DiscordTransientException(
							DiscordApiExceptionMapper.TRANSIENT_MESSAGE,
							new IOException("Rate-limit exhausted after " + MAX_429_RETRIES + " retries"));
				}
				long sleepMs = parseRetryAfterMs(response.getHeaders()) + jitterMs();
				response.close();
				sleep(sleepMs);
				four29Attempts++;
				continue;
			}
			if (status >= 500 && status < 600) {
				if (fiveXxAttempts >= fiveXxBackoffMs.length) {
					response.close();
					throw new DiscordTransientException(
							DiscordApiExceptionMapper.TRANSIENT_MESSAGE,
							new IOException("5xx exhausted after " + fiveXxBackoffMs.length + " retries"));
				}
				long sleepMs = fiveXxBackoffMs[fiveXxAttempts];
				response.close();
				sleep(sleepMs);
				fiveXxAttempts++;
				continue;
			}
			return response;
		}
	}

	public BucketState bucketState(String bucketName) {
		return buckets.get(bucketName);
	}

	private void updateBucket(HttpHeaders headers) {
		String bucket = headers.getFirst("X-RateLimit-Bucket");
		if (bucket == null || bucket.isBlank()) {
			return;
		}
		int remaining = parseIntSafe(headers.getFirst("X-RateLimit-Remaining"), 0);
		double resetAfter = parseDoubleSafe(headers.getFirst("X-RateLimit-Reset-After"), 0.0);
		long resetAtMillis = clock.instant().toEpochMilli() + Math.round(resetAfter * 1000.0);
		buckets.put(bucket, new BucketState(remaining, Instant.ofEpochMilli(resetAtMillis)));
	}

	private static int parseIntSafe(String value, int defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException _) {
			return defaultValue;
		}
	}

	private static double parseDoubleSafe(String value, double defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Double.parseDouble(value.trim());
		} catch (NumberFormatException _) {
			return defaultValue;
		}
	}

	private long parseRetryAfterMs(HttpHeaders headers) {
		String retryAfter = headers.getFirst("Retry-After");
		if (retryAfter == null || retryAfter.isBlank()) {
			return 0L;
		}
		try {
			double seconds = Double.parseDouble(retryAfter);
			return Math.max(0L, Math.round(seconds * 1000.0));
		} catch (NumberFormatException _) {
			return 0L;
		}
	}

	private long jitterMs() {
		if (jitterMaxMs <= jitterMinMs) {
			return jitterMinMs;
		}
		return jitterMinMs + (long) (random.nextDouble() * (jitterMaxMs - jitterMinMs));
	}

	private void sleep(long ms) throws IOException {
		if (ms <= 0L) {
			return;
		}
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while sleeping for rate-limit backoff", e);
		}
	}

	private static long[] parseJitter(String range) {
		if (range == null || range.isBlank()) {
			return new long[] {100L, 500L};
		}
		String trimmed = range.trim();
		if (trimmed.contains("-")) {
			String[] parts = trimmed.split("-", 2);
			long lo = Long.parseLong(parts[0].trim());
			long hi = Long.parseLong(parts[1].trim());
			return new long[] {lo, hi};
		}
		long single = Long.parseLong(trimmed);
		return new long[] {single, single};
	}

	private static long[] parseBackoff(String csv) {
		if (csv == null || csv.isBlank()) {
			return DEFAULT_FIVE_XX_BACKOFF_MS;
		}
		String[] parts = csv.split(",");
		long[] out = new long[parts.length];
		for (int i = 0; i < parts.length; i++) {
			out[i] = Long.parseLong(parts[i].trim());
		}
		return out;
	}
}
