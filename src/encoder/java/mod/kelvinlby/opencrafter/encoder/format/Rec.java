package mod.kelvinlby.opencrafter.encoder.format;

/**
 * One record of a recording: a packet (varint id + body, server protocol) or a gap marker.
 *
 * @param flags      direction, phase and gap bits as stored
 * @param timeMicros session-clock time of the record
 * @param data       packet bytes, or for a gap {@code varint droppedCount + varlong droppedBytes}
 */
public record Rec(int flags, long timeMicros, byte[] data) {
	public static Rec packet(boolean c2s, Phase phase, long timeMicros, byte[] data) {
		int flags = (c2s ? McrecFormat.FLAG_C2S : 0) | phase.ordinal() << McrecFormat.PHASE_SHIFT;
		return new Rec(flags, timeMicros, data);
	}

	public boolean c2s() {
		return (flags & McrecFormat.FLAG_C2S) != 0;
	}

	public Phase phase() {
		return Phase.byId((flags & McrecFormat.PHASE_MASK) >>> McrecFormat.PHASE_SHIFT);
	}

	public boolean gap() {
		return (flags & McrecFormat.FLAG_GAP) != 0;
	}

	/** The packet id (leading varint); meaningless for a gap record. */
	public int packetId() {
		return VarInts.peekVarInt(data, 0);
	}

	public Rec withData(byte[] newData) {
		return new Rec(flags, timeMicros, newData);
	}
}
