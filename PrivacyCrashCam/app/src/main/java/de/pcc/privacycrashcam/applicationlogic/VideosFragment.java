package de.pcc.privacycrashcam.applicationlogic;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import de.pcc.privacycrashcam.R;
import de.pcc.privacycrashcam.data.Metadata;
import de.pcc.privacycrashcam.data.Video;
import de.pcc.privacycrashcam.data.memoryaccess.MemoryManager;
import de.pcc.privacycrashcam.data.serverconnection.RequestState;
import de.pcc.privacycrashcam.data.serverconnection.ServerProxy;
import de.pcc.privacycrashcam.data.serverconnection.ServerResponseCallback;

import static de.pcc.privacycrashcam.data.serverconnection.RequestState.SUCCESS;

/**
 * Shows all videos which were recorded by the user.
 *
 * @author David Laubenstein, Giorgio Gross
 */

public class VideosFragment extends Fragment {

    /* #############################################################################################
     *                                  attributes
     * ###########################################################################################*/

    private VideoListAdapter videoListAdapter;

    /* #############################################################################################
     *                                  methods
     * ###########################################################################################*/

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // get the main layout describing the content and init view components
        RelativeLayout base = (RelativeLayout) inflater.inflate(R.layout.content_videos, container, false);
        ListView videosListView = (ListView) base.findViewById(R.id.lv_videos);

        // set up content
        MemoryManager memoryManager = new MemoryManager(getContext());
        videoListAdapter = new VideoListAdapter(memoryManager.getAllVideos(), memoryManager);
        videosListView.setAdapter(videoListAdapter);
        videosListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                videoListAdapter.info(position);
            }
        });
        return base;
    }

    protected class VideoViewHolder {
        ProgressBar progressUpload;
        ImageButton upload;
        ImageButton delete;
        TextView title;
        TextView caption;
    }

    private class VideoListAdapter extends BaseAdapter {
        private boolean isUploading = false;
        private MemoryManager memoryManager;
        private LayoutInflater inflater;
        private ArrayList<Video> videos;

        private VideoListAdapter(ArrayList<Video> videos, MemoryManager memoryManager) {
            this.inflater = LayoutInflater.from(getContext());
            this.videos = videos;
            this.memoryManager = memoryManager;
        }

        @Override
        public int getCount() {
            return videos.size();
        }

        @Override
        public Object getItem(int i) {
            return videos.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int position, View view, ViewGroup viewGroup) {
            final VideoViewHolder mHolder;
            if (view == null) {
                view = inflater.inflate(R.layout.list_item_video, viewGroup, false);

                mHolder = new VideoViewHolder();
                mHolder.title = (TextView) view.findViewById(R.id.title);
                mHolder.caption = (TextView) view.findViewById(R.id.caption);
                mHolder.upload = (ImageButton) view.findViewById(R.id.upload);
                mHolder.progressUpload = (ProgressBar) view.findViewById(R.id.progress_upload);
                mHolder.delete = (ImageButton) view.findViewById(R.id.ib_delete);

                view.setTag(mHolder);
            } else {
                mHolder = (VideoViewHolder) view.getTag();
            }

            mHolder.title.setText(videos.get(position).getName());
            mHolder.caption.setText(getDate(
                    videos.get(position).getReadableMetadata().getDate(), "dd.MM.yyyy HH:mm:ss"));

            mHolder.upload.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isUploading) upload(position, mHolder);
                    else Toast.makeText(getContext(), getString(R.string.upload_wait),
                            Toast.LENGTH_SHORT).show();
                }
            });
            mHolder.delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(!isUploading) delete(position);
                    else Toast.makeText(getContext(), getString(R.string.upload_wait),
                            Toast.LENGTH_SHORT).show();
                }
            });

            return view;
        }

        private void toggleProgressBar(VideoViewHolder mHolder) {
            if (mHolder.progressUpload.getVisibility() == View.VISIBLE) {
                mHolder.progressUpload.setVisibility(View.GONE);
                mHolder.upload.setVisibility(View.VISIBLE);
            } else {
                mHolder.progressUpload.setVisibility(View.VISIBLE);
                mHolder.upload.setVisibility(View.GONE);
            }
        }

        /**
         * Deletes the video at the passed index and updates the list
         *
         * @param index video index
         */
        private void delete(int index) {
            Video item = videos.get(index);
            String videoTag = Video.ExtractTagFromName(item.getName());
            memoryManager.deleteEncryptedVideoFile(videoTag);
            memoryManager.deleteEncryptedMetadataFile(videoTag);
            memoryManager.deleteReadableMetadata(videoTag);
            memoryManager.deleteEncryptedSymmetricKeyFile(videoTag);

            videos.remove(item);
            this.notifyDataSetChanged();
        }

        /**
         * Uploads the video at the passed index
         *
         * @param index   video index in the video list
         * @param mHolder View Holder to be updated
         */
        private void upload(int index, final VideoViewHolder mHolder) {
            toggleProgressBar(mHolder);
            isUploading = true;

            Video item = videos.get(index);
            ServerProxy proxy = new ServerProxy(getContext());
            proxy.videoUpload(item.getEncVideoFile(), item.getEncMetaFile(),
                    item.getEncSymKeyFile(), memoryManager.getAccountData(),
                    new ServerResponseCallback<RequestState>() {
                        @Override
                        public void onResponse(RequestState response) {
                            switch (response) {
                                case SUCCESS:
                                    Toast.makeText(getContext(), getString(R.string.video_upload_success),
                                            Toast.LENGTH_SHORT).show();
                                    break;
                                case ACCOUNT_FAILURE:
                                    Toast.makeText(getContext(), getString(R.string.error_account),
                                            Toast.LENGTH_SHORT).show();
                                    break;
                                case NETWORK_FAILURE:
                                    Toast.makeText(getContext(), getString(R.string.error_no_connection),
                                            Toast.LENGTH_SHORT).show();
                                    break;
                                default:
                                    Toast.makeText(getContext(), getString(R.string.error_undefined),
                                            Toast.LENGTH_SHORT).show();

                            }

                            toggleProgressBar(mHolder);
                            isUploading = false;
                        }

                        @Override
                        public void onProgress(int percent) {
                            // ignored
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(getContext(), getString(R.string.error_no_connection),
                                    Toast.LENGTH_SHORT).show();

                            toggleProgressBar(mHolder);
                            isUploading = false;
                        }
                    });
        }

        /**
         * Show video info.
         *
         * @param index video index
         */
        private void info(int index) {
            // set up the metadata content so that a user can read it easily
            Metadata videoMeta = videos.get(index).getReadableMetadata();
            String unformatted = getContext().getString(R.string.meta_info);
            String formatted = String.format(unformatted,
                    getDate(videoMeta.getDate(), "dd.MM.yyyy HH:mm:ss") + "<br>",
                    videoMeta.getTriggerType() + "<br>",
                    videoMeta.getgForce()[0] + ", " + videoMeta.getgForce()[1] + ", "
                            + videoMeta.getgForce()[2]);

            // show a dialog
            new HTMLDialogViewer(getContext(), inflater, getContext().getResources().getString(R.string.meta_info_title), formatted).showDialog();
        }

        /**
         * Return date in specified format.
         *
         * @param milliSeconds Date in milliseconds
         * @param dateFormat   Date format
         * @return String representing date in specified format
         */
        private String getDate(long milliSeconds, String dateFormat) {
            // Create a DateFormatter object for displaying date in specified format.
            SimpleDateFormat formatter = new SimpleDateFormat(dateFormat);

            // Create a calendar object that will convert the date and time value in milliseconds to date.
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(milliSeconds);
            return formatter.format(calendar.getTime());
        }
    }
}