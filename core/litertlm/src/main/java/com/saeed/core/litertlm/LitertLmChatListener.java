package com.saeed.core.litertlm;

/**
 * Streaming callbacks for one generation. Implementations are invoked from the engine's worker
 * thread, so UI callers must hop to their own main thread.
 */
public interface LitertLmChatListener {

    /**
     * Called for every streamed update.
     *
     * @param textSoFar the complete answer generated so far (never a raw delta).
     */
    void onDelta(String textSoFar);

    /** Called once when generation finished normally. */
    void onComplete(String fullText);

    /** Called once when generation failed; the engine stays usable afterwards. */
    void onError(Throwable error);
}