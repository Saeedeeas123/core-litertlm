package com.saeed.core.litertlm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** The pre-flight model checks: precise failure kinds and actionable messages. */
public class LitertLmModelsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void missingFileReportsModelMissing() {
        try {
            LitertLmModels.requireReadable(folder.getRoot().getAbsolutePath() + "/absent.litertlm");
            fail("expected LitertLmException");
        } catch (LitertLmException e) {
            assertEquals(LitertLmException.Kind.MODEL_MISSING, e.kind());
        }
    }

    @Test
    public void directoryReportsModelUnreadable() {
        try {
            LitertLmModels.requireReadable(folder.getRoot().getAbsolutePath());
            fail("expected LitertLmException");
        } catch (LitertLmException e) {
            assertEquals(LitertLmException.Kind.MODEL_UNREADABLE, e.kind());
        }
    }

    @Test
    public void blankPathReportsModelMissing() {
        try {
            LitertLmModels.requireReadable("  ");
            fail("expected LitertLmException");
        } catch (LitertLmException e) {
            assertEquals(LitertLmException.Kind.MODEL_MISSING, e.kind());
        }
    }

    @Test
    public void wrongExtensionIsRejected() throws Exception {
        File file = folder.newFile("model.task");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(LitertLmModelFormats.MAGIC.getBytes(StandardCharsets.US_ASCII));
        }

        try {
            LitertLmModels.requireLitertLm(file);
            fail("expected LitertLmException");
        } catch (LitertLmException e) {
            assertEquals(LitertLmException.Kind.MODEL_NOT_LITERTLM, e.kind());
            assertTrue(e.getMessage().contains(".litertlm"));
        }
    }

    @Test
    public void wrongMagicIsRejectedWithTheObservedBytes() throws Exception {
        File file = folder.newFile("wrong.litertlm");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[]{0x00, 0x00, 0x50, 0x4b, 0x03, 0x04, 0x14, 0x00});
        }

        try {
            LitertLmModels.requireLitertLm(file);
            fail("expected LitertLmException");
        } catch (LitertLmException e) {
            assertEquals(LitertLmException.Kind.MODEL_NOT_LITERTLM, e.kind());
            assertTrue("message must show the header bytes",
                    e.getMessage().contains("0000504b0304"));
        }
    }

    @Test
    public void validBundlePassesTheFullCheck() throws Exception {
        File file = folder.newFile("good.litertlm");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(LitertLmModelFormats.MAGIC.getBytes(StandardCharsets.US_ASCII));
            out.write(new byte[1024]);
        }

        File checked = LitertLmModels.requireLoadableModel(file.getAbsolutePath());

        assertNotNull(checked);
        assertEquals(file.getAbsolutePath(), checked.getAbsolutePath());
    }
}