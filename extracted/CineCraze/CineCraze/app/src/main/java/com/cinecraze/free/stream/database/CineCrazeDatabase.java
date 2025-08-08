package com.cinecraze.free.stream.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.cinecraze.free.stream.database.dao.EntryDao;
import com.cinecraze.free.stream.database.dao.CacheMetadataDao;
import com.cinecraze.free.stream.database.entities.EntryEntity;
import com.cinecraze.free.stream.database.entities.CacheMetadataEntity;

@Database(
    entities = {EntryEntity.class, CacheMetadataEntity.class},
    version = 1,
    exportSchema = false
)
public abstract class CineCrazeDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "cinecraze_database";
    private static CineCrazeDatabase instance;
    
    public abstract EntryDao entryDao();
    public abstract CacheMetadataDao cacheMetadataDao();
    
    public static synchronized CineCrazeDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                context.getApplicationContext(),
                CineCrazeDatabase.class,
                DATABASE_NAME
            )
            .allowMainThreadQueries() // For simplicity, but ideally use background threads
            .build();
        }
        return instance;
    }
}