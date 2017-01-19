package com.nader.mp4lighteditor;

import android.Manifest;
import android.app.DialogFragment;
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
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;


public class RecorderDialogFragment extends DialogFragment implements View.OnClickListener {
    final Handler handler = new Handler();
    int secondsCounter = 0;
    private Button btnRecord;
    private Button btnCancel;
    private Button btnSave;
    private MediaRecorder mediaRecorder;
    private File audioFile;
    private String filePath;
    private Timer timer;
    private TimerTask timerTask;
    private LinearLayout llRecorded;
    private RecordButtonState recordButtonState = RecordButtonState.STOP_PRESSED;
    private MainActivity.OnRecordingSavedListener listener;
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_record_dialog, container, false);
        init(rootView);
        return rootView;
    }

    private void init(View rootView) {

        if (ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    1);
        }
        btnRecord = (Button) rootView.findViewById(R.id.btnRecord);
        btnRecord.setOnClickListener(this);
        btnCancel = (Button) rootView.findViewById(R.id.btnCancelRecorded);
        btnCancel.setOnClickListener(this);
        btnSave = (Button) rootView.findViewById(R.id.btnSaveRecorded);
        btnSave.setOnClickListener(this);
        llRecorded = (LinearLayout) rootView.findViewById(R.id.llRecorded);
        //create file in external memory, in our folder with name plus timestamp
        filePath = Environment.getExternalStorageDirectory().getPath()+ "/mp4Test/audioMP4"+System.currentTimeMillis()+".mp4";
        audioFile = new File(filePath);
        setSaveAndCancelButtonActivation(false);
        updateRecordingButtonText();
    }

    /**
     * Set Save and cancel buttons activation
     */
    private void setSaveAndCancelButtonActivation(boolean state) {
        setSaveAndCancelButtonsClickability(state);
        setSaveAndCancelButtonOpacity(state);
    }

    public void setListener(MainActivity.OnRecordingSavedListener listnener){
        this.listener = listnener;
    }

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
     * Updates the timing text of the record button
     */
    private void updateRecordingButtonText() {
        //set the timer string format,(0:00)
        String time = String.format("%01d:%02d",
                (secondsCounter % 3600) / 60, (secondsCounter % 60));
        //set the time string text to record button
        btnRecord.setText(time);
    }

    /**
     * Prepares the recorder, adding the wanted configurations to it.
     * @return true if prepare was successful, false otherwise
     */
    private boolean resetRecorder() {
        //set audio source to microphone
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        //set output format to mp4
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        //set encoding to aac
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        //set output file
        mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
        try {
            //try to prepare the media recorder
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            return false;
        } catch (IOException e) {
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
            listener.onSaved(filePath);
            mediaRecorder = null;
            //set save and cancel buttons to unclickable
            setSaveAndCancelButtonActivation(false);
            //show toast conforming the successful saving of the recorded audio file
            Toast.makeText(getActivity(), "Audio file saved", Toast.LENGTH_SHORT).show();
            dismiss();
        } catch (Exception e) {
            //show toast saying that the saving of the recorded audio file failed
            Toast.makeText(getActivity(), "Audio failed to save", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        //reset the media recording when dialog fragment is detached
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder = null;
        }
    }


    public enum RecordButtonState {
        START_PRESSED, STOP_PRESSED
    }
}
