package com.saeed.core.litertlm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Extension and magic-header detection — the gate that keeps bad files away from the native layer. */
public class LitertLmModelFormatsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File writeModel(String name, byte[] head, int extraBytes) throws Exception {
        File file = folder.newFile(name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(head);
            if (extraBytes > 0) {
                out.write(new byte[extraBytes]);
            }
        }
        return file;
    }

    @Test
    public void supportedNameIsCaseInsensitive() {
        assertTrue(LitertLmModelFormats.isSupportedName("gemma-3n-E2B-it-int4.litertlm"));
        assertTrue(LitertLmModelFormats.isSupportedName("MODEL.LITERTLM"));
        assertTrue(LitertLmModelFormats.isSupportedName("a.LitertLm"));

        assertFalse(LitertLmModelFormats.isSupportedName("model.task"));
        assertFalse(LitertLmModelFormats.isSupportedName("model.gguf"));
        assertFalse(LitertLmModelFormats.isSupportedName("litertlm"));
        assertFalse(LitertLmModelFormats.isSupportedName(null));
    }

    @Test
    public void hasMagicAcceptsTheRealHeader() throws Exception {
        File model = writeModel("real.litertlm",
                LitertLmModelFormats.MAGIC.getBytes(StandardCharsets.US_ASCII), 64);

        assertTrue(LitertLmModelFormats.hasMagic(model));
    }

    @Test
    public void hasMagicRejectsAZipBundle() throws Exception {
        // A MediaPipe .task bundle starts with a ZIP header — not a LiteRT-LM model.
        File zip = writeModel("fake.litertlm", new byte[]{0x00, 0x00, 0x50, 0x4b, 0x03, 0x04}, 64);

        assertFalse(LitertLmModelFormats.hasMagic(zip));
    }

    @Test
    public void hasMagicRejectsShortAndMissingFiles() throws Exception {
        File tiny = writeModel("tiny.litertlm", "LITE".getBytes(StandardCharsets.US_ASCII), 0);

        assertFalse("a truncated file must not pass", LitertLmModelFormats.hasMagic(tiny));
        assertFalse(LitertLmModelFormats.hasMagic(new File(folder.getRoot(), "nope.litertlm")));
        assertFalse(LitertLmModelFormats.hasMagic(null));
    }

    @Test
    public void describeHeadIsHexForMessages() throws Exception {
        File file = writeModel("head.litertlm", new byte[]{0x4c, 0x49, 0x54}, 0);

        assertEquals("4c4954", LitertLmModelFormats.describeHead(file));
        assertEquals("<unreadable>",
                LitertLmModelFormats.describeHead(new File(folder.getRoot(), "absent.litertlm")));
    }
}