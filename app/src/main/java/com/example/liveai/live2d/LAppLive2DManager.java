package com.example.liveai.live2d;

import com.live2d.sdk.cubism.framework.math.CubismMatrix44;

public class LAppLive2DManager {

    private LAppModel model;
    private final LAppTextureManager textureManager;
    private int windowWidth;
    private int windowHeight;

    private final CubismMatrix44 projection = CubismMatrix44.create();

    public LAppLive2DManager(LAppTextureManager textureManager) {
        this.textureManager = textureManager;
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

        if (model.getModel().getCanvasWidth() > 1.0f && windowWidth < windowHeight) {
            model.getModelMatrix().setWidth(2.0f);
            projection.scale(1.0f, (float) windowWidth / (float) windowHeight);
        } else {
            projection.scale((float) windowHeight / (float) windowWidth, 1.0f);
        }

        model.update();
        model.draw(projection);
    }

    public void onDrag(float x, float y) {
        if (model != null) {
            model.setDragging(x, y);
        }
    }
}
