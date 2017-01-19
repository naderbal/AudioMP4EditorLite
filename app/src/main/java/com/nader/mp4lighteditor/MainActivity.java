package com.nader.mp4lighteditor;

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TRIMMED_FILE = "outputAfterTrim.mp4";
    private static final String LOOPED_FILE = "loopedAfterTrim.mp4";
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final int PICKFILE_RESULT_CODE = 10;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static String fileToTrim = "audioMP4.mp4";
    private Button btnRecordAudio;
    private Button btnChooseFile;
    private Button btnTrimAndLoop;
    private TextView tvChosenFile;
    private LinearLayout llCosenFile;
    private TextView tvLoop;
    private String fileSrc;
    private SeekBar seekBarStartTime;
    private SeekBar seekBarEndTime;
    private TextView txtSeekStart;
    private TextView txtSeekEnd;
    private Button btnAdd;
    private Button btnSubtract;
    private TextView tvSavedFile;
    private OnRecordingSavedListener listener = new OnRecordingSavedListener() {
        @Override
        public void onSaved(String filePath) {
            fileSrc = filePath;
            fileToTrim = filePath;
            updateChosenFileText(fileSrc);
            int fileDuration = getAudioFileDuration(fileSrc);
            //updates seekBars maximum value according to duration of recorded file
            configureSeekBars(fileDuration - (int)(fileDuration*0.1));
            setTrimAndLoopClickability(true);
        }
    };

    /**
     * Syncs time with nearest sample, since trim can only be done from the start of a sample
     */
    private static double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getSampleDurations().length; i++) {
            long delta = track.getSampleDurations()[i];

            if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
                // samples always start with 1 but we start with zero therefore +1
                timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
            }
            currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
            currentSample++;

        }
        double previous = 0;
        for (double timeOfSyncSample : timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                if (next) {
                    return timeOfSyncSample;
                } else {
                    return previous;
                }
            }
            previous = timeOfSyncSample;
        }
        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
    }

    /**
     * Checks if the app has permission to write to device storage
     * If the app does not has permission then the user will be prompted to grant permissions
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    /**
     * Gets the physical path of a uri
     * @retun the physical path as a string
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            }

            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        btnRecordAudio = (Button) findViewById(R.id.btnRecordAudio);
        btnRecordAudio.setOnClickListener(this);
        btnChooseFile = (Button) findViewById(R.id.btnChooseFile);
        btnChooseFile.setOnClickListener(this);
        btnTrimAndLoop = (Button) findViewById(R.id.btnTrimAndLoop);
        btnTrimAndLoop.setOnClickListener(this);
        tvChosenFile = (TextView) findViewById(R.id.tvChosenFile);
        llCosenFile = (LinearLayout) findViewById(R.id.frmChosenFile);
        tvLoop = (TextView) findViewById(R.id.tvLoop);
        txtSeekStart = (TextView) findViewById(R.id.txtSeekStart);
        txtSeekEnd = (TextView) findViewById(R.id.txtSeekEnd);
        seekBarStartTime = (SeekBar) findViewById(R.id.seekStart);
        btnAdd = (Button) findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(this);
        btnSubtract = (Button) findViewById(R.id.btnSubtract);
        btnSubtract.setOnClickListener(this);
        seekBarStartTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                //convert i which is in value of milliseconds to seconds with one decimal
                int value = i/100;
                txtSeekStart.setText(value/10.0+"s");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        seekBarEndTime = (SeekBar) findViewById(R.id.seekEnd);
        seekBarEndTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                //convert i which is in value of milliseconds to seconds with one decimal
                int value = i/100;
                txtSeekEnd.setText(value/10.0+"s");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        tvSavedFile = (TextView) findViewById(R.id.tvSavedFile);
        setTrimAndLoopClickability(false);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnRecordAudio:
                handleRecordAudioButtonClick();
                break;
            case R.id.btnChooseFile:
                chooseFile();
                break;
            case R.id.btnTrimAndLoop:
                handleTrimAndLoopButtonClick();
                break;
            case R.id.btnAdd:
                addToLoop();
                break;
            case R.id.btnSubtract:
                subtractFromLoop();
                break;
        }
    }

    /**
     * Subtracts one from the value of the loop field.
     */
    private void subtractFromLoop() {
        int tvLoopNumber = Integer.valueOf(tvLoop.getText().toString());
        if (tvLoopNumber == 0) return;
        tvLoop.setText(--tvLoopNumber+"");
    }

    /**
     * Adds one to the value of the loop field.
     */
    private void addToLoop() {
        int tvLoopNumber = Integer.valueOf(tvLoop.getText().toString());
        tvLoop.setText(++tvLoopNumber+"");
    }

    /**
     * Handles trim and loop button click.
     */
    private void handleTrimAndLoopButtonClick() {
        if (fileSrc == null) {
            //if fileSource null tell the user to record an audio or choose a file
            Toast.makeText(this, "Please record an audio or choose a file", Toast.LENGTH_SHORT).show();
            return;
        }
            int fileDuration = getAudioFileDuration(fileSrc);

            double trimFromStartTime = 0;
            double trimFromEndTime = 0;
            trimFromStartTime = seekBarStartTime.getProgress();
            trimFromEndTime = seekBarEndTime.getProgress();
            //trim file from start time to end time
            double endTime = fileDuration - trimFromEndTime;
            trimFile(fileToTrim, trimFromStartTime/1000.0, endTime/1000.0);
            //loop file
            loopFile(TRIMMED_FILE);
    }

    /**
     * Handles recordAudio Button click.
     */
    private void handleRecordAudioButtonClick() {
        //get fragment manager
        FragmentManager fm = getFragmentManager();
        //init new RecorderDialogFragment
        RecorderDialogFragment dialogFragment = new RecorderDialogFragment();
        //show fragment
        dialogFragment.show(fm, "Sample Fragment");
        //pass listener to dialogFragment
        dialogFragment.setListener(listener);
    }

    /**
     * Sets trim and loop views clickability.
     * True if clickable, false otherwise.
     */
    public void setTrimAndLoopClickability(boolean state) {
        //set trimAndLoop button, startTime endTime seekbars, addBtn and subtractBtn to state
        btnTrimAndLoop.setEnabled(state);
        seekBarStartTime.setEnabled(state);
        seekBarEndTime.setEnabled(state);
        btnAdd.setEnabled(state);
        btnSubtract.setEnabled(state);
    }

    /**
     * Trims a file to get a result of the file from startTime to endTime.
     */
    private void trimFile(String inputFilePath, double startTime, double endTime) {
        try {
            // get file from memory
            File dir = new File(Environment.getExternalStorageDirectory(), "/mp4Test/");
            dir.mkdirs();
            File inputFile = new File(inputFilePath);
            // create movie from the file
            Movie movie = MovieCreator.build(inputFile.getPath());

            // get tracks from movie
            List<Track> tracks = movie.getTracks();
            movie.setTracks(new LinkedList<Track>());

            //start time which will be updated according to samples
            double startTimeAfterSync = startTime;
            //end time which will be updated according to samples
            double endTimeAfterSync = endTime;

            boolean timeCorrected = false;

            // Here we try to find a track that has sync samples. Since we can only start decoding
            // at such a sample we SHOULD make sure that the start of the new fragment is exactly
            // such a frame
            for (Track track : tracks) {
                if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
                    if (timeCorrected) {
                        // This exception here could be a false positive in case we have multiple tracks
                        // with sync samples at exactly the same positions. E.g. a single movie containing
                        // multiple qualities of the same video (Microsoft Smooth Streaming file)

                        throw new RuntimeException("The startTime has already been corrected by another track with SyncSample. Not Supported.");
                    }
                    startTimeAfterSync = correctTimeToSyncSample(track, startTimeAfterSync, false);
                    endTimeAfterSync = correctTimeToSyncSample(track, endTimeAfterSync, true);
                    timeCorrected = true;
                }
            }

            for (Track track : tracks) {
                long currentSample = 0;
                double currentTime = 0;
                double lastTime = -1;
                long startSample1 = -1;
                long endSample1 = -1;

                for (int i = 0; i < track.getSampleDurations().length; i++) {
                    long delta = track.getSampleDurations()[i];
                    if (currentTime > lastTime && currentTime <= startTimeAfterSync) {
                        // current sample is still before the new starttime
                        startSample1 = currentSample;
                    }
                    if (currentTime > lastTime && currentTime <= endTimeAfterSync) {
                        // current sample is after the new start time and still before the new endtime
                        endSample1 = currentSample;
                    }
                    lastTime = currentTime;
                    currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
                    currentSample++;
                }
                movie.addTrack(new AppendTrack(new CroppedTrack(track, startSample1, endSample1)));
            }
            long start1 = System.currentTimeMillis();
            Container out = new DefaultMp4Builder().build(movie);
            long start2 = System.currentTimeMillis();
            File output = new File(dir + "/" + TRIMMED_FILE);
            if (output.exists()) {
                output.delete();
            }
            FileOutputStream fos = new FileOutputStream(dir + "/" + TRIMMED_FILE); //String.format("output-%f-%f.mp4", startTime1, endTime1)
            FileChannel fc = fos.getChannel();
            out.writeContainer(fc);

            fc.close();
            fos.close();
            long start3 = System.currentTimeMillis();
            System.err.println("Building IsoFile took : " + (start2 - start1) + "ms");
            System.err.println("Writing IsoFile took  : " + (start3 - start2) + "ms");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a loop of the given audio file according to the given loop number.
     */
    private void loopFile(String inputFilePath) {
        verifyStoragePermissions(this);
        String tvLoopString = tvLoop.getText().toString();

        int loopNumber = Integer.valueOf(tvLoopString);
        if (loopNumber == 0) {
            Toast.makeText(this,"Please Enter Number Greater than 0",Toast.LENGTH_SHORT).show();
            return;
        }
            File dir = new File(Environment.getExternalStorageDirectory(), "/mp4Test/");
            dir.mkdirs();
            //file which we will work on
            File inputFile = new File(dir, inputFilePath);
            //file to be saved
            File outputFile = new File(dir, LOOPED_FILE);
            // create movie from input file
            Movie inputMovie = null;
            try {
                inputMovie = MovieCreator.build(inputFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            // create array of input movies
            Movie[] inputMovies = new Movie[loopNumber];
            for (int i = 0; i < loopNumber; i++) {
                inputMovies[i] = inputMovie;
            }
            // create output movie
            final Movie outputMovie = new Movie();
            // create audio tracks
            List<Track> audioTracks = new ArrayList<>();
            for (Movie movie : inputMovies) {
                if (movie != null) {
                    for (Track track : movie.getTracks()) {
                        if (track.getHandler().equals("soun")) {
                            audioTracks.add(track);
                        }
                    }
                }
            }
            // add audio tracks to output movie
            try {
                outputMovie.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));
            } catch (Exception e) {
                e.printStackTrace();
            }
            // save output to file
            Container container = null;
            try {
                container = new DefaultMp4Builder().build(outputMovie);
            } catch (Exception e) {

            }
            FileChannel fc = null;
            try {
                fc = new RandomAccessFile(outputFile, "rw").getChannel();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                container.writeContainer(fc);
                Toast.makeText(this, "File was saved", Toast.LENGTH_SHORT).show();
                tvSavedFile.setText("File Saved: "+outputFile.getPath());
            } catch (Exception e) {
                Toast.makeText(this, "File failed to be saved", Toast.LENGTH_SHORT).show();
            }
            try {
                fc.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    /**
     * Handles the file chooser intent.
     */
    private void chooseFile() {
        //create intent of Action get content
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.setType("*/*");
        //set available types to audio and video only
        String[] mimetypes = {"audio/*", "video/*"};
        chooseFile.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
        //trigger file chooser
        chooseFile = Intent.createChooser(chooseFile, "Choose a file");
        //start activity and wait for the result
        startActivityForResult(chooseFile, PICKFILE_RESULT_CODE);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICKFILE_RESULT_CODE) {
            if (data != null) {
                //get uri from intent returned
                Uri uri = data.getData();
                //get file path of the uri
                fileSrc = getPath(this, uri);
                fileToTrim = fileSrc;
                //update chosen file textView with filesrc
                updateChosenFileText(fileSrc);
                int fileDuration = getAudioFileDuration(fileSrc);
                configureSeekBars(fileDuration - (int)(fileDuration*0.1));
                // update trimAndLoop buttons to clickable
                setTrimAndLoopClickability(true);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void configureSeekBars(int fileDuration) {
        seekBarStartTime.setMax(fileDuration/2);
        seekBarEndTime.setMax(fileDuration/2);
    }

    /**
     * Update chosenFileTextView text to passed parameter.
     */
    private void updateChosenFileText(String text) {
        tvChosenFile.setText(text);
    }

    /**
     * Returns the duration of the audio file found at the given path.
     * If anything went wrong, 0 will be returned.
     */
    private int getAudioFileDuration(String filePath) {
        int duration = 0;
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(filePath);
            String strDuration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            mmr.release();
            duration = Integer.parseInt(strDuration);
        } catch (Exception e) {
            // nothing to be done
        }
        return duration;
    }

    /**
     * Interface to be passed to the RecorderDialogFragment
     */
    public interface OnRecordingSavedListener {
        /**
         * Method to be called when audio file saving successfully done.
         * @param filePath is the string path of the saved file.
         */
        void onSaved(String filePath);
    }
}
