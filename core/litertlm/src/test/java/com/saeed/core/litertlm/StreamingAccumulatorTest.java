package com.saeed.core.litertlm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Streaming must work whether the library sends deltas or cumulative snapshots. */
public class StreamingAccumulatorTest {

    @Test
    public void accumulatesDeltaChunks() {
        StreamingAccumulator accumulator = new StreamingAccumulator();

        assertEquals("The", accumulator.accept("The"));
        assertEquals("The capital", accumulator.accept(" capital"));
        assertEquals("The capital is", accumulator.accept(" is"));
        assertEquals("The capital is", accumulator.text());
    }

    @Test
    public void replacesCumulativeSnapshots() {
        StreamingAccumulator accumulator = new StreamingAccumulator();

        assertEquals("The", accumulator.accept("The"));
        assertEquals("The capital", accumulator.accept("The capital"));
        assertEquals("The capital of France", accumulator.accept("The capital of France"));
        assertEquals("The capital of France", accumulator.text());
    }

    @Test
    public void ignoresEmptyAndNullChunks() {
        StreamingAccumulator accumulator = new StreamingAccumulator();

        assertEquals("", accumulator.accept(null));
        assertEquals("", accumulator.accept(""));
        assertTrue(accumulator.isEmpty());

        accumulator.accept("hi");

        assertEquals("hi", accumulator.accept(null));
        assertEquals("hi", accumulator.accept(""));
        assertFalse(accumulator.isEmpty());
    }

    @Test
    public void resetClearsState() {
        StreamingAccumulator accumulator = new StreamingAccumulator();
        accumulator.accept("something");

        accumulator.reset();

        assertTrue(accumulator.isEmpty());
        assertEquals("", accumulator.text());
        assertEquals("new", accumulator.accept("new"));
    }

    @Test
    public void identicalChunkIsTreatedAsNoChange() {
        // Deliberate ambiguity resolution: an identical chunk means "nothing new" under cumulative
        // semantics and "repeat this text" under delta semantics. Cumulative wins, because wrongly
        // appending would duplicate the entire answer on screen (very visible), while the delta
        // reading only loses one repeated character (and only when the whole answer so far is a
        // single repeated character).
        StreamingAccumulator accumulator = new StreamingAccumulator();

        accumulator.accept("a");

        assertEquals("a", accumulator.accept("a"));
    }

    @Test
    public void longerCumulativeSnapshotReplacesTheText() {
        StreamingAccumulator accumulator = new StreamingAccumulator();

        accumulator.accept("The cap");

        assertEquals("The capital", accumulator.accept("The capital"));
    }
}