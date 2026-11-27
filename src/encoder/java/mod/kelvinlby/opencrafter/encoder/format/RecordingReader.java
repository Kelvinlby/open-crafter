package mod.kelvinlby.opencrafter.encoder.format;

import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a whole recording as one record stream across its segments. The session clock runs on
 * across segments, so the times simply keep increasing.
 */
public final class RecordingReader implements Closeable {
	private final List<Recording.SegmentSource> segments;
	private final List<String> warnings = new ArrayList<>();
	private final McrecHeader header;
	private int nextSegment;
	private @Nullable SegmentReader current;

	RecordingReader(List<Recording.SegmentSource> segments) throws IOException {
		if (segments.isEmpty()) {
			throw new IOException("Recording has no segments");
		}
		this.segments = segments;
		advance();
		if (current == null) {
			throw new IOException("Recording has no readable segment: " + String.join("; ", warnings));
		}
		this.header = current.header();
	}

	/** Header of the first segment: session metadata and the protocol of the stored packets. */
	public McrecHeader header() {
		return header;
	}

	/** Problems met while reading, such as a segment truncated by a crash. */
	public List<String> warnings() {
		return List.copyOf(warnings);
	}

	public @Nullable Rec next() throws IOException {
		while (current != null) {
			Rec rec = current.next();
			if (rec != null) {
				return rec;
			}
			if (current.truncation() != null) {
				warnings.add(current.name() + ": " + current.truncation());
			}
			current.close();
			current = null;
			advance();
		}
		return null;
	}

	private void advance() throws IOException {
		while (current == null && nextSegment < segments.size()) {
			Recording.SegmentSource source = segments.get(nextSegment++);
			try {
				current = SegmentReader.open(source.name(), source.opener().open());
			} catch (IOException e) {
				warnings.add(source.name() + ": " + e.getMessage());
			}
		}
	}

	@Override
	public void close() throws IOException {
		if (current != null) {
			current.close();
			current = null;
		}
	}
}
