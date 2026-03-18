package com.example.liveai.live2d;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class PostProcessFilter {

    private int fboId;
    private int fboTextureId;
    private int fboWidth;
    private int fboHeight;

    // Saturation shader
    private int saturationProgram;
    private int satPositionHandle;
    private int satTexCoordHandle;
    private int satTextureHandle;
    private int satAmountHandle;

    // Outline shader
    private int outlineProgram;
    private int outPositionHandle;
    private int outTexCoordHandle;
    private int outTextureHandle;
    private int outTexelSizeHandle;
    private int outColorHandle;
    private int outThicknessHandle;

    // Second FBO for multi-pass
    private int fbo2Id;
    private int fbo2TextureId;

    private boolean saturationEnabled = false;
    private boolean outlineEnabled = false;
    private float saturationAmount = 1.5f;
    private float outlineThickness = 1.5f;
    private float[] outlineColor = {0.0f, 0.0f, 0.0f, 1.0f};

    private final FloatBuffer quadBuffer;

    public PostProcessFilter() {
        float[] quadVertices = {
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
        };
        quadBuffer = ByteBuffer.allocateDirect(quadVertices.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
        quadBuffer.put(quadVertices).position(0);
    }

    public void init() {
        initSaturationShader();
        initOutlineShader();
    }

    public void resize(int width, int height) {
        fboWidth = width;
        fboHeight = height;
        createFBO();
    }

    public boolean isAnyFilterEnabled() {
        return saturationEnabled || outlineEnabled;
    }

    public void setSaturationEnabled(boolean enabled) {
        this.saturationEnabled = enabled;
    }

    public boolean isSaturationEnabled() {
        return saturationEnabled;
    }

    public void setOutlineEnabled(boolean enabled) {
        this.outlineEnabled = enabled;
    }

    public boolean isOutlineEnabled() {
        return outlineEnabled;
    }

    public void setSaturationAmount(float amount) {
        this.saturationAmount = amount;
    }

    public float getSaturationAmount() {
        return saturationAmount;
    }

    public float getOutlineThickness() {
        return outlineThickness;
    }

    public void setOutlineThickness(float thickness) {
        this.outlineThickness = thickness;
    }

    public float[] getOutlineColor() {
        return outlineColor;
    }

    public void setOutlineColor(float r, float g, float b, float a) {
        outlineColor[0] = r;
        outlineColor[1] = g;
        outlineColor[2] = b;
        outlineColor[3] = a;
    }

    public void beginCapture() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        GLES20.glViewport(0, 0, fboWidth, fboHeight);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    }

    public void endCaptureAndApply() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, fboWidth, fboHeight);

        int currentTexture = fboTextureId;

        if (saturationEnabled && outlineEnabled) {
            // Pass 1: saturation -> fbo2
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo2Id);
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            applySaturation(currentTexture);

            // Pass 2: outline -> screen
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            applyOutline(fbo2TextureId);
        } else if (saturationEnabled) {
            applySaturation(currentTexture);
        } else if (outlineEnabled) {
            applyOutline(currentTexture);
        } else {
            // No filter, just draw the texture
            drawTexturePassthrough(currentTexture);
        }
    }

    private void applySaturation(int textureId) {
        GLES20.glUseProgram(saturationProgram);

        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(satPositionHandle);
        GLES20.glVertexAttribPointer(satPositionHandle, 2, GLES20.GL_FLOAT, false, 16, quadBuffer);

        quadBuffer.position(2);
        GLES20.glEnableVertexAttribArray(satTexCoordHandle);
        GLES20.glVertexAttribPointer(satTexCoordHandle, 2, GLES20.GL_FLOAT, false, 16, quadBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(satTextureHandle, 0);
        GLES20.glUniform1f(satAmountHandle, saturationAmount);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(satPositionHandle);
        GLES20.glDisableVertexAttribArray(satTexCoordHandle);
    }

    private void applyOutline(int textureId) {
        GLES20.glUseProgram(outlineProgram);

        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(outPositionHandle);
        GLES20.glVertexAttribPointer(outPositionHandle, 2, GLES20.GL_FLOAT, false, 16, quadBuffer);

        quadBuffer.position(2);
        GLES20.glEnableVertexAttribArray(outTexCoordHandle);
        GLES20.glVertexAttribPointer(outTexCoordHandle, 2, GLES20.GL_FLOAT, false, 16, quadBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(outTextureHandle, 0);
        GLES20.glUniform2f(outTexelSizeHandle, 1.0f / fboWidth, 1.0f / fboHeight);
        GLES20.glUniform4f(outColorHandle, outlineColor[0], outlineColor[1], outlineColor[2], outlineColor[3]);
        GLES20.glUniform1f(outThicknessHandle, outlineThickness);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(outPositionHandle);
        GLES20.glDisableVertexAttribArray(outTexCoordHandle);
    }

    private void drawTexturePassthrough(int textureId) {
        applySaturation(textureId); // saturation at 1.0 is passthrough
    }

    private void createFBO() {
        // Clean up existing
        if (fboId != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{fboId}, 0);
            GLES20.glDeleteTextures(1, new int[]{fboTextureId}, 0);
            GLES20.glDeleteFramebuffers(1, new int[]{fbo2Id}, 0);
            GLES20.glDeleteTextures(1, new int[]{fbo2TextureId}, 0);
        }

        // FBO 1
        int[] fboIds = new int[1];
        GLES20.glGenFramebuffers(1, fboIds, 0);
        fboId = fboIds[0];

        int[] texIds = new int[1];
        GLES20.glGenTextures(1, texIds, 0);
        fboTextureId = texIds[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, fboWidth, fboHeight,
            0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, fboTextureId, 0);

        // FBO 2 (for multi-pass)
        GLES20.glGenFramebuffers(1, fboIds, 0);
        fbo2Id = fboIds[0];

        GLES20.glGenTextures(1, texIds, 0);
        fbo2TextureId = texIds[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbo2TextureId);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, fboWidth, fboHeight,
            0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo2Id);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, fbo2TextureId, 0);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private void initSaturationShader() {
        String vertexCode =
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    gl_Position = aPosition;\n" +
            "    vTexCoord = aTexCoord;\n" +
            "}\n";

        String fragmentCode =
            "precision mediump float;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform float uSaturation;\n" +
            "void main() {\n" +
            "    vec4 color = texture2D(uTexture, vTexCoord);\n" +
            "    // Unpremultiply alpha\n" +
            "    vec3 rgb = color.a > 0.0 ? color.rgb / color.a : color.rgb;\n" +
            "    float luminance = dot(rgb, vec3(0.299, 0.587, 0.114));\n" +
            "    vec3 grey = vec3(luminance);\n" +
            "    rgb = mix(grey, rgb, uSaturation);\n" +
            "    // Re-premultiply\n" +
            "    gl_FragColor = vec4(rgb * color.a, color.a);\n" +
            "}\n";

        saturationProgram = createProgram(vertexCode, fragmentCode);
        satPositionHandle = GLES20.glGetAttribLocation(saturationProgram, "aPosition");
        satTexCoordHandle = GLES20.glGetAttribLocation(saturationProgram, "aTexCoord");
        satTextureHandle = GLES20.glGetUniformLocation(saturationProgram, "uTexture");
        satAmountHandle = GLES20.glGetUniformLocation(saturationProgram, "uSaturation");
    }

    private void initOutlineShader() {
        String vertexCode =
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    gl_Position = aPosition;\n" +
            "    vTexCoord = aTexCoord;\n" +
            "}\n";

        // Disk sampling: check if any pixel within radius has alpha.
        // If yes and current pixel is transparent, this is an outline pixel.
        // Samples 16 points around a circle at the given thickness radius.
        String fragmentCode =
            "precision mediump float;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform vec2 uTexelSize;\n" +
            "uniform vec4 uOutlineColor;\n" +
            "uniform float uThickness;\n" +
            "\n" +
            "void main() {\n" +
            "    vec4 center = texture2D(uTexture, vTexCoord);\n" +
            "    float maxAlpha = 0.0;\n" +
            "    \n" +
            "    // Sample 16 points around a circle\n" +
            "    for (int i = 0; i < 16; i++) {\n" +
            "        float angle = float(i) * 6.28318 / 16.0;\n" +
            "        vec2 offset = vec2(cos(angle), sin(angle)) * uTexelSize * uThickness;\n" +
            "        maxAlpha = max(maxAlpha, texture2D(uTexture, vTexCoord + offset).a);\n" +
            "    }\n" +
            "    // Also sample at half radius for filling gaps\n" +
            "    for (int i = 0; i < 8; i++) {\n" +
            "        float angle = float(i) * 6.28318 / 8.0;\n" +
            "        vec2 offset = vec2(cos(angle), sin(angle)) * uTexelSize * uThickness * 0.5;\n" +
            "        maxAlpha = max(maxAlpha, texture2D(uTexture, vTexCoord + offset).a);\n" +
            "    }\n" +
            "    \n" +
            "    // Outline where neighbors have alpha but center is transparent\n" +
            "    float outline = clamp(maxAlpha - center.a, 0.0, 1.0);\n" +
            "    \n" +
            "    // Composite: outline behind model (premultiplied alpha)\n" +
            "    vec4 outlineCol = vec4(uOutlineColor.rgb * outline * uOutlineColor.a, outline * uOutlineColor.a);\n" +
            "    gl_FragColor = center + outlineCol * (1.0 - center.a);\n" +
            "}\n";

        outlineProgram = createProgram(vertexCode, fragmentCode);
        outPositionHandle = GLES20.glGetAttribLocation(outlineProgram, "aPosition");
        outTexCoordHandle = GLES20.glGetAttribLocation(outlineProgram, "aTexCoord");
        outTextureHandle = GLES20.glGetUniformLocation(outlineProgram, "uTexture");
        outTexelSizeHandle = GLES20.glGetUniformLocation(outlineProgram, "uTexelSize");
        outColorHandle = GLES20.glGetUniformLocation(outlineProgram, "uOutlineColor");
        outThicknessHandle = GLES20.glGetUniformLocation(outlineProgram, "uThickness");
    }

    private int createProgram(String vertexCode, String fragmentCode) {
        int vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexCode);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode);

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        return program;
    }

    private int compileShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
