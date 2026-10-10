package com.saeed.core.litertlm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Free-RAM gate. Loading a multi-GB model on a phone is the most common way to get killed by the
 * OS (or to fail inside the native layer with an opaque error), so the module checks <em>before</em>
 * loading.
 *
 * <p>Pure Java with an injectable reader, so the decision logic is unit-testable without a device.
 */
public final class LitertLmMemory {

    /** Native runtime overhead on top of the weights (KV cache, tokenizer, activations). */
    public static final double OVERHEAD_FACTOR = 1.25;

    /** Fixed overhead that exists even for a tiny model. */
    public static final long FIXED_OVERHEAD_BYTES = 256L * 1024 * 1024;

    private LitertLmMemory() {
    }

    /** @return an estimate of the RAM the engine needs for a model of {@code modelBytes}. */
    public static long estimateRequiredBytes(long modelBytes) {
        if (modelBytes <= 0) {
            return FIXED_OVERHEAD_BYTES;
        }
        return (long) (modelBytes * OVERHEAD_FACTOR) + FIXED_OVERHEAD_BYTES;
    }

    /** @return true when {@code freeBytes} can hold the estimated requirement. */
    public static boolean fitsIn(long freeBytes, long modelBytes) {
        return freeBytes >= estimateRequiredBytes(modelBytes);
    }

    /**
     * @return free RAM in bytes, or {@code -1} when it cannot be determined. Reads
     *     {@code MemAvailable} from {@code /proc/meminfo} (Android exposes this to apps).
     */
    public static long readFreeRamBytes() {
        return parseMemAvailable("/proc/meminfo");
    }

    /** Testable core of {@link #readFreeRamBytes()}. */
    static long parseMemAvailable(String meminfoPath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(meminfoPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemAvailable:")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        return Long.parseLong(parts[1]) * 1024L; // value is in kB
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            return -1L;
        }
        return -1L;
    }

    /**
     * Fails fast when the model clearly cannot fit, so the caller gets an actionable error instead
     * of an OS kill.
     *
     * @throws LitertLmException NOT_ENOUGH_MEMORY when free RAM is known and insufficient.
     */
    public static void requireEnoughRam(File model) {
        long modelBytes = model == null ? 0L : model.length();
        long free = readFreeRamBytes();
        if (free < 0) {
            return; // unknown -> do not block loading
        }
        if (!fitsIn(free, modelBytes)) {
            throw new LitertLmException(
                    LitertLmException.Kind.NOT_ENOUGH_MEMORY,
                    "Not enough free RAM for " + (modelBytes / (1024 * 1024)) + " MB model: needs about "
                            + (estimateRequiredBytes(modelBytes) / (1024 * 1024)) + " MB, only "
                            + (free / (1024 * 1024)) + " MB available. Close other apps or use a "
                            + "smaller model.");
        }
    }
}