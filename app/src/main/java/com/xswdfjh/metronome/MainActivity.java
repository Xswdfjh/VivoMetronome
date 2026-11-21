package com.xswdfjh.metronome;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvBpm;
    private SeekBar seekBarBpm;
    private Button btnPlay, btnPlus, btnMinus, btnExit;
    private Spinner spinnerTimeSig, spinnerSound;
    private Switch switchFlash;

    private int bpm = 60;
    private boolean isPlaying = false;
    private int currentBeat = 0;
    private int beatsPerMeasure = 4;
    private String soundMode = "woodblock";
    private boolean isFlashOn = false;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable metronomeRunnable;

    private AudioTrack woodblockTrackAccent; 
    private AudioTrack woodblockTrackNormal; 
    private TextToSpeech tts;

    private CameraManager cameraManager;
    private String cameraId;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initAudio();
        initCamera();
        initWakeLock();
        setupMetronomeRunnable();
    }

    private void initViews() {
        tvBpm = findViewById(R.id.tvBpm);
        seekBarBpm = findViewById(R.id.seekBarBpm);
        btnPlay = findViewById(R.id.btnPlay);
        btnPlus = findViewById(R.id.btnPlus);
        btnMinus = findViewById(R.id.btnMinus);
        btnExit = findViewById(R.id.btnExit);
        spinnerTimeSig = findViewById(R.id.spinnerTimeSig);
        spinnerSound = findViewById(R.id.spinnerSound);
        switchFlash = findViewById(R.id.switchFlash);

        seekBarBpm.setMax(60);
        seekBarBpm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                bpm = 40 + progress;
                tvBpm.setText(String.valueOf(bpm));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnPlus.setOnClickListener(v -> { if (bpm < 100) { bpm++; updateBpmUI(); } });
        btnMinus.setOnClickListener(v -> { if (bpm > 40) { bpm--; updateBpmUI(); } });
        btnPlay.setOnClickListener(v -> togglePlay());
        btnExit.setOnClickListener(v -> exitApp());

        String[] timeSigs = {"4/4 拍", "3/4 拍", "2/4 拍", "6/8 拍", "3/8 拍", "12/8 拍"};
        ArrayAdapter<String> adapterTime = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, timeSigs);
        adapterTime.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTimeSig.setAdapter(adapterTime);
        spinnerTimeSig.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: beatsPerMeasure = 4; break;
                    case 1: beatsPerMeasure = 3; break;
                    case 2: beatsPerMeasure = 2; break;
                    case 3: beatsPerMeasure = 6; break;
                    case 4: beatsPerMeasure = 3; break;
                    case 5: beatsPerMeasure = 12; break;
                }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        String[] soundTypes = {"清脆木鱼", "温柔女声"};
        ArrayAdapter<String> adapterSound = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, soundTypes);
        adapterSound.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSound.setAdapter(adapterSound);
        spinnerSound.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                soundMode = (position == 0) ? "woodblock" : "voice";
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        switchFlash.setOnCheckedChangeListener((v, isChecked) -> isFlashOn = isChecked);
    }

    private void updateBpmUI() {
        tvBpm.setText(String.valueOf(bpm));
        seekBarBpm.setProgress(bpm - 40);
    }

    private void initAudio() {
        woodblockTrackAccent = generateWoodblockTone(700.0, 900.0, 1.0, 0.04);
        woodblockTrackNormal = generateWoodblockTone(1200.0, 1500.0, 0.4, 0.06);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINA);
                tts.setPitch(1.1f);       
                tts.setSpeechRate(0.95f); 
                AudioAttributes ttsAttr = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                tts.setAudioAttributes(ttsAttr);
            }
        });
    }

    private AudioTrack generateWoodblockTone(double baseFreq, double overtoneFreq, double volume, double duration) {
        int sampleRate = 44100;
        int numSamples = (int) (duration * sampleRate);
        double[] sample = new double[numSamples];
        short[] buffer = new short[numSamples];

        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;
            double envelope = Math.exp(-(1.0 / duration) * t * 2); 
            double wave = Math.sin(2 * Math.PI * baseFreq * t) + 0.6 * Math.sin(2 * Math.PI * overtoneFreq * t);
            sample[i] = wave * envelope * volume;
            buffer[i] = (short) (sample[i] * 32767);
        }

        AudioTrack track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.length * 2,
                AudioTrack.MODE_STATIC
        );
        track.write(buffer, 0, buffer.length);
        return track;
    }

    private void initCamera() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try { if (cameraManager != null) cameraId = cameraManager.getCameraIdList()[0]; }
        catch (CameraAccessException e) { e.printStackTrace(); }
    }

    private void initWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Metronome::WakeLock");
    }

    private void setupMetronomeRunnable() {
        metronomeRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPlaying) return;

                if ("woodblock".equals(soundMode)) {
                    if (currentBeat == 0) {
                        woodblockTrackAccent.stop();
                        woodblockTrackAccent.reloadStaticData();
                        woodblockTrackAccent.play();
                    } else {
                        woodblockTrackNormal.stop();
                        woodblockTrackNormal.reloadStaticData();
                        woodblockTrackNormal.play();
                    }
                } else {
                    if (tts != null) {
                        String[] cnNums = {"一", "二", "三", "四"};
                        tts.speak(cnNums[currentBeat], TextToSpeech.QUEUE_FLUSH, null, "Beat");
                    }
                }

                if (isFlashOn) {
                    flashLight();
                }

                currentBeat++;
                if (currentBeat >= beatsPerMeasure) currentBeat = 0;
                
                handler.postDelayed(this, (long) (60000.0 / bpm));
            }
        };
    }

    private void flashLight() {
        if (cameraManager == null || cameraId == null) return;
        try {
            cameraManager.setTorchMode(cameraId, true);
            handler.postDelayed(() -> {
                try { cameraManager.setTorchMode(cameraId, false); } catch (Exception e) {}
            }, 60);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void togglePlay() {
        if (isPlaying) {
            isPlaying = false; handler.removeCallbacks(metronomeRunnable);
            btnPlay.setText("开始"); currentBeat = 0;
            if (tts != null) tts.stop();
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } else {
            isPlaying = true; btnPlay.setText("停止"); currentBeat = 0;
            if (wakeLock != null) wakeLock.acquire();
            handler.post(metronomeRunnable);
        }
    }

    private void exitApp() {
        if (isPlaying) {
            isPlaying = false;
            handler.removeCallbacks(metronomeRunnable);
            if (tts != null) tts.stop();
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
        finishAffinity();
        System.exit(0);
    }

    @Override
    public void onBackPressed() {
        exitApp();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (woodblockTrackAccent != null) { woodblockTrackAccent.release(); }
        if (woodblockTrackNormal != null) { woodblockTrackNormal.release(); }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        handler.removeCallbacks(metronomeRunnable);
    }
}
