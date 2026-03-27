package com.example.liveai.live2d;

import android.util.Log;

import com.example.liveai.audio.AudioDrivenMotion;
import com.example.liveai.gyroscope.GyroscopeDrivenMotion;
import com.live2d.sdk.cubism.framework.id.CubismId;
import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.model.CubismModel;

import org.json.JSONArray;
import org.json.JSONObject;

import com.example.liveai.interaction.InteractionTarget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LAppLive2DManager implements InteractionTarget {

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

    private AudioDrivenMotion audioDrivenMotion;
    private GyroscopeDrivenMotion gyroscopeDrivenMotion;

    public void setAudioDrivenMotion(AudioDrivenMotion motion) {
        this.audioDrivenMotion = motion;
        if (model != null) {
            model.setAudioDrivenMotion(motion);
        }
    }

    public void setGyroscopeDrivenMotion(GyroscopeDrivenMotion motion) {
        this.gyroscopeDrivenMotion = motion;
        if (model != null) {
            model.setGyroscopeDrivenMotion(motion);
        }
    }

    public void loadModel(String dir, String modelJsonName) {
        releaseModel();

        model = new LAppModel(textureManager);
        model.loadAssets(dir, modelJsonName);

        if (audioDrivenMotion != null) {
            model.setAudioDrivenMotion(audioDrivenMotion);
        }
        if (gyroscopeDrivenMotion != null) {
            model.setGyroscopeDrivenMotion(gyroscopeDrivenMotion);
        }
        if (!parameterOverrides.isEmpty()) {
            model.setParameterOverrides(parameterOverrides);
        }

        loadCdiDisplayNames(dir, modelJsonName);
    }

    private void loadCdiDisplayNames(String dir, String modelJsonName) {
        cdiDisplayNames.clear();
        try {
            // Parse model3.json to find the DisplayInfo (CDI) file path
            byte[] modelJson = LAppPal.loadFileAsBytes(dir + modelJsonName);
            if (modelJson == null || modelJson.length == 0) return;

            JSONObject root = new JSONObject(new String(modelJson));
            JSONObject fileRefs = root.optJSONObject("FileReferences");
            if (fileRefs == null) return;

            String cdiPath = fileRefs.optString("DisplayInfo", "");
            if (cdiPath.isEmpty()) return;

            byte[] cdiBytes = LAppPal.loadFileAsBytes(dir + cdiPath);
            if (cdiBytes == null || cdiBytes.length == 0) return;

            JSONObject cdi = new JSONObject(new String(cdiBytes));
            JSONArray params = cdi.optJSONArray("Parameters");
            if (params == null) return;

            for (int i = 0; i < params.length(); i++) {
                JSONObject p = params.getJSONObject(i);
                String id = p.optString("Id", "");
                String name = p.optString("Name", "");
                if (!id.isEmpty() && !name.isEmpty() && !name.equals("------------")) {
                    cdiDisplayNames.put(id, name);
                }
            }
            Log.d("LAppLive2DManager", "Loaded " + cdiDisplayNames.size() + " CDI display names");
        } catch (Exception e) {
            Log.w("LAppLive2DManager", "Failed to load CDI display names", e);
        }
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
            model.getModelMatrix().setWidth(2.0f);
            projection.scale(1.0f, (float) windowWidth / (float) windowHeight);
            projection.scaleRelative(modelScale, modelScale);
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

    public String getMaskDiagnostics() {
        if (model == null) return "no model";
        return model.getMaskDiagnostics();
    }

    public float getCanvasWidth() {
        if (model == null || model.getModel() == null) return 0;
        return model.getModel().getCanvasWidth();
    }

    public float getCanvasHeight() {
        if (model == null || model.getModel() == null) return 0;
        return model.getModel().getCanvasHeight();
    }

    // --- Parameter access ---

    /** Holds info about a single model parameter. */
    public static class ParameterInfo {
        public final int index;
        public final String id;
        public final String displayName;
        public final float min;
        public final float max;
        public final float defaultValue;

        ParameterInfo(int index, String id, String displayName, float min, float max, float defaultValue) {
            this.index = index;
            this.id = id;
            this.displayName = displayName;
            this.min = min;
            this.max = max;
            this.defaultValue = defaultValue;
        }
    }

    /** Returns info for all parameters on the loaded model. */
    public List<ParameterInfo> getParameterList() {
        List<ParameterInfo> result = new ArrayList<>();
        if (model == null || model.getModel() == null) return result;

        CubismModel m = model.getModel();
        int count = m.getParameterCount();
        for (int i = 0; i < count; i++) {
            CubismId cid = m.getParameterId(i);
            String id = cid.getString();
            String displayName = cdiDisplayNames.getOrDefault(id, id);
            result.add(new ParameterInfo(
                i,
                id,
                displayName,
                m.getParameterMinimumValue(i),
                m.getParameterMaximumValue(i),
                m.getParameterDefaultValue(i)
            ));
        }
        return result;
    }

    /** Get the current value of a parameter by index. */
    public float getParameterValue(int index) {
        if (model == null || model.getModel() == null) return 0f;
        return model.getModel().getParameterValue(index);
    }

    /** Set a parameter override that will be applied each frame after all other systems. */
    public void setParameterOverride(String paramId, float value) {
        parameterOverrides.put(paramId, value);
        if (model != null) {
            model.setParameterOverrides(parameterOverrides);
        }
    }

    /** Remove a parameter override, letting the model's own systems control it. */
    public void clearParameterOverride(String paramId) {
        parameterOverrides.remove(paramId);
        if (model != null) {
            model.setParameterOverrides(parameterOverrides);
        }
    }

    /** Set all parameter overrides at once (e.g. from saved preferences). */
    public void setAllParameterOverrides(Map<String, Float> overrides) {
        parameterOverrides.clear();
        parameterOverrides.putAll(overrides);
        if (model != null) {
            model.setParameterOverrides(parameterOverrides);
        }
    }

    /** Get the current parameter overrides map. */
    public Map<String, Float> getParameterOverrides() {
        return new HashMap<>(parameterOverrides);
    }

    private final Map<String, Float> parameterOverrides = new HashMap<>();
    private final Map<String, String> cdiDisplayNames = new HashMap<>();

    /** Trigger a random motion from the given group at the given priority. */
    public int startRandomMotion(String group, int priority) {
        if (model == null) return -1;
        return model.startRandomMotion(group, priority);
    }

    public void onDrag(float x, float y) {
        if (model != null) {
            model.setDragging(x, y);
        }
    }

    // --- InteractionTarget ---

    @Override
    public void setInteractionParams(java.util.Map<String, Float> params) {
        if (model != null) {
            model.setInteractionParams(params);
        }
    }

    @Override
    public void clearInteractionParams(java.util.Set<String> paramIds) {
        if (model != null) {
            model.clearInteractionParams(paramIds);
        }
    }

    @Override
    public int getScreenWidth() { return windowWidth; }

    @Override
    public int getScreenHeight() { return windowHeight; }

    // getModelScale() already exists above — satisfies InteractionTarget

    @Override
    public float getModelOffsetX() { return modelOffsetX; }

    @Override
    public float getModelOffsetY() { return modelOffsetY; }
}
