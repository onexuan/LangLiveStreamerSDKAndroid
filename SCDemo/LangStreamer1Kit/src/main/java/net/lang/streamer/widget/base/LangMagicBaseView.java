package net.lang.streamer.widget.base;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.helper.MagicFilterFactory;
import net.lang.gpuimage.helper.MagicFilterParam;
import net.lang.gpuimage.helper.MagicFilterType;
import net.lang.gpuimage.utils.*;

//import net.lang.streamer.helper.SavePictureTask;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public abstract class LangMagicBaseView extends GLSurfaceView implements GLSurfaceView.Renderer{

    /**
     * 所选择的滤镜，类型为MagicBaseGroupFilter
     * 1.mCameraInputFilter将SurfaceTexture中YUV数据绘制到FrameBuffer
     * 2.filter将FrameBuffer中的纹理绘制到屏幕中
     */
    protected GPUImageFilter filter;

    /**
     * SurfaceTexure纹理id
     */
    protected int textureId = OpenGlUtils.NO_TEXTURE;

    /**
     * 顶点坐标
     */
    protected final FloatBuffer gLCubeBuffer;

    /**
     * 纹理坐标
     */
    protected final FloatBuffer gLTextureBuffer;

    /**
     * GLSurfaceView的宽高
     */
    protected int surfaceWidth, surfaceHeight;

    /**
     * 图像宽高
     */
    protected int imageWidth, imageHeight;

    /**
     * EGLContext上下文
     */
    //protected EGLContext mEGLCurrentContext;

    /*
    public static class SnailContextFactory implements EGLContextFactory {

        private static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private static final String TAG = SnailContextFactory.class.getSimpleName();

        private LangMagicBaseView mRenderer;

        public SnailContextFactory(LangMagicBaseView renderer) {
            DebugLog.d(TAG, "SnailContextFactory " + renderer);
            this.mRenderer = renderer;
        }

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
            //Log.d(TAG, "createContext " + egl + " " + display + " " + eglConfig);
            checkEglError("before createContext", egl);
            int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};

            EGLContext ctx;

            if (mRenderer.mEGLCurrentContext == null) {
                mRenderer.mEGLCurrentContext = egl.eglCreateContext(display, eglConfig,
                        EGL10.EGL_NO_CONTEXT, attrib_list);
                ctx = mRenderer.mEGLCurrentContext;
            } else {
                ctx = mRenderer.mEGLCurrentContext;
            }
            checkEglError("after createContext", egl);
            return ctx;
        }

        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            //Log.d(TAG, "destroyContext " + egl + " " + display + " " + context + " " + mRenderer.mEGLCurrentContext);
            if (mRenderer.mEGLCurrentContext != null) {
                egl.eglDestroyContext(display, context);
            }
        }

        private static void checkEglError(String prompt, EGL10 egl) {
            int error;
            while ((error = egl.eglGetError()) != EGL10.EGL_SUCCESS) {
                Log.d(TAG, String.format(Locale.US, "%s: EGL error: 0x%x", prompt, error));
            }
        }
    }
    */

    public LangMagicBaseView(Context context) {
        this(context, null);
    }

    public LangMagicBaseView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

        gLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);

        //setEGLContextFactory(new SnailContextFactory(this));

        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glDisable(GL10.GL_DITHER);
        GLES20.glClearColor(0,0, 0, 0);
        GLES20.glEnable(GL10.GL_CULL_FACE);
        GLES20.glEnable(GL10.GL_DEPTH_TEST);
        MagicFilterParam.initMagicFilterParam(gl);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0,0,width, height);
        surfaceWidth = width;
        surfaceHeight = height;
        onFilterChanged();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    }

    protected void onFilterChanged(){
        if(filter != null) {
            filter.onDisplaySizeChanged(surfaceWidth, surfaceHeight);
            filter.onInputSizeChanged(imageWidth, imageHeight);
        }
    }

    public void onResume(){

    }

    public void onPause(){

    }

    public void onDestroy(){

    }

    public void setFilter(final MagicFilterType type, final Context context){
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (filter != null)
                    filter.destroy();
                filter = null;
                filter = MagicFilterFactory.initFilters(type, context);
                if (filter != null)
                    filter.init();
                onFilterChanged();
            }
        });
        requestRender();
    }

    protected void deleteTextures() {
        if(textureId != OpenGlUtils.NO_TEXTURE){
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLES20.glDeleteTextures(1, new int[]{
                            textureId
                    }, 0);
                    textureId = OpenGlUtils.NO_TEXTURE;
                }
            });
        }
    }
}