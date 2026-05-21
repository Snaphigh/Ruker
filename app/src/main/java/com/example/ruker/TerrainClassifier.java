package com.example.ruker;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerrainClassifier implements SensorEventListener {

    public interface ClassifierListener {
        void onTerrainDetected(String label, int color, float confidence);
    }

    public enum Terrain {
        IDLE(Color.GRAY, "Idle"),
        SMOOTH(Color.parseColor("#4CAF50"), "Smooth"), // Green
        TOUGH(Color.parseColor("#FFA500"), "Tough"),   // Orange/Yellow
        ROUGH(Color.parseColor("#F44336"), "Rough");   // Red

        public final int color;
        public final String label;

        Terrain(int color, String label) {
            this.color = color;
            this.label = label;
        }

        public static int getColorByLabel(String label) {
            for (Terrain t : values()) {
                if (t.label.equalsIgnoreCase(label)) return t.color;
            }
            return Color.GRAY;
        }
    }

    private static final int RAW_SAMPLE_COUNT = 125;
    private static final int SAMPLES_PER_FRAME = 3;
    private final float[] inputBuffer = new float[RAW_SAMPLE_COUNT * SAMPLES_PER_FRAME];
    
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();
    private int inferenceCounter = 0;
    private static final int INFERENCE_INTERVAL_SAMPLES = 8;
    private static final int SMOOTHING_WINDOW_SIZE = 3;
    private final LinkedList<Terrain> recentTerrains = new LinkedList<>();

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final ClassifierListener listener;

    public TerrainClassifier(Context context, ClassifierListener listener) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.listener = listener;
    }

    public void start() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(inputBuffer, 3, inputBuffer, 0, inputBuffer.length - 3);
            inputBuffer[inputBuffer.length - 3] = event.values[0];
            inputBuffer[inputBuffer.length - 2] = event.values[1];
            inputBuffer[inputBuffer.length - 1] = event.values[2];

            inferenceCounter++;
            if (inferenceCounter >= INFERENCE_INTERVAL_SAMPLES) {
                final float[] bufferCopy = inputBuffer.clone();
                inferenceExecutor.execute(() -> runInference(bufferCopy));
                inferenceCounter = 0;
            }
        }
    }

    private void runInference(float[] data) {
        float[] results = classify(data);
        if (results != null && results.length > 0) {
            int maxIdx = 0;
            float maxVal = -1;
            for (int i = 0; i < results.length; i++) {
                if (results[i] > maxVal) {
                    maxVal = results[i];
                    maxIdx = i;
                }
            }

            Terrain detected;
            // Map indexes to Terrain enum.
            // Assumption based on common Edge Impulse order: Idle (0), Smooth (1), Tough (2), Rough (3)
            switch (maxIdx) {
                case 1: detected = Terrain.SMOOTH; break;
                case 2: detected = Terrain.TOUGH; break;
                case 3: detected = Terrain.ROUGH; break;
                default: detected = Terrain.IDLE;
            }

            synchronized (recentTerrains) {
                recentTerrains.add(detected);
                if (recentTerrains.size() > SMOOTHING_WINDOW_SIZE) {
                    recentTerrains.removeFirst();
                }
                Terrain smoothed = getMostFrequent(recentTerrains);
                if (listener != null) {
                    listener.onTerrainDetected(smoothed.label, smoothed.color, maxVal);
                }
            }
        }
    }

    private Terrain getMostFrequent(List<Terrain> list) {
        if (list.isEmpty()) return Terrain.IDLE;
        Terrain winner = list.get(0);
        int maxCount = 0;
        for (Terrain t : Terrain.values()) {
            int count = Collections.frequency(list, t);
            if (count > maxCount) {
                maxCount = count;
                winner = t;
            }
        }
        return winner;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void shutdown() {
        inferenceExecutor.shutdown();
    }

    static {
        System.loadLibrary("ruker");
    }

    public native float[] classify(float[] input);
}
