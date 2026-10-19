package com.gankdat.pomegranate.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
import com.gankdat.pomegranate.data.db.dao.MatchDao
import com.gankdat.pomegranate.data.db.dao.MediaGroupDao
import com.gankdat.pomegranate.data.db.dao.OriginRuleDao
import com.gankdat.pomegranate.data.db.dao.PickerRoundDao
import com.gankdat.pomegranate.data.db.dao.TransferJobDao
import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.CloudItemGroupCrossRef
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.data.db.entity.DeviceItemGroupCrossRef
import com.gankdat.pomegranate.data.db.entity.MatchEntity
import com.gankdat.pomegranate.data.db.entity.MediaGroupEntity
import com.gankdat.pomegranate.data.db.entity.OriginRuleEntity
import com.gankdat.pomegranate.data.db.entity.PickerRoundEntity
import com.gankdat.pomegranate.data.db.entity.TransferJobEntity

/**
 * The app's single on-device database: the device census, the cloud index
 * built from Picker rounds, the matches between them, transfer jobs and
 * origin rules. Lives in app-private storage, is excluded from cloud backup
 * and device-to-device transfer, and is wiped on Disconnect.
 */
@Database(
    entities = [
        DeviceItemEntity::class,
        CloudItemEntity::class,
        MediaGroupEntity::class,
        DeviceItemGroupCrossRef::class,
        CloudItemGroupCrossRef::class,
        MatchEntity::class,
        TransferJobEntity::class,
        OriginRuleEntity::class,
        PickerRoundEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
internal abstract class PomegranateDatabase : RoomDatabase() {
    abstract fun deviceItemDao(): DeviceItemDao

    abstract fun cloudItemDao(): CloudItemDao

    abstract fun mediaGroupDao(): MediaGroupDao

    abstract fun matchDao(): MatchDao

    abstract fun transferJobDao(): TransferJobDao

    abstract fun originRuleDao(): OriginRuleDao

    abstract fun pickerRoundDao(): PickerRoundDao

    companion object {
        internal const val NAME: String = "pomegranate.db"

        @Volatile
        private var instance: PomegranateDatabase? = null

        /** Process-wide singleton bound to the application context. */
        fun getInstance(context: Context): PomegranateDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): PomegranateDatabase =
            Room.databaseBuilder(context, PomegranateDatabase::class.java, NAME).build()
    }
}
