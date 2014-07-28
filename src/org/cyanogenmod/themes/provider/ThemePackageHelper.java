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
import android.content.pm.LegacyThemeInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ThemeInfo;
import android.content.pm.ThemeUtils;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.ThemeConfig;
import android.content.res.ThemeManager;
import android.database.Cursor;
import android.provider.ThemesContract;
import android.provider.ThemesContract.MixnMatchColumns;
import android.provider.ThemesContract.ThemesColumns;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static android.content.res.ThemeConfig.HOLO_DEFAULT;

/**
 * Helper class to populate the provider with info from the theme.
 */
public class ThemePackageHelper {
    public final static String TAG = ThemePackageHelper.class.getName();

    // Maps the theme component to its folder name in assets.
    public static HashMap<String, String> sComponentToFolderName = new HashMap<String, String>();
    static {
        sComponentToFolderName.put(ThemesColumns.MODIFIES_OVERLAYS, "overlays");
        sComponentToFolderName.put(ThemesColumns.MODIFIES_BOOT_ANIM, "bootanimation");
        sComponentToFolderName.put(ThemesColumns.MODIFIES_FONTS, "fonts");
        sComponentToFolderName.put(ThemesColumns.MODIFIES_ICONS, "icons");
        sComponentToFolderName.put(ThemesColumns.MODIFIES_LAUNCHER, "wallpapers");
        sComponentToFolderName.put(ThemesColumns.MODIFIES_LOCKSCREEN, "lockscreen");
        sComponentToFolderName.put(ThemesColumns.MODIFIES_ALARMS, "alarms");
        sComponentToFolderName.put(ThemesColumns.MODIFIES_NOTIFICATIONS, "notifications");
        sComponentToFolderName.put(ThemesColumns.MODIFIES_RINGTONES, "ringtones");
    }

    public static boolean insertPackage(Context context, String pkgName)
            throws NameNotFoundException {
        PackageInfo pi = context.getPackageManager().getPackageInfo(pkgName, 0);
        if (pi == null)
            return false;

        Map<String, Boolean> capabilities = getCapabilities(context, pkgName);
        if (pi.themeInfos != null && pi.themeInfos.length > 0) {
            insertPackageInternal(context, pi, capabilities);
        } else if (pi.legacyThemeInfos != null && pi.legacyThemeInfos.length > 0) {
            insertLegacyPackageInternal(context, pi, capabilities);
        } else if (pi.isLegacyIconPackApk){
            // We must be here because it is a legacy icon pack
            capabilities = new HashMap<String, Boolean>();
            capabilities.put(ThemesColumns.MODIFIES_ICONS, true);
            insertLegacyIconPackInternal(context, pi, capabilities);
        }
        return true;
    }

    private static void insertPackageInternal(Context context, PackageInfo pi,
            Map<String, Boolean> capabilities) {
        ThemeInfo info = pi.themeInfos[0];
        boolean isPresentableTheme = ThemePackageHelper.isPresentableTheme(capabilities);

        ContentValues values = new ContentValues();
        values.put(ThemesColumns.PKG_NAME, pi.packageName);
        values.put(ThemesColumns.TITLE, info.name);
        values.put(ThemesColumns.AUTHOR, info.author);
        values.put(ThemesColumns.DATE_CREATED, System.currentTimeMillis());
        values.put(ThemesColumns.PRESENT_AS_THEME, isPresentableTheme);
        values.put(ThemesColumns.IS_LEGACY_THEME, pi.isLegacyThemeApk);
        values.put(ThemesColumns.IS_DEFAULT_THEME,
                ThemeUtils.getDefaultThemePackageName(context).equals(pi.packageName) ? 1 : 0);
        values.put(ThemesColumns.LAST_UPDATE_TIME, pi.lastUpdateTime);

        // Insert theme capabilities
        insertCapabilities(capabilities, values);

        context.getContentResolver().insert(ThemesColumns.CONTENT_URI, values);
    }

    private static void insertLegacyPackageInternal(Context context, PackageInfo pi,
            Map<String, Boolean> capabilities) {
        LegacyThemeInfo info = pi.legacyThemeInfos[0];
        ContentValues values = new ContentValues();
        values.put(ThemesColumns.PKG_NAME, pi.packageName);
        values.put(ThemesColumns.TITLE, info.name);
        values.put(ThemesColumns.AUTHOR, info.author);
        values.put(ThemesColumns.DATE_CREATED, System.currentTimeMillis());
        values.put(ThemesColumns.PRESENT_AS_THEME, 1);
        values.put(ThemesColumns.IS_LEGACY_THEME, pi.isLegacyThemeApk);
        values.put(ThemesColumns.IS_DEFAULT_THEME,
                ThemeUtils.getDefaultThemePackageName(context).equals(pi.packageName) ? 1 : 0);
        values.put(ThemesColumns.LAST_UPDATE_TIME, pi.lastUpdateTime);

        // Insert theme capabilities
        insertCapabilities(capabilities, values);

        context.getContentResolver().insert(ThemesColumns.CONTENT_URI, values);
    }

    private static void insertLegacyIconPackInternal(Context context, PackageInfo pi,
                                                    Map<String, Boolean> capabilities) {
        PackageManager pm = context.getPackageManager();
        CharSequence labelName = pm.getApplicationLabel(pi.applicationInfo);
        if (labelName == null) labelName = context.getString(R.string.unknown_app_name);

        ContentValues values = new ContentValues();
        values.put(ThemesColumns.PKG_NAME, pi.packageName);
        values.put(ThemesColumns.TITLE, labelName.toString());
        values.put(ThemesColumns.AUTHOR, "");
        values.put(ThemesColumns.DATE_CREATED, System.currentTimeMillis());
        values.put(ThemesColumns.LAST_UPDATE_TIME, pi.lastUpdateTime);
        values.put(ThemesColumns.IS_LEGACY_ICONPACK, 1);

        // Insert theme capabilities
        insertCapabilities(capabilities, values);

        context.getContentResolver().insert(ThemesColumns.CONTENT_URI, values);
    }

    public static void updatePackage(Context context, String pkgName) throws NameNotFoundException {
        if (HOLO_DEFAULT.equals(pkgName)) {
            updateHoloPackageInternal(context);
        } else {
            PackageInfo pi = context.getPackageManager().getPackageInfo(pkgName, 0);
            Map<String, Boolean> capabilities = getCapabilities(context, pkgName);
            if (pi.themeInfos != null && pi.themeInfos.length > 0) {
                updatePackageInternal(context, pi, capabilities);
            } else if (pi.legacyThemeInfos != null && pi.legacyThemeInfos.length > 0) {
                updateLegacyPackageInternal(context, pi, capabilities);
            } else if (pi.isLegacyIconPackApk) {
                updateLegacyIconPackInternal(context, pi, capabilities);
            }

            // We should reapply any components that are currently applied for this theme.
            reapplyInstalledComponentsForTheme(context, pkgName);
        }
    }

    private static void updatePackageInternal(Context context, PackageInfo pi,
            Map<String, Boolean> capabilities) {
        ThemeInfo info = pi.themeInfos[0];
        boolean isPresentableTheme = ThemePackageHelper.isPresentableTheme(capabilities);
        ContentValues values = new ContentValues();
        values.put(ThemesColumns.PKG_NAME, pi.packageName);
        values.put(ThemesColumns.TITLE, info.name);
        values.put(ThemesColumns.AUTHOR, info.author);
        values.put(ThemesColumns.DATE_CREATED, System.currentTimeMillis());
        values.put(ThemesColumns.PRESENT_AS_THEME, isPresentableTheme);
        values.put(ThemesColumns.IS_LEGACY_THEME, pi.isLegacyThemeApk);
        values.put(ThemesColumns.IS_DEFAULT_THEME,
                ThemeUtils.getDefaultThemePackageName(context).equals(pi.packageName) ? 1 : 0);
        values.put(ThemesColumns.LAST_UPDATE_TIME, pi.lastUpdateTime);

        String where = ThemesColumns.PKG_NAME + "=?";
        String[] args = { pi.packageName };
        context.getContentResolver().update(ThemesColumns.CONTENT_URI, values, where, args);
    }

    private static void updateHoloPackageInternal(Context context) {
        ContentValues values = new ContentValues();
        values.put(ThemesColumns.IS_DEFAULT_THEME,
                HOLO_DEFAULT == ThemeUtils.getDefaultThemePackageName(context) ? 1 : 0);
        String where = ThemesColumns.PKG_NAME + "=?";
        String[] args = { HOLO_DEFAULT };
        context.getContentResolver().update(ThemesColumns.CONTENT_URI, values, where, args);
    }

    private static void updateLegacyPackageInternal(Context context, PackageInfo pi,
            Map<String, Boolean> capabilities) {
        LegacyThemeInfo info = pi.legacyThemeInfos[0];
        ContentValues values = new ContentValues();
        values.put(ThemesColumns.PKG_NAME, pi.packageName);
        values.put(ThemesColumns.TITLE, info.name);
        values.put(ThemesColumns.AUTHOR, info.author);
        values.put(ThemesColumns.DATE_CREATED, System.currentTimeMillis());
        values.put(ThemesColumns.PRESENT_AS_THEME, 1);
        values.put(ThemesColumns.IS_LEGACY_THEME, pi.isLegacyThemeApk);
        values.put(ThemesColumns.IS_DEFAULT_THEME,
                ThemeUtils.getDefaultThemePackageName(context).equals(pi.packageName) ? 1 : 0);
        values.put(ThemesColumns.LAST_UPDATE_TIME, pi.lastUpdateTime);

        // Insert theme capabilities
        insertCapabilities(capabilities, values);

        String where = ThemesColumns.PKG_NAME + "=?";
        String[] args = { pi.packageName };
        context.getContentResolver().update(ThemesColumns.CONTENT_URI, values, where, args);
    }

    private static void updateLegacyIconPackInternal(Context context, PackageInfo pi,
                                              Map<String, Boolean> capabilities) {
        PackageManager pm = context.getPackageManager();
        CharSequence labelName = pm.getApplicationLabel(pi.applicationInfo);
        if (labelName == null) labelName = context.getString(R.string.unknown_app_name);

        boolean isPresentableTheme = ThemePackageHelper.isPresentableTheme(capabilities);
        ContentValues values = new ContentValues();
        values.put(ThemesColumns.PKG_NAME, pi.packageName);
        values.put(ThemesColumns.TITLE, labelName.toString());
        values.put(ThemesColumns.DATE_CREATED, System.currentTimeMillis());
        values.put(ThemesColumns.LAST_UPDATE_TIME, pi.lastUpdateTime);

        String where = ThemesColumns.PKG_NAME + "=?";
        String[] args = { pi.packageName };
        context.getContentResolver().update(ThemesColumns.CONTENT_URI, values, where, args);
    }

    public static void removePackage(Context context, String pkgToRemove) {
        // Check currently applied components (fonts, wallpapers etc) and verify the theme is still
        // installed. If it is not installed, we need to set the component back to the default theme
        List<String> moveToDefault = new LinkedList<String>(); // components to move back to default
        Cursor mixnmatch = context.getContentResolver().query(MixnMatchColumns.CONTENT_URI, null,
                null, null, null);
        while (mixnmatch.moveToNext()) {
            String mixnmatchKey = mixnmatch.getString(mixnmatch
                    .getColumnIndex(MixnMatchColumns.COL_KEY));
            String component = ThemesContract.MixnMatchColumns
                    .mixNMatchKeyToComponent(mixnmatchKey);
            String pkg = mixnmatch.getString(mixnmatch.getColumnIndex(MixnMatchColumns.COL_VALUE));
            if (pkgToRemove.equals(pkg)) {
                moveToDefault.add(component);
            }
        }
        String pkgName = ThemeUtils.getDefaultThemePackageName(context);
        ThemeManager manager = (ThemeManager) context.getSystemService(Context.THEME_SERVICE);
        manager.requestThemeChange(pkgName, moveToDefault);

        // Delete the theme from the db
        String selection = ThemesColumns.PKG_NAME + "= ?";
        String[] selectionArgs = { pkgToRemove };
        context.getContentResolver().delete(ThemesColumns.CONTENT_URI, selection, selectionArgs);
    }

    /**
     * Returns a map of components with value of true if the APK themes that component or false
     * otherwise. Example of a theme that handles fonts but not ringtones: (MODIFIES_FONTS -> true,
     * MODIFIES_RINGTONES -> false)
     */
    public static Map<String, Boolean> getCapabilities(Context context, String pkgName) {
        PackageInfo pi = null;
        try {
            pi = context.getPackageManager().getPackageInfo(pkgName, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error getting pi during insert", e);
            return Collections.emptyMap();
        }

        // Determine what this theme is capable of
        Context themeContext = null;
        try {
            themeContext = context.createPackageContext(pkgName, Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Error getting themeContext during insert", e);
            return Collections.emptyMap();
        }

        // Determine what components the theme implements.
        // TODO: Some sort of verification for valid elements (ex font should have valid ttf files)
        HashMap<String, Boolean> implementMap = new HashMap<String, Boolean>();
        for (Map.Entry<String, String> entry : sComponentToFolderName.entrySet()) {
            String component = entry.getKey();
            String folderName = entry.getValue();
            boolean hasComponent = pi.isLegacyThemeApk ? hasThemeComponentLegacy(pi, component)
                    : hasThemeComponent(themeContext, folderName);
            implementMap.put(component, hasComponent);
        }
        return implementMap;
    }

    private static void insertCapabilities(Map<String, Boolean> capabilities,
            ContentValues values) {
        for (Map.Entry<String, Boolean> entry : capabilities.entrySet()) {
            String component = entry.getKey();
            Boolean isImplemented =  entry.getValue();
            values.put(component, isImplemented);
        }
    }

    private static boolean hasThemeComponentLegacy(PackageInfo pi, String component) {
        if (ThemesColumns.MODIFIES_OVERLAYS.equals(component)) {
            return true;
        } else if (ThemesColumns.MODIFIES_LAUNCHER.equals(component)) {
            if (pi.legacyThemeInfos != null && pi.legacyThemeInfos.length > 0
                    && pi.legacyThemeInfos[0].wallpaperResourceId != 0) {
                return true;
            }
        } else if (ThemesColumns.MODIFIES_RINGTONES.equals(component)) {
            if (pi.legacyThemeInfos != null && pi.legacyThemeInfos.length > 0
                    && !TextUtils.isEmpty(pi.legacyThemeInfos[0].ringtoneFileName)) {
                return true;
            }
        } else if (ThemesColumns.MODIFIES_NOTIFICATIONS.equals(component)) {
            if (pi.legacyThemeInfos != null && pi.legacyThemeInfos.length > 0
                    && !TextUtils.isEmpty(pi.legacyThemeInfos[0].notificationFileName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasThemeComponent(Context themeContext, String component) {
        boolean found = false;
        AssetManager assetManager = themeContext.getAssets();
        try {
            String[] assetList = assetManager.list(component);
            if (assetList != null && assetList.length > 0) {
                found = true;
            }
        } catch (IOException e) {
            Log.e(TAG, "There was an error checking for asset " + component, e);
        }
        return found;
    }

    // Presently we are defining a "presentable" theme as any theme
    // which implements 2+ components. Such themes can be shown to the user
    // under the "themes" category.
    public static boolean isPresentableTheme(Map<String, Boolean> implementMap) {
        int count = 0;
        for (Boolean isImplemented : implementMap.values()) {
            count += isImplemented ? 1 : 0;
        }
        return count >= 2;
    }

    private static void reapplyInstalledComponentsForTheme(Context context, String pkgName) {
        Configuration config = context.getResources().getConfiguration();
        if (config == null || config.themeConfig == null) return;

        List<String> reApply = new LinkedList<String>(); // components to re-apply
        // Other packages such as wallpaper can be changed outside of themes
        // and are not tracked well by the provider. We only care to apply resources that may crash
        // the system if they are not reapplied.
        ThemeConfig themeConfig = config.themeConfig;
        if (pkgName.equals(themeConfig.getFontPkgName())) {
            reApply.add(ThemesColumns.MODIFIES_FONTS);
        }
        if (pkgName.equals(themeConfig.getIconPackPkgName())) {
            reApply.add(ThemesColumns.MODIFIES_ICONS);
        }
        if (pkgName.equals(themeConfig.getOverlayPkgName())) {
            reApply.add(ThemesColumns.MODIFIES_OVERLAYS);
        }

        ThemeManager manager = (ThemeManager) context.getSystemService(Context.THEME_SERVICE);
        manager.requestThemeChange(pkgName, reApply);
    }
}
