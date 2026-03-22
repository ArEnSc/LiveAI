package com.example.liveai.live2d

import android.opengl.GLES20

/**
 * Saves and restores the full GLES20 state around a rendering phase.
 * Covers everything CubismRendererProfileAndroid covers, PLUS FBO and
 * viewport (which Cubism saves but does not restore).
 *
 * Usage:
 *   GlStateGuard.withGuard { live2DManager?.onUpdate() }
 */
class GlStateGuard {

    // Pre-allocated buffers to avoid per-frame allocation
    private val program = IntArray(1)
    private val activeTexture = IntArray(1)
    private val texture0 = IntArray(1)
    private val texture1 = IntArray(1)
    private val arrayBuffer = IntArray(1)
    private val elementArrayBuffer = IntArray(1)
    private val fbo = IntArray(1)
    private val viewport = IntArray(4)
    private val frontFace = IntArray(1)
    private val blendSrcRgb = IntArray(1)
    private val blendDstRgb = IntArray(1)
    private val blendSrcAlpha = IntArray(1)
    private val blendDstAlpha = IntArray(1)
    private val colorMask = BooleanArray(4)
    private val vertexAttrib = Array(4) { IntArray(1) }

    private var scissorTest = false
    private var stencilTest = false
    private var depthTest = false
    private var cullFace = false
    private var blend = false

    fun save() {
        GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, program, 0)
        GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, activeTexture, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, texture1, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, texture0, 0)

        GLES20.glGetIntegerv(GLES20.GL_ARRAY_BUFFER_BINDING, arrayBuffer, 0)
        GLES20.glGetIntegerv(GLES20.GL_ELEMENT_ARRAY_BUFFER_BINDING, elementArrayBuffer, 0)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, fbo, 0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0)
        GLES20.glGetIntegerv(GLES20.GL_FRONT_FACE, frontFace, 0)

        for (i in 0..3) {
            GLES20.glGetVertexAttribiv(i, GLES20.GL_VERTEX_ATTRIB_ARRAY_ENABLED, vertexAttrib[i], 0)
        }

        scissorTest = GLES20.glIsEnabled(GLES20.GL_SCISSOR_TEST)
        stencilTest = GLES20.glIsEnabled(GLES20.GL_STENCIL_TEST)
        depthTest = GLES20.glIsEnabled(GLES20.GL_DEPTH_TEST)
        cullFace = GLES20.glIsEnabled(GLES20.GL_CULL_FACE)
        blend = GLES20.glIsEnabled(GLES20.GL_BLEND)

        GLES20.glGetBooleanv(GLES20.GL_COLOR_WRITEMASK, colorMask, 0)

        GLES20.glGetIntegerv(GLES20.GL_BLEND_SRC_RGB, blendSrcRgb, 0)
        GLES20.glGetIntegerv(GLES20.GL_BLEND_DST_RGB, blendDstRgb, 0)
        GLES20.glGetIntegerv(GLES20.GL_BLEND_SRC_ALPHA, blendSrcAlpha, 0)
        GLES20.glGetIntegerv(GLES20.GL_BLEND_DST_ALPHA, blendDstAlpha, 0)
    }

    fun restore() {
        GLES20.glUseProgram(program[0])

        for (i in 0..3) {
            if (vertexAttrib[i][0] != 0) {
                GLES20.glEnableVertexAttribArray(i)
            } else {
                GLES20.glDisableVertexAttribArray(i)
            }
        }

        setEnabled(GLES20.GL_SCISSOR_TEST, scissorTest)
        setEnabled(GLES20.GL_STENCIL_TEST, stencilTest)
        setEnabled(GLES20.GL_DEPTH_TEST, depthTest)
        setEnabled(GLES20.GL_CULL_FACE, cullFace)
        setEnabled(GLES20.GL_BLEND, blend)

        GLES20.glFrontFace(frontFace[0])
        GLES20.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3])

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, arrayBuffer[0])
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer[0])

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture1[0])
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture0[0])
        GLES20.glActiveTexture(activeTexture[0])

        GLES20.glBlendFuncSeparate(
            blendSrcRgb[0], blendDstRgb[0],
            blendSrcAlpha[0], blendDstAlpha[0]
        )

        // FBO and viewport — the critical ones Cubism does NOT restore
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
    }

    private fun setEnabled(cap: Int, enabled: Boolean) {
        if (enabled) GLES20.glEnable(cap) else GLES20.glDisable(cap)
    }

    companion object {
        private val threadLocalGuard = ThreadLocal<GlStateGuard>()

        /** Get or create a thread-local guard instance (avoids per-frame allocation). */
        @JvmStatic
        fun getOrCreate(): GlStateGuard =
            threadLocalGuard.get() ?: GlStateGuard().also { threadLocalGuard.set(it) }

        inline fun withGuard(block: () -> Unit) {
            val guard = getOrCreate()
            guard.save()
            try {
                block()
            } finally {
                guard.restore()
            }
        }
    }
}
