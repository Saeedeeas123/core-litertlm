package com.saeed.core.litertlm;

import java.io.File;

/**
 * Turns "the model file is bad" into a precise, actionable exception <em>before</em> the native
 * layer is touched. Pure Java, JVM-testable.
 */
public final class LitertLmModels {

    private LitertLmModels() {
    }

    /**
     * @return the model file when it exists and can be opened for reading.
     * @throws LitertLmException MODEL_MISSING when the path does not exist, MODEL_UNREADABLE when
     *     it exists but cannot be opened (on API 30+ this almost always means the app lacks
     *     "All files access" for shared storage).
     */
    public static File requireReadable(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new LitertLmException(
                    LitertLmException.Kind.MODEL_MISSING,
                    "No model path configured.");
        }
        File file = new File(path.trim());
        if (!file.exists()) {
            throw new LitertLmException(
                    LitertLmException.Kind.MODEL_MISSING,
                    "Model file does not exist: " + path);
        }
        if (!file.isFile()) {
            throw new LitertLmException(
                    LitertLmException.Kind.MODEL_UNREADABLE,
                    "Model path is not a file: " + path);
        }
        if (!file.canRead()) {
            throw new LitertLmException(
                    LitertLmException.Kind.MODEL_UNREADABLE,
                    "Model file is not readable: " + path
                            + ". On Android 11+ grant \"All files access\" "
                            + "(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION) when the "
                            + "model lives in shared storage, or copy it into app-private storage.");
        }
        return file;
    }

    /**
     * Verifies the file is plausibly a LiteRT-LM bundle: right extension, then right magic header.
     *
     * @throws LitertLmException MODEL_NOT_LITERTLM with the observed header bytes.
     */
    public static void requireLitertLm(File file) {
        if (!LitertLmModelFormats.isSupportedName(file.getName())) {
            throw new LitertLmException(
                    LitertLmException.Kind.MODEL_NOT_LITERTLM,
                    "Not a LiteRT-LM bundle: " + file.getName() + " (expected a "
                            + LitertLmModelFormats.EXTENSION + " file).");
        }
        if (!LitertLmModelFormats.hasMagic(file)) {
            throw new LitertLmException(
                    LitertLmException.Kind.MODEL_NOT_LITERTLM,
                    "File " + file.getName() + " does not start with the LiteRT-LM magic \""
                            + LitertLmModelFormats.MAGIC + "\" (first bytes: "
                            + LitertLmModelFormats.describeHead(file)
                            + "). Refusing to load it: the native engine would abort instead of "
                            + "throwing.");
        }
    }

    /** Convenience: full check — readable, right extension, right magic. */
    public static File requireLoadableModel(String path) {
        File file = requireReadable(path);
        requireLitertLm(file);
        return file;
    }
}