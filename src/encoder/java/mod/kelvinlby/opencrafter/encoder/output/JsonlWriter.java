package mod.kelvinlby.opencrafter.encoder.output;

import com.github.luben.zstd.ZstdOutputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/** One JSON object per line, zstd-compressed ({@code .jsonl.zst}); readable with {@code zstdcat}. */
public final class JsonlWriter implements AutoCloseable {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeSpecialFloatingPointValues().create();

	private final Path target;
	private final Path temp;
	private final BufferedWriter out;
	private long lines;

	public JsonlWriter(Path target, int zstdLevel) throws IOException {
		this.target = target;
		this.temp = target.resolveSibling(target.getFileName() + ".tmp");
		ZstdOutputStream zstd = new ZstdOutputStream(Files.newOutputStream(temp), zstdLevel);
		this.out = new BufferedWriter(new OutputStreamWriter(zstd, StandardCharsets.UTF_8), 1 << 16);
	}

	public void write(Map<String, ?> object) throws IOException {
		GSON.toJson(object, out);
		out.write('\n');
		lines++;
	}

	public long lines() {
		return lines;
	}

	@Override
	public void close() throws IOException {
		out.close();
		Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
