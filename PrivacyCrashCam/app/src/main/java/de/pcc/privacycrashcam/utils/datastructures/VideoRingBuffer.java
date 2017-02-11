package de.pcc.privacycrashcam.utils.datastructures;

import android.os.FileObserver;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Buffer which stores video files in a fifo queue and keeps a reference table for quick access of
 * each file through its file name.
 *
 * @author Giorgio Groß, Josh Romanowski
 */
public class VideoRingBuffer {
    /**
     * Hash table to quickly look up the buffered items from the file observer's onEvent method.
     * We use filenames as key. The reference table might contain more elements than the queue
     * since files can be written before {@link #put(File)} can be called. Method calls on
     * {@link VideoRingBuffer} will always rely on the queue, the reference map is used internally
     * only.
     */
    private HashMap<String, Boolean> fileSavedLookupTable;
    private Queue<File> queue;
    private int capacity;
    private FileObserver directoryObserver;

    /**
     * Creates a new queue with the passed capacity.
     *
     * @param capacity  max number of elements
     * @param directory directory where the files will be saved to
     * @param suffix    video file suffix
     */
    public VideoRingBuffer(int capacity, final File directory, final String suffix) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.fileSavedLookupTable = new HashMap<>();
        this.capacity = capacity;
        this.directoryObserver = new FileObserver(directory.getAbsolutePath(),
                FileObserver.CLOSE_WRITE) {
            @Override
            public void onEvent(int event, String path) {
                if (event == FileObserver.CLOSE_WRITE) {
                    // exclude (sub)directories and non video files
                    if (path == null || !path.endsWith("." + suffix) || path.contains("/")) return;

                    Log.i("VideoRingBuffer", "Saved file named " + path);
                    fileSavedLookupTable.put(path, true);
                }
            }
        };
        this.directoryObserver.startWatching();
    }

    /**
     * Returns the real capacity of the buffer which is the original capacity + 1.
     * @return total capacity
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Add a new file to the buffer. Will remove and delete the oldest file from the buffer if the
     * buffer is filled.
     *
     * @param file element to be added
     */
    public void put(File file) {
        if (!queue.offer(file)) {
            // Queue reached its capacity. Remove head and retry.
            boolean res = pop().delete();
            Log.i("VideoRingBuffer", "Res of delete: "+res);
            queue.add(file);
        }

        // no need to add file to lookup table
    }

    /**
     * Gets and removes the head of the queue. The file will (of course) not be deleted.
     *
     * @return the queue head or null
     */
    @Nullable
    public File pop() {
        File file = queue.poll();
        if (file == null) return null;
        fileSavedLookupTable.remove(file.getName());
        return file;
    }

    /**
     * Demands the VideoRingBuffer to provide the data.
     * As writing to the buffer happens asynchronous to demanding the data
     * so demand data waits until all writing has finished.
     */
    public Queue<File> demandData() {
        Queue<File> copiedQueue = new ArrayBlockingQueue<>(capacity);
        for (File file : queue) {
            Log.i("VideoRingBuffer", "checking if file is saved: " + file.getAbsolutePath() + "    name: "+file.getName());

            try {
                // busy waiting
                while (fileSavedLookupTable.get(file.getName()) == null
                        || !fileSavedLookupTable.get(file.getName())) {
                    Thread.sleep(1000);
                }
                copiedQueue.add(file);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return copiedQueue;
    }

    /**
     * Removes all files from the buffer and deletes them.
     */
    public void flushAll() {
        fileSavedLookupTable.clear();

        File file;
        while ((file = queue.poll()) != null) {
            file.delete();
        }
    }

    /**
     * Cleans up buffer and stops watching for file events.
     */
    public void destroy() {
        flushAll();
        directoryObserver.stopWatching();
    }
}