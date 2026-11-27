package mod.kelvinlby.opencrafter.encoder.client;

import mod.kelvinlby.opencrafter.encoder.format.Rec;

import java.util.List;

/**
 * Everything the recorded client sent during one of its ticks, in order, up to and including its
 * {@code ClientTickEnd}, still undecoded.
 *
 * @param micros when the tick ran (see {@link TickSchedule})
 */
record TickGroup(long micros, List<Rec> records) {
}
