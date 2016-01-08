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
import android.net.Uri;
import android.util.Log;
import org.cyanogenmod.themes.provider.util.ProviderUtils;

public class AppReceiver extends BroadcastReceiver {
    public final static String TAG = AppReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        final Uri uri = intent.getData();
        final String pkgName = uri != null ? uri.getSchemeSpecificPart() : null;
        final boolean isReplacing = intent.getExtras().getBoolean(Intent.EXTRA_REPLACING, false);
        final String action = intent.getAction();
        try {
            // All themes/icon packs go to the theme service for processing now so assume
            // isProcessing is always true when installing/replacing
            if (Intent.ACTION_PACKAGE_ADDED.equals(action) && !isReplacing
                    && !ProviderUtils.themeExistsInProvider(context, pkgName)) {
                ThemePackageHelper.insertPackage(context, pkgName, true);
            } else if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action)) {
                ThemePackageHelper.removePackage(context, pkgName);
            } else if (Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
                if (ProviderUtils.themeExistsInProvider(context, pkgName)) {
                    ThemePackageHelper.updatePackage(context, pkgName, true);
                } else {
                    // Edge case where app was not a theme in previous install
                    ThemePackageHelper.insertPackage(context, pkgName, true);
                }
            } else if (Intent.ACTION_THEME_RESOURCES_CACHED.equals(action)) {
                final String themePkgName = intent.getStringExtra(Intent.EXTRA_THEME_PACKAGE_NAME);
                final int result = intent.getIntExtra(Intent.EXTRA_THEME_RESULT,
                        PackageManager.INSTALL_FAILED_THEME_UNKNOWN_ERROR);
                if (result == 0) {
                    if (ProviderUtils.themeExistsInProvider(context, themePkgName)) {
                        ThemePackageHelper.updatePackage(context, themePkgName, false);
                    } else {
                        // Edge case where app was not a theme in previous install
                        ThemePackageHelper.insertPackage(context, themePkgName, false);
                    }
                } else {
                    Log.e(TAG, "Unable to update theme " + themePkgName + ", result=" + result);
                }
            }
        } catch(NameNotFoundException e) {
            Log.e(TAG, "Unable to add package to theme's provider ", e);
        }
    }
}
