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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.ThemeManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ThemesContract;
import android.util.Log;

import java.util.Set;

public class AppReceiver extends BroadcastReceiver {
    public final static String TAG = AppReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        final Uri uri = intent.getData();
        final String pkgName = uri != null ? uri.getSchemeSpecificPart() : null;
        final boolean isReplacing = intent.getExtras().getBoolean(Intent.EXTRA_REPLACING, false);
        final String action = intent.getAction();
        try {
            if (Intent.ACTION_PACKAGE_ADDED.equals(action) && !isReplacing) {
                final boolean themeProcessing = isThemeBeingProcessed(context, pkgName);
                ThemePackageHelper.insertPackage(context, pkgName, !themeProcessing);

                if (themeProcessing) {
                    // store this package name so we know it's being processed and it can be
                    // added to the DB when ACTION_THEME_RESOURCES_CACHED is received
                    PreferenceUtils.addThemeBeingProcessed(context, pkgName);
                }
            } else if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action)) {
                ThemePackageHelper.removePackage(context, pkgName);
            } else if (Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
                final boolean themeProcessing = isThemeBeingProcessed(context, pkgName);
                if (themeExistsInProvider(context, pkgName)) {
                    ThemePackageHelper.updatePackage(context, pkgName);
                } else {
                    // Edge case where app was not a theme in previous install
                    ThemePackageHelper.insertPackage(context, pkgName, !themeProcessing);

                    if (themeProcessing) {
                        // store this package name so we know it's being processed and it can be
                        // added or updated when ACTION_THEME_RESOURCES_CACHED is received
                        PreferenceUtils.addThemeBeingProcessed(context, pkgName);
                    }
                }
            } else if (Intent.ACTION_THEME_RESOURCES_CACHED.equals(action)) {
                final String themePkgName = intent.getStringExtra(Intent.EXTRA_THEME_PACKAGE_NAME);
                final int result = intent.getIntExtra(Intent.EXTRA_THEME_RESULT,
                        PackageManager.INSTALL_FAILED_THEME_UNKNOWN_ERROR);
                Set<String> processingThemes =
                        PreferenceUtils.getInstalledThemesBeingProcessed(context);
                if (processingThemes != null &&
                        processingThemes.contains(themePkgName) && result >= 0) {
                    PreferenceUtils.removeThemeBeingProcessed(context, themePkgName);
                    if (themeExistsInProvider(context, themePkgName)) {
                        ThemePackageHelper.updatePackage(context, themePkgName);
                    } else {
                        // Edge case where app was not a theme in previous install
                        ThemePackageHelper.insertPackage(context, themePkgName, true);
                    }
                }
            }
        } catch(NameNotFoundException e) {
            Log.e(TAG, "Unable to add package to theme's provider ", e);
        }
    }

    private static boolean themeExistsInProvider(Context context, String pkgName) {
        boolean exists = false;
        String[] projection = new String[] { ThemesContract.ThemesColumns.PKG_NAME };
        String selection = ThemesContract.ThemesColumns.PKG_NAME + "=?";
        String[] selectionArgs = new String[] { pkgName };
        Cursor c = context.getContentResolver().query(ThemesContract.ThemesColumns.CONTENT_URI,
                projection, selection, selectionArgs, null);

        if (c != null) {
            exists = c.getCount() >= 1;
            c.close();
        }
        return exists;
    }

    private boolean isThemeBeingProcessed(Context context, String pkgName) {
        ThemeManager tm = (ThemeManager) context.getSystemService(Context.THEME_SERVICE);
        return tm.isThemeBeingProcessed(pkgName);
    }
}
