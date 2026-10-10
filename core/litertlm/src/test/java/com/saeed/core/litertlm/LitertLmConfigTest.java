package com.saeed.core.litertlm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Config defaults, clamping and validation. */
public class LitertLmConfigTest {

    @Test
    public void defaultsAreUsable() {
        LitertLmConfig config = LitertLmConfig.builder("/models/gemma.litertlm").build();

        assertEquals(LitertLmConfig.Backend.CPU, config.backend());
        assertEquals(LitertLmConfig.DEFAULT_TOP_K, config.topK());
        assertEquals(LitertLmConfig.DEFAULT_TOP_P, config.topP(), 1e-9);
        assertEquals(LitertLmConfig.DEFAULT_TEMPERATURE, config.temperature(), 1e-9);
        assertFalse("RAM gate must be opt-in", config.failFastOnLowMemory());
        assertEquals("gemma.litertlm", config.modelFileName());
    }

    @Test
    public void normalizationClampsOutOfRangeValues() {
        LitertLmConfig wild = LitertLmConfig.builder("/models/x.litertlm")
                .topK(0)
                .topP(4.0)
                .temperature(-3.0)
                .build()
                .normalized();

        assertEquals(1, wild.topK());
        assertEquals(1.0, wild.topP(), 1e-9);
        assertEquals(0.0, wild.temperature(), 1e-9);

        LitertLmConfig tooBig = LitertLmConfig.builder("/models/x.litertlm")
                .topK(99999)
                .temperature(50.0)
                .build()
                .normalized();

        assertEquals(1000, tooBig.topK());
        assertEquals(2.0, tooBig.temperature(), 1e-9);
    }

    @Test
    public void normalizationTreatsNaNAsMinimum() {
        LitertLmConfig config = LitertLmConfig.builder("/models/x.litertlm")
                .temperature(Double.NaN)
                .topP(Double.NaN)
                .build()
                .normalized();

        assertEquals(0.0, config.temperature(), 1e-9);
        assertEquals(0.0, config.topP(), 1e-9);
    }

    @Test
    public void normalizationKeepsTheRamGateFlag() {
        LitertLmConfig config = LitertLmConfig.builder("/models/x.litertlm")
                .failFastOnLowMemory(true)
                .build()
                .normalized();

        assertTrue(config.failFastOnLowMemory());
    }

    @Test
    public void validateRejectsMissingPath() {
        try {
            LitertLmConfig.builder("   ").build().validate();
            fail("expected LitertLmException");
        } catch (LitertLmException e) {
            assertEquals(LitertLmException.Kind.MODEL_MISSING, e.kind());
        }
    }

    @Test
    public void nullBackendKeepsTheDefault() {
        LitertLmConfig config = LitertLmConfig.builder("/models/x.litertlm")
                .backend(null)
                .build();

        assertEquals(LitertLmConfig.Backend.CPU, config.backend());
    }

    @Test
    public void modelFileNameHandlesPlainNamesAndNull() {
        assertEquals("x.litertlm", LitertLmConfig.builder("x.litertlm").build().modelFileName());
        assertEquals("<none>", LitertLmConfig.builder(null).build().modelFileName());
    }
}