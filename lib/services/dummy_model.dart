/// A stand-in for real model inference.
///
/// Until a real backend is wired in, [dummyInference] streams a fixed markdown
/// reply word-by-word so the chat UI's streaming/render/persist loop can be
/// exercised end-to-end. Swapping in a real model later is a drop-in: keep the
/// `Stream<String>` signature (cumulative text per tick) and the prompt input.
///
/// Before replacing it, read the "STREAMING CONTRACT" doc on [dummyInference] —
/// it spells out exactly what the rest of the app expects from the stream.
library;

/// Delay between emitted words. Word-by-word (rather than char-by-char) keeps
/// the rebuild rate low enough that the markdown renders smoothly instead of
/// flickering, while still reading as live streaming.
const Duration _kWordDelay = Duration(milliseconds: 40);

/// A simulated mid-stream network stall: after [_kStallAfterWords] words the
/// stream pauses for [_kStallDuration] (emitting nothing) before resuming, so
/// the UI's "still generating" indicator can be exercised against a real gap.
const int _kStallAfterWords = 12;
const Duration _kStallDuration = Duration(seconds: 5);

/// The fixed reply. Exercises the full render path: headings, bold/italic,
/// inline code, a fenced code block, and a table — so theming/sizing is
/// visually verifiable against the surrounding UI.
const String _kFixedReply = '''
## Streaming demo

This is a **dummy** response that streams in *word by word* to simulate model
inference. It renders inline `code`, a fenced block, and a table.

```dart
void main() {
  print('Hello from the dummy model!');
}
```

| Capability | Status |
|------------|--------|
| Headings   | ✅     |
| Code block | ✅     |
| Table      | ✅     |

That's the whole loop: prompt in, markdown streamed out, saved to disk.
''';

/// Streams a fixed markdown reply for [prompt], emitting the cumulative text so
/// far on each tick. The caller only needs to render the latest value.
///
/// [prompt] is accepted (and ignored for content) so the call site already
/// matches a real inference API.
///
/// ---------------------------------------------------------------------------
/// STREAMING CONTRACT — read before replacing this with a real pipeline.
/// ---------------------------------------------------------------------------
/// The Home page (`home_page.dart` -> `_send`) and the pair renderer
/// (`widgets/message_pair.dart`) depend on the following behaviour. A real
/// backend (HTTP SSE, websocket, local model, etc.) is a drop-in replacement
/// **only if it honours every point below**:
///
/// 1. CUMULATIVE, NOT DELTA. Each event yields the *entire* response text
///    accumulated so far, not just the newest token(s). The UI renders the
///    latest value verbatim (re-parses the whole markdown each tick); it does
///    NOT concatenate events. If your transport emits token deltas, accumulate
///    them in a buffer here and yield `buffer.toString()` each time. Yielding
///    deltas directly would make the response render only the last fragment.
///
/// 2. ALWAYS WELL-FORMED MARKDOWN AS IT GROWS. Emit on whitespace/word
///    boundaries (or larger), never mid-token, so each partial value is still
///    parseable markdown. In particular the block separators markdown needs
///    (blank lines before headings, the newlines inside ``` fences and tables)
///    must already be present in a partial value, or those blocks render as
///    plain text until they happen to complete. (This is the bug that mangled
///    the very first version — see the `dart-split-discards-delimiters` note.)
///
/// 3. COMPLETION == THE STREAM CLOSING, NOT A CONTENT MARKER. There is no
///    in-band "[DONE]"/sentinel in the yielded text. The reply is considered
///    finished exactly when this Stream closes (the async* function returns,
///    i.e. the caller's `onDone` fires). `_send` only appends the agent
///    `Message` and writes the second JSON save on `onDone`; the indeterminate
///    progress bar between prompt and response shows for as long as the stream
///    is open. So: map your transport's end-of-stream (SSE `[DONE]` line, the
///    HTTP body ending, the socket's final frame) to *closing this Stream* —
///    do not yield a sentinel string and keep the stream open.
///
/// 4. A LAG IS A PAUSE, NOT A CLOSE. While the network/model stalls, simply
///    emit nothing and keep the Stream open (as the simulated stall below
///    does). Because the stream stays open, the "generating" indicator keeps
///    animating and no agent message is committed. Never close the stream to
///    represent a transient pause — closing is irreversibly read as "done".
///
/// 5. ERRORS. Surface failures as a Stream error (`addError`/`throw`), not by
///    closing silently. Today `_send` listens with `cancelOnError: true` and
///    does not yet render an error state for a *failed* stream — when wiring a
///    real backend, decide there how a mid-stream failure should appear
///    (e.g. keep the partial text, drop the progress bar, show a retry).
///    TODO(streaming): add that error handling in `_send` alongside the real
///    pipeline so a dropped/failed connection is shown to the user.
///
/// 6. CANCELLATION. The caller cancels its subscription when the user selects
///    another conversation or the page is disposed. A real implementation must
///    treat cancellation as a signal to stop work and release resources
///    (abort the HTTP request / close the socket / stop generation) in the
///    Stream's `onCancel`, rather than continuing to generate in the
///    background.
///
/// TODO(streaming): replace `dummyInference` with the real inference pipeline.
/// Keep the `Stream<String> Function(String prompt)` shape and satisfy points
/// 1-6 above; if so, no changes are needed in `home_page.dart` or
/// `message_pair.dart`. The constants below (`_kWordDelay`, `_kStall*`) and the
/// fixed `_kFixedReply` are dummy-only and can be deleted with this function.
Stream<String> dummyInference(String prompt) async* {
  // Dart's String.split discards the delimiters (even when captured), which
  // would collapse the reply into one space-/newline-free token and break the
  // markdown. Instead, walk the text keeping the whitespace runs: emit a chunk
  // ending at each word so the separators (incl. the newlines markdown needs
  // for headings/code fences/tables) are preserved as the text grows.
  final String reply = _kFixedReply.trim();
  final List<Match> words = RegExp(r'\S+').allMatches(reply).toList();
  for (int i = 0; i < words.length; i++) {
    // Simulate a network lag partway through: pause without emitting, then
    // resume. The stream stays *open*, so the UI keeps showing "generating".
    if (i == _kStallAfterWords) {
      await Future<void>.delayed(_kStallDuration);
    }
    await Future<void>.delayed(_kWordDelay);
    // Cumulative text up to and including this word, retaining all the
    // whitespace/newlines that came before it.
    yield reply.substring(0, words[i].end);
  }
}
