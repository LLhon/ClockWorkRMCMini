package com.palmbaby.lib_stfacedetect.model;

import android.os.Parcel;
import android.os.Parcelable;

public class ScanFaceInfo implements Parcelable {

    private boolean isTeacherFace;  //是否是家长人脸

    //如果 isTeacherFace true    id则对应园丁端teacherId     false家长的parentId
    private String id;

    private int faceId;

    public boolean isTeacherFace() {
        return isTeacherFace;
    }

    public void setTeacherFace(boolean teacherFace) {
        isTeacherFace = teacherFace;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getFaceId() {
        return faceId;
    }

    public void setFaceId(int faceId) {
        this.faceId = faceId;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(this.isTeacherFace ? (byte) 1 : (byte) 0);
        dest.writeString(this.id);
        dest.writeInt(this.faceId);
    }

    public ScanFaceInfo() {
    }

    protected ScanFaceInfo(Parcel in) {
        this.isTeacherFace = in.readByte() != 0;
        this.id = in.readString();
        this.faceId = in.readInt();
    }

    public static final Creator<ScanFaceInfo> CREATOR = new Creator<ScanFaceInfo>() {
        @Override
        public ScanFaceInfo createFromParcel(Parcel source) {
            return new ScanFaceInfo(source);
        }

        @Override
        public ScanFaceInfo[] newArray(int size) {
            return new ScanFaceInfo[size];
        }
    };
}
