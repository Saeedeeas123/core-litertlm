package com.saeed.core.litertlm;

import android.content.Context;

/**
 * Process-wide holder for a single loaded model — the recommended entry point.
 *
 * <p>Rationale: an engine can hold gigabytes, and re-loading the same model per screen wastes both
 * RAM and 10+ seconds. This class loads once, reuses across activity recreation, and — unlike a
 * naive holder — retires the old engine <em>before</em> loading a new one, so a
 * close/reload sequence can never hand out a dead conversation.
 *
 * <p>All methods are safe to call from any thread, but {@link #acquire} blocks while the model
 * loads, so call it from a worker thread.
 */
public final class LitertLmRuntime {

    private static final Object LOCK = new Object();

    private static LitertLmEngine engine;
    private static String loadedPath;

    private LitertLmRuntime() {
    }

    /**
     * Returns a loaded engine for {@code config.modelPath()}, loading it if necessary.
     *
     * @throws LitertLmException from {@link LitertLmEngine#load()} when loading fails.
     */
    public static LitertLmEngine acquire(Context context, LitertLmConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config == null");
        }
        config.validate();
        synchronized (LOCK) {
            String wanted = config.modelPath();
            if (engine != null && engine.isLoaded() && wanted.equals(loadedPath)) {
                return engine;
            }
            // Retire first, then load: never leave a half-replaced holder behind.
            if (engine != null) {
                try {
                    engine.close();
                } catch (Throwable ignored) {
                    // Best effort.
                }
                engine = null;
                loadedPath = null;
            }
            LitertLmEngine fresh = new LitertLmEngine(context, config);
            fresh.load();
            engine = fresh;
            loadedPath = wanted;
            return engine;
        }
    }

    /** Unloads the model and frees its memory. Idempotent. */
    public static void release() {
        synchronized (LOCK) {
            if (engine != null) {
                try {
                    engine.close();
                } catch (Throwable ignored) {
                    // Best effort.
                }
            }
            engine = null;
            loadedPath = null;
        }
    }

    /** @return true when a model is currently loaded and usable. */
    public static boolean isLoaded() {
        synchronized (LOCK) {
            return engine != null && engine.isLoaded();
        }
    }

    /** @return the path of the loaded model, or null when nothing is loaded. */
    public static String loadedPath() {
        synchronized (LOCK) {
            return loadedPath;
        }
    }
}