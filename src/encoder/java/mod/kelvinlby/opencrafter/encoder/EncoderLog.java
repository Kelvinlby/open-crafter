package mod.kelvinlby.opencrafter.encoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** The encoder's logger, with per-topic throttling for things that can repeat for every packet. */
public final class EncoderLog {
	public static final Logger LOGGER = LoggerFactory.getLogger("open-crafter-encoder");
	private static final int FULL_LOGS_PER_TOPIC = 5;
	private static final Map<String, AtomicLong> COUNTS = new ConcurrentHashMap<>();

	private EncoderLog() {
	}

	/** Logs the first few occurrences of a topic in full, then every 1000th as a count. */
	public static void warnThrottled(String topic, String message, Throwable cause) {
		long n = COUNTS.computeIfAbsent(topic, t -> new AtomicLong()).incrementAndGet();
		if (n <= FULL_LOGS_PER_TOPIC) {
			LOGGER.warn("{} (#{})", message, n, cause);
		} else if (n % 1000 == 0) {
			LOGGER.warn("{} (#{}, last: {})", message, n, cause.toString());
		}
	}
}
