package com.nader.mp4lighteditor;

import android.app.FragmentManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.nader.mp4lighteditor.playback.AudioPlayerFragment;
import com.nader.mp4lighteditor.recording.RecorderDialogFragment;

public class MainActivity extends AppCompatActivity implements View.OnClickListener,
        RecorderDialogFragment.OnRecordingSavedListener{
    // request codes
    private static final int RC_PICK_FILE = 10;
    // views
    private Button btnRecordAudio;
    private Button btnChooseFile;
    private TextView tvChosenFile;
    private Button btnTrimAndLoop;
    // objects
    private String fileSrc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        initAudioPlayerFragment();
    }

    /**
     * init Audio Player Fragment
     *
     */
    private void initAudioPlayerFragment() {
        // create new fragment
        Fragment audioPlayerFragment = new AudioPlayerFragment();
        // add it to backstack
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.frmFragment, audioPlayerFragment, "audioPlayerFragment");
        transaction.commit();
    }

    private void init() {
        // views
        btnRecordAudio = (Button) findViewById(R.id.btnRecordAudio);
        btnChooseFile = (Button) findViewById(R.id.btnChooseFile);
        btnTrimAndLoop = (Button) findViewById(R.id.btnTrimAndLoop);
        tvChosenFile = (TextView) findViewById(R.id.tvChosenFile);
        // set click events
        btnRecordAudio.setOnClickListener(this);
        btnChooseFile.setOnClickListener(this);
        btnTrimAndLoop.setEnabled(false);
        btnTrimAndLoop.setOnClickListener(this);
    }

    /**
     * Handles media player when file is chosen or recorded
     */
    private void handleFileChosenMediaPlayer(String fileSrc) {
        AudioPlayerFragment articleFrag = (AudioPlayerFragment)
                getSupportFragmentManager().findFragmentById(R.id.frmFragment);
        articleFrag.updateFileSrc(fileSrc);
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
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnRecordAudio:
                handleRecordAudioButtonClick();
                break;
            case R.id.btnChooseFile:
                chooseFile();
                break;
            case R.id.btnTrimAndLoop:
                handleTrimAndLoopBtnClicked();
                break;
        }
    }

    /**
     * Handles click of TrimAndLoop button.
     */
    private void handleTrimAndLoopBtnClicked() {
        Intent intent = new Intent(this, TrimAndLoopActivity.class);
        intent.putExtra("fileSrc",fileSrc);
        startActivity(intent);
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
        dialogFragment.show(fm, "dialogFragment");
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
        startActivityForResult(chooseFile, RC_PICK_FILE);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_PICK_FILE) {
            if (data != null) {
                //get uri from intent returned
                Uri uri = data.getData();
                //get file path of the uri
                fileSrc = getPath(this, uri);
                //update chosen file textView with filesrc
                updateChosenFileText(fileSrc);
                handleFileChosenMediaPlayer(fileSrc);
                btnTrimAndLoop.setEnabled(true);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);

    }

    /**
     * Update chosenFileTextView text to passed parameter.
     */
    private void updateChosenFileText(String text) {
        tvChosenFile.setText(text);
    }

    // FRAGMENT CALLBACKS //


    @Override
    public void onSaved(String filePath) {
        //show toast conforming the successful saving of the recorded audio file
        Toast.makeText(getApplicationContext(), "Audio file saved", Toast.LENGTH_SHORT).show();
        // todo what are you doing here?
        fileSrc = filePath;
        btnTrimAndLoop.setEnabled(true);
        updateChosenFileText(fileSrc);
        handleFileChosenMediaPlayer(fileSrc);
    }

    @Override
    public void onFailed() {
        //show toast saying that the saving of the recorded audio file failed
        Toast.makeText(getApplicationContext(), "Audio failed to save", Toast.LENGTH_SHORT).show();
    }
}
