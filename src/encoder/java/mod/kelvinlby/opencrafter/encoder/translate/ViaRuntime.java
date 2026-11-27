package mod.kelvinlby.opencrafter.encoder.translate;

import com.viaversion.vialoader.ViaLoader;
import com.viaversion.vialoader.impl.platform.ViaBackwardsPlatformImpl;
import com.viaversion.vialoader.impl.platform.ViaVersionPlatformImpl;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;

import java.io.File;
import java.nio.file.Path;

/**
 * The process-wide ViaVersion + ViaBackwards instance, embedded through ViaLoader. Once started,
 * any protocol pair Via can bridge (older recordings forward, newer ones backward) is available.
 */
public final class ViaRuntime {
	private static boolean started;

	private ViaRuntime() {
	}

	/** Starts Via once; {@code dataDir} receives its config files. */
	public static synchronized void ensureStarted(Path dataDir) {
		if (started) {
			return;
		}
		File root = dataDir.toAbsolutePath().toFile();
		root.mkdirs();
		ViaLoader.init(new ViaVersionPlatformImpl(root), null, null, null, ViaBackwardsPlatformImpl::new);
		Via.getManager().getProviders().use(VersionProvider.class, new PairVersionProvider());
		started = true;
	}

	/** True if Via knows this protocol number as a release version. */
	public static boolean knows(int protocol) {
		return ProtocolVersion.isRegistered(protocol);
	}

	/** True if Via can translate between the two protocols (in the direction given). */
	public static boolean canBridge(int from, int to) {
		if (from == to) {
			return true;
		}
		if (!knows(from) || !knows(to)) {
			return false;
		}
		return Via.getManager().getProtocolManager().getProtocolPath(ProtocolVersion.getProtocol(to), ProtocolVersion.getProtocol(from)) != null;
	}
}
