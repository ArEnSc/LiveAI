package com.example.liveai.gyroscope;

import com.live2d.sdk.cubism.framework.CubismFramework;
import com.live2d.sdk.cubism.framework.id.CubismId;
import com.live2d.sdk.cubism.framework.model.CubismModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps device tilt (from GyroscopeTiltSource) to configurable Live2D parameters.
 *
 * Each {@link GyroBinding} maps a tilt axis to a parameter with a signed scale
 * (negative = inverted). Call {@link #update(CubismModel, float)} every frame
 * after motion/physics, following the same pattern as AudioDrivenMotion.
 */
public class GyroscopeDrivenMotion {

    private final GyroscopeTiltSource tiltSource;
    private GyroMotionConfig config;

    // Resolved bindings (lazy, needs CubismFramework initialized)
    private final List<ResolvedBinding> resolvedBindings = new ArrayList<>();
    private boolean bindingsResolved = false;

    private static class ResolvedBinding {
        CubismId paramId;
        GyroAxis axis;
        float scale;
        boolean enabled;
    }

    public GyroscopeDrivenMotion(GyroscopeTiltSource tiltSource) {
        this.tiltSource = tiltSource;
        this.config = new GyroMotionConfig(false, java.util.Collections.emptyList());
    }

    public GyroMotionConfig getConfig() {
        return config;
    }

    public void setConfig(GyroMotionConfig config) {
        boolean wasEnabled = this.config.getEnabled();
        this.config = config;
        bindingsResolved = false;

        if (config.getEnabled() && !wasEnabled) {
            tiltSource.start();
        } else if (!config.getEnabled() && wasEnabled) {
            tiltSource.stop();
        }
    }

    private void resolveBindings() {
        resolvedBindings.clear();
        for (GyroBinding binding : config.getBindings()) {
            ResolvedBinding rb = new ResolvedBinding();
            rb.paramId = CubismFramework.getIdManager().getId(binding.getParamId());
            rb.axis = binding.getAxis();
            rb.scale = binding.getScale();
            rb.enabled = binding.getEnabled();
            resolvedBindings.add(rb);
        }
    }

    /**
     * Apply tilt-driven motion to the model. Call after motion/physics
     * but before model.update().
     */
    public void update(CubismModel model, float deltaTime) {
        if (!config.getEnabled() || model == null) return;

        if (!bindingsResolved) {
            try {
                resolveBindings();
                bindingsResolved = true;
            } catch (Exception e) {
                return;
            }
        }

        float tiltX = tiltSource.getTiltX(); // left/right roll: -1..1
        float tiltY = tiltSource.getTiltY(); // forward/back pitch: -1..1

        for (ResolvedBinding rb : resolvedBindings) {
            if (!rb.enabled) continue;

            float tilt = (rb.axis == GyroAxis.TILT_X) ? tiltX : tiltY;
            float value = tilt * rb.scale;

            model.addParameterValue(rb.paramId, value);
        }
    }
}
