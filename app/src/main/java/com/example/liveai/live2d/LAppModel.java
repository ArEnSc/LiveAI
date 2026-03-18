package com.example.liveai.live2d;

import com.live2d.sdk.cubism.framework.CubismDefaultParameterId.ParameterId;
import com.live2d.sdk.cubism.framework.CubismFramework;
import com.live2d.sdk.cubism.framework.CubismModelSettingJson;
import com.live2d.sdk.cubism.framework.ICubismModelSetting;
import com.live2d.sdk.cubism.framework.effect.CubismBreath;
import com.live2d.sdk.cubism.framework.effect.CubismEyeBlink;
import com.live2d.sdk.cubism.framework.id.CubismId;
import com.live2d.sdk.cubism.framework.id.CubismIdManager;
import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.model.CubismMoc;
import com.live2d.sdk.cubism.framework.model.CubismUserModel;
import com.live2d.sdk.cubism.framework.motion.ACubismMotion;
import com.live2d.sdk.cubism.framework.motion.CubismExpressionMotion;
import com.live2d.sdk.cubism.framework.motion.CubismMotion;
import com.live2d.sdk.cubism.framework.rendering.CubismRenderer;
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LAppModel extends CubismUserModel {

    private final LAppTextureManager textureManager;

    private ICubismModelSetting modelSetting;
    private String modelHomeDirectory;
    private float userTimeSeconds;

    private final List<CubismId> eyeBlinkIds = new ArrayList<>();
    private final List<CubismId> lipSyncIds = new ArrayList<>();
    private final Map<String, ACubismMotion> motions = new HashMap<>();
    private final Map<String, ACubismMotion> expressions = new HashMap<>();

    private final CubismId idParamAngleX;
    private final CubismId idParamAngleY;
    private final CubismId idParamAngleZ;
    private final CubismId idParamBodyAngleX;
    private final CubismId idParamEyeBallX;
    private final CubismId idParamEyeBallY;

    public LAppModel(LAppTextureManager textureManager) {
        this.textureManager = textureManager;

        if (LAppDefine.MOC_CONSISTENCY_VALIDATION_ENABLE) {
            mocConsistency = true;
        }
        if (LAppDefine.MOTION_CONSISTENCY_VALIDATION_ENABLE) {
            motionConsistency = true;
        }
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            debugMode = true;
        }

        CubismIdManager idManager = CubismFramework.getIdManager();
        idParamAngleX = idManager.getId(ParameterId.ANGLE_X.getId());
        idParamAngleY = idManager.getId(ParameterId.ANGLE_Y.getId());
        idParamAngleZ = idManager.getId(ParameterId.ANGLE_Z.getId());
        idParamBodyAngleX = idManager.getId(ParameterId.BODY_ANGLE_X.getId());
        idParamEyeBallX = idManager.getId(ParameterId.EYE_BALL_X.getId());
        idParamEyeBallY = idManager.getId(ParameterId.EYE_BALL_Y.getId());
    }

    public void loadAssets(String dir, String fileName) {
        modelHomeDirectory = dir;
        String filePath = dir + fileName;

        LAppPal.printLog("Loading model: " + filePath);

        byte[] buffer = createBuffer(filePath);
        ICubismModelSetting setting = new CubismModelSettingJson(buffer);

        setupModel(setting);

        if (model == null) {
            LAppPal.printLog("Failed to load model.");
            return;
        }

        CubismRenderer renderer = CubismRendererAndroid.create();
        setupRenderer(renderer, 4);
        setupTextures();
    }

    public void deleteModel() {
        delete();
    }

    public void update() {
        float deltaTimeSeconds = LAppPal.getDeltaTime();
        userTimeSeconds += deltaTimeSeconds;

        dragManager.update(deltaTimeSeconds);
        dragX = dragManager.getX();
        dragY = dragManager.getY();

        boolean isMotionUpdated = false;

        model.loadParameters();

        if (motionManager.isFinished()) {
            startRandomMotion(LAppDefine.MotionGroup.IDLE.getId(), LAppDefine.Priority.IDLE.getPriority());
        } else {
            isMotionUpdated = motionManager.updateMotion(model, deltaTimeSeconds);
        }

        model.saveParameters();

        opacity = model.getModelOpacity();

        if (!isMotionUpdated && eyeBlink != null) {
            eyeBlink.updateParameters(model, deltaTimeSeconds);
        }

        if (expressionManager != null) {
            expressionManager.updateMotion(model, deltaTimeSeconds);
        }

        model.addParameterValue(idParamAngleX, dragX * 30);
        model.addParameterValue(idParamAngleY, dragY * 30);
        model.addParameterValue(idParamAngleZ, dragX * dragY * (-30));
        model.addParameterValue(idParamBodyAngleX, dragX * 10);
        model.addParameterValue(idParamEyeBallX, dragX);
        model.addParameterValue(idParamEyeBallY, dragY);

        if (breath != null) {
            breath.updateParameters(model, deltaTimeSeconds);
        }

        if (physics != null) {
            physics.evaluate(model, deltaTimeSeconds);
        }

        if (pose != null) {
            pose.updateParameters(model, deltaTimeSeconds);
        }

        model.update();
    }

    public void draw(CubismMatrix44 matrix) {
        if (model == null) return;

        CubismMatrix44.multiply(
            modelMatrix.getArray(),
            matrix.getArray(),
            matrix.getArray()
        );

        this.<CubismRendererAndroid>getRenderer().setMvpMatrix(matrix);
        this.<CubismRendererAndroid>getRenderer().drawModel();
    }

    public int startRandomMotion(String group, int priority) {
        if (modelSetting.getMotionCount(group) == 0) {
            return -1;
        }
        Random random = new Random();
        int number = random.nextInt(Integer.MAX_VALUE) % modelSetting.getMotionCount(group);
        return startMotion(group, number, priority);
    }

    public int startMotion(String group, int number, int priority) {
        if (priority == LAppDefine.Priority.FORCE.getPriority()) {
            motionManager.setReservationPriority(priority);
        } else if (!motionManager.reserveMotion(priority)) {
            return -1;
        }

        String name = group + "_" + number;
        CubismMotion motion = (CubismMotion) motions.get(name);

        if (motion == null) {
            String fileName = modelSetting.getMotionFileName(group, number);
            if (!fileName.equals("")) {
                String path = modelHomeDirectory + fileName;
                byte[] buffer = createBuffer(path);
                motion = loadMotion(buffer, null, null, motionConsistency);
                if (motion != null) {
                    float fadeIn = modelSetting.getMotionFadeInTimeValue(group, number);
                    if (fadeIn != -1.0f) motion.setFadeInTime(fadeIn);
                    float fadeOut = modelSetting.getMotionFadeOutTimeValue(group, number);
                    if (fadeOut != -1.0f) motion.setFadeOutTime(fadeOut);
                    motion.setEffectIds(eyeBlinkIds, lipSyncIds);
                }
            }
        }

        if (motion == null) return -1;
        return motionManager.startMotionPriority(motion, priority);
    }

    private static byte[] createBuffer(String path) {
        return LAppPal.loadFileAsBytes(path);
    }

    private void setupModel(ICubismModelSetting setting) {
        modelSetting = setting;
        isUpdated = true;
        isInitialized = false;

        // Load moc
        String fileName = modelSetting.getModelFileName();
        if (!fileName.equals("")) {
            byte[] buffer = createBuffer(modelHomeDirectory + fileName);
            loadModel(buffer, mocConsistency);
        }

        // Load expressions
        for (int i = 0; i < modelSetting.getExpressionCount(); i++) {
            String name = modelSetting.getExpressionName(i);
            String path = modelHomeDirectory + modelSetting.getExpressionFileName(i);
            byte[] buffer = createBuffer(path);
            CubismExpressionMotion motion = loadExpression(buffer);
            if (motion != null) {
                expressions.put(name, motion);
            }
        }

        // Physics
        String physicsPath = modelSetting.getPhysicsFileName();
        if (!physicsPath.equals("")) {
            loadPhysics(createBuffer(modelHomeDirectory + physicsPath));
        }

        // Pose
        String posePath = modelSetting.getPoseFileName();
        if (!posePath.equals("")) {
            loadPose(createBuffer(modelHomeDirectory + posePath));
        }

        // Eye blink
        if (modelSetting.getEyeBlinkParameterCount() > 0) {
            eyeBlink = CubismEyeBlink.create(modelSetting);
        }

        // Breath
        breath = CubismBreath.create();
        List<CubismBreath.BreathParameterData> breathParams = new ArrayList<>();
        breathParams.add(new CubismBreath.BreathParameterData(idParamAngleX, 0.0f, 15.0f, 6.5345f, 0.5f));
        breathParams.add(new CubismBreath.BreathParameterData(idParamAngleY, 0.0f, 8.0f, 3.5345f, 0.5f));
        breathParams.add(new CubismBreath.BreathParameterData(idParamAngleZ, 0.0f, 10.0f, 5.5345f, 0.5f));
        breathParams.add(new CubismBreath.BreathParameterData(idParamBodyAngleX, 0.0f, 4.0f, 15.5345f, 0.5f));
        breathParams.add(new CubismBreath.BreathParameterData(
            CubismFramework.getIdManager().getId(ParameterId.BREATH.getId()), 0.5f, 0.5f, 3.2345f, 0.5f));
        breath.setParameters(breathParams);

        // EyeBlink IDs
        for (int i = 0; i < modelSetting.getEyeBlinkParameterCount(); i++) {
            eyeBlinkIds.add(modelSetting.getEyeBlinkParameterId(i));
        }

        // LipSync IDs
        for (int i = 0; i < modelSetting.getLipSyncParameterCount(); i++) {
            lipSyncIds.add(modelSetting.getLipSyncParameterId(i));
        }

        // Layout
        Map<String, Float> layout = new HashMap<>();
        if (modelSetting.getLayoutMap(layout)) {
            modelMatrix.setupFromLayout(layout);
        }

        model.saveParameters();

        // Pre-load motions
        for (int i = 0; i < modelSetting.getMotionGroupCount(); i++) {
            String group = modelSetting.getMotionGroupName(i);
            preLoadMotionGroup(group);
        }

        motionManager.stopAllMotions();
        isUpdated = false;
        isInitialized = true;
    }

    private void preLoadMotionGroup(String group) {
        int count = modelSetting.getMotionCount(group);
        for (int i = 0; i < count; i++) {
            String name = group + "_" + i;
            String path = modelSetting.getMotionFileName(group, i);
            if (!path.equals("")) {
                byte[] buffer = createBuffer(modelHomeDirectory + path);
                CubismMotion tmp = loadMotion(buffer, motionConsistency);
                if (tmp == null) continue;

                float fadeIn = modelSetting.getMotionFadeInTimeValue(group, i);
                if (fadeIn != -1.0f) tmp.setFadeInTime(fadeIn);
                float fadeOut = modelSetting.getMotionFadeOutTimeValue(group, i);
                if (fadeOut != -1.0f) tmp.setFadeOutTime(fadeOut);
                tmp.setEffectIds(eyeBlinkIds, lipSyncIds);
                motions.put(name, tmp);
            }
        }
    }

    private void setupTextures() {
        for (int i = 0; i < modelSetting.getTextureCount(); i++) {
            String texturePath = modelSetting.getTextureFileName(i);
            if (texturePath.equals("")) continue;

            texturePath = modelHomeDirectory + texturePath;

            LAppTextureManager.TextureInfo texture = textureManager.createTextureFromPngFile(texturePath);
            if (texture == null) continue;

            this.<CubismRendererAndroid>getRenderer().bindTexture(i, texture.id);

            if (LAppDefine.PREMULTIPLIED_ALPHA_ENABLE) {
                this.<CubismRendererAndroid>getRenderer().isPremultipliedAlpha(true);
            } else {
                this.<CubismRendererAndroid>getRenderer().isPremultipliedAlpha(false);
            }
        }
    }
}
