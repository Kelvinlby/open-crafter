package mod.kelvinlby.opencrafter.api;

import java.util.List;
import java.util.Map;

/** Typed, validating view over a request's argument map. */
public final class Args {
	private final Map<String, Object> values;

	public Args(Map<String, Object> values) {
		this.values = values == null ? Map.of() : values;
	}

	public Map<String, Object> raw() {
		return values;
	}

	public boolean has(String key) {
		return values.get(key) != null;
	}

	public Object require(String key) {
		Object value = values.get(key);
		if (value == null) {
			throw new IllegalArgumentException("Missing argument: " + key);
		}
		return value;
	}

	public String string(String key) {
		return cast(key, require(key), String.class);
	}

	public String string(String key, String fallback) {
		return has(key) ? string(key) : fallback;
	}

	public double number(String key) {
		return cast(key, require(key), Number.class).doubleValue();
	}

	public double number(String key, double fallback) {
		return has(key) ? number(key) : fallback;
	}

	public int integer(String key) {
		return cast(key, require(key), Number.class).intValue();
	}

	public int integer(String key, int fallback) {
		return has(key) ? integer(key) : fallback;
	}

	public long longValue(String key, long fallback) {
		return has(key) ? cast(key, require(key), Number.class).longValue() : fallback;
	}

	public boolean bool(String key) {
		return cast(key, require(key), Boolean.class);
	}

	public boolean bool(String key, boolean fallback) {
		return has(key) ? bool(key) : fallback;
	}

	public List<?> list(String key) {
		return cast(key, require(key), List.class);
	}

	@SuppressWarnings("unchecked")
	public Args map(String key) {
		return new Args(cast(key, require(key), Map.class));
	}

	private static <T> T cast(String key, Object value, Class<T> type) {
		if (!type.isInstance(value)) {
			throw new IllegalArgumentException("Argument '" + key + "' must be " + type.getSimpleName());
		}
		return type.cast(value);
	}
}
