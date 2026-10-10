package com.saeed.core.litertlm;

import java.io.File;

/**
 * Configuration for one LiteRT-LM engine. Immutable, pure Java (no Android types) so it is
 * unit-testable on the JVM.
 *
 * <p>Sampling values are clamped rather than rejected by {@link #normalized()}, so a bad UI value
 * can never produce a native failure; {@link #validate()} only rejects what cannot be repaired
 * (a missing model path).
 */
public final class LitertLmConfig {

    public enum Backend {
        /** Always available; the safe default. */
        CPU,
        /** Faster on devices with working OpenCL; requires the two uses-native-library entries. */
        GPU
    }

    public static final int DEFAULT_TOP_K = 40;
    public static final double DEFAULT_TOP_P = 0.95;
    public static final double DEFAULT_TEMPERATURE = 0.8;
    public static final int DEFAULT_SEED = 0;

    static final int MIN_TOP_K = 1;
    static final int MAX_TOP_K = 1000;
    static final double MIN_TOP_P = 0.0;
    static final double MAX_TOP_P = 1.0;
    static final double MIN_TEMPERATURE = 0.0;
    static final double MAX_TEMPERATURE = 2.0;

    private final String modelPath;
    private final Backend backend;
    private final String cacheDir;
    private final int topK;
    private final double topP;
    private final double temperature;
    private final int seed;
    private final String systemInstruction;
    private final Integer maxOutputTokens;
    private final boolean failFastOnLowMemory;

    private LitertLmConfig(Builder builder) {
        this.modelPath = builder.modelPath;
        this.backend = builder.backend;
        this.cacheDir = builder.cacheDir;
        this.topK = builder.topK;
        this.topP = builder.topP;
        this.temperature = builder.temperature;
        this.seed = builder.seed;
        this.systemInstruction = builder.systemInstruction;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.failFastOnLowMemory = builder.failFastOnLowMemory;
    }

    public static Builder builder(String modelPath) {
        return new Builder(modelPath);
    }

    public String modelPath() {
        return modelPath;
    }

    public Backend backend() {
        return backend;
    }

    /** Optional writable directory; a warm cache improves the second load. May be null. */
    public String cacheDir() {
        return cacheDir;
    }

    public int topK() {
        return topK;
    }

    public double topP() {
        return topP;
    }

    public double temperature() {
        return temperature;
    }

    public int seed() {
        return seed;
    }

    public String systemInstruction() {
        return systemInstruction;
    }

    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * When true, {@link LitertLmEngine#load()} refuses to load a model whose estimated RAM need
     * exceeds the device's free RAM.
     *
     * <p>Default is <b>false</b> on purpose: LiteRT-LM maps the weights from the file, so the file
     * size is not the resident requirement — a 3.66 GB {@code .litertlm} is verified working on an
     * 8 GB device. A hard gate by default would reject models that actually work. Enable this only
     * where an OS kill is worse than a refusal.
     */
    public boolean failFastOnLowMemory() {
        return failFastOnLowMemory;
    }

    /** @return the file name without any directory part, for messages and logs. */
    public String modelFileName() {
        if (modelPath == null) {
            return "<none>";
        }
        int slash = modelPath.lastIndexOf('/');
        return slash >= 0 ? modelPath.substring(slash + 1) : modelPath;
    }

    /** @return a copy with every numeric field clamped into its supported range. */
    public LitertLmConfig normalized() {
        return new Builder(modelPath)
                .backend(backend)
                .cacheDir(cacheDir)
                .topK(clamp(topK, MIN_TOP_K, MAX_TOP_K))
                .topP(clamp(topP, MIN_TOP_P, MAX_TOP_P))
                .temperature(clamp(temperature, MIN_TEMPERATURE, MAX_TEMPERATURE))
                .seed(seed)
                .systemInstruction(systemInstruction)
                .maxOutputTokens(maxOutputTokens)
                .failFastOnLowMemory(failFastOnLowMemory)
                .build();
    }

    /**
     * Rejects the configuration only when it cannot be repaired automatically.
     *
     * @throws LitertLmException with {@code Kind.MODEL_MISSING} when no path is configured.
     */
    public void validate() {
        if (modelPath == null || modelPath.trim().isEmpty()) {
            throw new LitertLmException(
                    LitertLmException.Kind.MODEL_MISSING,
                    "No model path configured. Set LitertLmConfig.builder(\"/path/to/model.litertlm\").");
        }
    }

    /** Convenience: the configured path as a {@link File} (no existence check). */
    public File modelFile() {
        return new File(modelPath);
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString() {
        return "LitertLmConfig{model=" + modelFileName()
                + ", backend=" + backend
                + ", topK=" + topK
                + ", topP=" + topP
                + ", temperature=" + temperature
                + ", seed=" + seed
                + ", cacheDir=" + (cacheDir == null ? "<none>" : cacheDir)
                + '}';
    }

    /** Fluent builder; every field has a working default. */
    public static final class Builder {
        private final String modelPath;
        private Backend backend = Backend.CPU;
        private String cacheDir;
        private int topK = DEFAULT_TOP_K;
        private double topP = DEFAULT_TOP_P;
        private double temperature = DEFAULT_TEMPERATURE;
        private int seed = DEFAULT_SEED;
        private String systemInstruction;
        private Integer maxOutputTokens;
        private boolean failFastOnLowMemory;

        private Builder(String modelPath) {
            this.modelPath = modelPath;
        }

        public Builder backend(Backend backend) {
            if (backend != null) {
                this.backend = backend;
            }
            return this;
        }

        public Builder cacheDir(String cacheDir) {
            this.cacheDir = cacheDir;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder topP(double topP) {
            this.topP = topP;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder seed(int seed) {
            this.seed = seed;
            return this;
        }

        public Builder systemInstruction(String systemInstruction) {
            this.systemInstruction = systemInstruction;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder failFastOnLowMemory(boolean failFastOnLowMemory) {
            this.failFastOnLowMemory = failFastOnLowMemory;
            return this;
        }

        public LitertLmConfig build() {
            return new LitertLmConfig(this);
        }
    }
}