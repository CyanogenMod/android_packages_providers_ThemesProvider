/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cyanogenmod.themes.provider;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ThemeUtils;
import android.content.res.ThemeConfig;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.ThemesContract;
import android.provider.ThemesContract.ThemesColumns;
import android.provider.ThemesContract.MixnMatchColumns;
import android.util.Log;

public class ThemesOpenHelper extends SQLiteOpenHelper {
    private static final String TAG = ThemesOpenHelper.class.getName();

    private static final int DATABASE_VERSION = 5;
    private static final String DATABASE_NAME = "themes.db";
    private static final String DEFAULT_PKG_NAME = ThemeConfig.HOLO_DEFAULT;

    private Context mContext;

    public ThemesOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(ThemesTable.THEMES_TABLE_CREATE);
        db.execSQL(MixnMatchTable.MIXNMATCH_TABLE_CREATE);

        ThemesTable.insertHoloDefaults(db, mContext);
        MixnMatchTable.insertDefaults(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading DB from version " + oldVersion + " to " + newVersion);
        try {
            if (oldVersion == 1) {
                upgradeToVersion2(db);
                oldVersion = 2;
            }
            if (oldVersion == 2) {
                upgradeToVersion3(db);
                oldVersion = 3;
            }
            if (oldVersion == 3) {
                upgradeToVersion4(db);
                oldVersion = 4;
            }
            if (oldVersion == 4) {
                upgradeToVersion5(db);
                oldVersion = 5;
            }
            if (oldVersion != DATABASE_VERSION) {
                Log.e(TAG, "Recreating db because unknown database version: " + oldVersion);
                dropTables(db);
                onCreate(db);
            }
        } catch(SQLiteException e) {
            Log.e(TAG, "onUpgrade: SQLiteException, recreating db. ", e);
            Log.e(TAG, "(oldVersion was " + oldVersion + ")");
            dropTables(db);
            onCreate(db);
            return;
        }
    }

    private void upgradeToVersion2(SQLiteDatabase db) {
        String addStyleColumn = String.format("ALTER TABLE %s ADD COLUMN %s TEXT",
                ThemesTable.TABLE_NAME, ThemesColumns.STYLE_URI);
        db.execSQL(addStyleColumn);
    }

    private void upgradeToVersion3(SQLiteDatabase db) {
        // Add default value to mixnmatch for KEY_ALARM
        ContentValues values = new ContentValues();
        values.put(MixnMatchColumns.COL_KEY, ThemesContract.MixnMatchColumns.KEY_ALARM);
        values.put(MixnMatchColumns.COL_VALUE, DEFAULT_PKG_NAME);
        db.insert(MixnMatchTable.TABLE_NAME, null, values);
    }

    private void upgradeToVersion4(SQLiteDatabase db) {
        String isLegacyIconPackColumn = String.format("ALTER TABLE %s" +
                        " ADD COLUMN %s INTEGER DEFAULT 0",
                ThemesTable.TABLE_NAME, ThemesColumns.IS_LEGACY_ICONPACK);
        db.execSQL(isLegacyIconPackColumn);
    }

    private void upgradeToVersion5(SQLiteDatabase db) {
        String addIsDefault = String.format("ALTER TABLE %s ADD COLUMN %s TEXT",
                ThemesTable.TABLE_NAME, ThemesColumns.IS_DEFAULT_THEME);
        db.execSQL(addIsDefault);

        // change default package name to holo
        String changeDefaultToHolo = String.format("UPDATE %s SET %s='%s' WHERE" +
                        " %s='%s'", ThemesTable.TABLE_NAME, ThemesColumns.PKG_NAME,
                DEFAULT_PKG_NAME, ThemesColumns.PKG_NAME, "default");
        db.execSQL(changeDefaultToHolo);

        if (isHoloDefault(mContext)) {
            // flag holo as default if
            String makeHoloDefault = String.format("UPDATE %s SET %s=%d WHERE" +
                            " %s='%s'", ThemesTable.TABLE_NAME, ThemesColumns.IS_DEFAULT_THEME, 1,
                    ThemesColumns.PKG_NAME, DEFAULT_PKG_NAME);
            db.execSQL(makeHoloDefault);
        }

        // change any existing mixnmatch values set to "default" to "holo"
        db.execSQL(String.format("UPDATE %s SET %s='%s' WHERE %s='%s'",
                MixnMatchTable.TABLE_NAME, MixnMatchColumns.COL_VALUE, DEFAULT_PKG_NAME,
                MixnMatchColumns.COL_VALUE, "default"));
    }

    private void dropTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + ThemesTable.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + MixnMatchTable.TABLE_NAME);
    }

    public static class ThemesTable {
        protected static final String TABLE_NAME = "themes";

        private static final String THEMES_TABLE_CREATE =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        ThemesColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                        ThemesColumns.TITLE + " TEXT," +
                        ThemesColumns.AUTHOR + " TEXT," +
                        ThemesColumns.PKG_NAME + " TEXT UNIQUE NOT NULL," +
                        ThemesColumns.DATE_CREATED + " INTEGER," +
                        ThemesColumns.HOMESCREEN_URI + " TEXT," +
                        ThemesColumns.LOCKSCREEN_URI + " TEXT," +
                        ThemesColumns.STYLE_URI + " TEXT," +
                        ThemesColumns.WALLPAPER_URI + " TEXT," +
                        ThemesColumns.BOOT_ANIM_URI + " TEXT," +
                        ThemesColumns.FONT_URI + " TEXT," +
                        ThemesColumns.STATUSBAR_URI + " TEXT," +
                        ThemesColumns.ICON_URI + " TEXT," +
                        ThemesColumns.PRIMARY_COLOR + " TEXT," +
                        ThemesColumns.SECONDARY_COLOR + " TEXT," +
                        ThemesColumns.MODIFIES_LAUNCHER + " INTEGER DEFAULT 0, " +
                        ThemesColumns.MODIFIES_LOCKSCREEN + " INTEGER DEFAULT 0, " +
                        ThemesColumns.MODIFIES_ICONS + " INTEGER DEFAULT 0, " +
                        ThemesColumns.MODIFIES_BOOT_ANIM + " INTEGER DEFAULT 0, " +
                        ThemesColumns.MODIFIES_FONTS + " INTEGER DEFAULT 0, " +
                        ThemesColumns.MODIFIES_RINGTONES + " INTEGER DEFAULT 0, " +
                        ThemesColumns.MODIFIES_NOTIFICATIONS + " INTEGER DEFAULT 0, " +
                        ThemesColumns.MODIFIES_ALARMS + " INTEGER DEFAULT 0, " +
                        ThemesColumns.MODIFIES_OVERLAYS + " INTEGER DEFAULT 0, " +
                        ThemesColumns.PRESENT_AS_THEME + " INTEGER DEFAULT 0, " +
                        ThemesColumns.IS_LEGACY_THEME + " INTEGER DEFAULT 0," +
                        ThemesColumns.IS_DEFAULT_THEME + " INTEGER DEFAULT 0," +
                        ThemesColumns.IS_LEGACY_ICONPACK + " INTEGER DEFAULT 0," +
                        ThemesColumns.LAST_UPDATE_TIME + " INTEGER DEFAULT 0" +
                        ")";

        public static void insertHoloDefaults(SQLiteDatabase db, Context context) {
            int isDefault = isHoloDefault(context) ? 1 : 0;
            ContentValues values = new ContentValues();
            values.put(ThemesColumns.TITLE, "Holo (Default)");
            values.put(ThemesColumns.PKG_NAME, DEFAULT_PKG_NAME);
            values.put(ThemesColumns.PRIMARY_COLOR, 0xff33b5e5);
            values.put(ThemesColumns.SECONDARY_COLOR, 0xff000000);
            values.put(ThemesColumns.AUTHOR, "Android");
            values.put(ThemesColumns.BOOT_ANIM_URI, "file:///android_asset/default_holo_theme/holo_boot_anim.jpg");
            values.put(ThemesColumns.HOMESCREEN_URI, "file:///android_asset/default_holo_theme/holo_homescreen.png");
            values.put(ThemesColumns.LOCKSCREEN_URI, "file:///android_asset/default_holo_theme/holo_lockscreen.png");
            values.put(ThemesColumns.STYLE_URI, "file:///android_asset/default_holo_theme/style.jpg");
            values.put(ThemesColumns.WALLPAPER_URI, "file:///android_asset/default_holo_theme/blueice_modcircle.jpg");
            values.put(ThemesColumns.MODIFIES_ALARMS, 1);
            values.put(ThemesColumns.MODIFIES_BOOT_ANIM, 1);
            values.put(ThemesColumns.MODIFIES_FONTS, 1);
            values.put(ThemesColumns.MODIFIES_ICONS, 1);
            values.put(ThemesColumns.MODIFIES_LAUNCHER, 1);
            values.put(ThemesColumns.MODIFIES_LOCKSCREEN, 1);
            values.put(ThemesColumns.MODIFIES_NOTIFICATIONS, 1);
            values.put(ThemesColumns.MODIFIES_RINGTONES, 1);
            values.put(ThemesColumns.PRESENT_AS_THEME, 1);
            values.put(ThemesColumns.IS_LEGACY_THEME, 0);
            values.put(ThemesColumns.IS_DEFAULT_THEME, isDefault);
            values.put(ThemesColumns.IS_LEGACY_ICONPACK, 0);
            values.put(ThemesColumns.MODIFIES_OVERLAYS, 1);
            db.insert(TABLE_NAME, null, values);
        }
    }

    public static class MixnMatchTable {
        protected static final String TABLE_NAME = "mixnmatch";
        private static final String MIXNMATCH_TABLE_CREATE =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        MixnMatchColumns.COL_KEY + " TEXT PRIMARY KEY," +
                        MixnMatchColumns.COL_VALUE + " TEXT" +
                        ")";

        public static void insertDefaults(SQLiteDatabase db) {
            ContentValues values = new ContentValues();
            for(String key : MixnMatchColumns.ROWS) {
                values.put(MixnMatchColumns.COL_KEY, key);
                values.put(MixnMatchColumns.COL_VALUE, DEFAULT_PKG_NAME);
                db.insert(TABLE_NAME, null, values);
            }
        }
    }

    private static boolean isHoloDefault(Context context) {
        // == is okay since we are checking if what is returned is the same constant string value
        return ThemeConfig.HOLO_DEFAULT == ThemeUtils.getDefaultThemePackageName(context);
    }
}


