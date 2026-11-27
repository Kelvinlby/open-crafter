package mod.kelvinlby.opencrafter.encoder.translate;

import com.google.gson.JsonObject;
import mod.kelvinlby.opencrafter.encoder.format.McrecHeader;
import mod.kelvinlby.opencrafter.encoder.format.Phase;
import mod.kelvinlby.opencrafter.encoder.format.Rec;
import mod.kelvinlby.opencrafter.encoder.format.Recording;
import mod.kelvinlby.opencrafter.encoder.format.RecordingReader;
import mod.kelvinlby.opencrafter.encoder.format.SegmentWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites a recording made with another protocol into the native one, through Via, so the replay
 * client only ever decodes its own protocol. Works for any pair Via bridges: older recordings go
 * forward through ViaVersion, newer ones backward through ViaBackwards.
 *
 * <p>Two translating connections run side by side, each fed both directions so its trackers see
 * the whole conversation: one is the native client in front of the recorded server (translates
 * S2C), the other is the native server behind the recorded client (translates C2S).
 */
public final class ProtocolNormalizer {
	/** At most this share of packets may fail to translate before a recording is rejected. */
	private static final double MAX_FAILURE_SHARE = 0.001;
	private static final int MAX_LOGGED_FAILURES = 20;

	private ProtocolNormalizer() {
	}

	public record Result(long packetsIn, long packetsOut, long failed, List<String> warnings) {
	}

	/** True when this recording's packets are not in the native protocol and need {@link #normalize}. */
	public static boolean needsTranslation(McrecHeader header, int nativeProtocol) {
		return header.protocolVersion() != nativeProtocol;
	}

	public static Result normalize(Recording recording, Path target, int nativeProtocol, String nativeVersionName) throws IOException {
		try (RecordingReader reader = recording.reader()) {
			McrecHeader header = reader.header();
			int recorded = header.protocolVersion();
			if (!ViaRuntime.canBridge(recorded, nativeProtocol)) {
				throw new IOException("Via cannot translate protocol " + recorded + " (" + header.minecraftVersion() + ") to " + nativeProtocol);
			}
			TranslatingConnection s2c = new TranslatingConnection(true, nativeProtocol, recorded);
			TranslatingConnection c2s = new TranslatingConnection(false, recorded, nativeProtocol);
			List<String> failures = new ArrayList<>();
			long in = 0;
			long out = 0;
			long failed = 0;
			try (SegmentWriter writer = new SegmentWriter(target, nativeHeader(header, nativeProtocol, nativeVersionName))) {
				for (Rec rec = reader.next(); rec != null; rec = reader.next()) {
					if (rec.gap()) {
						writer.write(rec);
						continue;
					}
					if (rec.phase() == Phase.HANDSHAKING || rec.phase() == Phase.STATUS) {
						continue;
					}
					in++;
					try {
						out += translate(rec, s2c, c2s, writer);
					} catch (TranslationException e) {
						failed++;
						if (failed <= MAX_LOGGED_FAILURES) {
							failures.add("t=" + rec.timeMicros() + "us " + e.getMessage() + ": " + rootMessage(e));
						}
					}
				}
			}
			List<String> warnings = new ArrayList<>(reader.warnings());
			warnings.addAll(failures);
			if (failed > Math.max(10, in * MAX_FAILURE_SHARE)) {
				throw new IOException("Too many untranslatable packets (" + failed + " of " + in + "): " + String.join("; ", warnings));
			}
			return new Result(in, out, failed, warnings);
		}
	}

	/** Translates one record, writes what comes out, and keeps the other connection's trackers informed. */
	private static int translate(Rec rec, TranslatingConnection s2c, TranslatingConnection c2s, SegmentWriter writer) throws IOException {
		List<byte[]> packets = rec.c2s() ? c2s.serverbound(rec.phase(), rec.data()) : s2c.clientbound(rec.phase(), rec.data());
		for (byte[] packet : packets) {
			writer.write(rec.withData(packet));
			if (rec.c2s()) {
				s2c.serverbound(rec.phase(), packet);
			} else {
				c2s.clientbound(rec.phase(), packet);
			}
		}
		return packets.size();
	}

	private static McrecHeader nativeHeader(McrecHeader original, int nativeProtocol, String nativeVersionName) {
		McrecHeader header = original.copy();
		JsonObject json = header.json();
		JsonObject from = new JsonObject();
		from.addProperty("protocolVersion", original.protocolVersion());
		from.addProperty("minecraftVersion", original.minecraftVersion());
		json.add("translatedFrom", from);
		json.addProperty("protocolVersion", nativeProtocol);
		json.addProperty("serverProtocolVersion", nativeProtocol);
		json.addProperty("minecraftVersion", nativeVersionName);
		json.remove("serverPacketVersion");
		// The id table names the recorded protocol's packets; it no longer matches the bytes.
		json.remove("protocolTable");
		return header;
	}

	private static String rootMessage(Throwable e) {
		Throwable root = e;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
	}
}
