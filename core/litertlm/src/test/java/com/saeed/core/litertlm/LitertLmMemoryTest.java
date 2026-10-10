package com.saeed.core.litertlm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;

/** RAM estimation and the (opt-in) low-memory gate. */
public class LitertLmMemoryTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void estimateAddsProportionalAndFixedOverhead() {
        long oneGb = 1024L * 1024 * 1024;

        assertEquals(LitertLmMemory.FIXED_OVERHEAD_BYTES,
                LitertLmMemory.estimateRequiredBytes(0));
        assertEquals((long) (oneGb * LitertLmMemory.OVERHEAD_FACTOR)
                        + LitertLmMemory.FIXED_OVERHEAD_BYTES,
                LitertLmMemory.estimateRequiredBytes(oneGb));
    }

    @Test
    public void fitsInIsBoundaryExact() {
        long model = 100L * 1024 * 1024;
        long required = LitertLmMemory.estimateRequiredBytes(model);

        assertTrue(LitertLmMemory.fitsIn(required, model));
        assertFalse(LitertLmMemory.fitsIn(required - 1, model));
    }

    @Test
    public void memAvailableIsParsedFromProcFormat() throws Exception {
        File meminfo = folder.newFile("meminfo");
        try (Writer writer = new FileWriter(meminfo)) {
            writer.write("MemTotal:       8000000 kB\n");
            writer.write("MemFree:         100000 kB\n");
            writer.write("MemAvailable:   3500000 kB\n");
            writer.write("SwapTotal:            0 kB\n");
        }

        long available = LitertLmMemory.parseMemAvailable(meminfo.getAbsolutePath());

        assertEquals(3_500_000L * 1024L, available);
    }

    @Test
    public void unknownMemoryIsReportedAsMinusOne() {
        assertEquals(-1L, LitertLmMemory.parseMemAvailable("/definitely/not/here"));

        File malformed = new File(folder.getRoot(), "empty-meminfo");
        assertEquals(-1L, LitertLmMemory.parseMemAvailable(malformed.getAbsolutePath()));
    }

    @Test
    public void gateStaysSilentWhenMemoryIsUnknown() {
        // A file whose size is huge would fail the gate — but the gate must never throw when the
        // free-RAM figure is unavailable, otherwise loading would break on any device that hides it.
        try {
            LitertLmMemory.requireEnoughRam(null);
        } catch (LitertLmException e) {
            fail("null model must not throw: " + e.getMessage());
        }
    }
}