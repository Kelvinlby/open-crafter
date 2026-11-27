package mod.kelvinlby.opencrafter.encoder.client;

import mod.kelvinlby.opencrafter.encoder.EncoderLog;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * What the encoder's mixins call. Every hook is a no-op without a replay, and a replay that fails
 * ends the worker process with {@link #FAILURE_EXIT_CODE} instead of leaving a broken game running.
 */
public final class ReplayHooks {
	public static final int FAILURE_EXIT_CODE = 3;

	private ReplayHooks() {
	}

	public static boolean active() {
		return ReplaySession.current() != null;
	}

	public static void onLoopStart() {
		run(ReplaySession::onLoopStart);
	}

	public static void onClientTick() {
		run(ReplaySession::onClientTick);
	}

	/** @return true to skip the game's own key handling */
	public static boolean replaceKeybinds() {
		return test(ReplaySession::replaceKeybinds);
	}

	/** @return true to skip the player's own movement packets */
	public static boolean replaceSendChanges(LocalPlayer player) {
		return test(session -> session.replaceSendChanges(player));
	}

	public static Vec3 steerMove(Entity entity, MoverType type, Vec3 delta) {
		ReplaySession session = ReplaySession.current();
		if (session == null) {
			return delta;
		}
		try {
			return session.steerMove(entity, type, delta);
		} catch (RuntimeException e) {
			fail(e);
			return delta;
		}
	}

	public static void beforeRender() {
		run(ReplaySession::beforeRender);
	}

	public static void afterRender() {
		run(ReplaySession::afterRender);
	}

	private static void run(Consumer<ReplaySession> hook) {
		ReplaySession session = ReplaySession.current();
		if (session != null) {
			try {
				hook.accept(session);
			} catch (RuntimeException e) {
				fail(e);
			}
		}
	}

	private static boolean test(Predicate<ReplaySession> hook) {
		ReplaySession session = ReplaySession.current();
		if (session == null) {
			return false;
		}
		try {
			return hook.test(session);
		} catch (RuntimeException e) {
			fail(e);
			return true;
		}
	}

	static void fail(Throwable error) {
		EncoderLog.LOGGER.error("Replay failed", error);
		Runtime.getRuntime().halt(FAILURE_EXIT_CODE);
	}
}
