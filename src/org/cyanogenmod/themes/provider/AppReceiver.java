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
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ThemesContract;
import android.util.Log;

public class AppReceiver extends BroadcastReceiver {
    public final static String TAG = AppReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Uri uri = intent.getData();
        String pkgName = uri != null ? uri.getSchemeSpecificPart() : null;
        boolean isReplacing = intent.getExtras().getBoolean(Intent.EXTRA_REPLACING, false);

        try {
            if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED) && !isReplacing) {
                ThemePackageHelper.insertPackage(context, pkgName);
            } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_FULLY_REMOVED)) {
                ThemePackageHelper.removePackage(context, pkgName);
            } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_REPLACED)) {
                if (themeExistsInProvider(context, pkgName)) {
                    ThemePackageHelper.updatePackage(context, pkgName);
                } else {
                    // Edge case where app was not a theme in previous install
                    ThemePackageHelper.insertPackage(context, pkgName);
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
}
