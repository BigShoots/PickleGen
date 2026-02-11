package com.picklecal.lg.renderer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import com.picklecal.lg.model.AppState
import com.picklecal.lg.model.DrawCommand
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 3.0 renderer for test pattern display.
 * Port of dogegen's D3D11 rendering pipeline to GLES.
 */
class PatternRenderer : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "PatternRenderer"
        private const val FLOATS_PER_VERTEX = 6
        private const val VERTICES_PER_QUAD = 4
        private const val BYTES_PER_FLOAT = 4

        private const val VERTEX_SHADER = """#version 300 es
            in vec2 aPosition;
            in vec3 aColor;
            in float aQuant;

            out vec4 vColor;
            flat out float vQuant;

            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vColor = vec4(aColor, 1.0);
                vQuant = aQuant;
            }
        """

        private const val FRAGMENT_SHADER = """#version 300 es
            precision highp float;

            in vec4 vColor;
            flat in float vQuant;

            out vec4 fragColor;

            void main() {
                if (vQuant != 0.0) {
                    fragColor = floor(vColor / vQuant) * vQuant;
                } else {
                    fragColor = vColor;
                }
            }
        """
    }

    private var programId: Int = 0
    private var positionLoc: Int = 0
    private var colorLoc: Int = 0
    private var quantLoc: Int = 0
    private var vboId: Int = 0

    private var currentCommands: List<DrawCommand> = emptyList()
    private var flickerCycle: Int = 0
    private var flickerCounter: Int = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)

        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertexShader)
        GLES30.glAttachShader(programId, fragmentShader)
        GLES30.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES30.glGetProgramInfoLog(programId)
            Log.e(TAG, "Program link error: $error")
            GLES30.glDeleteProgram(programId)
            programId = 0
            return
        }

        positionLoc = GLES30.glGetAttribLocation(programId, "aPosition")
        colorLoc = GLES30.glGetAttribLocation(programId, "aColor")
        quantLoc = GLES30.glGetAttribLocation(programId, "aQuant")

        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vboId = vbos[0]

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        Log.i(TAG, "Renderer initialized successfully")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        Log.i(TAG, "Surface changed: ${width}x${height}")
    }

    override fun onDrawFrame(gl: GL10?) {
        if (AppState.isPending()) {
            currentCommands = AppState.getCommands()
            AppState.clearPending()
        }

        val newFlickerCycle = AppState.flicker
        if (flickerCycle != newFlickerCycle) {
            flickerCycle = newFlickerCycle
            flickerCounter = 0
        }

        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        if (programId == 0) return

        GLES30.glUseProgram(programId)

        for (cmd in currentCommands) {
            if (flickerCycle != 0 && flickerCounter != flickerCycle) break

            val vertexData = cmd.toVertexData()
            val buffer = createFloatBuffer(vertexData)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                vertexData.size * BYTES_PER_FLOAT,
                buffer,
                GLES30.GL_DYNAMIC_DRAW
            )

            val stride = FLOATS_PER_VERTEX * BYTES_PER_FLOAT

            GLES30.glEnableVertexAttribArray(positionLoc)
            GLES30.glVertexAttribPointer(positionLoc, 2, GLES30.GL_FLOAT, false, stride, 0)

            GLES30.glEnableVertexAttribArray(colorLoc)
            GLES30.glVertexAttribPointer(colorLoc, 3, GLES30.GL_FLOAT, false, stride, 2 * BYTES_PER_FLOAT)

            GLES30.glEnableVertexAttribArray(quantLoc)
            GLES30.glVertexAttribPointer(quantLoc, 1, GLES30.GL_FLOAT, false, stride, 5 * BYTES_PER_FLOAT)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, VERTICES_PER_QUAD)

            GLES30.glDisableVertexAttribArray(positionLoc)
            GLES30.glDisableVertexAttribArray(colorLoc)
            GLES30.glDisableVertexAttribArray(quantLoc)
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        if (flickerCycle != 0) {
            flickerCounter = (flickerCounter + 1) % (flickerCycle + 1)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            val typeName = if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment"
            Log.e(TAG, "Error compiling $typeName shader: $error")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun createFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    fun cleanup() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
    }
}
