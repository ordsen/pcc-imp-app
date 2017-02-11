package de.pcc.privacycrashcam.utils.datastructures;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Queue;

import de.pcc.privacycrashcam.data.Video;
import de.pcc.privacycrashcam.data.memoryaccess.MemoryManagerTest;

import static org.junit.Assert.*;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class VideoRingBufferTest {
    private static final int CAPACITY = 3;
    /**
     * Parent directory for all files needed for or created in this test
     */
    private static final String TEST_DIRECTORY_NAME = "bufferTestData";

    private File[] videoChunks;
    private VideoRingBuffer mBuffer;

    @BeforeClass
    public static void initialize() throws Exception {

    }

    @Before
    public void setUp() throws Exception {
        File bufferTestDirectory = InstrumentationRegistry.getTargetContext().getDir(TEST_DIRECTORY_NAME, Context.MODE_PRIVATE);

        mBuffer = new VideoRingBuffer(CAPACITY, bufferTestDirectory, Video.SUFFIX);
        // create test files
        videoChunks = new File[CAPACITY * 2];
        for (int i = 0; i < videoChunks.length; i++) {
            videoChunks[i] = MemoryManagerTest.CreateFile(bufferTestDirectory, Video.PREFIX + i + "." + Video.SUFFIX);
        }
    }

    @Test
    public void bufferValidation() {
        assertEquals(CAPACITY, mBuffer.getCapacity());
    }

    @Test
    public void putCapacity() throws Exception {
        for (int i = 0; i < mBuffer.getCapacity(); i++) mBuffer.put(videoChunks[i]);
        Queue<File> bufferContent = mBuffer.demandData();

        assertTrue(bufferContent.size() == mBuffer.getCapacity());
        assertEquals(bufferContent.poll(), videoChunks[0]);
        assertEquals(mBuffer.pop(), videoChunks[0]);
    }

    @Test
    public void putMoreThanCapacity() throws Exception {
        for (File videoChunk : videoChunks) mBuffer.put(videoChunk);

        Queue<File> bufferContent = mBuffer.demandData();
        assertEquals(CAPACITY, bufferContent.size());
        assertTrue(videoChunks.length > bufferContent.size());
        for (int i = videoChunks.length - bufferContent.size(); i < videoChunks.length; i++) {
            assertEquals(videoChunks[i], bufferContent.poll());
        }
    }

    @Test
    public void popCapacity() throws Exception {
        for (int i = 0; i < mBuffer.getCapacity(); i++) mBuffer.put(videoChunks[i]);

        assertTrue(videoChunks.length >= mBuffer.getCapacity());
        for (int i = 0; i < mBuffer.getCapacity(); i++) {
            assertEquals(videoChunks[i], mBuffer.pop());
        }
    }

    @Test
    public void popMoreThanCapacity() throws Exception {
        for (int i = 0; i < mBuffer.getCapacity(); i++) mBuffer.put(videoChunks[i]);

        assertTrue(videoChunks.length >= mBuffer.getCapacity());
        for (int i = 0; i < mBuffer.getCapacity(); i++) {
            assertEquals(videoChunks[i], mBuffer.pop());
        }

        assertNull(mBuffer.pop());
    }

    @Test
    public void destroy() throws Exception {
        for (int i = 0; i < mBuffer.getCapacity(); i++) mBuffer.put(videoChunks[i]);

        Queue<File> bufferContent = mBuffer.demandData();
        assertEquals(CAPACITY, bufferContent.size());
        mBuffer.destroy();
        for (File videoChunk : bufferContent) {
            assertFalse(videoChunk.exists());
        }
    }

    @Test
    public void flushAll() throws Exception {
        for (int i = 0; i < mBuffer.getCapacity(); i++) mBuffer.put(videoChunks[i]);

        Queue<File> bufferContent = mBuffer.demandData();
        assertEquals(CAPACITY, bufferContent.size());
        mBuffer.flushAll();
        for (File videoChunk : bufferContent) {
            assertFalse(videoChunk.exists());
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @After
    public void tearDown() throws Exception {
        for (File file : videoChunks) {
            if (file != null) file.delete();
        }
    }

    @AfterClass
    public static void shutDown() throws Exception {

    }

}