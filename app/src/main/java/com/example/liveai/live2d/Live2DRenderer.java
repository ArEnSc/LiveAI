package com.example.liveai.live2d;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.live2d.sdk.cubism.framework.CubismFramework;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLES20.*;

public class Live2DRenderer implements GLSurfaceView.Renderer {

    private final LAppLive2DManager manager;
    private boolean modelLoaded = false;

    private final String modelDir;
    private final String modelJson;

    public Live2DRenderer(LAppLive2DManager manager, String modelDir, String modelJson) {
        this.manager = manager;
        this.modelDir = modelDir;
        this.modelJson = modelJson;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

        GLES20.glEnable(GL_BLEND);
        GLES20.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        CubismFramework.initialize();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        manager.setWindowSize(width, height);

        if (!modelLoaded) {
            manager.loadModel(modelDir, modelJson);
            modelLoaded = true;
        }
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        LAppPal.updateTime();

        // Transparent background for overlay
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glClearDepthf(1.0f);

        GLES20.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        manager.onUpdate();
    }
}
