package com.palmbaby.lib_stfacedetect.event;

public class UpdateCameraInfoEvent {
    private boolean isShowCameraErrinfo;

    public UpdateCameraInfoEvent(boolean isShowCameraErrinfo) {
        this.isShowCameraErrinfo = isShowCameraErrinfo;
    }

    public boolean isShowCameraErrinfo() {
        return isShowCameraErrinfo;
    }

    public void setShowCameraErrinfo(boolean showCameraErrinfo) {
        isShowCameraErrinfo = showCameraErrinfo;
    }
}
