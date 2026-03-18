package com.example.liveai.live2d;

import android.content.Context;
import android.util.Log;
import com.live2d.sdk.cubism.core.ICubismLogger;

import java.io.IOException;
import java.io.InputStream;

public class LAppPal {

    public static class PrintLogFunction implements ICubismLogger {
        @Override
        public void print(String message) {
            Log.d(TAG, message);
        }
    }

    private static Context appContext;

    public static void setup(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void updateTime() {
        s_currentFrame = System.nanoTime();
        deltaNanoTime = s_currentFrame - lastNanoTime;
        lastNanoTime = s_currentFrame;
    }

    public static float getDeltaTime() {
        return (float) (deltaNanoTime / 1000000000.0f);
    }

    public static byte[] loadFileAsBytes(String path) {
        try (InputStream stream = appContext.getAssets().open(path)) {
            int size = stream.available();
            byte[] buffer = new byte[size];
            stream.read(buffer, 0, size);
            return buffer;
        } catch (IOException e) {
            Log.e(TAG, "Failed to load file: " + path, e);
            return new byte[0];
        }
    }

    public static void printLog(String message) {
        Log.d(TAG, message);
    }

    private static double s_currentFrame;
    private static double lastNanoTime;
    private static double deltaNanoTime;

    private static final String TAG = "LiveAI-L2D";
}
