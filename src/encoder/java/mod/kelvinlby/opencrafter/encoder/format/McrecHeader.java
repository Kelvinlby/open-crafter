package mod.kelvinlby.opencrafter.encoder.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;

/**
 * A segment's JSON header. Kept as the raw JSON object so fields written by any recorder
 * version survive a rewrite; typed accessors cover what the encoder needs.
 */
public final class McrecHeader {
	private final JsonObject json;

	public McrecHeader(JsonObject json) {
		this.json = json;
	}

	public static McrecHeader parse(byte[] utf8) {
		return new McrecHeader(JsonParser.parseString(new String(utf8, StandardCharsets.UTF_8)).getAsJsonObject());
	}

	public byte[] toBytes() {
		return json.toString().getBytes(StandardCharsets.UTF_8);
	}

	public McrecHeader copy() {
		return new McrecHeader(json.deepCopy());
	}

	public JsonObject json() {
		return json;
	}

	public int formatVersion() {
		return intOr("formatVersion", McrecFormat.FORMAT_VERSION);
	}

	/** Protocol number of the stored packet bytes. */
	public int protocolVersion() {
		return intOr("serverProtocolVersion", intOr("protocolVersion", -1));
	}

	public String minecraftVersion() {
		return stringOr("minecraftVersion", "unknown");
	}

	public String playerName() {
		return stringOr("playerName", "unknown");
	}

	public String playerUuid() {
		return stringOr("playerUuid", "");
	}

	public long sessionStartEpochMillis() {
		return longOr("sessionStartEpochMillis", 0L);
	}

	public int codecId() {
		return intOr("codecId", McrecFormat.CODEC_NONE);
	}

	/** Leading records copied from the connection's bootstrap (26.3 recorder only; 0 otherwise). */
	public int bootstrapRecords() {
		return intOr("bootstrapRecords", 0);
	}

	public String startReason() {
		return stringOr("startReason", "JOIN");
	}

	private int intOr(String key, int fallback) {
		JsonElement e = json.get(key);
		return e == null || e.isJsonNull() ? fallback : e.getAsInt();
	}

	private long longOr(String key, long fallback) {
		JsonElement e = json.get(key);
		return e == null || e.isJsonNull() ? fallback : e.getAsLong();
	}

	private String stringOr(String key, String fallback) {
		JsonElement e = json.get(key);
		return e == null || e.isJsonNull() ? fallback : e.getAsString();
	}
}
