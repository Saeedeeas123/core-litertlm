package com.saeed.core.litertlm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Format facts for LiteRT-LM model bundles. Pure Java, no Android types — fully JVM-testable.
 *
 * <p>Why this exists: handing a non-LiteRT-LM file to the native engine risks a hard abort that
 * cannot be caught. Checking the extension and the file's magic header in Java first turns that
 * into an ordinary, catchable exception.
 */
public final class LitertLmModelFormats {

    /** The only extension LiteRT-LM loads. */
    public static final String EXTENSION = ".litertlm";

    /**
     * ASCII magic that genuine {@code .litertlm} bundles start with.
     * Verified on device against {@code gemma-3n-E2B-it-int4.litertlm} (2026-10-10).
     */
    public static final String MAGIC = "LITERTLM";

    private static final int MAGIC_LENGTH = MAGIC.length();

    private LitertLmModelFormats() {
    }

    /** @return true when the file name ends with {@code .litertlm}, case-insensitively. */
    public static boolean isSupportedName(String fileName) {
        return fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(EXTENSION);
    }

    /**
     * Reads the first bytes of the file and compares them with {@link #MAGIC}.
     *
     * @return true only when the file starts with the LiteRT-LM magic.
     */
    public static boolean hasMagic(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        byte[] head = readHead(file, MAGIC_LENGTH);
        if (head == null || head.length < MAGIC_LENGTH) {
            return false;
        }
        return MAGIC.equals(new String(head, StandardCharsets.US_ASCII));
    }

    /** @return the first bytes as hex, for an actionable error message. */
    public static String describeHead(File file) {
        byte[] head = readHead(file, 8);
        if (head == null || head.length == 0) {
            return "<unreadable>";
        }
        StringBuilder sb = new StringBuilder(head.length * 2);
        for (byte b : head) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] readHead(File file, int count) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[count];
            int read = 0;
            while (read < count) {
                int step = in.read(buffer, read, count - read);
                if (step < 0) {
                    break;
                }
                read += step;
            }
            if (read == count) {
                return buffer;
            }
            byte[] shortBuffer = new byte[read];
            System.arraycopy(buffer, 0, shortBuffer, 0, read);
            return shortBuffer;
        } catch (IOException e) {
            return null;
        }
    }
}