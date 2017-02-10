package com.nader.mp4lighteditor.playback;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import com.nader.mp4lighteditor.R;

/**
 * Created by Nader on 06-Feb-17.
 */

public class CustomAudioPlayer extends LinearLayout {
    public CustomAudioPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.custom_audio_player, this);
    }
}
