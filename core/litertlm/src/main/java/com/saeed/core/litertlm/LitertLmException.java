package com.saeed.core.litertlm;

/**
 * The single exception type of this module. Every failure carries a {@link Kind} so callers can
 * react programmatically, and a message that says what to DO — not just what went wrong.
 */
public class LitertLmException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Kind {
        /** The configured model path does not exist. */
        MODEL_MISSING,
        /** The path exists but cannot be read (usually a missing storage permission). */
        MODEL_UNREADABLE,
        /** The file is not a LiteRT-LM bundle (wrong extension or wrong magic header). */
        MODEL_NOT_LITERTLM,
        /** The device does not have enough free RAM for this model. */
        NOT_ENOUGH_MEMORY,
        /** Engine creation or model loading failed inside the native layer. */
        LOAD_FAILED,
        /** A generation was attempted before {@code load()} succeeded. */
        NOT_LOADED,
        /** The engine was closed; create a new instance (or use LitertLmRuntime). */
        CLOSED,
        /** A generation call was made from the main thread. */
        ON_MAIN_THREAD,
        /** Generation failed inside the native layer. */
        GENERATION_FAILED
    }

    private final Kind kind;

    public LitertLmException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LitertLmException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public String toString() {
        return "LitertLmException[" + kind + "]: " + getMessage();
    }
}