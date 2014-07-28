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


import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.UriMatcher;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ThemeUtils;
import android.content.res.ThemeManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ThemesContract;
import android.provider.ThemesContract.MixnMatchColumns;
import android.provider.ThemesContract.ThemesColumns;
import android.util.Log;

import org.cyanogenmod.themes.provider.ThemesOpenHelper.MixnMatchTable;
import org.cyanogenmod.themes.provider.ThemesOpenHelper.ThemesTable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static android.content.res.ThemeConfig.HOLO_DEFAULT;

public class ThemesProvider extends ContentProvider {
    private static final String TAG = ThemesProvider.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final int MIXNMATCH = 1;
    private static final int MIXNMATCH_KEY = 2;
    private static final int THEMES = 3;
    private static final int THEMES_ID = 4;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    private final Handler mHandler = new Handler();
    private ThemesOpenHelper mDatabase;

    static {
        sUriMatcher.addURI(ThemesContract.AUTHORITY, "mixnmatch/", MIXNMATCH);
        sUriMatcher.addURI(ThemesContract.AUTHORITY, "mixnmatch/*", MIXNMATCH_KEY);
        sUriMatcher.addURI(ThemesContract.AUTHORITY, "themes/", THEMES);
        sUriMatcher.addURI(ThemesContract.AUTHORITY, "themes/#", THEMES_ID);
    }

    public static void setActiveTheme(Context context, String pkgName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Editor edit = prefs.edit();
        edit.putString("SelectedThemePkgName", pkgName);
        edit.commit();
    }

    public static String getActiveTheme(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("SelectedThemePkgName",
                ThemeUtils.getDefaultThemePackageName(context));
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int match = sUriMatcher.match(uri);
        switch (match) {
        case THEMES:
            SQLiteDatabase sqlDB = mDatabase.getWritableDatabase();

            // Determine the pkg name and delete preview images
            String[] columns = new String[] {ThemesColumns.PKG_NAME};
            Cursor c = sqlDB.query(ThemesTable.TABLE_NAME, columns, selection,
                    selectionArgs, null, null, null);
            if (c == null) return 0;
            if (c.moveToFirst()) {
                String pkgName = c.getString(0);
                Intent intent = new Intent(getContext(), CopyImageService.class);
                intent.setAction(CopyImageService.ACTION_DELETE);
                intent.putExtra(CopyImageService.EXTRA_PKG_NAME, pkgName);
                getContext().startService(intent);
            }
            c.close();

            int rowsDeleted = sqlDB.delete(ThemesTable.TABLE_NAME, selection, selectionArgs);
            getContext().getContentResolver().notifyChange(uri, null);
            return rowsDeleted;
        case MIXNMATCH:
            throw new UnsupportedOperationException("Cannot delete rows in MixNMatch table");
        }
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        int match = sUriMatcher.match(uri);
        switch (match) {
        case THEMES:
            return "vnd.android.cursor.dir/themes";
        case THEMES_ID:
            return "vnd.android.cursor.item/themes";
        case MIXNMATCH:
            return "vnd.android.cursor.dir/mixnmatch";
        case MIXNMATCH_KEY:
            return "vnd.android.cursor.item/mixnmatch";
        default:
            return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        int uriType = sUriMatcher.match(uri);
        SQLiteDatabase sqlDB = mDatabase.getWritableDatabase();
        long id = 0;
        switch (uriType) {
        case THEMES:
            id = sqlDB.insert(ThemesOpenHelper.ThemesTable.TABLE_NAME, null, values);
            Intent intent = new Intent(getContext(), CopyImageService.class);
            intent.setAction(CopyImageService.ACTION_INSERT);
            intent.putExtra(CopyImageService.EXTRA_PKG_NAME,
                    values.getAsString(ThemesColumns.PKG_NAME));
            getContext().startService(intent);
            break;
        case MIXNMATCH:
            throw new UnsupportedOperationException("Cannot insert rows into MixNMatch table");
        default:
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return Uri.parse(MixnMatchColumns.CONTENT_URI + "/" + id);
    }

    @Override
    public boolean onCreate() {
        mDatabase = new ThemesOpenHelper(getContext());

        /**
         * Sync database with package manager
         */
        mHandler.post(new Runnable() {
            public void run() {
                new VerifyInstalledThemesThread().start();
            }
        });

        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {

        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        SQLiteDatabase db = mDatabase.getReadableDatabase();

        /*
         * Choose the table to query and a sort order based on the code returned for the incoming
         * URI. Here, too, only the statements for table 3 are shown.
         */
        switch (sUriMatcher.match(uri)) {
        case THEMES:
            queryBuilder.setTables(ThemesOpenHelper.ThemesTable.TABLE_NAME);
            break;
        case THEMES_ID:
            queryBuilder.setTables(ThemesOpenHelper.ThemesTable.TABLE_NAME);
            queryBuilder.appendWhere(ThemesColumns._ID + "=" + uri.getLastPathSegment());
            break;
        case MIXNMATCH:
            queryBuilder.setTables(THEMES_MIXNMATCH_INNER_JOIN);
            break;
        case MIXNMATCH_KEY:
            queryBuilder.setTables(THEMES_MIXNMATCH_INNER_JOIN);
            queryBuilder.appendWhere(MixnMatchColumns.COL_KEY + "=" + uri.getLastPathSegment());
            break;
        default:
            return null;
        }

        Cursor cursor = queryBuilder.query(db, projection, selection, selectionArgs, null, null,
                sortOrder);
        if (cursor != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }

        return cursor;
    }

    private static final String THEMES_MIXNMATCH_INNER_JOIN = MixnMatchTable.TABLE_NAME
            + " INNER JOIN " + ThemesTable.TABLE_NAME + " ON (" + MixnMatchColumns.COL_VALUE
            + " = " + ThemesColumns.PKG_NAME + ")";

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        int rowsUpdated = 0;
        SQLiteDatabase sqlDB = mDatabase.getWritableDatabase();

        switch (sUriMatcher.match(uri)) {
        case THEMES:
            rowsUpdated = sqlDB.update(ThemesTable.TABLE_NAME, values, selection, selectionArgs);
            if (updateNotTriggeredByContentProvider(values)) {
                Intent intent = new Intent(getContext(), CopyImageService.class);
                intent.putExtra(CopyImageService.EXTRA_PKG_NAME,
                        values.getAsString(ThemesColumns.PKG_NAME));
                getContext().startService(intent);
                getContext().getContentResolver().notifyChange(uri, null);
            }
            return rowsUpdated;
        case THEMES_ID:
            rowsUpdated = sqlDB.update(ThemesTable.TABLE_NAME, values, selection, selectionArgs);
            if (updateNotTriggeredByContentProvider(values)) {
                Intent intent = new Intent(getContext(), CopyImageService.class);
                intent.putExtra(CopyImageService.EXTRA_PKG_NAME,
                        values.getAsString(ThemesColumns.PKG_NAME));
                getContext().startService(intent);
                getContext().getContentResolver().notifyChange(uri, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
            return rowsUpdated;
        case MIXNMATCH:
            rowsUpdated = sqlDB.update(MixnMatchTable.TABLE_NAME, values, selection, selectionArgs);
            getContext().getContentResolver().notifyChange(uri, null);
        case MIXNMATCH_KEY:
            // Don't support right now. Any need?
        }
        return rowsUpdated;
    }

    /**
     * When there is an insert or update to a theme, an async service will kick off to update
     * several of the preview image columns. Since this service also calls a 2nd update on the
     * content resolver, we need to break the loop so that we don't kick off the service again.
     */
    private boolean updateNotTriggeredByContentProvider(ContentValues values) {
        if (values == null) return true;
        return !(values.containsKey(ThemesColumns.HOMESCREEN_URI)
                || values.containsKey(ThemesColumns.LOCKSCREEN_URI) || values
                    .containsKey(ThemesColumns.STYLE_URI));
    }

    /**
     * This class has been modified from its original source. Original Source: ThemesProvider.java
     * See https://github.com/tmobile/themes-platform-vendor-tmobile-providers-ThemeManager
     * Copyright (C) 2010, T-Mobile USA, Inc. http://www.apache.org/licenses/LICENSE-2.0
     */
    private class VerifyInstalledThemesThread extends Thread {
        private final SQLiteDatabase mDb;

        public VerifyInstalledThemesThread() {
            mDb = mDatabase.getWritableDatabase();
        }

        public void run() {
            android.os.Process.setThreadPriority(Thread.MIN_PRIORITY);

            long start;

            if (DEBUG) {
                start = System.currentTimeMillis();
            }

            SQLiteDatabase db = mDb;
            db.beginTransaction();
            try {
                verifyPackages();
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();

                if (DEBUG) {
                    Log.d(TAG, "VerifyInstalledThemesThread took "
                            + (System.currentTimeMillis() - start) + " ms.");
                }
            }
        }

        private void verifyPackages() {
            /* List all currently installed theme packages according to PM */
            List<PackageInfo> packages = getContext().getPackageManager().getInstalledPackages(0);
            List<PackageInfo> themePackages = new ArrayList<PackageInfo>();
            Map<String, PackageInfo> pmThemes = new HashMap<String, PackageInfo>();
            for (PackageInfo info : packages) {
                if (info.isThemeApk || info.isLegacyThemeApk || info.isLegacyIconPackApk) {
                    themePackages.add(info);
                    pmThemes.put(info.packageName, info);
                }
            }

            /*
             * Get all the known themes according to the provider. Then discover which themes have
             * been deleted, need updating, or need to be inserted into the db
             */
            Cursor current = mDb.query(ThemesTable.TABLE_NAME, null, null, null, null, null, null);
            List<String> deleteList = new LinkedList<String>();
            List<String> updateList = new LinkedList<String>();
            String defaultThemePkg = ThemeUtils.getDefaultThemePackageName(getContext());
            while (current.moveToNext()) {
                int updateTimeIdx = current.getColumnIndex(
                        ThemesContract.ThemesColumns.LAST_UPDATE_TIME);
                int pkgNameIdx = current.getColumnIndex(ThemesContract.ThemesColumns.PKG_NAME);
                int isDefaultIdx = current.getColumnIndex(ThemesColumns.IS_DEFAULT_THEME);
                long updateTime = current.getLong(updateTimeIdx);
                String pkgName = current.getString(pkgNameIdx);
                boolean isDefault = current.getInt(isDefaultIdx) == 1;

                // Ignore holo theme
                if (pkgName.equals(HOLO_DEFAULT)) {
                    if (defaultThemePkg.equals(HOLO_DEFAULT) != isDefault) {
                        updateList.add(HOLO_DEFAULT);
                    }
                    continue;
                }

                // Packages which are not in PM should be deleted from db
                PackageInfo info = pmThemes.get(pkgName);
                if (info == null) {
                    deleteList.add(pkgName);
                    continue;
                }

                // Updated packages in PM should be
                // updated in the db
                long pmUpdateTime = (info.lastUpdateTime == 0) ? info.firstInstallTime
                        : info.lastUpdateTime;
                if (pmUpdateTime != updateTime ||
                        (defaultThemePkg.equals(info.packageName) != isDefault)) {
                    updateList.add(info.packageName);
                }

                // The remaining packages in pmThemes
                // will be the ones to insert into the provider
                pmThemes.remove(pkgName);
            }

            // Check currently applied components (fonts, wallpapers etc) and verify the theme is
            // still installed. If it is not installed, set the component back to the default theme
            List<String> moveToDefault = new LinkedList<String>();
            Cursor mixnmatch = mDb.query(MixnMatchTable.TABLE_NAME, null, null, null, null, null,
                    null);
            while (mixnmatch.moveToNext()) {
                String mixnmatchKey = mixnmatch.getString(mixnmatch
                        .getColumnIndex(MixnMatchColumns.COL_KEY));
                String component = ThemesContract.MixnMatchColumns
                        .mixNMatchKeyToComponent(mixnmatchKey);

                String pkg = mixnmatch.getString(mixnmatch
                        .getColumnIndex(MixnMatchColumns.COL_VALUE));
                if (deleteList.contains(pkg)) {
                    moveToDefault.add(component);
                }
            }
            ThemeManager mService = (ThemeManager) getContext().getSystemService(
                    Context.THEME_SERVICE);
            mService.requestThemeChange(HOLO_DEFAULT, moveToDefault);

            // Update the database after we revert to default
            deleteThemes(deleteList);
            insertThemes(pmThemes.values());
            updateThemes(updateList);
        }

        private void deleteThemes(List<String> themesToDelete) {
            int rows = 0;
            String where = ThemesColumns.PKG_NAME + "=?";
            for (String pkgName : themesToDelete) {
                String[] whereArgs = { pkgName };
                rows += mDb.delete(ThemesTable.TABLE_NAME, where, whereArgs);
            }
            Log.d(TAG, "Deleted " + rows);
        }

        private void insertThemes(Collection<PackageInfo> themesToInsert) {
            for (PackageInfo themeInfo : themesToInsert) {
                try {
                    ThemePackageHelper.insertPackage(getContext(), themeInfo.packageName);
                } catch (NameNotFoundException e) {
                    Log.e(TAG, "Unable to insert theme " + themeInfo.packageName, e);
                }
            }
        }

        private void updateThemes(List<String> themesToUpdate) {
            for (String pkgName : themesToUpdate) {
                try {
                    ThemePackageHelper.updatePackage(getContext(), pkgName);
                } catch (NameNotFoundException e) {
                    Log.e(TAG, "Unable to update theme " + pkgName, e);
                }
            }
        }
    }

}
