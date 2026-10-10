package com.saeed.core.litertlm;

/**
 * Accumulates streamed chunks into the answer so far.
 *
 * <p>LiteRT-LM's streaming callback may deliver either <em>deltas</em> (each chunk is new text) or
 * <em>cumulative</em> snapshots (each chunk repeats everything so far). Rather than assume one
 * behaviour — which would produce duplicated or truncated output on the other — this accepts both:
 * a chunk that extends the current text replaces it, anything else is appended.
 *
 * <p>Pure Java, unit-tested.
 */
public final class StreamingAccumulator {

    private final StringBuilder text = new StringBuilder();

    /** @return the answer so far, after folding in {@code chunk}. */
    public String accept(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return text.toString();
        }
        String current = text.toString();
        if (chunk.startsWith(current)) {
            // Cumulative snapshot: the chunk already contains everything.
            text.setLength(0);
            text.append(chunk);
        } else {
            text.append(chunk);
        }
        return text.toString();
    }

    /** @return the answer so far. */
    public String text() {
        return text.toString();
    }

    /** @return true when nothing has been accumulated yet. */
    public boolean isEmpty() {
        return text.length() == 0;
    }

    /** Forgets everything, ready for the next turn. */
    public void reset() {
        text.setLength(0);
    }
}