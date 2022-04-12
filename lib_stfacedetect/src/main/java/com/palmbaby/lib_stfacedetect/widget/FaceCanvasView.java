package com.palmbaby.lib_stfacedetect.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Looper;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;
import com.palmbaby.lib_stfacedetect.R;
import com.smdt.facesdk.mipsFaceInfoTrack;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FaceCanvasView extends AppCompatImageView {
    public static int DETECT_STATE = 0;
    public static int REGISTER_STATE = 1;
    public static int RECO_STATE = 2;
    public static int ANALYSIS_STATE = 3;
    //	protected int mState = DETECT_STATE;
    private ArrayList<mipsFaceInfoTrack> mFaceLivenessList;
    private int mCanvasWidth;
    private int mCanvasHeight;
    private float mXRatio;
    private float mYRatio;

    private Paint mRectPaint;
    private Paint mNamePaint;
    private RectF mDrawFaceRect = new RectF();
    public Rect mOverRect, srcRect;
    private int mCameraWidth;
    private int mCameraHeight;
    private int flgPortrait = 0;
    private Lock lockFace = new ReentrantLock();
    private Bitmap mBitmap;
    private NinePatch ninePatch = null;
    private boolean isCameraFacingFront = false;  //是否前置摄像头
    private boolean isMonocular;

    FaceCanvasView(Context context) {
        super(context);
        reset();
    }

    public FaceCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        reset();
    }

    public FaceCanvasView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        reset();
    }

    public void reset() {
        lockFace.lock();
        try {
            if (mFaceLivenessList == null) {
                mFaceLivenessList = new ArrayList<>();
            }
            mFaceLivenessList.clear();
            mCameraWidth = 1;
            mCameraHeight = 1;

            mOverRect = new Rect(0, 0, 0, 0);

            // 矩形框
            mRectPaint = new Paint();
            mRectPaint.setColor(Color.BLUE);
            mRectPaint.setStyle(Paint.Style.STROKE);
            mRectPaint.setStrokeWidth(8);
            mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.face_frame4);
            byte[] ninePatchChunk = mBitmap.getNinePatchChunk();
            if (ninePatchChunk != null) {
                ninePatch = new NinePatch(mBitmap, ninePatchChunk, null);
            }
            srcRect = new Rect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
            // 识别名
            mNamePaint = new Paint();
            mNamePaint.setColor(Color.BLUE);
            mNamePaint.setTextSize(30);
            mNamePaint.setStyle(Paint.Style.FILL);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lockFace.unlock();
        }
//		setCavasPortrait();
    }


    public void setCameraPreviewSize(int width, int height, int degrees, boolean isMonocular) {
        switch (degrees) {
            case 0:
                flgPortrait = 0;
                mCameraWidth = width;
                mCameraHeight = height;
                break;
            case 90:
                flgPortrait = 1;
                mCameraWidth = height;
                mCameraHeight = width;
                break;
            case 180:
                flgPortrait = 2;
                mCameraWidth = width;
                mCameraHeight = height;
                break;
            case 270:
                flgPortrait = 3;
                mCameraWidth = height;
                mCameraHeight = width;
                break;
        }
        this.isMonocular = isMonocular;
        mXRatio = (float) mOverRect.width() / (float) mCameraWidth;
        mYRatio = (float) mOverRect.height() / (float) mCameraHeight;
    }

    /**
     * 是否使用前置摄像头
     *
     * @param cameraFacingFront
     */
    public void setCameraFacingFront(boolean cameraFacingFront) {
        isCameraFacingFront = cameraFacingFront;
    }

    //	public void setState(int state) {
//		mState = state;
//	}

//	public void setOverlayRect(int left, int right, int top, int bottom,int camWidth, int camHeight) {
//		mOverRect = new Rect(left, top, right, bottom);
//		mCameraWidth = camWidth;
//		mCameraHeight = camHeight;
//		mXRatio = (float)mOverRect.width()/(float)mCameraWidth;
//		mYRatio = (float)mOverRect.height()/(float)mCameraHeight;
//	}

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            mOverRect.set(left, top, right, bottom);
            mXRatio = (float) mOverRect.width() / (float) mCameraWidth;
            mYRatio = (float) mOverRect.height() / (float) mCameraHeight;
        }
    }

    public void addFacesFrames(mipsFaceInfoTrack[] faceInfo) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (lockFace.tryLock()) {
                try {
                    progressAddFaces(faceInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    lockFace.unlock();
                }
                postInvalidate();
            }
        } else {
            lockFace.lock();
            try {
                progressAddFaces(faceInfo);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lockFace.unlock();
            }
            postInvalidate();
        }
    }



    private void progressAddFaces(mipsFaceInfoTrack[] faceInfo) {
        mFaceLivenessList.clear();
        if (faceInfo == null) {
            return;
        }
        for (int i = 0; i < mipsFaceInfoTrack.MAX_FACE_CNT_ONEfRAME; i++) {
            if (faceInfo[i] == null) {
                continue;
            }
            mipsFaceInfoTrack face = faceInfo[i];
            mFaceLivenessList.add(face);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawFaceResult(canvas);
    }

    /**
     * 画人脸框：与人脸检测、注册、识别相关
     */
    private void drawFaceResult(Canvas canvas) {
        // 清空画布
        canvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);
        try {
            for (mipsFaceInfoTrack faceinfo : mFaceLivenessList) {
                if (faceinfo == null) {
                    continue;
                }
                if (flgPortrait == 0) {
                    mDrawFaceRect.left = mOverRect.left + (float) (mCameraWidth - faceinfo.faceRect. right) * mXRatio;
                    mDrawFaceRect.right = mOverRect.left + (float) (mCameraWidth - faceinfo.faceRect.left) * mXRatio;

                    mDrawFaceRect.top = mOverRect.top + (float) faceinfo.faceRect.top * mYRatio;
                    mDrawFaceRect.bottom = mOverRect.top + (float) faceinfo.faceRect.bottom * mYRatio;
                } else if (flgPortrait == 1) {
                    mDrawFaceRect.left = mOverRect.left + (float) (mCameraWidth - faceinfo.faceRect.bottom) * mXRatio;
                    mDrawFaceRect.right = mOverRect.left + (float) (mCameraWidth - faceinfo.faceRect.top) * mXRatio;
                    if (isMonocular) { //前置摄像头旋转预览之前需要水平翻转，所以，需要对faceinfo.faceRect的left和right做转换
                        mDrawFaceRect.top = mOverRect.top + (float) (mCameraHeight - faceinfo.faceRect.right) * mYRatio;
                        mDrawFaceRect.bottom = mOverRect.top + (float) (mCameraHeight - faceinfo.faceRect.left) * mYRatio;
                    } else {
                        mDrawFaceRect.top = mOverRect.top + (float) faceinfo.faceRect.left * mYRatio;
                        mDrawFaceRect.bottom = mOverRect.top + (float) faceinfo.faceRect.right * mYRatio;
                    }
                } else if (flgPortrait == 2) {
                    mDrawFaceRect.left = mOverRect.left + (float) (mCameraWidth - faceinfo.faceRect.right) * mXRatio;
                    mDrawFaceRect.right = mOverRect.left + (float) (mCameraWidth - faceinfo.faceRect.left) * mXRatio;
                    mDrawFaceRect.top = mOverRect.top + (float) (mCameraHeight - faceinfo.faceRect.bottom) * mYRatio;
                    mDrawFaceRect.bottom = mOverRect.top + (float) (mCameraHeight - faceinfo.faceRect.top) * mYRatio;
                } else if (flgPortrait == 3) {
                    mDrawFaceRect.left = mOverRect.left + (float) (faceinfo.faceRect.top) * mXRatio;
                    mDrawFaceRect.right = mOverRect.left + (float) (faceinfo.faceRect.bottom) * mXRatio;
                    mDrawFaceRect.top = mOverRect.top + (float) (mCameraHeight - faceinfo.faceRect.right) * mYRatio;
                    mDrawFaceRect.bottom = mOverRect.top + (float) (mCameraHeight - faceinfo.faceRect.left) * mYRatio;
                }
                if (ninePatch != null) {  //点9图的绘制
                    ninePatch.draw(canvas, mDrawFaceRect);
                } else {
                    canvas.drawBitmap(mBitmap, srcRect, mDrawFaceRect, mRectPaint);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
