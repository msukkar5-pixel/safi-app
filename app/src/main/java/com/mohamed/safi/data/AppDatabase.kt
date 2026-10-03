package com.mohamed.safi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Expense::class, Transfer::class, Bill::class, Debt::class, Reminder::class,
        LocationLog::class, SavedPlace::class, MerchantRule::class, Budget::class,
        CarItem::class, ChatMsg::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "safi.db").build()
    }
}
