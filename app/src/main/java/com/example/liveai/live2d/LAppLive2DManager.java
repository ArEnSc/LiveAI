package com.example.liveai.live2d;

import com.live2d.sdk.cubism.framework.math.CubismMatrix44;

public class LAppLive2DManager {

    private LAppModel model;
    private final LAppTextureManager textureManager;
    private int windowWidth;
    private int windowHeight;

    private final CubismMatrix44 projection = CubismMatrix44.create();
    private float modelScale = LAppDefine.DEFAULT_MODEL_SCALE;
    private boolean fitToScreen = false;
    private float modelOffsetX = 0.0f;
    private float modelOffsetY = 0.0f;

    public LAppLive2DManager(LAppTextureManager textureManager) {
        this.textureManager = textureManager;
    }

    public float getModelScale() {
        return modelScale;
    }

    public void setModelScale(float scale) {
        this.modelScale = Math.max(0.5f, Math.min(scale, 10.0f));
    }

    public void setFitToScreen(boolean fit) {
        this.fitToScreen = fit;
    }

    public void setModelOffset(float x, float y) {
        this.modelOffsetX = x;
        this.modelOffsetY = y;
    }

    public void loadModel(String dir, String modelJsonName) {
        releaseModel();

        model = new LAppModel(textureManager);
        model.loadAssets(dir, modelJsonName);
    }

    public void releaseModel() {
        if (model != null) {
            model.deleteModel();
            model = null;
        }
    }

    public void setWindowSize(int width, int height) {
        this.windowWidth = width;
        this.windowHeight = height;
    }

    public void onUpdate() {
        if (model == null || model.getModel() == null) return;

        projection.loadIdentity();

        if (fitToScreen) {
            // Aspect-fit: scale model to fit within screen without distortion
            float screenAspect = (float) windowWidth / (float) windowHeight;
            model.getModelMatrix().setWidth(2.0f);
            projection.scale(1.0f, (float) windowWidth / (float) windowHeight);
        } else {
            if (model.getModel().getCanvasWidth() > 1.0f && windowWidth < windowHeight) {
                model.getModelMatrix().setWidth(2.0f);
                projection.scale(1.0f, (float) windowWidth / (float) windowHeight);
            } else {
                projection.scale((float) windowHeight / (float) windowWidth, 1.0f);
            }
            projection.scaleRelative(modelScale, modelScale);
        }

        projection.translateRelative(modelOffsetX, modelOffsetY);

        model.update();
        model.draw(projection);
    }

    public void onDrag(float x, float y) {
        if (model != null) {
            model.setDragging(x, y);
        }
    }
}
