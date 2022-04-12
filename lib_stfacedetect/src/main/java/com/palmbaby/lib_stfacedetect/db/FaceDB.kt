package com.palmbaby.lib_stfacedetect.db

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 人脸数据库
 * Created by LLhon on 2022/4/11 10:48.
 */
@Database(entities = [FaceData::class], version = 1, exportSchema = false)
abstract class FaceDB : RoomDatabase() {

    abstract val faceDao: FaceDao

    companion object {
        const val DB_NAME = "face_db"

        @Volatile
        private var instance: FaceDB? = null

        fun get(context: Context) : FaceDB {
            return instance ?: Room.databaseBuilder(context, FaceDB::class.java, DB_NAME)
                .build().also {
                    instance = it
                }
        }
    }
}

@Entity(tableName = "face_data")
data class FaceData(
    @PrimaryKey
    val id: Int,
    val studentId: String,
    val teacherId: String,
    //人脸id，即图片id
    val faceId: Int,
    //新的人脸url
    val imageUrl: String,
    //上一次人脸注册成功的url
    val oldImageUrl: String,
    //注册人脸失败状态：0：还未注册; 1 ：注册成功;  -22:下载失败; < 0：提取人脸特征失败错误码
    val registerStatus: Int,
    //用于增量更新，全量更新不用关心
    val delFlag: Int
)

@Dao
interface FaceDao {

    @Query("select * from face_data where faceId =:faceId")
    fun queryFace(faceId: Int = 0): LiveData<FaceData>
}