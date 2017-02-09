package com.nader.mp4lighteditor.playback;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.nader.mp4lighteditor.R;

import java.io.File;


public class AudioPlayerFragment extends Fragment {
    private Button btnChosenFilePlayAudio;
    private MediaPlayer mediaPlayer;
    private SeekBar seekChosenAudio;
    private TextView tvAudioCurrentPosition;
    boolean isAudioFilePlaying = false;
    private Handler myHandler = new Handler();
    //Integer which increments with the runnable of audio track
    int runnableTimeCounter = 0;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.custom_audio_player, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        init(view);
    }
    public void updateFileSrc(String fileSrc){
        handleFileChosenMediaPlayer(fileSrc);
    }

    private void init(View view) {
        seekChosenAudio = (SeekBar) view.findViewById(R.id.seekPlayAudio);
        seekChosenAudio.setClickable(false);
        btnChosenFilePlayAudio = (Button) view.findViewById(R.id.btnPlayAudio);
        setPlayButtonEnabled(false);
        btnChosenFilePlayAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handlePlayClicked();
                if (!isAudioFilePlaying) {
                    btnChosenFilePlayAudio.setBackgroundResource(R.drawable.ic_pause_black_24dp);
                    isAudioFilePlaying = true;
                }else {
                    btnChosenFilePlayAudio.setBackgroundResource(R.drawable.ic_play_arrow_black_24dp);
                    isAudioFilePlaying = false;
                }
            }
        });
        tvAudioCurrentPosition = (TextView) view.findViewById(R.id.tvAudioCurrentPosition);
        tvAudioCurrentPosition.setText("0.0s");
    }

    /**
     * Handles the actions of pressing the media play button.
     */
    private void handlePlayClicked() {
        //run updateTrackTime after 100ms
        myHandler.postDelayed(trackTimeRunnable, 100);
        //if already playing, pause
        if (mediaPlayer.isPlaying()){
            mediaPlayer.pause();
        }
        //else start from from beginning or last time paused
        else {
            mediaPlayer.start();
        }
    }

    /**
     * Sets play button to enabled or not.
     */
    private void setPlayButtonEnabled(boolean enabled) {
        if (enabled) btnChosenFilePlayAudio.setBackgroundResource(R.drawable.ic_play_arrow_black_24dp);
        else btnChosenFilePlayAudio.setBackgroundResource(R.drawable.ic_play_arrow_faded_24dp);
        btnChosenFilePlayAudio.setEnabled(enabled);
    }

    /**
     * Runnable which runs with the media player and updates UI accordingly
     */
    private Runnable trackTimeRunnable = new Runnable() {
        public void run() {
            //checks if time passed is less than or equal to the track's duration
            if (runnableTimeCounter <= mediaPlayer.getDuration()) {
                //check if the track is running
                if ((isAudioFilePlaying)) {
                    //get current position of the track
                    int mediaPlayerPosition = mediaPlayer.getCurrentPosition();
                    //change the ms to seconds with one decimal
                    int value = mediaPlayerPosition/100;
                    //set value to the text view of the seek bar
                    tvAudioCurrentPosition.setText(value / 10.0 + "s");
                    //update progress of seek bar
                    seekChosenAudio.setProgress(mediaPlayerPosition);
                    //re run after 100ms delay
                    myHandler.postDelayed(this, 100);
                    //increment timer by 100ms
                    runnableTimeCounter += 100;
                }
            }
            else{
                //else the track reached it's end
                handleTrackEnd();
            }
        }
    };

    /**
     * Handles the end of the track
     */
    private void handleTrackEnd() {
        resetAudioUI();

    }

    /**
     * Set the UI components and parameters to start position
     */
    private void resetAudioUI() {
        //set seek bar to start
        seekChosenAudio.setProgress(0);
        //set text of text view of the seek bar to 0.0s
        tvAudioCurrentPosition.setText("0.0s");
        //change value of is playing to false
        isAudioFilePlaying = false;
        //change icon from pause to play
        btnChosenFilePlayAudio.setBackgroundResource(R.drawable.ic_play_arrow_black_24dp);
        runnableTimeCounter = 0;
    }

    /**
     * Handles media player when file is chosen or recorded
     */
    private void handleFileChosenMediaPlayer(String fileSrc) {
        resetMediaPlayer(fileSrc);
        seekChosenAudio.setMax(mediaPlayer.getDuration());
        setPlayButtonEnabled(true);
        //set seek bar to start
        resetAudioUI();
    }

    /**
     * Resets media player and creates a new instance with the file path passed to it.
     * @param filePath is the file path string of the file.
     */
    private void resetMediaPlayer(String filePath){
        if (mediaPlayer != null) {
            mediaPlayer.reset();
        }
        mediaPlayer = MediaPlayer.create(getActivity(), Uri.fromFile(new File(filePath)));
    }

}
