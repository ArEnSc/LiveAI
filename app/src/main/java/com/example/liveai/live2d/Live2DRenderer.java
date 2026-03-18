package com.example.liveai.live2d;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.live2d.sdk.cubism.framework.CubismFramework;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLES20.*;

public class Live2DRenderer implements GLSurfaceView.Renderer {

    private final LAppLive2DManager manager;
    private boolean modelLoaded = false;

    private final String modelDir;
    private final String modelJson;

    private int debugShaderProgram = 0;
    private int debugPositionHandle = 0;
    private int debugColorHandle = 0;

    private final PostProcessFilter postProcess = new PostProcessFilter();

    public Live2DRenderer(LAppLive2DManager manager, String modelDir, String modelJson) {
        this.manager = manager;
        this.modelDir = modelDir;
        this.modelJson = modelJson;
    }

    public PostProcessFilter getPostProcess() {
        return postProcess;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

        GLES20.glEnable(GL_BLEND);
        GLES20.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        CubismFramework.initialize();

        postProcess.init();

        if (LAppDefine.DEBUG_DRAW_BOUNDS) {
            initDebugShader();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        manager.setWindowSize(width, height);
        postProcess.resize(width, height);

        if (!modelLoaded) {
            manager.loadModel(modelDir, modelJson);
            modelLoaded = true;
        }
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        LAppPal.updateTime();

        boolean usePostProcess = postProcess.isAnyFilterEnabled();

        if (usePostProcess) {
            // Render model to FBO
            postProcess.beginCapture();
            GLES20.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            manager.onUpdate();

            // Apply filters and draw to screen
            glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glClearDepthf(1.0f);
            postProcess.endCaptureAndApply();
        } else {
            // Direct render (no filters)
            glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glClearDepthf(1.0f);
            GLES20.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            manager.onUpdate();
        }

        if (LAppDefine.DEBUG_DRAW_BOUNDS) {
            drawDebugBounds();
        }
    }

    private void initDebugShader() {
        String vertexShaderCode =
            "attribute vec4 vPosition;" +
            "void main() { gl_Position = vPosition; }";

        String fragmentShaderCode =
            "precision mediump float;" +
            "uniform vec4 vColor;" +
            "void main() { gl_FragColor = vColor; }";

        int vertexShader = loadShader(GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GL_FRAGMENT_SHADER, fragmentShaderCode);

        debugShaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(debugShaderProgram, vertexShader);
        GLES20.glAttachShader(debugShaderProgram, fragmentShader);
        GLES20.glLinkProgram(debugShaderProgram);

        debugPositionHandle = GLES20.glGetAttribLocation(debugShaderProgram, "vPosition");
        debugColorHandle = GLES20.glGetUniformLocation(debugShaderProgram, "vColor");
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    private void drawDebugBounds() {
        float[] lineVertices = {
            -1.0f, -1.0f,
             1.0f, -1.0f,
             1.0f,  1.0f,
            -1.0f,  1.0f,
            -1.0f, -1.0f
        };

        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(lineVertices.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
        vertexBuffer.put(lineVertices).position(0);

        GLES20.glUseProgram(debugShaderProgram);
        GLES20.glEnableVertexAttribArray(debugPositionHandle);
        GLES20.glVertexAttribPointer(debugPositionHandle, 2, GL_FLOAT, false, 0, vertexBuffer);

        // Green border
        GLES20.glUniform4f(debugColorHandle, 0.0f, 1.0f, 0.0f, 1.0f);
        GLES20.glLineWidth(3.0f);
        GLES20.glDrawArrays(GL_LINE_STRIP, 0, 5);

        GLES20.glDisableVertexAttribArray(debugPositionHandle);
    }
}
