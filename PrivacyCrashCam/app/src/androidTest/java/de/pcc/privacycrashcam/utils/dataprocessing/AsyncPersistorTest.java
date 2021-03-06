package de.pcc.privacycrashcam.utils.dataprocessing;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;

import de.pcc.privacycrashcam.BaseTest;
import de.pcc.privacycrashcam.data.Metadata;
import de.pcc.privacycrashcam.testUtils.FileUtils;

import static org.junit.Assert.*;

/**
 * Tests the AsyncPersistor class
 *
 * @author Giorgio Gross
 */
public class AsyncPersistorTest extends BaseTest {
    private AsyncPersistor mPersistor;
    private PersistCallback mCallback = new PersistCallback() {
        @Override
        public void onPersistingStarted() {
            calledOnPersistingStarted = true;

            //noinspection StatementWithEmptyBody
            while (!doProceed) {
                // loop until doProceed is true
            }
        }

        @Override
        public void onPersistingStopped(boolean success) {
            resultStoppedPersisting = success;
        }
    };
    private Boolean resultStoppedPersisting = null;
    private boolean calledOnPersistingStarted = false;
    /**
     * Set this to false in order to simulate that the UI threaad needs some time. This will cause
     * the callback not to return immediately and thus the background task will have to wait for
     * this event.
     */
    private boolean doProceed = true;

    @Before
    public void setUp() throws Exception {
        mPersistor = new AsyncPersistor(bufferMock, memoryManagerMock, mCallback, context);
    }

    @Test
    public void sleepEven() throws Exception {
        Mockito.when(settingsMock.getBufferSizeSec()).thenReturn(10); // even
        mPersistor.execute(metadataMock);
        Thread.sleep(settingsMock.getBufferSizeSec() * 1000 / 2 - 100);
        mPersistor.cancel(true);
        assertFalse(calledOnPersistingStarted);
    }

    @Test
    public void sleepUneven() throws Exception {
        Mockito.when(settingsMock.getBufferSizeSec()).thenReturn(9); // uneven
        mPersistor.execute(metadataMock);
        Thread.sleep(settingsMock.getBufferSizeSec() * 1000 / 2 - 100);
        mPersistor.cancel(true);
        assertFalse(calledOnPersistingStarted);
    }

    @Ignore // will run when executed alone but fail when executed together with all other tests
    @Test
    public void waitForUI() throws Exception {
        doProceed = false;
        mPersistor.execute(metadataMock);
        // sleep slightly longer than the persistor
        Thread.sleep(settingsMock.getBufferSizeSec() * 1000 / 2 + 200);
        // notified the ui
        assertTrue(calledOnPersistingStarted);
        // not ended yet
        assertNull(resultStoppedPersisting);

        Thread.sleep(1000);
        // still not ended
        assertNull(resultStoppedPersisting);

        // release the persistor
        doProceed = true;
    }

    @Test
    public void noMeta() throws Exception {
        Metadata[] array = {null};
        assertFalse(mPersistor.doInBackground(array));
    }

    @Test
    public void nonWritableReadableMetadata() throws Exception {
        File meta = FileUtils.CreateFile(testDirectory, TEST_METADATA_R);
        assertTrue(meta.setWritable(false, false));
        Mockito.when(memoryManagerMock.createReadableMetadataFile(VIDEO_TAG))
                .thenReturn(meta);
        assertFalse(mPersistor.doInBackground(metadataMock));
    }

    @Test
    public void nonWritableEncryptedMeta() throws Exception {
        File meta = FileUtils.CreateFile(testDirectory, TEST_METADATA);
        assertTrue(meta.setWritable(false, false));
        Mockito.when(memoryManagerMock.createEncryptedMetaFile(VIDEO_TAG))
                .thenReturn(meta);
        assertFalse(mPersistor.doInBackground(metadataMock));
    }

    @Test
    public void nonWritableSymmetricKey() throws Exception {
        File key = FileUtils.CreateFile(testDirectory, TEST_ENC_SYMM_KEY);
        assertTrue(key.setWritable(false, false));
        Mockito.when(memoryManagerMock.createEncryptedSymmetricKeyFile(VIDEO_TAG))
                .thenReturn(key);
        assertFalse(mPersistor.doInBackground(metadataMock));
    }

    @Test
    public void noVideoSnippets() throws Exception {
        Mockito.when(bufferMock.demandData()).thenReturn(null);
        assertFalse(mPersistor.doInBackground(metadataMock));
    }

    @Test
    public void nonWritableTempVideo() throws Exception {
        File vid = FileUtils.CreateFile(testDirectory, TEST_VIDEO_TEMP);
        assertTrue(vid.setWritable(false, false));
        Mockito.when(memoryManagerMock.getTempVideoFile())
                .thenReturn(vid);
        assertFalse(mPersistor.doInBackground(metadataMock));
    }

    @Test
    public void nonWritableEncryptedVideo() throws Exception {
        File vid = FileUtils.CreateFile(testDirectory, TEST_VIDEO);
        assertTrue(vid.setWritable(false, false));
        Mockito.when(memoryManagerMock.createEncryptedVideoFile(VIDEO_TAG))
                .thenReturn(vid);
        assertFalse(mPersistor.doInBackground(metadataMock));
    }

    @Test
    public void doInBackground() throws Exception {
        assertTrue(mPersistor.doInBackground(metadataMock));
        assertTrue(calledOnPersistingStarted);
        // check if the files were created (if this is the case, AsyncPersistor has made appropriate
        // calls to create these files)
        assertNotNull(memoryManagerMock.getEncryptedMetadata(VIDEO_TAG));
        assertNotNull(memoryManagerMock.getReadableMetadata(VIDEO_TAG));
        assertNotNull(memoryManagerMock.getEncryptedVideo(VIDEO_TAG));
        assertNotNull(memoryManagerMock.getEncryptedSymmetricKey(VIDEO_TAG));
    }

    @Test
    public void onPostExecute() throws Exception {
        // check if callback passes the value without modifying it
        mPersistor.onPostExecute(true);
        assertNotNull(resultStoppedPersisting);
        assertTrue(resultStoppedPersisting);

        mPersistor.onPostExecute(false);
        assertFalse(resultStoppedPersisting);
    }

    @After
    public void tearDown() throws Exception {
        // reset
        calledOnPersistingStarted = false;
        resultStoppedPersisting = null;
    }
}