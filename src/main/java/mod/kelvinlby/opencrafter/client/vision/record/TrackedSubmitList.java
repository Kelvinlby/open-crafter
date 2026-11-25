package mod.kelvinlby.opencrafter.client.vision.record;

import java.util.AbstractList;
import java.util.List;

/**
 * Read-only view of a feature renderer's submit group that tells the recorder which submit
 * is being turned into vertices: every renderer walks its group in order and finishes one
 * submit before fetching the next.
 */
public final class TrackedSubmitList<T> extends AbstractList<T> {
	private final List<T> delegate;
	private final MotionRecorder recorder;

	public TrackedSubmitList(List<T> delegate, MotionRecorder recorder) {
		this.delegate = delegate;
		this.recorder = recorder;
	}

	@Override
	public T get(int index) {
		T submit = delegate.get(index);
		recorder.beginPrepare(submit);
		return submit;
	}

	@Override
	public int size() {
		return delegate.size();
	}
}
