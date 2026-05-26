package com.example.media_playerapp;
import android.annotation.SuppressLint;                        //Used to ignore warnings
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;                                       //Represents file path
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;                                     //Ensures code runs on main UI thread
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
    private Uri currentAudioUri = null;
    private String currentVideoUrl = null;

    // Progress updater
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (isAudioMode && mediaPlayer != null && mediaPlayer.isPlaying()) {
                int current = mediaPlayer.getCurrentPosition();
                int total = mediaPlayer.getDuration();
                seekBar.setMax(total);
                seekBar.setProgress(current);
                tvCurrentTime.setText(formatTime(current));
                tvTotalTime.setText(formatTime(total));
                handler.postDelayed(this, 500);
            } else if (!isAudioMode && videoView != null && videoView.isPlaying()) {
                int current = videoView.getCurrentPosition();
                int total = videoView.getDuration();
                if (total > 0) {
                    seekBar.setMax(total);
                    seekBar.setProgress(current);
                    tvCurrentTime.setText(formatTime(current));
                    tvTotalTime.setText(formatTime(total));
                }
                handler.postDelayed(this, 500);
            }
        }
    };

    // File picker launcher
    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedUri = result.getData().getData();
                    if (selectedUri != null) {
                        // Persist permissions to ensure we can play it
                        try {
                            getContentResolver().takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception ignored) {}

                        resetPlayer();
                        currentAudioUri = selectedUri;
                        
                        String fileName = selectedUri.getLastPathSegment();
                        if (fileName != null && fileName.contains("/")) {
                            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
                        }
                        tvNowPlaying.setText(fileName != null ? fileName : "Selected file");
                        
                        if (isAudioMode) {
                            setStatus("Audio loaded — press Play", "#03DAC6");
                            setDotColor("#03DAC6");
                        } else {
                            videoView.setVideoURI(selectedUri);
                            isVideoReady = true;
                            setStatus("Video loaded — press Play", "#03DAC6");
                            setDotColor("#03DAC6");
                        }
                    }
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
            btnOpenFile.setVisibility(View.VISIBLE);
            if (isAudioMode) {
                cardAudio.setVisibility(View.VISIBLE);
                cardVideo.setVisibility(View.GONE);
                layoutUrl.setVisibility(View.GONE);
                btnOpenFile.setText("📁  Open Audio File from Disk");
                tvNowPlaying.setText("No file selected");
                setStatus("Audio mode — open a file to begin", "#888888");
            } else {
                cardAudio.setVisibility(View.GONE);
                cardVideo.setVisibility(View.VISIBLE);
                layoutUrl.setVisibility(View.VISIBLE);
                btnOpenFile.setText("📁  Open Video File from Disk");
                setStatus("Video mode — Load URL or Open File", "#888888");
            }
            resetProgress();
        });
    }

    private void setupButtons() {
        btnOpenFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            String[] mimeTypes = {"audio/*", "video/*"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            filePickerLauncher.launch(intent);
        });

        btnOpenUrl.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) {
                setStatus("⚠ Please enter a video URL", "#CF6679");
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                setStatus("⚠ URL must start with http:// or https://", "#CF6679");
                return;
            }
            resetPlayer();
            currentVideoUrl = url;
            isVideoReady = false;
            setStatus("Loading video...", "#FF9800");
            setDotColor("#FF9800");

            Uri videoUri = Uri.parse(currentVideoUrl);
            videoView.setVideoURI(videoUri);

            videoView.setOnPreparedListener(mp -> {
                isVideoReady = true;
                tvTotalTime.setText(formatTime(videoView.getDuration()));
                setStatus("Video ready — press Play", "#03DAC6");
                setDotColor("#03DAC6");
            });

            videoView.setOnCompletionListener(mp -> {
                setStatus("Playback complete", "#BB86FC");
                setDotColor("#BB86FC");
                resetProgress();
                handler.removeCallbacks(progressUpdater);
            });

            videoView.setOnErrorListener((mp, what, extra) -> {
                setStatus("⚠ Error loading video", "#CF6679");
                setDotColor("#CF6679");
                isVideoReady = false;
                return true;
            });

            MediaController mediaController = new MediaController(this);
            mediaController.setAnchorView(videoView);
            videoView.setMediaController(mediaController);
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
                        tvCurrentTime.setText(formatTime(progress));
                    } else if (!isAudioMode && (isVideoReady || videoView.isPlaying())) {
                        videoView.seekTo(progress);
                        tvCurrentTime.setText(formatTime(progress));
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void handlePlay() {
        if (isAudioMode) {
            if (currentAudioUri == null) {
                setStatus("⚠ No audio file selected", "#CF6679");
                return;
            }
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(this, currentAudioUri);
                    mediaPlayer.prepare();
                    mediaPlayer.setOnCompletionListener(mp -> {
                        setStatus("Playback complete", "#BB86FC");
                        setDotColor("#BB86FC");
                        resetProgress();
                        handler.removeCallbacks(progressUpdater);
                    });
                } catch (Exception e) {
                    setStatus("⚠ Error: " + e.getMessage(), "#CF6679");
                    return;
                }
            }
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                setStatus("▶ Playing", "#4CAF50");
                setDotColor("#4CAF50");
                handler.post(progressUpdater);
            }
        } else {
            if (!isVideoReady && !videoView.isPlaying()) {
                setStatus("⚠ Load a video first", "#CF6679");
                return;
            }
            videoView.start();
            setStatus("▶ Playing", "#4CAF50");
            setDotColor("#4CAF50");
            handler.post(progressUpdater);
        }
    }

    private void handlePause() {
        if (isAudioMode) {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                setStatus("⏸ Paused", "#FF9800");
                setDotColor("#FF9800");
                handler.removeCallbacks(progressUpdater);
            }
        } else {
            if (videoView.isPlaying()) {
                videoView.pause();
                setStatus("⏸ Paused", "#FF9800");
                setDotColor("#FF9800");
                handler.removeCallbacks(progressUpdater);
            }
        }
    }

    private void handleStop() {
        resetPlayer();
        setStatus("⏹ Stopped", "#888888");
        setDotColor("#555555");
        resetProgress();
    }

    private void handleRestart() {
        if (isAudioMode) {
            if (currentAudioUri == null) {
                setStatus("⚠ No audio file selected", "#CF6679");
                return;
            }
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
            handlePlay();
        } else {
            if (!isVideoReady && currentVideoUrl == null && currentAudioUri == null) {
                setStatus("⚠ No video loaded", "#CF6679");
                return;
            }
            videoView.seekTo(0);
            videoView.start();
            setStatus("↺ Restarted", "#4CAF50");
            setDotColor("#4CAF50");
            handler.post(progressUpdater);
        }
    }

    private void resetPlayer() {
        handler.removeCallbacks(progressUpdater);
        if (mediaPlayer != null) {
            try { if (mediaPlayer.isPlaying()) mediaPlayer.stop(); } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
        videoView.stopPlayback();
        // Keep isVideoReady true if we are just stopping, 
        // but handle it in playback logic
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(progressUpdater);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
