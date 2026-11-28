package mod.kelvinlby.opencrafter.encoder.client;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import mod.kelvinlby.opencrafter.encoder.EncoderLog;
import mod.kelvinlby.opencrafter.encoder.format.McrecHeader;
import mod.kelvinlby.opencrafter.encoder.format.Phase;
import mod.kelvinlby.opencrafter.encoder.format.Rec;
import mod.kelvinlby.opencrafter.encoder.format.Recording;
import mod.kelvinlby.opencrafter.encoder.format.RecordingReader;
import mod.kelvinlby.opencrafter.encoder.mixin.DeltaTrackerTimerAccessor;
import mod.kelvinlby.opencrafter.encoder.mixin.MinecraftAccessor;
import mod.kelvinlby.opencrafter.encoder.output.EpisodeWriter;
import mod.kelvinlby.opencrafter.encoder.pipeline.EncodeConfig;
import mod.kelvinlby.opencrafter.encoder.pipeline.Job;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Replays one recording in this client and captures it. Driven from a few game-loop hooks:
 * <ol>
 *   <li><b>Loading</b>: the recording is fed as fast as the client takes it, up to the moment the
 *   recorded client finished loading the world; then the client is given time to load too.</li>
 *   <li><b>Playing</b>: the game clock is frozen and stepped one frame period per captured frame.
 *   S2C packets arrive when their recorded time is reached; the recorded client's ticks are
 *   re-enacted by {@link ActionReplayer} and {@link MotionReplayer}. When a frame's chunk meshes
 *   are still being built it is first rendered uncaptured (time stands still) until they are done.</li>
 *   <li><b>Draining</b>: capture stops, the last GPU readbacks land, the episode is closed.</li>
 * </ol>
 */
public final class ReplaySession {
	private static final long TICK_MICROS = 50_000;
	/** A recorded tick counts for the replay tick it ends closest to. */
	private static final long TICK_TOLERANCE_MICROS = TICK_MICROS / 2;
	private static final int DRAIN_FRAMES = 240;
	private static final long PROGRESS_FRAMES = 1200;

	private static volatile @Nullable ReplaySession current;

	private final Job job;
	private final EncodeConfig config;
	private final Minecraft minecraft;
	private final VirtualClock clock;
	private final RecordFeed feed;
	private final McrecHeader header;
	private final ReplayConnection connection;
	private final MotionReplayer motion = new MotionReplayer();
	private final ActionReplayer actions;
	private final ReplayLabels labels = new ReplayLabels();
	private final ChunkSettler settler = new ChunkSettler();
	private final ReplayStats stats = new ReplayStats();
	private final C2SDecoder decoder = new C2SDecoder();
	private long c2sDecodeErrors;
	private @Nullable EpisodeWriter writer;
	private Stage stage = Stage.LOADING;
	private long replayMicros;
	private long captureFromMicros;
	private long captureUntilMicros;
	private boolean capturingThisLoop;
	private boolean frameDue;
	private int settleRenders;
	private int drainFrames;
	private @Nullable LocalPlayer lastPlayer;

	private enum Stage { LOADING, PLAYING, DRAINING, FINISHED }

	private ReplaySession(Job job, Minecraft minecraft, VirtualClock clock) throws IOException {
		this.job = job;
		this.config = job.config();
		this.minecraft = minecraft;
		this.clock = clock;
		Recording recording = Recording.open(job.recordingPath());
		int tickEndId = C2SDecoder.playId(ServerboundClientTickEndPacket.INSTANCE);
		TickSchedule schedule;
		try (RecordingReader scan = recording.reader()) {
			schedule = TickSchedule.scan(scan, tickEndId);
		}
		RecordingReader reader = recording.reader();
		this.header = reader.header();
		int delayMillis = config.s2cDelayMillis >= 0 ? config.s2cDelayMillis : header.pingMillis();
		this.feed = new RecordFeed(reader, schedule, tickEndId, C2SDecoder.playId(new ServerboundPlayerLoadedPacket()), delayMillis * 1000L);
		this.connection = new ReplayConnection(config.serverResourcePacks);
		this.actions = new ActionReplayer(minecraft);
	}

	static void start(Job job, Minecraft minecraft, VirtualClock clock) throws IOException {
		ReplaySession session = new ReplaySession(job, minecraft, clock);
		current = session;
		session.connect();
	}

	static @Nullable ReplaySession current() {
		return current;
	}

	/** The {@code replay} entry of the frame being captured. */
	static @Nullable Map<String, Object> labelsForFrame() {
		ReplaySession session = current;
		return session == null ? null : session.labels.takeForFrame(session.replayMicros);
	}

	// ---- game loop hooks -------------------------------------------------------------------------

	/** Start of each game loop iteration, before ticks and rendering. */
	void onLoopStart() {
		capturingThisLoop = false;
		switch (stage) {
			case LOADING -> pumpLoading();
			case PLAYING -> stepPlaying();
			case DRAINING -> stepDraining();
			case FINISHED -> {
			}
		}
		EncoderFrames.INSTANCE.setCapturing(capturingThisLoop);
	}

	/** Start of a client tick. */
	void onClientTick() {
		LocalPlayer player = minecraft.player;
		if (stage != Stage.PLAYING || player == null) {
			return;
		}
		if (player != lastPlayer) {
			motion.reset();
			actions.reset();
			lastPlayer = player;
		}
		List<TickGroup> due = feed.takeTicksUntil(replayMicros + TICK_TOLERANCE_MICROS);
		List<Packet<?>> packets = decode(due);
		labels.onTick(due.size(), packets);
		stats.onRecordedTicks(due.size());
		List<Packet<?>> tickAction = new ArrayList<>();
		for (Packet<?> packet : packets) {
			if (RecordedPackets.isMotion(packet)) {
				motion.accept(packet);
			} else if (!RecordedPackets.isBookkeeping(packet)) {
				tickAction.add(packet);
			}
		}
		motion.beforeTick(player);
		if (minecraft.gameMode != null) {
			actions.replayTick(player, tickAction);
		}
	}

	/**
	 * The game's own key handling never runs: input comes from the recording. (Actions are not
	 * re-enacted there, because the game skips key handling while a screen is open, and a recorded
	 * client acts in screens too.)
	 */
	boolean replaceKeybinds() {
		return true;
	}

	/** Replaces the player's own movement packets with the recorded ones. */
	boolean replaceSendChanges(LocalPlayer player) {
		if (stage == Stage.PLAYING) {
			motion.afterTick(player);
		}
		return true;
	}

	Vec3 steerMove(Entity entity, MoverType type, Vec3 delta) {
		LocalPlayer player = minecraft.player;
		if (stage != Stage.PLAYING || type != MoverType.SELF || player == null) {
			return delta;
		}
		if (entity == player) {
			return motion.steer(player, delta);
		}
		if (entity == MotionReplayer.drivenVehicle(player)) {
			return motion.steerVehicle(entity, delta);
		}
		return delta;
	}

	/** Right before a frame renders. */
	void beforeRender() {
		if (stage == Stage.PLAYING && minecraft.player != null) {
			motion.beforeFrame(minecraft.player, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		}
	}

	/** After a frame rendered. */
	void afterRender() {
		if (capturingThisLoop) {
			stats.onFrameCaptured();
			logProgressEvery(PROGRESS_FRAMES);
		}
		if (stage == Stage.PLAYING && replayFinished()) {
			beginDraining();
		}
	}

	// ---- stages --------------------------------------------------------------------------------

	private void connect() {
		ServerData server = new ServerData(job.name(), "replay", ServerData.Type.OTHER);
		minecraft.gui.setScreen(new GenericMessageScreen(Component.literal("Replaying " + job.name())));
		connection.initiateServerboundPlayConnection("replay", 25565, LoginProtocols.SERVERBOUND, LoginProtocols.CLIENTBOUND,
			new ClientHandshakePacketListenerImpl(connection, minecraft, server, null, false, null, status -> {
			}, new LevelLoadTracker(), null), false);
		((MinecraftAccessor) minecraft).openCrafterEncoder$setPendingConnection(connection);
		EncoderLog.LOGGER.info("Replaying {} ({} by {}, {})", job.name(), header.minecraftVersion(), header.playerName(), job.recording());
	}

	/** Feeds the recording up to where its client had loaded the world, then waits for this client to load. */
	private void pumpLoading() {
		try {
			feed.readUntilLoaded();
		} catch (IOException e) {
			throw new ReplayException("Could not read " + job.recording(), e);
		}
		feed.deliverAllS2C(this::receive);
		if (clientLoaded()) {
			beginPlaying();
		}
	}

	private void beginPlaying() {
		long loaded = feed.loadedMicros();
		if (loaded < 0) {
			throw new ReplayException("The recording never reached a loaded world", null);
		}
		clock.freeze();
		alignTickPhase();
		replayMicros = loaded;
		captureFromMicros = loaded + Math.round(config.startSeconds * 1e6);
		captureUntilMicros = config.maxSeconds > 0 ? captureFromMicros + Math.round(config.maxSeconds * 1e6) : Long.MAX_VALUE;
		feed.dropTicksUntil(loaded);
		writer = EncoderCapture.openEpisode(job);
		EncoderFrames.INSTANCE.attach(writer);
		EncoderCapture.configureVision(config.streams);
		stage = Stage.PLAYING;
		stats.onPlayingStarted(loaded);
		EncoderLog.LOGGER.info("World loaded at {} s of the recording; capturing", loaded / 1e6);
	}

	/**
	 * One loop iteration while playing: either the next frame period (time advances, packets
	 * arrive, ticks run) or, while chunk meshes are still building, the same instant again.
	 */
	private void stepPlaying() {
		if (frameDue && (settler.allSectionsMeshed(minecraft) || settleRenders >= config.chunkSettleFrames)) {
			capturingThisLoop = replayMicros >= captureFromMicros;
			frameDue = false;
			settleRenders = 0;
			return;
		}
		if (frameDue) {
			settleRenders++;
			stats.onSettleRender();
			return;
		}
		advanceFrame();
		boolean meshesPending = connection.takeSectionsTouched() || !settler.allSectionsMeshed(minecraft);
		if (config.chunkSettleFrames > 0 && meshesPending) {
			frameDue = true;
		} else {
			capturingThisLoop = replayMicros >= captureFromMicros;
		}
	}

	private void advanceFrame() {
		long frameNanos = config.frameNanos();
		clock.advance(frameNanos);
		replayMicros += frameNanos / 1000;
		try {
			feed.readAhead(replayMicros);
		} catch (IOException e) {
			throw new ReplayException("Could not read " + job.recording(), e);
		}
		feed.deliverS2C(replayMicros, this::receive);
	}

	private boolean replayFinished() {
		return feed.exhausted() || replayMicros >= captureUntilMicros;
	}

	private void beginDraining() {
		EncoderCapture.configureVision(List.of());
		stage = Stage.DRAINING;
		EncoderLog.LOGGER.info("Recording replayed up to {} s; finishing the episode", replayMicros / 1e6);
	}

	/** Renders (uncaptured, time frozen) until every readback in flight reached the writer. */
	private void stepDraining() {
		drainFrames++;
		if (EncoderCapture.allCapturedFramesPublished() || drainFrames > DRAIN_FRAMES) {
			finish();
		}
	}

	private void finish() {
		stage = Stage.FINISHED;
		try {
			EpisodeWriter episode = writer;
			if (episode != null) {
				episode.close();
				EpisodeSummary.write(job, header, stats, connection.decodeErrors(), c2sDecodeErrors, feed.gaps(), episode);
			}
			feed.close();
		} catch (IOException e) {
			throw new ReplayException("Could not finish the episode", e);
		}
		EncoderLog.LOGGER.info("Episode {} done: {} frames", job.name(), stats.framesCaptured());
		current = null;
		minecraft.stop();
	}

	// ---- helpers ---------------------------------------------------------------------------------

	private List<Packet<?>> decode(List<TickGroup> ticks) {
		ClientPacketListener listener = minecraft.getConnection();
		List<Packet<?>> packets = new ArrayList<>();
		if (listener == null) {
			return packets;
		}
		for (TickGroup tick : ticks) {
			for (Rec rec : tick.records()) {
				try {
					packets.add(decoder.decodePlay(rec.data(), listener.registryAccess()));
				} catch (RuntimeException e) {
					c2sDecodeErrors++;
					EncoderLog.warnThrottled("c2s-decode", "Skipped a recorded C2S packet that could not be decoded", e);
				}
			}
		}
		return packets;
	}

	private void logProgressEvery(long frames) {
		if (stats.framesCaptured() % frames == 0) {
			EncoderLog.LOGGER.info("{}: {} frames, recording at {} s", job.name(), stats.framesCaptured(), Math.round(replayMicros / 1e6));
		}
	}

	private void receive(Rec rec) {
		if (rec.phase() == Phase.HANDSHAKING || rec.phase() == Phase.STATUS) {
			return;
		}
		if (rec.phase() != Phase.LOGIN && listenerProtocol() == ConnectionProtocol.LOGIN) {
			connection.receive(syntheticLoginFinished());
		}
		connection.receive(rec.data());
	}

	private @Nullable ConnectionProtocol listenerProtocol() {
		PacketListener listener = connection.getPacketListener();
		return listener == null ? null : listener.protocol();
	}

	/** Recordings without a login phase (the 26.3 recorder starts at configuration) get the player's login made up. */
	private byte[] syntheticLoginFinished() {
		UUID uuid = header.playerUuid().isEmpty() ? UUID.nameUUIDFromBytes(header.playerName().getBytes()) : UUID.fromString(header.playerUuid());
		ClientboundLoginFinishedPacket packet = new ClientboundLoginFinishedPacket(new GameProfile(uuid, header.playerName()), uuid);
		ByteBuf buf = Unpooled.buffer();
		LoginProtocols.CLIENTBOUND.codec().encode(new FriendlyByteBuf(buf), packet);
		return ByteBufUtil.getBytes(buf);
	}

	private boolean clientLoaded() {
		ClientPacketListener listener = minecraft.getConnection();
		return minecraft.player != null && listener != null && listener.hasClientLoaded() && minecraft.gui.screen() == null;
	}

	/** From now on every frame falls at the configured point within its tick (exactly, when fps is a multiple of 20). */
	private void alignTickPhase() {
		if (minecraft.getDeltaTracker() instanceof DeltaTracker.Timer timer) {
			DeltaTrackerTimerAccessor access = (DeltaTrackerTimerAccessor) timer;
			access.openCrafterEncoder$setLastMs(Util.getMillis());
			access.openCrafterEncoder$setDeltaTickResidual((float) Math.clamp(config.tickPhase, 0.0, 0.999));
		}
	}
}
