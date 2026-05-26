package com.example.media_playerapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // UI Elements
    private RadioGroup radioGroupMode;
    private CardView cardAudio, cardVideo;
    private LinearLayout layoutUrl;
    private VideoView videoView;
    private SeekBar seekBar;
    private TextView tvNowPlaying, tvStatus, tvCurrentTime, tvTotalTime;
    private View statusDot;
    private Button btnOpenFile, btnOpenUrl, btnPlay, btnPause, btnStop, btnRestart;
    private EditText etUrl;

    // Media
    private MediaPlayer mediaPlayer;
    private boolean isAudioMode = true;
    private boolean isVideoReady = false;
    private Uri currentMediaUri = null;
    private String currentVideoUrl = null;

    // Progress updater
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (isAudioMode && mediaPlayer != null && mediaPlayer.isPlaying()) {
                int current = mediaPlayer.getCurrentPosition();
                int total = mediaPlayer.getDuration();
                updateProgressUI(current, total);
                handler.postDelayed(this, 500);
            } else if (!isAudioMode && videoView != null && videoView.isPlaying()) {
                int current = videoView.getCurrentPosition();
                int total = videoView.getDuration();
                if (total > 0) {
                    updateProgressUI(current, total);
                }
                handler.postDelayed(this, 500);
            }
        }
    };

    private void updateProgressUI(int current, int total) {
        seekBar.setMax(total);
        seekBar.setProgress(current);
        tvCurrentTime.setText(formatTime(current));
        tvTotalTime.setText(formatTime(total));
    }

    // Modern File picker launcher using OpenDocument contract
    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    try {
                        // Persist permissions so we can play the file later
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}

                    resetPlayer();
                    currentMediaUri = uri;
                    
                    String fileName = getFileName(uri);
                    tvNowPlaying.setText(fileName);
                    
                    if (isAudioMode) {
                        setStatus("Audio file loaded — press Play", "#03DAC6");
                        setDotColor("#03DAC6");
                    } else {
                        videoView.setVideoURI(uri);
                        videoView.setOnPreparedListener(mp -> {
                            isVideoReady = true;
                            tvTotalTime.setText(formatTime(videoView.getDuration()));
                            setStatus("Video file loaded — press Play", "#03DAC6");
                            setDotColor("#03DAC6");
                        });
                        videoView.setOnErrorListener((mp, what, extra) -> {
                            setStatus("⚠ Error loading video file", "#CF6679");
                            setDotColor("#CF6679");
                            isVideoReady = false;
                            return true;
                        });
                    }
                } else {
                    setStatus("File selection cancelled", "#888888");
                }
            });

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupModeToggle();
        setupButtons();
        setupSeekBar();
        
        setStatus("Ready — select a file or URL", "#888888");
    }

    private void bindViews() {
        radioGroupMode  = findViewById(R.id.radioGroupMode);
        cardAudio       = findViewById(R.id.cardAudio);
        cardVideo       = findViewById(R.id.cardVideo);
        layoutUrl       = findViewById(R.id.layoutUrl);
        videoView       = findViewById(R.id.videoView);
        seekBar         = findViewById(R.id.seekBar);
        tvNowPlaying    = findViewById(R.id.tvNowPlaying);
        tvStatus        = findViewById(R.id.tvStatus);
        tvCurrentTime   = findViewById(R.id.tvCurrentTime);
        tvTotalTime     = findViewById(R.id.tvTotalTime);
        statusDot       = findViewById(R.id.statusDot);
        btnOpenFile     = findViewById(R.id.btnOpenFile);
        btnOpenUrl      = findViewById(R.id.btnOpenUrl);
        btnPlay         = findViewById(R.id.btnPlay);
        btnPause        = findViewById(R.id.btnPause);
        btnStop         = findViewById(R.id.btnStop);
        btnRestart      = findViewById(R.id.btnRestart);
        etUrl           = findViewById(R.id.etUrl);
    }

    private void setupModeToggle() {
        radioGroupMode.setOnCheckedChangeListener((group, checkedId) -> {
            isAudioMode = (checkedId == R.id.radioAudio);
            resetPlayer();
            resetProgress();
            
            if (isAudioMode) {
                cardAudio.setVisibility(View.VISIBLE);
                cardVideo.setVisibility(View.GONE);
                layoutUrl.setVisibility(View.GONE);
                btnOpenFile.setText("📁  Open Audio File");
                tvNowPlaying.setText("No audio selected");
                setStatus("Switched to Audio Mode", "#888888");
            } else {
                cardAudio.setVisibility(View.GONE);
                cardVideo.setVisibility(View.VISIBLE);
                layoutUrl.setVisibility(View.VISIBLE);
                btnOpenFile.setText("📁  Open Video File");
                tvNowPlaying.setText("No video selected");
                setStatus("Switched to Video Mode", "#888888");
            }
        });
    }

    private void setupButtons() {
        btnOpenFile.setOnClickListener(v -> {
            // Launching the picker with both audio and video types to ensure files are not greyed out
            filePickerLauncher.launch(new String[]{"audio/*", "video/*"});
        });

        btnOpenUrl.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) {
                setStatus("⚠ Please enter a URL", "#CF6679");
                return;
            }
            resetPlayer();
            currentVideoUrl = url;
            isVideoReady = false;
            setStatus("Loading URL...", "#FF9800");
            setDotColor("#FF9800");

            videoView.setVideoURI(Uri.parse(currentVideoUrl));
            videoView.setOnPreparedListener(mp -> {
                isVideoReady = true;
                tvTotalTime.setText(formatTime(videoView.getDuration()));
                setStatus("URL loaded — press Play", "#03DAC6");
                setDotColor("#03DAC6");
            });
            videoView.setOnErrorListener((mp, what, extra) -> {
                setStatus("⚠ Error loading URL", "#CF6679");
                setDotColor("#CF6679");
                return true;
            });
        });

        btnPlay.setOnClickListener(v -> handlePlay());
        btnPause.setOnClickListener(v -> handlePause());
        btnStop.setOnClickListener(v -> handleStop());
        btnRestart.setOnClickListener(v -> handleRestart());
    }

    private void setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (isAudioMode && mediaPlayer != null) {
                        mediaPlayer.seekTo(progress);
                    } else if (!isAudioMode && (isVideoReady || videoView.isPlaying())) {
                        videoView.seekTo(progress);
                    }
                    tvCurrentTime.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void handlePlay() {
        if (isAudioMode) {
            if (currentMediaUri == null) {
                setStatus("⚠ Select an audio file first", "#CF6679");
                return;
            }
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(this, currentMediaUri);
                    mediaPlayer.prepare();
                    mediaPlayer.setOnCompletionListener(mp -> {
                        setStatus("Playback finished", "#BB86FC");
                        setDotColor("#BB86FC");
                        handler.removeCallbacks(progressUpdater);
                    });
                } catch (Exception e) {
                    setStatus("⚠ Playback Error", "#CF6679");
                    return;
                }
            }
            mediaPlayer.start();
            setStatus("▶ Playing Audio", "#4CAF50");
        } else {
            if (!isVideoReady && !videoView.isPlaying() && currentMediaUri != null) {
                videoView.setVideoURI(currentMediaUri);
            }
            if (!isVideoReady && !videoView.isPlaying()) {
                setStatus("⚠ Load video first", "#CF6679");
                return;
            }
            videoView.start();
            setStatus("▶ Playing Video", "#4CAF50");
        }
        setDotColor("#4CAF50");
        handler.post(progressUpdater);
    }

    private void handlePause() {
        if (isAudioMode && mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        } else if (!isAudioMode && videoView.isPlaying()) {
            videoView.pause();
        }
        setStatus("⏸ Paused", "#FF9800");
        setDotColor("#FF9800");
        handler.removeCallbacks(progressUpdater);
    }

    private void handleStop() {
        resetPlayer();
        resetProgress();
        setStatus("⏹ Stopped", "#888888");
        setDotColor("#555555");
    }

    private void handleRestart() {
        handleStop();
        handlePlay();
    }

    private void resetPlayer() {
        handler.removeCallbacks(progressUpdater);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        videoView.stopPlayback();
        isVideoReady = false;
    }

    private void resetProgress() {
        seekBar.setProgress(0);
        tvCurrentTime.setText("0:00");
        tvTotalTime.setText("0:00");
    }

    private void setStatus(String message, String hexColor) {
        tvStatus.setText(message);
        tvStatus.setTextColor(android.graphics.Color.parseColor(hexColor));
    }

    private void setDotColor(String hexColor) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(android.graphics.Color.parseColor(hexColor));
        statusDot.setBackground(dot);
    }

    private String formatTime(int millis) {
        int seconds = (millis / 1000) % 60;
        int minutes = (millis / 1000) / 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    @SuppressLint("Range")
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) result = result.substring(cut + 1);
            }
        }
        return result != null ? result : "Unknown File";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        resetPlayer();
    }
}
