package de.pcc.privacycrashcam.utils.dataprocessing;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import de.pcc.privacycrashcam.R;
import de.pcc.privacycrashcam.data.Metadata;
import de.pcc.privacycrashcam.data.Settings;
import de.pcc.privacycrashcam.data.memoryaccess.MemoryManager;
import de.pcc.privacycrashcam.utils.datastructures.VideoRingBuffer;
import de.pcc.privacycrashcam.utils.encryption.Encryptor;

/**
 * The AsyncPersistor saves all data after recording gets invoked in the app.
 * First it saves the metadata of the recording to a json file.
 * Then it takes all recorded video snippets and creates one coherent file.
 * After that all data gets encrypted and save to the app's data storage.
 * <p>The process of persisting is asynchronous to the app's main thread.
 * Therefore callbacks are used to inform the app about the persisting's progress.</p>
 *
 * @author Josh Romanowski, Giorgio Groß
 */
public class AsyncPersistor extends AsyncTask<Metadata, Void, Boolean> {

    private final static String TAG = AsyncPersistor.class.getName();

    /* #############################################################################################
     *                                  attributes
     * ###########################################################################################*/

    /**
     * Manager used to access the app's memory e.g. saving the video data.
     */
    private MemoryManager memoryManager;
    /**
     * Callback used to inform the app about the progress of persisting.
     */
    private PersistCallback persistCallback;
    /**
     * Context of the persisting. Used to load resources.
     */
    private Context context;
    /**
     * Ringbuffer that contains the recorded video snippets
     */
    private VideoRingBuffer ringbuffer;
    /**
     * Encryptor used to encryptAndPersist files and keys.
     */
    private Encryptor encryptor;
    /**
     * Settings used to determine the ringbuffer size.
     */
    private Settings settings;

    /* #############################################################################################
     *                                  constructors
     * ###########################################################################################*/

    /**
     * Creates a new persistor with the given parameters. The AsyncPersistor will use its own
     * temporary directory.
     *
     * @param ringbuffer      Buffer containing the recorded video snippets.
     * @param persistCallback Callback used to give asynchronous response.
     * @param context         Android context of the recording.
     */
    public AsyncPersistor(VideoRingBuffer ringbuffer,
                          PersistCallback persistCallback, Context context) {
        // new mem manager will provide own temp directory for this operation
        this.memoryManager = new MemoryManager(context);

        this.ringbuffer = ringbuffer;
        this.persistCallback = persistCallback;
        this.context = context;
        this.encryptor = new Encryptor();
        this.settings = memoryManager.getSettings();
    }

    /**
     * Creates a new persistor with the given parameters. The AsyncPersistor will use create
     * temporary data and operate on temporary files accessible to the passed {@link MemoryManager}
     * instance. See {@link #AsyncPersistor(VideoRingBuffer, PersistCallback, Context)} if you
     * want AsyncPersisort to use its own temp directory.
     *
     * @param ringbuffer      Buffer containing the recorded video snippets.
     * @param memoryManager   MemoryManager instance to access temp files
     * @param persistCallback Callback used to give asynchronous response.
     * @param context         Android context of the recording.
     */
    public AsyncPersistor(VideoRingBuffer ringbuffer, MemoryManager memoryManager,
                          PersistCallback persistCallback, Context context) {
        // new mem manager will provide own temp directory for this operation
        this.memoryManager = memoryManager;

        this.ringbuffer = ringbuffer;
        this.persistCallback = persistCallback;
        this.context = context;
        this.encryptor = new Encryptor();
        this.settings = memoryManager.getSettings();
    }

    /* #############################################################################################
     *                                  methods
     * ###########################################################################################*/

    @Override
    protected Boolean doInBackground(Metadata... params) {
        Log.i(TAG, "Background task started");

        // wait half a buffer size
        int timeToWait = settings.getBufferSizeSec() * 1000 / 2;
        try {
            Thread.sleep(timeToWait);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        // post to UI thread and wait until the UI has migrated to a completely new RingBuffer
        final CyclicBarrier mBarrier = new CyclicBarrier(2);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "updating UI and CamHandler");
                persistCallback.onPersistingStarted();
                try {
                    mBarrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }
        });
        try {
            Log.i(TAG, "waiting for UI to finish update");
            mBarrier.await();
        } catch (InterruptedException ex) {
            return false;
        } catch (BrokenBarrierException ex) {
            return false;
        }
        // UI has no reference to the ring buffer and the memory manager instance anymore. We can
        // now freely operate on it.
        Log.i(TAG, "Start writing files");

        // save metadata
        Metadata metaData = params[0];
        if (metaData == null) {
            Log.w(TAG, "Did not receive metadata");
            return false;
        }

        String videoTag = String.valueOf(metaData.getDate());
        File metaLocation = memoryManager.createReadableMetadataFile(videoTag);
        if (!saveMetadataToFile(metaLocation, metaData))
            return false;

        // concat video snippets
        Queue<File> vidSnippets = ringbuffer.demandData();
        if (vidSnippets == null)
            return false;
        Log.i(TAG, "All files to be concatenated were written");

        File concatVid = memoryManager.getTempVideoFile();
        if (!concatVideos(vidSnippets, concatVid))
            return false;

        // encryptAndPersist files
        if (!encryptAndPersist(videoTag, concatVid, metaLocation))
            return false;

        // delete temporary files
        memoryManager.deleteCurrentTempData();
        ringbuffer.flushAll();

        Log.i(TAG, "Finished writing files");

        return true;
    }

    @Override
    protected void onPostExecute(Boolean status) {
        super.onPostExecute(status);
        persistCallback.onPersistingStopped(status);
    }

    /* #############################################################################################

                                        helper methods

     * ###########################################################################################*/

    /**
     * Encrypts metadata and video with a hybrid encryption algorithm.
     * Saves the files on the app. Destination files will be created according to the MemoryManager.
     *
     * @param videoTag    Name added to the actual video name
     * @param concatVideo Location of the video to encryptAndPersist.
     * @param meta        Location of the metadata to encryptAndPersist.
     * @return Returns whether encrypting was successful or not.
     */
    private boolean encryptAndPersist(String videoTag, File concatVideo, File meta) {
        // encrypt
        File[] input = new File[]{
                concatVideo,
                meta};
        File[] output = new File[]{
                memoryManager.getTempVideoFile(),
                memoryManager.createEncryptedMetaFile(videoTag)};
        File encKey = memoryManager.createEncryptedSymmetricKeyFile(videoTag);
        InputStream publicKey = context.getResources().openRawResource(R.raw.publickey);
        if (!encryptor.encrypt(input, output, publicKey, encKey))
            return false;

        // finally persist encrypted video
        byte[] buffer = new byte[1024];
        int read;
        try {
            FileInputStream fis = new FileInputStream(output[0]);
            FileOutputStream fos = new FileOutputStream(memoryManager.createEncryptedVideoFile(videoTag));

            while ((read = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
            fis.close();
            fos.close();
        } catch (IOException e) {
            Log.w(TAG, "Persisting encrypted video failed");
            return false;
        }

        return true;
    }

    /**
     * Takes a collection of videos and appends them in order.
     * Through that creates a continuous video and saves it to the desired location.
     *
     * @param videos      Collection of video snippets.
     * @param concatVideo Location of the merged video.
     * @return Returns whether concatting the videos was successful or not.
     */
    private boolean concatVideos(Queue<File> videos, File concatVideo) {
        // read all video snippets
        List<Movie> clips = new LinkedList<>();
        try {
            for (File video : videos) {
                Movie tm = MovieCreator.build(video.getAbsolutePath());
                clips.add(tm);
            }
        } catch (IOException e) {
            Log.w(TAG, "Error while reading video snippets");
            return false;
        }

        // filter out video tracks and ignore audio tracks
        List<Track> videoTracks = new LinkedList<>();
        for (Movie m : clips) {
            for (Track t : m.getTracks()) {
                if (t.getHandler().equals("vide")) {
                    videoTracks.add(t);
                }
            }
        }

        // create video
        Movie result = new Movie();
        try {
            if (videoTracks.size() > 0) {
                result.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
            }

            Container out = new DefaultMp4Builder().build(result);
            RandomAccessFile raf = new RandomAccessFile(concatVideo, "rw");
            FileChannel fc = raf.getChannel();
            out.writeContainer(fc);

            fc.close();
            raf.close();
        } catch (IOException|NoSuchElementException e) {
            Log.w(TAG, "Error while writing concat video");
            return false;
        }
        return true;
    }

    /**
     * Takes a metadata object, parses it into json format and saves it to a file.
     *
     * @param output   Output location of the metadata file.
     * @param metadata Metadata to be saved.
     * @return Returns whether saving was successful or not.
     */
    private boolean saveMetadataToFile(File output, Metadata metadata) {
        String metaJson = metadata.getAsJSON();
        try (PrintWriter out = new PrintWriter(output)) {
            out.println(metaJson);
        } catch (IOException e) {
            Log.w(TAG, "Error when saving metadata to files");
            return false;
        }
        return true;
    }
}
