package com.example.canny418.ui.gallery;
import com.example.canny418.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

/**
 * This class implements our custom renderer. Note that the GL10 parameter passed in is unused for OpenGL ES 2.0
 * renderers -- the static class GLES20 is used instead.
 */
public class GPURenderer implements GLSurfaceView.Renderer
{
    private final Context mActivityContext;
    /**
     * Store the model matrix. This matrix is used to move models from object space (where each model can be thought
     * of being located at the center of the universe) to world space.
     */
    private float[] mModelMatrix = new float[16];

    /**
     * Store the view matrix. This can be thought of as our camera. This matrix transforms world space to eye space;
     * it positions things relative to our eye.
     */
    private float[] mViewMatrix = new float[16];

    /** Store the projection matrix. This is used to project the scene onto a 2D viewport. */
    private float[] mProjectionMatrix = new float[16];

    /** Allocate storage for the final combined matrix. This will be passed into the shader program. */
    private float[] mMVPMatrix = new float[16];

    /** This will be used to pass in the texture. */
    private int mTextureUniformHandle;


    /** Store our model data in a float buffer. */
    private final FloatBuffer mCubePositions;
    private final FloatBuffer mCubeColors;
    private final FloatBuffer mCubeTextureCoordinates;

    /** This will be used to pass in the transformation matrix. */
    private int mMVPMatrixHandle;

    /** This will be used to pass in model position information. */
    private int mPositionHandle;

    /** This will be used to pass in model pixel step information. */
    private float[] mPixelStep = new float[2];

    private float[] mTexSize = new float[2];

    /** This will be used to pass in model threshold information. */
    private float[] mThreshold = new float[2];

    /** This will be used to pass in model color information. */
//    private int mColorHandle;

    /** This will be used to pass in model texture coordinate information. */
    private int mTextureCoordinateHandle;

    /** How many bytes per float. */
    private final int mBytesPerFloat = 4;


    /** Size of the position data in elements. */
    private final int mPositionDataSize = 3;


    /** Size of the texture coordinate data in elements. */
    private final int mTextureCoordinateDataSize = 2;

    /** This is a handle to our cube shading program. */
    private int mGaussProgram;
    private int mSobelProgram;
    private int mNMSProgram;
    private int mWeakPixelProgram;

    /** This is a handle to pixel step. */
    private int mPixelStepHandle;
    private int mTexSizeHandle;
    private int mThresholdHandle;

    /** This is a handle to our texture data. */
    private int mTexture1;
    private int mTexture2;

    private int mmWidth;
    private int mmHeight;

    private int[] mFrameBuffer = new int[2];

    private int num_iter = 0;
    private long counter = 0;

    private int mVertexShaderHandle;
    private int mVertexShaderPassthroughHandle;
    private int mFragmentShaderHandle1;
    private int mFragmentShaderHandle2;
    private int mFragmentShaderHandle3;
    private int mFragmentShaderHandle4;

    private final String vertexShader =
            "uniform mat4 u_MVPMatrix;      \n"		// A constant representing the combined model/view/projection matrix.

                    + "attribute vec4 a_Position;     \n"
                    + "attribute vec2 a_TexCoordinate;        \n"

                    + "varying vec2 v_TexCoordinate;          \n"

                    + "void main()                    \n"
                    + "{                              \n"
                    + "   v_TexCoordinate = a_TexCoordinate;          \n"
                    + "   gl_Position = u_MVPMatrix * a_Position;   \n"
                    + "}                              \n";

    private final String vertexPassthrough =
            "uniform mat4 u_MVPMatrix;      \n"		// A constant representing the combined model/view/projection matrix.
                    + "attribute vec4 a_Position;     \n"
                    + "attribute vec2 a_TexCoordinate;        \n"

                    + "varying vec2 v_TexCoordinate;          \n"

                    + "void main()                    \n"
                    + "{                              \n"
                    + "   v_TexCoordinate = a_TexCoordinate;          \n"
                    + "   gl_Position = a_Position;   \n"
                    + "}                              \n";


    private final String defaultShader =
            "precision mediump float;       \n"
                    + "uniform sampler2D u_Texture;    \n"
                    + "varying vec2 v_TexCoordinate;          \n"
                    + "void main()                    \n"		// The entry point for our fragment shader.
                    + "{                              \n"
                    + "   gl_FragColor = (texture2D(u_Texture, v_TexCoordinate));     \n"		// Pass the color directly through the pipeline.
                    + "}                              \n";


    private final String gaussblur55_f =
            "precision mediump float;       \n"
                    + "uniform sampler2D u_Texture;    \n"
                    + "varying vec2 v_TexCoordinate;          \n"
                    + "uniform vec2 pixelStep;          \n"
                    + "void main()                      \n"
                    + "{                                \n"
                    + "     float sum = 0.0625*texture2D(u_Texture, v_TexCoordinate - (pixelStep + pixelStep)).r \n"
                    + "         + 0.25*texture2D(u_Texture, v_TexCoordinate - pixelStep).r                       \n"
                    + "         + 0.375*texture2D(u_Texture, v_TexCoordinate).r                                  \n"
                    + "         + 0.25*texture2D(u_Texture, v_TexCoordinate + pixelStep).r                       \n"
                    + "         + 0.0625*texture2D(u_Texture, v_TexCoordinate + (pixelStep + pixelStep)).r;      \n"
                    + "     gl_FragColor = vec4(sum);   \n"
                    + "}                                \n";

    private final String sobel_f =
            "precision mediump float;                   \n"
                + "uniform sampler2D u_Texture;         \n"
                + "varying vec2 v_TexCoordinate;        \n"
                + "uniform vec2 pixelStep;              \n"
                + "uniform vec2 tex_size;               \n"
                + "const mat2 ROTATION_MATRIX = mat2(0.92388, 0.38268, -0.38268, 0.92388);       \n"
                + "void main()                          \n"
                + "{                                    \n"
                + "     float a11 = texture2D(u_Texture, v_TexCoordinate - pixelStep).r;        \n"
                + "     float a12 = texture2D(u_Texture, vec2(v_TexCoordinate.s, v_TexCoordinate.t - pixelStep.t)).r;        \n"
                + "     float a13 = texture2D(u_Texture, vec2(v_TexCoordinate.s + pixelStep.s, v_TexCoordinate.t - pixelStep.t)).r;        \n"
                + "     float a21 = texture2D(u_Texture, vec2(v_TexCoordinate.s - pixelStep.s, v_TexCoordinate.t)).r;        \n"
                + "     float a22 = texture2D(u_Texture, v_TexCoordinate).r;        \n"
                + "     float a23 = texture2D(u_Texture, vec2(v_TexCoordinate.s + pixelStep.s, v_TexCoordinate.t)).r;        \n"
                + "     float a31 = texture2D(u_Texture, vec2(v_TexCoordinate.s - pixelStep.s, v_TexCoordinate.t + pixelStep.t)).r;        \n"
                + "     float a32 = texture2D(u_Texture, vec2(v_TexCoordinate.s, v_TexCoordinate.t + pixelStep.t)).r;        \n"
                + "     float a33 = texture2D(u_Texture, v_TexCoordinate + pixelStep).r;        \n"
                + "     vec2 sobel = vec2((a13+a23+a23+a33) - (a11+a21+a21+a31), (a31+a32+a32+a33) - (a11+a12+a12+a13));        \n"
                + "     vec2 sobelAbs = abs(sobel);     \n"
                + "     vec2 rotatedSobel = ROTATION_MATRIX*sobel;      \n"
                + "     vec2 quadrantSobel = vec2(rotatedSobel.x * rotatedSobel.x - rotatedSobel.y * rotatedSobel.y, 2.0 * rotatedSobel.x * rotatedSobel.y);    \n"
                + "     vec2 neighDir = vec2(step(-1.5, sign(quadrantSobel.x)+sign(quadrantSobel.y)), step(0.0, -quadrantSobel.x) - step(0.0, quadrantSobel.x)*step(0.0, -quadrantSobel.y));        \n"
//                + "     gl_FragColor.r = (sobelAbs.x + sobelAbs.y)*0.125;       \n"
//                + "     gl_FragColor.gb = neighDir * 0.5 + vec2(0.5);           \n"
//                + "     gl_FragColor.a = 0.0;       \n"
                + "     float mag = length(sobel);\n"
                + "     vec3 target = vec3(mag, mag, mag); \n"
                + "     gl_FragColor = vec4(mix(texture2D(u_Texture, v_TexCoordinate).rgb, target, 1.0),1.); \n"
                + "}        \n";

    private final String nonmaxsupress_f =
            "precision mediump float;       \n"
                + "uniform sampler2D u_Texture;         \n"
                + "varying vec2 v_TexCoordinate;        \n"
                + "uniform vec2 pixelStep;              \n"
                + "uniform vec2 threshold;              \n"
                + "void main() \n"
                + "{ \n"
                + "     vec4 texCoord = texture2D(u_Texture, v_TexCoordinate); \n"
                + "     vec2 neighDir = texCoord.gb * 2.0 - vec2(1.0);         \n"
                + "     vec4 n1 = texture2D(u_Texture, v_TexCoordinate + (neighDir * pixelStep)); \n"
                + "     vec4 n2 = texture2D(u_Texture, v_TexCoordinate - (neighDir * pixelStep)); \n"
                + "     float edgeStrength = texCoord.r * step(max(n1.r,n2.r),texCoord.r); \n"
                + "     gl_FragColor = vec4(smoothstep(threshold.s, threshold.t, edgeStrength), 0.0, 0.0, 0.0); \n"
                + "} \n";

    private final String weakpixeltest_f =
            "precision mediump float;       \n"
                + "uniform sampler2D u_Texture;         \n"
                + "varying vec2 v_TexCoordinate;        \n"
                + "uniform vec2 pixelStep;              \n"
                + "void main()                          \n"
                + "{                                    \n"
                + "     float edgeStrength = texture2D(u_Texture, v_TexCoordinate).r;       \n"
                + "     float a11 = texture2D(u_Texture, v_TexCoordinate - pixelStep).r;        \n"
                + "     float a12 = texture2D(u_Texture, vec2(v_TexCoordinate.s, v_TexCoordinate.t - pixelStep.t)).r;        \n"
                + "     float a13 = texture2D(u_Texture, vec2(v_TexCoordinate.s + pixelStep.s, v_TexCoordinate.t - pixelStep.t)).r;        \n"
                + "     float a21 = texture2D(u_Texture, vec2(v_TexCoordinate.s - pixelStep.s, v_TexCoordinate.t)).r;        \n"
                + "     float a23 = texture2D(u_Texture, vec2(v_TexCoordinate.s + pixelStep.s, v_TexCoordinate.t)).r;        \n"
                + "     float a31 = texture2D(u_Texture, vec2(v_TexCoordinate.s - pixelStep.s, v_TexCoordinate.t + pixelStep.t)).r;        \n"
                + "     float a32 = texture2D(u_Texture, vec2(v_TexCoordinate.s, v_TexCoordinate.t + pixelStep.t)).r;        \n"
                + "     float a33 = texture2D(u_Texture, v_TexCoordinate + pixelStep).r;        \n"
                + "     float strongPixel = step(2.0, edgeStrength+a11+a12+a13+a21+a23+a31+a32+a33);        \n"
                + "     gl_FragColor = vec4(1.0 - (strongPixel + (edgeStrength - strongPixel) * step(0.49,abs(edgeStrength-0.5))));         \n"
                + "} \n";


    /**
     * Initialize the model data.
     */
    public GPURenderer(final Context activityContext)
    {
        mActivityContext = activityContext;
//        mFrameBuffer = IntBuffer.allocate(3);
        // Define points for a cube.

        // X, Y, Z
        final float[] cubePositionData = {
                // In OpenGL counter-clockwise winding is default. This means
                // that when we look at a triangle,
                // if the points are counter-clockwise we are looking at the
                // "front". If not we are looking at
                // the back. OpenGL has an optimization where all back-facing
                // triangles are culled, since they
                // usually represent the backside of an object and aren't
                // visible anyways.

                // Front face
                -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, 1.0f, -1.0f,
                -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, 1.0f, 1.0f };

        // R, G, B, A
        final float[] cubeColorData = {
                // Front face (red)
                1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f,
                1.0f, 0.0f, 0.0f, 1.0f };

        // X, Y, Z
        // The normal is used in light calculations and is a vector which points
        // orthogonal to the plane of the surface. For a cube model, the normals
        // should be orthogonal to the points of each face.

        // S, T (or X, Y)
        // Texture coordinate data.
        // Because images have a Y axis pointing downward (values increase as
        // you move down the image) while
        // OpenGL has a Y axis pointing upward, we adjust for that here by
        // flipping the Y axis.
        // What's more is that the texture coordinates are the same for every
        // face.
        final float[] cubeTextureCoordinateData = {
                // Front face
                0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 0.0f };

        // Initialize the buffers.
        mCubePositions = ByteBuffer
                .allocateDirect(cubePositionData.length * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mCubePositions.put(cubePositionData).position(0);

        mCubeColors = ByteBuffer
                .allocateDirect(cubeColorData.length * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mCubeColors.put(cubeColorData).position(0);

        mCubeTextureCoordinates = ByteBuffer
                .allocateDirect(
                        cubeTextureCoordinateData.length * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mCubeTextureCoordinates.put(cubeTextureCoordinateData).position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config)
    {
        // Set the background clear color to black.
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        // Use culling to remove back faces.
        GLES20.glEnable(GLES20.GL_CULL_FACE);

//        // Enable depth testing
//        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Position the eye behind the origin.
        final float eyeX = 0.0f;
        final float eyeY = 0.0f;
        final float eyeZ = 1.5f;

        // We are looking toward the distance
        final float lookX = 0.0f;
        final float lookY = 0.0f;
        final float lookZ = -5.0f;

        // Set our up vector. This is where our head would be pointing were we holding the camera.
        final float upX = 0.0f;
        final float upY = 1.0f;
        final float upZ = 0.0f;

        // Set the view matrix. This matrix can be said to represent the camera position.
        // NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
        // view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
        Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);

//        // Load the texture
//        mTextureDataHandle0 = TextureHelper.loadTexture(mActivityContext,
//                R.drawable.img01);
//        mTextureDataHandle1 = TextureHelper.loadTexture(mActivityContext);
//        mTextureDataHandle2 = TextureHelper.loadTexture(mActivityContext);
//        mTextureDataHandle3 = TextureHelper.loadTexture(mActivityContext);

        mVertexShaderHandle = ShaderHelper.compileShader(
                GLES20.GL_VERTEX_SHADER, vertexShader);
        mVertexShaderPassthroughHandle = ShaderHelper.compileShader(
                GLES20.GL_VERTEX_SHADER, vertexPassthrough);
        mFragmentShaderHandle1 = ShaderHelper.compileShader(
                GLES20.GL_FRAGMENT_SHADER, gaussblur55_f);
        mFragmentShaderHandle2 = ShaderHelper.compileShader(
                GLES20.GL_FRAGMENT_SHADER, sobel_f);
        mFragmentShaderHandle3 = ShaderHelper.compileShader(
                GLES20.GL_FRAGMENT_SHADER, nonmaxsupress_f);
        mFragmentShaderHandle4 = ShaderHelper.compileShader(
                GLES20.GL_FRAGMENT_SHADER, weakpixeltest_f);


        mGaussProgram = ShaderHelper.createAndLinkProgram(mVertexShaderHandle,
                mFragmentShaderHandle1, new String[] { "a_Position",
                        "a_TexCoordinate" });

        mSobelProgram = ShaderHelper.createAndLinkProgram(mVertexShaderHandle,
                mFragmentShaderHandle2, new String[] { "a_Position",
                        "a_TexCoordinate" });

        mNMSProgram = ShaderHelper.createAndLinkProgram(mVertexShaderPassthroughHandle,
                mFragmentShaderHandle3, new String[] { "a_Position",
                        "a_TexCoordinate" });

        mWeakPixelProgram = ShaderHelper.createAndLinkProgram(mVertexShaderPassthroughHandle,
                mFragmentShaderHandle4, new String[] { "a_Position",
                        "a_TexCoordinate" });


        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 0.0f, 0.0f, -3.2f);
        Matrix.rotateM(mModelMatrix, 0, 0.0f, 1.0f, 1.0f, 0.0f);

        // This multiplies the view matrix by the model matrix, and stores the
        // result in the MVP matrix
        // (which currently contains model * view).
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);


        // TODO: Move to drawCube for real time usage, otherwise here is fine since only 1 load is needed
        // Load the image as texture. Binds to current unit
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        mTexture1 = TextureHelper.loadTexture(mActivityContext);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        mTexture2 = TextureHelper.loadTexture(mActivityContext,
                R.drawable.img01);

        mmWidth = 640;
        mmHeight = 480;

        GLES20.glGenFramebuffers( 2, mFrameBuffer, 0);
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height)
    {
        // Set the OpenGL viewport to the same size as the surface.
        GLES20.glViewport(0, 0, width, height);

        // Create a new perspective projection matrix. The height will stay the same
        // while the width will vary as per aspect ratio.
        final float ratio = (float) width / height;
        final float left = -ratio;
        final float right = ratio;
        final float bottom = -1.0f;
        final float top = 1.0f;
        final float near = 1.0f;
        final float far = 10.0f;

        Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
        initPrograms();

    }

    @Override
    public void onDrawFrame(GL10 glUnused)
    {
        long prevTime = System.nanoTime();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        drawCube();

        long currTime = System.nanoTime();
        counter += currTime-prevTime;
        num_iter++;
        if(num_iter == 1000){
            Log.d("CANNY", "time: "+counter/num_iter);
            num_iter = 0;
            counter = 0;
        }
    }

    private void initPrograms() {


        // This multiplies the modelview matrix by the projection matrix, and
        // stores the result in the MVP matrix
        // (which now contains model * view * projection).
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);

        // ==== GAUSS ==== //
        GLES20.glUseProgram(mGaussProgram);

        // Set program handles for cube drawing.
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mGaussProgram,
                "u_MVPMatrix");
        mPositionHandle = GLES20.glGetAttribLocation(mGaussProgram,
                "a_Position");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mGaussProgram,
                "a_TexCoordinate");

        // Pass in the position information
        mCubePositions.position(0);
        GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize,
                GLES20.GL_FLOAT, false, 0, mCubePositions);

        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Pass in the texture coordinate information
        mCubeTextureCoordinates.position(0);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle,
                mTextureCoordinateDataSize, GLES20.GL_FLOAT, false, 0,
                mCubeTextureCoordinates);

        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        // ==== SOBEL ==== //
        GLES20.glUseProgram(mSobelProgram);

        // Set program handles for cube drawing.
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mSobelProgram,
                "u_MVPMatrix");
        mPositionHandle = GLES20.glGetAttribLocation(mSobelProgram,
                "a_Position");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mSobelProgram,
                "a_TexCoordinate");

        // Pass in the position information
        mCubePositions.position(0);
        GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize,
                GLES20.GL_FLOAT, false, 0, mCubePositions);

        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Pass in the texture coordinate information
        mCubeTextureCoordinates.position(0);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle,
                mTextureCoordinateDataSize, GLES20.GL_FLOAT, false, 0,
                mCubeTextureCoordinates);

        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        // ==== NMS ==== //
        GLES20.glUseProgram(mNMSProgram);

        // Set program handles for cube drawing.
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mNMSProgram,
                "u_MVPMatrix");
        mPositionHandle = GLES20.glGetAttribLocation(mNMSProgram,
                "a_Position");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mNMSProgram,
                "a_TexCoordinate");

        // Pass in the position information
        mCubePositions.position(0);
        GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize,
                GLES20.GL_FLOAT, false, 0, mCubePositions);

        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Pass in the texture coordinate information
        mCubeTextureCoordinates.position(0);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle,
                mTextureCoordinateDataSize, GLES20.GL_FLOAT, false, 0,
                mCubeTextureCoordinates);

        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);


        // ==== WEAK PIXEL ==== //
        GLES20.glUseProgram(mWeakPixelProgram);

        // Set program handles for cube drawing.
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mWeakPixelProgram,
                "u_MVPMatrix");
        mPositionHandle = GLES20.glGetAttribLocation(mWeakPixelProgram,
                "a_Position");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mWeakPixelProgram,
                "a_TexCoordinate");

        // Pass in the position information
        mCubePositions.position(0);
        GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize,
                GLES20.GL_FLOAT, false, 0, mCubePositions);

        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Pass in the texture coordinate information
        mCubeTextureCoordinates.position(0);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle,
                mTextureCoordinateDataSize, GLES20.GL_FLOAT, false, 0,
                mCubeTextureCoordinates);

        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
    }

    /**
     * Draws a cube.
     */
    private void drawCube() {


        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mTexture1, 0);


        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.d("CANNY", "NOT COMPLETE!!!!");
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[1]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mTexture2, 0);

        status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.d("CANNY", "NOT COMPLETE!!!!");
        }



        // ==== GAUSS ==== //
        // Set our per-vertex lighting program.
        GLES20.glUseProgram(mGaussProgram);

        // FBO 0
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]); // w -> mTexture1
//        GLES20.glViewport(0, 0, 1000, 1000);

        // Set program handles for cube drawing.
        mTextureUniformHandle = GLES20.glGetUniformLocation(mGaussProgram,
                "u_Texture");
        mPixelStepHandle = GLES20.glGetUniformLocation(mGaussProgram,
                "pixelStep");

        // Set the active texture1 unit to texture unit 1.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2); // r -> mTexture2

        // Tell the texture uniform sampler to use this texture in the shader by
        // binding to texture unit 0.
        GLES20.glUniform1i(mTextureUniformHandle, 2);


        // Horizontal Gaussian
        mPixelStep[0] = (float)(1.0/mmWidth);
        mPixelStep[1] = 0;

        GLES20.glUniform2fv(mPixelStepHandle, 1, mPixelStep, 0);

        // Draw the cube.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        // Vertical Gaussian
        mPixelStep[0] = 0;
        mPixelStep[1] = (float)(1.0/mmHeight);

        GLES20.glUniform2fv(mPixelStepHandle, 1, mPixelStep, 0);

        // Draw the cube.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);


        // ==== SOBEL ==== //

        // Set our per-vertex lighting program.
        GLES20.glUseProgram(mSobelProgram);

        // FBO 1
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[1]);
//        GLES20.glViewport(0, 0, mmWidth, mmHeight);


        // Set program handles for cube drawing.
        mTextureUniformHandle = GLES20.glGetUniformLocation(mSobelProgram,
                "u_Texture");
        mPixelStepHandle = GLES20.glGetUniformLocation(mSobelProgram,
                "pixelStep");
        mTexSizeHandle = GLES20.glGetUniformLocation(mSobelProgram,
                "tex_size");

        // Set the active texture1 unit to texture unit 1.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);

//        // Bind previous texture to this unit.
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture1); //ALREADY BOUND?

        // Tell the texture uniform sampler to use this texture in the shader by
        // binding to texture unit 0.
        GLES20.glUniform1i(mTextureUniformHandle, 1);

        mPixelStep[0] = (float)(1.0/mmWidth);
        mPixelStep[1] = (float)(1.0/mmHeight);

        GLES20.glUniform2fv(mPixelStepHandle, 1, mPixelStep, 0);

//        mTexSize[0] = 640;
//        mTexSize[1] = 480;
//
//        GLES20.glUniform2fv(mTexSizeHandle, 1, mTexSize, 0);

        // Draw the cube.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        // ==== NMS ==== //

        // Set our per-vertex lighting program.
        GLES20.glUseProgram(mNMSProgram);

        // FBO 0
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);

        // Set program handles for cube drawing.
        mTextureUniformHandle = GLES20.glGetUniformLocation(mNMSProgram,
                "u_Texture");
        mPixelStepHandle = GLES20.glGetUniformLocation(mNMSProgram,
                "pixelStep");

        // Set the active texture1 unit to texture unit 1.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);


        // Tell the texture uniform sampler to use this texture in the shader by
        // binding to texture unit 0.
        GLES20.glUniform1i(mTextureUniformHandle, 2);


        mPixelStep[0] = (float)(1.0/mmWidth);
        mPixelStep[1] = (float)(1.0/mmHeight);

        GLES20.glUniform2fv(mPixelStepHandle, 1, mPixelStep, 0);

        // Draw the cube.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        // ==== WEAK PIXEL ==== //

        // Set our per-vertex lighting program.
        GLES20.glUseProgram(mWeakPixelProgram);

        // FBO Default
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        // Set program handles for cube drawing.
        mTextureUniformHandle = GLES20.glGetUniformLocation(mWeakPixelProgram,
                "u_Texture");
        mPixelStepHandle = GLES20.glGetUniformLocation(mWeakPixelProgram,
                "pixelStep");

        // Set the active texture1 unit to texture unit 1.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);


        // Tell the texture uniform sampler to use this texture in the shader by
        // binding to texture unit 0.
        GLES20.glUniform1i(mTextureUniformHandle, 1);

        mPixelStep[0] = (float)(1.0/mmWidth);
        mPixelStep[1] = (float)(1.0/mmHeight);

        GLES20.glUniform2fv(mPixelStepHandle, 1, mPixelStep, 0);

        // Draw the cube.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
    }
}