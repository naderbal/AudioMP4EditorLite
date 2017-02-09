package com.nader.mp4lighteditor.recording;

import android.Manifest;
import android.app.DialogFragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.nader.mp4lighteditor.R;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;


/**
 * TODO remove audio output path definition from here, it should be an argument
 */
public class RecorderDialogFragment extends DialogFragment implements View.OnClickListener {
    final Handler handler = new Handler();
    // callback
    OnRecordingSavedListener fragmentListener;
    // recorder state
    public enum RecordButtonState {
        START_PRESSED, STOP_PRESSED
    }
    // views
    private Button btnRecord;
    private Button btnCancel;
    private Button btnSave;
    private LinearLayout llRecorded;
    // timer
    private Timer timer;
    private TimerTask timerTask;
    int secondsCounter = 0;
    // recorder
    private MediaRecorder mediaRecorder;
    private RecordButtonState recordButtonState = RecordButtonState.STOP_PRESSED;
    private File audioFile;
    private String filePath;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_record_dialog, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        init(view);
        checkNeededPermission();
        defineAudioOutput();
        setSaveAndCancelButtonActivation(false);
        updateRecordingButtonText();
    }

    @Override
    public void onAttach(Context context) {
        if (context instanceof OnRecordingSavedListener) {
            fragmentListener = (OnRecordingSavedListener) context;
        } else {
            throw new IllegalStateException("Parent must implement OnRecordingSavedListener");
        }
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        fragmentListener = null;
        //reset the media recording when dialog fragment is detached
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder = null;
        }
    }

    /**
     * TODO?
     */
    private void checkNeededPermission() {
        if (ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    1);
        }
    }

    /**
     * Initializes the fragment's views.
     */
    private void init(View rootView) {
        // init views
        btnRecord = (Button) rootView.findViewById(R.id.btnRecord);
        btnCancel = (Button) rootView.findViewById(R.id.btnCancelRecorded);
        btnSave = (Button) rootView.findViewById(R.id.btnSaveRecorded);
        llRecorded = (LinearLayout) rootView.findViewById(R.id.llRecorded);
        // set click events
        btnRecord.setOnClickListener(this);
        btnCancel.setOnClickListener(this);
        btnSave.setOnClickListener(this);
    }

    private void defineAudioOutput() {
        //create file in external memory, in our folder with name plus timestamp
        filePath = Environment.getExternalStorageDirectory().getPath()+ "/mp4Test/audioMP4"+System.currentTimeMillis()+".mp4";
        audioFile = new File(filePath);
    }

    /**
     * Set Save and cancel buttons activation
     */
    private void setSaveAndCancelButtonActivation(boolean state) {
        setSaveAndCancelButtonsClickability(state);
        setSaveAndCancelButtonOpacity(state);
    }

    // TIMER //

    public void startTimer() {
        //set a new Timer
        timer = new Timer();
        //initialize the TimerTask's job
        initializeTimerTask();
        //schedule the timer, after the first 0ms the TimerTask will run every 1000ms
        timer.schedule(timerTask, 1000, 1000);
    }

    public void stopTimerTask() {
        //stop the timer, if it's not already null
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                //use a handler to run a toast that shows the current timestamp
                handler.post(new Runnable() {
                    public void run() {
                        secondsCounter++;
                        updateRecordingButtonText();
                    }
                });
            }
        };
    }

    /**
     * Updates the timing text of the record button.
     * Initially it is set to 0.
     */
    private void updateRecordingButtonText() {
        // set the timer string format,(0:00)
        String time = String.format("%01d:%02d",
                (secondsCounter % 3600) / 60, (secondsCounter % 60));
        // set the time string text to record button
        btnRecord.setText(time);
    }

    /**
     * Prepares the recorder, adding the wanted configurations to it.
     * @return true if prepare was successful, false otherwise
     */
    private boolean resetRecorder() {
        try {
            //set audio source to microphone
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            //set output format to mp4
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            //set encoding to aac
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            //set output file
            mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
            //try to prepare the media recorder
            mediaRecorder.prepare();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnRecord:
                handleBtnRecordClicked();
                break;
            case R.id.btnCancelRecorded:
                cancelFileSaving();
                break;
            case R.id.btnSaveRecorded:
                saveAudioFile();
                break;
        }
    }

    /**
     * Handles the click of record button
     */
    private void handleBtnRecordClicked() {
        //checks if record button is in the stop state
        if (recordButtonState == RecordButtonState.STOP_PRESSED) {
            handleStopStateRecordButtonPressed();
        }
        // the record button state is start
        else {
            handleStartStateRecordButtonPressed();
        }
    }

    /**
     * Handles the record button click when its in start state.
     */
    private void handleStartStateRecordButtonPressed() {
        //stop the timer
        stopTimerTask();
        //set save and cancel buttons to clickable
        setSaveAndCancelButtonActivation(true);
        //change the record button state to stop
        recordButtonState = RecordButtonState.STOP_PRESSED;
        btnRecord.setBackgroundResource(R.drawable.circular_record_button_green);
    }

    /**
     * Handles the record button click when its in stop state.
     */
    private void handleStopStateRecordButtonPressed() {
        //if media recorder not null reset it
        if (mediaRecorder != null) {
            mediaRecorder.reset();
        }
        //set seconds counter to 0 and initialize new media recorder
        secondsCounter = 0;
        mediaRecorder = new MediaRecorder();
        //checks if reset recorder was successful
        if (resetRecorder()) {
            try {
                btnRecord.setBackgroundResource(R.drawable.circular_record_button_red);
                //start media recorder and timer
                mediaRecorder.start();
                startTimer();
                //change the record button state to start
                recordButtonState = RecordButtonState.START_PRESSED;
                //set save and cancel buttons to unclickable
                setSaveAndCancelButtonActivation(false);
            } catch (IllegalStateException e) {
                //if an exception occurred, dismiss the dialog fragment
                dismiss();
            }
        }
    }

    /**
     * Updates the clickability of the save and cancel buttons.
     */
    private void setSaveAndCancelButtonsClickability(boolean state) {
        btnCancel.setEnabled(state);
        btnSave.setEnabled(state);
    }

    /**
     * Sets the opacity of Save and Cancel buttons according to state
     */
    private void setSaveAndCancelButtonOpacity(boolean state) {
        if (state) {
            btnCancel.getBackground().setAlpha(255);
            btnSave.getBackground().setAlpha(255);
        } else {
            btnCancel.getBackground().setAlpha(16);
            btnSave.getBackground().setAlpha(16);
        }
    }

    /**
     * Cancels the recording done.
     */
    private void cancelFileSaving() {
        if (mediaRecorder != null) {
            //resets the media recorder
            mediaRecorder.reset();
            //set the seconds counter to 0 and update the text of the recorder buuton
            secondsCounter = 0;
            updateRecordingButtonText();
            //set media recorder to null
            mediaRecorder = null;
            btnRecord.setBackgroundResource(R.drawable.circular_record_button_green);
            //set save and cancel buttons to unclickable
            setSaveAndCancelButtonActivation(false);
        }
    }

    /**
     * Saves the audio file recorded
     */
    private void saveAudioFile() {
        //set seconds counter to 0 and update text of recording button
        secondsCounter = 0;
        updateRecordingButtonText();
        btnRecord.setBackgroundResource(R.drawable.circular_record_button_green);
        try {
            //stop, reset and release media recorder
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
            //set save and cancel buttons to unclickable
            setSaveAndCancelButtonActivation(false);
            // inform parent
            if (fragmentListener != null) {
                fragmentListener.onSaved(filePath);
            }
        } catch (Exception e) {
            if (fragmentListener != null) {
                fragmentListener.onFailed();
            }
        }
        dismiss();
    }

    // CALLBACKS //

    /**
     * Interface definition for callbacks to be invoked
     * when events occur in the {@link RecorderDialogFragment}.
     */
    public interface OnRecordingSavedListener {
        /**
         * Called when audio file saving successfully done.
         * @param filePath The path of the saved file.
         */
        void onSaved(String filePath);

        /**
         * Called when an error occurs during file saving.
         */
        void onFailed();
    }
}
