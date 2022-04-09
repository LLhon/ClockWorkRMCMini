package com.palmbaby.lib_stfacedetect;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * Created by HBOK on 2015/8/25.
 */
public class DeviceInfo {
    public static int readType = 0;
    public static int mainLayoutId = 0;
    public static int degree = 0;  //指定摄像头的旋转角度，这里只保存主界面预览使用的摄像头旋转角，一般为后置摄像头
    public static String MODEL;
    public static boolean isVerticalScreen = false;
    public static int ScreenWidth;
    public static int ScreenHeight;

    public static void initInfo(Context context){
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        MODEL = android.os.Build.MODEL;
        ScreenWidth = dm.widthPixels;
        ScreenHeight = dm.heightPixels;
        {
            readType = 0;
            degree = -1;
        }
        if(ScreenWidth > ScreenHeight){
            isVerticalScreen = false;
        }else{
            isVerticalScreen = true;
        }
    }

    public static boolean isPreviewTakePicture(){
        if("DS83X".equals(MODEL) || "YL-A831".equals(MODEL)){
            return false;
        }
        return true;
    }
}
