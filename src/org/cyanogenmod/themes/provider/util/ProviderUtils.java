/*
 * Copyright (C) 2015 The CyanogenMod Project
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
package org.cyanogenmod.themes.provider.util;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.ThemeManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.ThemesContract;
import android.provider.ThemesContract.MixnMatchColumns;
import android.provider.ThemesContract.ThemesColumns;
import org.cyanogenmod.themes.provider.ThemesOpenHelper;

public class ProviderUtils {
    /**
     * Convenience method for determining if a theme exists in the provider
     * @param context
     * @param pkgName
     * @return True if the theme exists, false otherwise
     */
    public static boolean themeExistsInProvider(Context context, String pkgName) {
        boolean exists = false;
        String[] projection = new String[] { ThemesColumns.PKG_NAME };
        String selection = ThemesColumns.PKG_NAME + "=?";
        String[] selectionArgs = new String[] { pkgName };
        Cursor c = context.getContentResolver().query(ThemesColumns.CONTENT_URI,
                projection, selection, selectionArgs, null);

        if (c != null) {
            exists = c.getCount() >= 1;
            c.close();
        }
        return exists;
    }

    /**
     * Queries the {@link android.content.res.ThemeManager} to check if the theme is currently
     * being processed by {@link com.android.server.ThemeService}
     * @param context
     * @param pkgName
     * @return True if the theme is being processed or queued up for processing
     */
    public static boolean isThemeBeingProcessed(Context context, String pkgName) {
        ThemeManager tm = (ThemeManager) context.getSystemService(Context.THEME_SERVICE);
        return tm.isThemeBeingProcessed(pkgName);
    }

    /**
     * Convenience method for getting the install state of a theme in the provider
     * @param context
     * @param pkgName
     * @return
     */
    public static int getInstallStateForTheme(Context context, String pkgName) {
        if (context == null || pkgName == null) return ThemesColumns.InstallState.UNKNOWN;

        String[] projection = new String[] { ThemesColumns.INSTALL_STATE };
        String selection = ThemesColumns.PKG_NAME + "=?";
        String[] selectionArgs = new String[] { pkgName };
        Cursor c = context.getContentResolver().query(ThemesColumns.CONTENT_URI,
                projection, selection, selectionArgs, null);

        int state = ThemesColumns.InstallState.UNKNOWN;
        if (c != null) {
            if (c.moveToFirst()) {
                state = c.getInt(c.getColumnIndex(ThemesColumns.INSTALL_STATE));
            }
            c.close();
        }
        return state;
    }

    public static String getCurrentThemeForComponent(Context context, String selection,
            String[] selectionArgs) {
        if (context == null || selection == null || selectionArgs == null) {
            return null;
        }

        String[] projection = new String[] {MixnMatchColumns.COL_VALUE};
        Cursor c = context.getContentResolver().query(MixnMatchColumns.CONTENT_URI,
                projection, selection, selectionArgs, null);

        String themePkgName = null;
        if (c != null) {
            if (c.moveToFirst()) {
                themePkgName = c.getString(c.getColumnIndex(MixnMatchColumns.COL_VALUE));
            }
            c.close();
        }
        return themePkgName;
    }

    /**
     * Sends the {@link android.provider.ThemesContract.Intent#ACTION_THEME_INSTALLED} action
     * @param context
     * @param pkgName
     */
    public static void sendThemeInstalledBroadcast(Context context, String pkgName) {
        Intent intent = new Intent(ThemesContract.Intent.ACTION_THEME_INSTALLED,
                Uri.fromParts(ThemesContract.Intent.URI_SCHEME_PACKAGE, pkgName, null));
        context.sendBroadcast(intent, Manifest.permission.READ_THEMES);
    }

    /**
     * Sends the {@link android.provider.ThemesContract.Intent#ACTION_THEME_UPDATED} action
     * @param context
     * @param pkgName
     */
    public static void sendThemeUpdatedBroadcast(Context context, String pkgName) {
        Intent intent = new Intent(ThemesContract.Intent.ACTION_THEME_UPDATED,
                Uri.fromParts(ThemesContract.Intent.URI_SCHEME_PACKAGE, pkgName, null));
        context.sendBroadcast(intent, Manifest.permission.READ_THEMES);
    }

    /**
     * Sends the {@link android.provider.ThemesContract.Intent#ACTION_THEME_REMOVED} action
     * @param context
     * @param pkgName
     */
    public static void sendThemeRemovedBroadcast(Context context, String pkgName) {
        Intent intent = new Intent(ThemesContract.Intent.ACTION_THEME_REMOVED,
                Uri.fromParts(ThemesContract.Intent.URI_SCHEME_PACKAGE, pkgName, null));
        context.sendBroadcast(intent, Manifest.permission.READ_THEMES);
    }
}
