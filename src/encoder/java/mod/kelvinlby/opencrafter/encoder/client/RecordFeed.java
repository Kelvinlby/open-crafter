package mod.kelvinlby.opencrafter.encoder.client;

import mod.kelvinlby.opencrafter.encoder.format.Phase;
import mod.kelvinlby.opencrafter.encoder.format.Rec;
import mod.kelvinlby.opencrafter.encoder.format.RecordingReader;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The recording as the replay consumes it, on the recorded client's clock:
 * <ul>
 *   <li>S2C packets come due when the client received them: server send time plus the one-way delay;</li>
 *   <li>the client's C2S play packets are grouped into its ticks, each due when that tick ran
 *   ({@link TickSchedule}), which is earlier than the server received it, so the feed reads ahead.</li>
 * </ul>
 * C2S records stay raw until applied, when the client has the registries to decode them.
 */
final class RecordFeed implements AutoCloseable {
	/** How far past the replay time records are read (covers the schedule's look-back on C2S). */
	private static final long READ_AHEAD_MICROS = 3_000_000;

	private final RecordingReader reader;
	private final TickSchedule schedule;
	private final int tickEndId;
	private final int playerLoadedId;
	private final long s2cDelayMicros;
	private final ArrayDeque<Rec> s2c = new ArrayDeque<>();
	private final ArrayDeque<TickGroup> ticks = new ArrayDeque<>();
	private List<Rec> building = new ArrayList<>();
	private @Nullable Rec next;
	private int tickIndex;
	private long loadedMicros = -1;
	private long gaps;

	RecordFeed(RecordingReader reader, TickSchedule schedule, int tickEndId, int playerLoadedId, long s2cDelayMicros) throws IOException {
		this.reader = reader;
		this.schedule = schedule;
		this.tickEndId = tickEndId;
		this.playerLoadedId = playerLoadedId;
		this.s2cDelayMicros = s2cDelayMicros;
		this.next = reader.next();
	}

	/** Nothing left to read, deliver or apply. */
	boolean exhausted() {
		return next == null && s2c.isEmpty() && ticks.isEmpty();
	}

	/** When the recorded client finished loading the world, -1 if not read yet. */
	long loadedMicros() {
		return loadedMicros;
	}

	/** Reads records up to {@code micros} plus the read-ahead. */
	void readAhead(long micros) throws IOException {
		while (next != null && next.timeMicros() <= micros + READ_AHEAD_MICROS) {
			take();
		}
	}

	/** Reads records until the recorded client reported its world loaded (or the recording ends). */
	void readUntilLoaded() throws IOException {
		while (next != null && loadedMicros < 0) {
			take();
		}
	}

	/** Hands every S2C packet the client had received by {@code micros} to {@code sink}, in order. */
	void deliverS2C(long micros, Consumer<Rec> sink) {
		while (!s2c.isEmpty() && s2c.peekFirst().timeMicros() + s2cDelayMicros <= micros) {
			sink.accept(s2c.pollFirst());
		}
	}

	/** Hands over all S2C packets read so far, regardless of time (while loading). */
	void deliverAllS2C(Consumer<Rec> sink) {
		while (!s2c.isEmpty()) {
			sink.accept(s2c.pollFirst());
		}
	}

	/** Recorded ticks that ran no later than {@code micros}, oldest first. */
	List<TickGroup> takeTicksUntil(long micros) {
		List<TickGroup> due = new ArrayList<>();
		while (!ticks.isEmpty() && ticks.peekFirst().micros() <= micros) {
			due.add(ticks.pollFirst());
		}
		return due;
	}

	/** Forgets recorded ticks from before the world was loaded. */
	void dropTicksUntil(long micros) {
		while (!ticks.isEmpty() && ticks.peekFirst().micros() <= micros) {
			ticks.pollFirst();
		}
	}

	long gaps() {
		return gaps;
	}

	private void take() throws IOException {
		Rec rec = next;
		next = reader.next();
		if (rec.gap()) {
			gaps++;
		} else if (!rec.c2s()) {
			s2c.addLast(rec);
		} else if (rec.phase() == Phase.PLAY) {
			collect(rec);
		}
	}

	private void collect(Rec rec) {
		int id = rec.packetId();
		building.add(rec);
		if (id == playerLoadedId && loadedMicros < 0) {
			loadedMicros = rec.timeMicros();
		}
		if (id == tickEndId) {
			ticks.addLast(new TickGroup(schedule.micros(tickIndex++), List.copyOf(building)));
			building = new ArrayList<>();
		}
	}

	@Override
	public void close() throws IOException {
		reader.close();
	}
}
