package com.example.liveai.audio;

import com.live2d.sdk.cubism.framework.CubismFramework;
import com.live2d.sdk.cubism.framework.id.CubismId;
import com.live2d.sdk.cubism.framework.model.CubismModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Audio-driven head and body motion via sinusoidal oscillators.
 *
 * Uses multiple sine oscillators at incommensurate frequency ratios,
 * modulated by a smoothed audio volume envelope, to produce natural-looking
 * head nods, sway, tilt, and subtle body movement.
 *
 * Call {@link #update(CubismModel, float)} every frame after motion/physics.
 */
public class AudioDrivenMotion {

    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final AudioVolumeSource volumeSource;
    private AudioMotionConfig config;

    // Built from config
    private final List<Oscillator> oscillators = new ArrayList<>();

    // State
    private float smoothedVolume = 0f;
    private float phase = 0f;

    private boolean oscillatorsBuilt = false;

    private static class Oscillator {
        CubismId paramId;
        float freqRatio;
        float amplitude;
        boolean isBody;
    }

    public AudioDrivenMotion(AudioVolumeSource volumeSource, AudioMotionConfig config) {
        this.volumeSource = volumeSource;
        this.config = config;
        // Don't build oscillators here — CubismFramework.initialize() hasn't run yet.
        // They'll be built lazily on first update().
    }

    public void setConfig(AudioMotionConfig config) {
        this.config = config;
        oscillatorsBuilt = false;
    }

    public AudioMotionConfig getConfig() {
        return config;
    }

    private void rebuildOscillators() {
        oscillators.clear();
        for (ParamEntry entry : config.getParamEntries()) {
            if (!entry.getEnabled()) continue;
            CubismId id = CubismFramework.getIdManager().getId(entry.getParamId());
            for (Harmonic h : entry.getHarmonics()) {
                Oscillator osc = new Oscillator();
                osc.paramId = id;
                osc.amplitude = entry.getAmplitude() * h.getWeight();
                osc.freqRatio = h.getFreqRatio();
                osc.isBody = entry.isBody();
                oscillators.add(osc);
            }
        }
    }

    /**
     * Apply audio-driven motion to the model. Call after motion/physics/breath
     * but before model.update().
     *
     * @param model the CubismModel to apply parameters to
     * @param deltaTime frame delta in seconds
     */
    public void update(CubismModel model, float deltaTime) {
        if (!config.getEnabled() || model == null) return;

        if (!oscillatorsBuilt) {
            try {
                rebuildOscillators();
                oscillatorsBuilt = true;
            } catch (Exception e) {
                // Framework not ready yet, skip this frame
                return;
            }
        }

        // Clamp dt to avoid jumps after pause
        float dt = Math.min(deltaTime, 0.1f);

        // Volume envelope: fast attack, slow release
        float rawVolume = volumeSource.getVolume();
        float target = rawVolume > config.getVolumeThreshold() ? rawVolume : 0f;
        float attackAlpha = 1f - config.getSmoothing() * 0.5f;
        float releaseAlpha = 1f - config.getSmoothing();
        float alpha = target > smoothedVolume ? attackAlpha : releaseAlpha;
        smoothedVolume += (target - smoothedVolume) * alpha;

        if (smoothedVolume < 0.005f) return;

        // Advance continuous phase
        phase += dt;

        float intensity = config.getIntensity();
        float speed = config.getSpeed();
        float bodyFollowRatio = config.getBodyFollowRatio();
        float headAngleCap = config.getHeadAngleCap();
        float bodyAngleCap = config.getBodyAngleCap();

        for (Oscillator osc : oscillators) {
            float bodyScale = osc.isBody ? bodyFollowRatio : 1.0f;
            float value = (float) Math.sin(phase * osc.freqRatio * speed * TWO_PI)
                * osc.amplitude
                * intensity
                * smoothedVolume
                * bodyScale;

            // Clamp to angle caps
            float cap = osc.isBody ? bodyAngleCap : headAngleCap;
            value = Math.max(-cap, Math.min(cap, value));

            model.addParameterValue(osc.paramId, value);
        }
    }
}
