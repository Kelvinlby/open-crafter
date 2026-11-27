package mod.kelvinlby.opencrafter.client.contact.push;

import mod.kelvinlby.opencrafter.client.contact.anim.PushCurve;
import mod.kelvinlby.opencrafter.client.contact.anim.Timeline;
import net.minecraft.world.phys.Vec3;

/** One hit entity's push: {@code away} is the full displacement (direction times distance). */
final class Push {
	private final Vec3 away;
	private final Timeline timeline;

	Push(Vec3 away, double durationTicks, double now) {
		this.away = away;
		this.timeline = new Timeline(durationTicks, now);
	}

	/** Where the entity is drawn relative to where it is, in the frame at {@code now}. */
	Vec3 offset(double now) {
		return away.scale(PushCurve.amount(timeline.progress(now)));
	}

	boolean finished(double now) {
		return timeline.finished(now);
	}
}
