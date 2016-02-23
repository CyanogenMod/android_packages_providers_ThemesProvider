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
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ThemeConfig;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.util.Log;

import cyanogenmod.providers.ThemesContract;
import cyanogenmod.providers.ThemesContract.ThemesColumns;
import cyanogenmod.providers.ThemesContract.MixnMatchColumns;
import cyanogenmod.providers.ThemesContract.PreviewColumns;

import org.cyanogenmod.internal.util.ThemeUtils;

public class ThemesOpenHelper extends SQLiteOpenHelper {
    private static final String TAG = ThemesOpenHelper.class.getName();

    private static final int DATABASE_VERSION = 20;
    private static final String DATABASE_NAME = "themes.db";
    private static final String SYSTEM_THEME_PKG_NAME = ThemeConfig.SYSTEM_DEFAULT;
    private static final String OLD_SYSTEM_THEME_PKG_NAME = "holo";

    private Context mContext;

    public ThemesOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(ThemesTable.THEMES_TABLE_CREATE);
        db.execSQL(MixnMatchTable.MIXNMATCH_TABLE_CREATE);
        db.execSQL(PreviewsTable.PREVIEWS_TABLE_CREATE);

        ThemesTable.insertSystemDefaults(db, mContext);
        MixnMatchTable.insertDefaults(db);
        PreviewsTable.insertDefaults(mContext);
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
            if (oldVersion == 5) {
                upgradeToVersion6(db);
                oldVersion = 6;
            }
            if (oldVersion == 6) {
                upgradeToVersion7(db);
                oldVersion = 7;
            }
            if (oldVersion == 7) {
                upgradeToVersion8(db);
                oldVersion = 8;
            }
            if (oldVersion == 8) {
                upgradeToVersion9(db);
                oldVersion = 9;
            }
            if (oldVersion == 9) {
                upgradeToVersion10(db);
                oldVersion = 10;
            }
            if (oldVersion == 10) {
                upgradeToVersion11(db);
                oldVersion = 11;
            }
            if (oldVersion == 11) {
                upgradeToVersion12(db);
                oldVersion = 12;
            }
            if (oldVersion == 12) {
                upgradeToVersion13(db);
                oldVersion = 13;
            }
            if (oldVersion == 13) {
                upgradeToVersion14(db);
                oldVersion = 14;
            }
            if (oldVersion == 14 || oldVersion == 15) {
                // Versions 15 and 16 share same upgrade path, no need to run twice.
                upgradeToVersion16(db);
                oldVersion = 16;
            }
            if (oldVersion == 16) {
                upgradeToVersion17(db);
                oldVersion = 17;
            }
            if (oldVersion == 17) {
                upgradeToVersion18(db);
                oldVersion = 18;
            }
            if (oldVersion == 18) {
                upgradeToVersion19(db);
                oldVersion = 19;
            }
            if (oldVersion == 19) {
                upgradeToVersion20(db);
                oldVersion = 20;
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
        values.put(MixnMatchColumns.COL_VALUE, OLD_SYSTEM_THEME_PKG_NAME);
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
        String changeDefaultToSystem = String.format("UPDATE %s SET %s='%s' WHERE" +
                        " %s='%s'", ThemesTable.TABLE_NAME, ThemesColumns.PKG_NAME,
                OLD_SYSTEM_THEME_PKG_NAME, ThemesColumns.PKG_NAME, "default");
        db.execSQL(changeDefaultToSystem);

        if (isSystemDefault(mContext)) {
            // flag holo as default if
            String makeHoloDefault = String.format("UPDATE %s SET %s=%d WHERE" +
                            " %s='%s'", ThemesTable.TABLE_NAME, ThemesColumns.IS_DEFAULT_THEME, 1,
                    ThemesColumns.PKG_NAME, OLD_SYSTEM_THEME_PKG_NAME);
            db.execSQL(makeHoloDefault);
        }

        // change any existing mixnmatch values set to "default" to "holo"
        db.execSQL(String.format("UPDATE %s SET %s='%s' WHERE %s='%s'",
                MixnMatchTable.TABLE_NAME, MixnMatchColumns.COL_VALUE, OLD_SYSTEM_THEME_PKG_NAME,
                MixnMatchColumns.COL_VALUE, "default"));
    }

    private void upgradeToVersion6(SQLiteDatabase db) {
        db.execSQL(PreviewsTable.PREVIEWS_TABLE_CREATE);

        // remove (Default) from Holo's title
        db.execSQL(String.format("UPDATE %s SET %s='%s' WHERE %s='%s'", ThemesTable.TABLE_NAME,
                ThemesColumns.TITLE, "Holo", ThemesColumns.PKG_NAME, "holo"));

        // we need to update any existing themes
        final String[] projection = { ThemesColumns.PKG_NAME };
        final String selection = ThemesColumns.MODIFIES_OVERLAYS + "=?";
        final String[] selectionArgs = { "1" };
        final Cursor c = db.query(ThemesTable.TABLE_NAME, projection, selection, selectionArgs,
                null, null, null);
        if (c != null) {
            while(c.moveToNext()) {
                Intent intent = new Intent(mContext, PreviewGenerationService.class);
                intent.setAction(PreviewGenerationService.ACTION_INSERT);
                intent.putExtra(PreviewGenerationService.EXTRA_PKG_NAME, c.getString(0));
                mContext.startService(intent);
            }
            c.close();
        }
    }

    private void upgradeToVersion7(SQLiteDatabase db) {
        String addStatusBar = String.format("ALTER TABLE %s ADD COLUMN %s INTEGER",
                ThemesTable.TABLE_NAME, ThemesColumns.MODIFIES_STATUS_BAR);
        String addNavBar = String.format("ALTER TABLE %s ADD COLUMN %s INTEGER",
                ThemesTable.TABLE_NAME, ThemesColumns.MODIFIES_NAVIGATION_BAR);
        db.execSQL(addStatusBar);
        db.execSQL(addNavBar);

        // we need to update any existing themes
        final String[] projection = { ThemesColumns.PKG_NAME, ThemesColumns.IS_LEGACY_THEME };
        final String selection = ThemesColumns.MODIFIES_OVERLAYS + "=? OR " +
                ThemesColumns.IS_LEGACY_THEME + "=?";
        final String[] selectionArgs = { "1", "1" };
        final Cursor c = db.query(ThemesTable.TABLE_NAME, projection, selection, selectionArgs,
                null, null, null);
        if (c != null) {
            while(c.moveToNext()) {
                final String pkgName = c.getString(0);
                final boolean isLegacyTheme = c.getInt(1) == 1;
                boolean hasSystemUi = false;
                if (OLD_SYSTEM_THEME_PKG_NAME.equals(pkgName) || isLegacyTheme) {
                    hasSystemUi = true;
                } else {
                    try {
                        Context themeContext = mContext.createPackageContext(pkgName, 0);
                        hasSystemUi = ThemePackageHelper.hasThemeComponent(themeContext,
                                ThemePackageHelper.sComponentToFolderName.get(
                                        ThemesColumns.MODIFIES_STATUS_BAR));
                    } catch (PackageManager.NameNotFoundException e) {
                        // default to false
                    }
                }
                if (hasSystemUi) {
                    db.execSQL(String.format("UPDATE %S SET %s='1', %s='1' WHERE %s='%s'",
                            ThemesTable.TABLE_NAME, ThemesColumns.MODIFIES_STATUS_BAR,
                            ThemesColumns.MODIFIES_NAVIGATION_BAR, ThemesColumns.PKG_NAME,
                            pkgName));
                    Intent intent = new Intent(mContext, PreviewGenerationService.class);
                    intent.setAction(PreviewGenerationService.ACTION_INSERT);
                    intent.putExtra(PreviewGenerationService.EXTRA_PKG_NAME, pkgName);
                    mContext.startService(intent);
                }
            }
            c.close();
        }
    }

    private void upgradeToVersion8(SQLiteDatabase db) {
        String addNavBar = String.format("ALTER TABLE %s ADD COLUMN %s BLOB",
                PreviewsTable.TABLE_NAME, PreviewColumns.NAVBAR_BACKGROUND);
        db.execSQL(addNavBar);

        // we need to update any existing themes with the new NAVBAR_BACKGROUND
        final String[] projection = { ThemesColumns.PKG_NAME, ThemesColumns.IS_LEGACY_THEME };
        final String selection = ThemesColumns.MODIFIES_OVERLAYS + "=? OR " +
                ThemesColumns.IS_LEGACY_THEME + "=?";
        final String[] selectionArgs = { "1", "1" };
        final Cursor c = db.query(ThemesTable.TABLE_NAME, projection, selection, selectionArgs,
                null, null, null);
        if (c != null) {
            while(c.moveToNext()) {
                final String pkgName = c.getString(0);
                final boolean isLegacyTheme = c.getInt(1) == 1;
                boolean hasSystemUi = false;
                if (OLD_SYSTEM_THEME_PKG_NAME.equals(pkgName) || isLegacyTheme) {
                    hasSystemUi = true;
                } else {
                    try {
                        Context themeContext = mContext.createPackageContext(pkgName, 0);
                        hasSystemUi = ThemePackageHelper.hasThemeComponent(themeContext,
                                ThemePackageHelper.sComponentToFolderName.get(
                                        ThemesColumns.MODIFIES_STATUS_BAR));
                    } catch (PackageManager.NameNotFoundException e) {
                        // default to false
                    }
                }
                if (hasSystemUi) {
                    Intent intent = new Intent(mContext, PreviewGenerationService.class);
                    intent.setAction(PreviewGenerationService.ACTION_INSERT);
                    intent.putExtra(PreviewGenerationService.EXTRA_PKG_NAME, pkgName);
                    mContext.startService(intent);
                }
            }
            c.close();
        }
    }

    private void upgradeToVersion9(SQLiteDatabase db) {
        String addNavBar = String.format("ALTER TABLE %s ADD COLUMN %s INTEGER DEFAULT 0",
                ThemesTable.TABLE_NAME, ThemesColumns.INSTALL_TIME);
        db.execSQL(addNavBar);

        // we need to update any existing themes with their install time
        final String[] projection = { ThemesColumns.PKG_NAME };
        final Cursor c = db.query(ThemesTable.TABLE_NAME, projection, null, null, null, null, null);
        if (c != null) {
            while(c.moveToNext()) {
                final String pkgName = c.getString(0);
                try {
                    PackageInfo pi = mContext.getPackageManager().getPackageInfo(pkgName, 0);
                    db.execSQL(String.format("UPDATE %s SET %s='%d' WHERE %s='%s'",
                            ThemesTable.TABLE_NAME, ThemesColumns.INSTALL_TIME, pi.firstInstallTime,
                            ThemesColumns.PKG_NAME, pkgName));
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, "Unable to update install time for " + pkgName, e);
                }
            }
            c.close();
        }
    }

    private void upgradeToVersion10(SQLiteDatabase db) {
        // add API entries
        String sql = String.format("ALTER TABLE %s ADD COLUMN %s TEXT",
                ThemesTable.TABLE_NAME, ThemesColumns.TARGET_API);
        db.execSQL(sql);

        // we need to update any existing themes with their install time
        final String[] projection = { ThemesColumns.PKG_NAME };
        final Cursor c = db.query(ThemesTable.TABLE_NAME, projection, null, null, null, null, null);
        if (c != null) {
            while(c.moveToNext()) {
                final String pkgName = c.getString(0);
                int targetSdk = -1;
                if (OLD_SYSTEM_THEME_PKG_NAME.equals(pkgName)) {
                    // 0 is a special value used for the system theme, not to be confused with the
                    // default theme which may not be the same as the system theme.
                    targetSdk = 0;
                } else {
                    try {
                        PackageInfo pi = mContext.getPackageManager().getPackageInfo(pkgName, 0);
                        targetSdk = pi.applicationInfo.targetSdkVersion;
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.e(TAG, "Unable to update target sdk for " + pkgName, e);
                    }
                }
                if (targetSdk != -1) {
                    db.execSQL(String.format("UPDATE %s SET %s='%d' WHERE %s='%s'", ThemesTable
                                    .TABLE_NAME, ThemesColumns.TARGET_API,
                            targetSdk, ThemesColumns.PKG_NAME, pkgName));
                }
            }
            c.close();
        }
    }

    private void upgradeToVersion11(SQLiteDatabase db) {
        // Update holo theme to be called "system"
        final String NEW_THEME_TITLE = "System";
        String holoToSystem = String.format("UPDATE TABLE %s " +
                        "SET title=%s, pkg_name=%s " +
                        "WHERE %s='%s'",
                ThemesTable.TABLE_NAME,
                NEW_THEME_TITLE,
                SYSTEM_THEME_PKG_NAME,
                ThemesColumns.PKG_NAME, OLD_SYSTEM_THEME_PKG_NAME);
        db.execSQL(holoToSystem);

    }

    private void upgradeToVersion12(SQLiteDatabase db) {
        // This upgrade performs an update to the ThemesColumns.PRESENT_AS_THEME since the
        // requirements for what is a presentable theme have changed.
        final String[] projection = { ThemesColumns.PKG_NAME, ThemesColumns.MODIFIES_LAUNCHER,
                ThemesColumns.MODIFIES_OVERLAYS};
        final Cursor c = db.query(ThemesTable.TABLE_NAME, projection, null, null, null, null, null);
        if (c != null) {
            while(c.moveToNext()) {
                final String pkgName = c.getString(0);
                boolean presentAsTheme =
                        c.getInt(c.getColumnIndex(ThemesColumns.MODIFIES_LAUNCHER)) == 1 &&
                                c.getInt(c.getColumnIndex(ThemesColumns.MODIFIES_OVERLAYS)) == 1;
                db.execSQL(String.format("UPDATE %s SET %s='%d' WHERE %s='%s'",
                        ThemesTable.TABLE_NAME, ThemesColumns.PRESENT_AS_THEME,
                            presentAsTheme ? 1 : 0, ThemesColumns.PKG_NAME, pkgName));
            }
            c.close();
        }
    }

    private void upgradeToVersion13(SQLiteDatabase db) {
        // add install_state column to themes db
        String sql = String.format("ALTER TABLE %s ADD COLUMN %s INTEGER DEFAULT %d",
                ThemesTable.TABLE_NAME, ThemesColumns.INSTALL_STATE,
                ThemesColumns.InstallState.UNKNOWN);
        db.execSQL(sql);

        // we need to update any existing themes with their install state
        db.execSQL(String.format("UPDATE %s SET %s='%d'", ThemesTable.TABLE_NAME,
                ThemesColumns.INSTALL_STATE, ThemesColumns.InstallState.INSTALLED));
    }

    private void upgradeToVersion14(SQLiteDatabase db) {
        // add previous_value column to mixnmatch db
        String sql = String.format("ALTER TABLE %s ADD COLUMN %s TEXT",
                MixnMatchTable.TABLE_NAME, MixnMatchColumns.COL_PREV_VALUE);
        db.execSQL(sql);

        // add update_time column to mixnmatch db
        sql = String.format("ALTER TABLE %s ADD COLUMN %s INTEGER DEFAULT 0",
                MixnMatchTable.TABLE_NAME, MixnMatchColumns.COL_UPDATE_TIME);
        db.execSQL(sql);
    }

    // upgradeToVersion16 is the same upgrade path for both 14->15 and 15->16
    private void upgradeToVersion16(SQLiteDatabase db) {
        // Previews table upgraded
        db.execSQL("DROP TABLE IF EXISTS " + PreviewsTable.TABLE_NAME);
        db.execSQL(PreviewsTable.PREVIEWS_TABLE_CREATE);

        // we need to update any existing themes
        final String[] projection = { ThemesColumns.PKG_NAME };
        final Cursor c = db.query(ThemesTable.TABLE_NAME, projection, null, null,
                null, null, null);
        if (c != null) {
            while(c.moveToNext()) {
                Intent intent = new Intent(mContext, PreviewGenerationService.class);
                intent.setAction(PreviewGenerationService.ACTION_INSERT);
                intent.putExtra(PreviewGenerationService.EXTRA_PKG_NAME, c.getString(0));
                mContext.startService(intent);
            }
            c.close();
        }
    }

    private void upgradeToVersion17(SQLiteDatabase db) {
        // add componentId column to mixnmatch db
        String sql = String.format("ALTER TABLE %s ADD COLUMN %s INTEGER DEFAULT 0",
        MixnMatchTable.TABLE_NAME, MixnMatchColumns.COL_COMPONENT_ID);
        db.execSQL(sql);
    }

    private void upgradeToVersion18(SQLiteDatabase db) {
        // add install_state column to themes db
        String sql = String.format("ALTER TABLE %s ADD COLUMN %s INTEGER DEFAULT 0",
                ThemesTable.TABLE_NAME, ThemesColumns.MODIFIES_LIVE_LOCK_SCREEN);
        db.execSQL(sql);

        // add entry to mixnmatch table
        ContentValues values = new ContentValues();
        values.put(MixnMatchColumns.COL_VALUE, "");
        values.put(MixnMatchColumns.COL_PREV_VALUE, "");
        values.put(MixnMatchColumns.COL_UPDATE_TIME, 0);
        values.put(MixnMatchColumns.COL_KEY, MixnMatchColumns.KEY_LIVE_LOCK_SCREEN);
        db.insert(MixnMatchTable.TABLE_NAME, null, values);
    }

    // Update any themes that have live lock screen
    private void upgradeToVersion19(SQLiteDatabase db) {
        // we need to update any existing themes
        final String[] projection = { ThemesColumns.PKG_NAME };
        final String selection = ThemesColumns.MODIFIES_LIVE_LOCK_SCREEN + "=?";
        final String[] selectionArgs = { "1" };

        final Cursor c = db.query(ThemesTable.TABLE_NAME, projection, selection, selectionArgs,
                null, null, null);
        if (c != null) {
            while(c.moveToNext()) {
                Intent intent = new Intent(mContext, PreviewGenerationService.class);
                intent.setAction(PreviewGenerationService.ACTION_INSERT);
                intent.putExtra(PreviewGenerationService.EXTRA_PKG_NAME, c.getString(0));
                mContext.startService(intent);
            }
            c.close();
        }
    }

    private void upgradeToVersion20(SQLiteDatabase db) {
        //No default lock screen nor live lock screen for system theme
        db.execSQL(String.format("UPDATE %s SET %s='0', %s='0' WHERE %s='%s'",
                ThemesTable.TABLE_NAME, ThemesColumns.MODIFIES_LOCKSCREEN,
                ThemesColumns.MODIFIES_LIVE_LOCK_SCREEN, ThemesColumns.PKG_NAME,
                SYSTEM_THEME_PKG_NAME));
    }

    private void dropTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + ThemesTable.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + MixnMatchTable.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + PreviewsTable.TABLE_NAME);
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
                        ThemesColumns.MODIFIES_STATUS_BAR + " INTEGER DEFAULT 0, " +
                        ThemesColumns.MODIFIES_NAVIGATION_BAR + " INTEGER DEFAULT 0, " +
                        ThemesColumns.MODIFIES_LIVE_LOCK_SCREEN + " INTEGER DEFAULT 0, " +
                        ThemesColumns.PRESENT_AS_THEME + " INTEGER DEFAULT 0, " +
                        ThemesColumns.IS_LEGACY_THEME + " INTEGER DEFAULT 0, " +
                        ThemesColumns.IS_DEFAULT_THEME + " INTEGER DEFAULT 0, " +
                        ThemesColumns.IS_LEGACY_ICONPACK + " INTEGER DEFAULT 0, " +
                        ThemesColumns.LAST_UPDATE_TIME + " INTEGER DEFAULT 0, " +
                        ThemesColumns.INSTALL_TIME + " INTEGER DEFAULT 0, " +
                        ThemesColumns.TARGET_API + " INTEGER DEFAULT 0," +
                        ThemesColumns.INSTALL_STATE + " INTEGER DEFAULT " +
                        ThemesColumns.InstallState.UNKNOWN +
                        ")";

        public static void insertSystemDefaults(SQLiteDatabase db, Context context) {
            int isDefault = isSystemDefault(context) ? 1 : 0;
            ContentValues values = new ContentValues();
            values.put(ThemesColumns.TITLE, "System");
            values.put(ThemesColumns.PKG_NAME, SYSTEM_THEME_PKG_NAME);
            values.put(ThemesColumns.PRIMARY_COLOR, 0xff33b5e5);
            values.put(ThemesColumns.SECONDARY_COLOR, 0xff000000);
            values.put(ThemesColumns.AUTHOR, "Android");
            values.put(ThemesColumns.MODIFIES_ALARMS, 1);
            values.put(ThemesColumns.MODIFIES_BOOT_ANIM, 1);
            values.put(ThemesColumns.MODIFIES_FONTS, 1);
            values.put(ThemesColumns.MODIFIES_ICONS, 1);
            values.put(ThemesColumns.MODIFIES_LAUNCHER, 1);
            values.put(ThemesColumns.MODIFIES_NOTIFICATIONS, 1);
            values.put(ThemesColumns.MODIFIES_RINGTONES, 1);
            values.put(ThemesColumns.MODIFIES_STATUS_BAR, 1);
            values.put(ThemesColumns.MODIFIES_NAVIGATION_BAR, 1);
            values.put(ThemesColumns.PRESENT_AS_THEME, 1);
            values.put(ThemesColumns.IS_LEGACY_THEME, 0);
            values.put(ThemesColumns.IS_DEFAULT_THEME, isDefault);
            values.put(ThemesColumns.IS_LEGACY_ICONPACK, 0);
            values.put(ThemesColumns.MODIFIES_OVERLAYS, 1);
            values.put(ThemesColumns.TARGET_API, Build.VERSION.SDK_INT);
            values.put(ThemesColumns.INSTALL_STATE, ThemesColumns.InstallState.INSTALLED);
            db.insert(TABLE_NAME, null, values);
        }
    }

    public static class MixnMatchTable {
        public static final String TABLE_NAME = "mixnmatch";
        private static final String MIXNMATCH_TABLE_CREATE =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        MixnMatchColumns.COL_KEY + " TEXT PRIMARY KEY," +
                        MixnMatchColumns.COL_VALUE + " TEXT," +
                        MixnMatchColumns.COL_PREV_VALUE + " TEXT," +
                        MixnMatchColumns.COL_UPDATE_TIME + " INTEGER DEFAULT 0," +
                        MixnMatchColumns.COL_COMPONENT_ID + " INTEGER DEFAULT 0" +
                        ")";

        public static void insertDefaults(SQLiteDatabase db) {
            ContentValues values = new ContentValues();
            long updateTime = System.currentTimeMillis();
            for(String key : MixnMatchColumns.ROWS) {
                if (key.equals(MixnMatchColumns.KEY_LOCKSCREEN) ||
                        key.equals(MixnMatchColumns.KEY_LIVE_LOCK_SCREEN)) {
                    //No system default for lock wallpaper or live lock screen
                    values.put(MixnMatchColumns.COL_VALUE, "");
                    values.put(MixnMatchColumns.COL_UPDATE_TIME, 0);
                } else {
                    values.put(MixnMatchColumns.COL_VALUE, SYSTEM_THEME_PKG_NAME);
                    values.put(MixnMatchColumns.COL_UPDATE_TIME, updateTime);
                }
                values.put(MixnMatchColumns.COL_KEY, key);
                db.insert(TABLE_NAME, null, values);
            }
        }
    }

    public static class PreviewsTable {
        protected static final String TABLE_NAME = "previews";
        private static final String PREVIEWS_TABLE_CREATE =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        PreviewColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        PreviewColumns.THEME_ID + " INTEGER, " +
                        PreviewColumns.COMPONENT_ID + " INTEGER DEFAULT 0, " +
                        PreviewColumns.COL_KEY + " TEXT," +
                        PreviewColumns.COL_VALUE + " TEXT, " +
                        "FOREIGN KEY (" + PreviewColumns.THEME_ID + ") REFERENCES " +
                        ThemesTable.TABLE_NAME + "(" + ThemesColumns._ID + ")" +
                        ")";

        public static final String[] STATUS_BAR_PREVIEW_KEYS = {
                PreviewColumns.STATUSBAR_BACKGROUND,
                PreviewColumns.STATUSBAR_BLUETOOTH_ICON,
                PreviewColumns.STATUSBAR_WIFI_ICON,
                PreviewColumns.STATUSBAR_SIGNAL_ICON,
                PreviewColumns.STATUSBAR_BATTERY_PORTRAIT,
                PreviewColumns.STATUSBAR_BATTERY_LANDSCAPE,
                PreviewColumns.STATUSBAR_BATTERY_CIRCLE,
                PreviewColumns.STATUSBAR_WIFI_COMBO_MARGIN_END,
                PreviewColumns.STATUSBAR_CLOCK_TEXT_COLOR
        };
        public static final String[] NAVIGATION_BAR_PREVIEW_KEYS = {
                PreviewColumns.NAVBAR_BACK_BUTTON,
                PreviewColumns.NAVBAR_HOME_BUTTON,
                PreviewColumns.NAVBAR_RECENT_BUTTON,
                PreviewColumns.NAVBAR_BACKGROUND
        };
        public static final String[] ICON_PREVIEW_KEYS = {
                PreviewColumns.ICON_PREVIEW_1,
                PreviewColumns.ICON_PREVIEW_2,
                PreviewColumns.ICON_PREVIEW_3
        };

        public static void insertDefaults(Context context) {
            Intent intent = new Intent(context, PreviewGenerationService.class);
            intent.setAction(PreviewGenerationService.ACTION_INSERT);
            intent.putExtra(PreviewGenerationService.EXTRA_PKG_NAME, SYSTEM_THEME_PKG_NAME);
            context.startService(intent);
        }
    }

    private static boolean isSystemDefault(Context context) {
        // == is okay since we are checking if what is returned is the same constant string value
        return ThemeConfig.SYSTEM_DEFAULT == ThemeUtils.getDefaultThemePackageName(context);
    }
}


