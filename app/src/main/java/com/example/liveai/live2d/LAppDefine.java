package com.example.liveai.live2d;

import com.live2d.sdk.cubism.framework.CubismFrameworkConfig.LogLevel;

public class LAppDefine {
    public static final boolean DEBUG_LOG_ENABLE = true;
    public static final boolean DEBUG_DRAW_BOUNDS = true;
    public static final float DEFAULT_MODEL_SCALE = 1.0f;
    public static final boolean MOC_CONSISTENCY_VALIDATION_ENABLE = true;
    public static final boolean MOTION_CONSISTENCY_VALIDATION_ENABLE = true;
    public static final boolean PREMULTIPLIED_ALPHA_ENABLE = true;
    public static final LogLevel cubismLoggingLevel = LogLevel.VERBOSE;

    public enum MotionGroup {
        IDLE("Idle");

        private final String id;
        MotionGroup(String id) { this.id = id; }
        public String getId() { return id; }
    }

    public enum Priority {
        NONE(0), IDLE(1), NORMAL(2), FORCE(3);

        private final int priority;
        Priority(int priority) { this.priority = priority; }
        public int getPriority() { return priority; }
    }
}
