package mod.kelvinlby.opencrafter.encoder.translate;

import com.viaversion.viaversion.api.connection.StorableObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;

/** Tells Via, per connection, which server protocol sits on the far side of the pipeline. */
final class PairVersionProvider implements VersionProvider {
	/** Attached to each translating connection before its synthetic handshake. */
	record ServerVersion(ProtocolVersion version) implements StorableObject {
	}

	@Override
	public ProtocolVersion getClosestServerProtocol(UserConnection connection) {
		ServerVersion server = connection.get(ServerVersion.class);
		if (server == null) {
			throw new IllegalStateException("Connection has no target server version");
		}
		return server.version();
	}
}
