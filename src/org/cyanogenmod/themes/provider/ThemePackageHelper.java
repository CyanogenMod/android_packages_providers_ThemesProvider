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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ThemeInfo;
import android.content.pm.ThemeUtils;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.ThemeChangeRequest;
import android.content.res.ThemeChangeRequest.RequestType;
import android.content.res.ThemeConfig;
import android.content.res.ThemeManager;
import android.database.Cursor;
import android.provider.ThemesContract;
import android.provider.ThemesContract.MixnMatchColumns;
import android.provider.ThemesContract.ThemesColumns;
import android.provider.ThemesContract.ThemesColumns.InstallState;
import android.util.Log;

import org.cyanogenmod.internal.util.CmLockPatternUtils;
import org.cyanogenmod.themes.provider.util.ProviderUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static android.content.res.ThemeConfig.SYSTEMUI_NAVBAR_PKG;
import static android.content.res.ThemeConfig.SYSTEMUI_STATUS_BAR_PKG;
import static android.content.res.ThemeConfig.SYSTEM_DEFAULT;

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
        sComponentToFolderName.put(ThemesColumns.MODIFIES_STATUS_BAR,
                "overlays/com.android.systemui");
        sComponentToFolderName.put(ThemesColumns.MODIFIES_NAVIGATION_BAR,
                "overlays/com.android.systemui");
        sComponentToFolderName.put(ThemesColumns.MODIFIES_LIVE_LOCK_SCREEN,
                "live-lockscreen");
    }

    public static boolean insertPackage(Context context, String pkgName, boolean isProcessing)
            throws NameNotFoundException {
        PackageInfo pi = context.getPackageManager().getPackageInfo(pkgName, 0);
        if (pi == null)
            return false;

        Map<String, Boolean> capabilities = getCapabilities(context, pkgName);
        if (pi.themeInfo != null) {
            insertPackageInternal(context, pi, capabilities, isProcessing);
        } else if (pi.isLegacyIconPackApk){
            // We must be here because it is a legacy icon pack
            capabilities = new HashMap<String, Boolean>();
            capabilities.put(ThemesColumns.MODIFIES_ICONS, true);
            insertLegacyIconPackInternal(context, pi, capabilities, isProcessing);
        }
        return true;
    }

    private static void insertPackageInternal(Context context, PackageInfo pi,
            Map<String, Boolean> capabilities, boolean isProcessing) {
        ThemeInfo info = pi.themeInfo;
        boolean isPresentableTheme = isPresentableTheme(capabilities);

        ContentValues values = new ContentValues();
        values.put(ThemesColumns.PKG_NAME, pi.packageName);
        values.put(ThemesColumns.TITLE, info.name);
        values.put(ThemesColumns.AUTHOR, info.author);
        values.put(ThemesColumns.DATE_CREATED, System.currentTimeMillis());
        values.put(ThemesColumns.PRESENT_AS_THEME, isPresentableTheme);
        values.put(ThemesColumns.IS_LEGACY_THEME, false);
        values.put(ThemesColumns.IS_DEFAULT_THEME,
                ThemeUtils.getDefaultThemePackageName(context).equals(pi.packageName) ? 1 : 0);
        values.put(ThemesColumns.LAST_UPDATE_TIME, pi.lastUpdateTime);
        values.put(ThemesColumns.INSTALL_TIME, pi.firstInstallTime);
        values.put(ThemesColumns.TARGET_API, pi.applicationInfo.targetSdkVersion);
        values.put(ThemesColumns.INSTALL_STATE, isProcessing ? InstallState.INSTALLING :
                InstallState.INSTALLED);

        // Insert theme capabilities
        insertCapabilities(capabilities, values);

        context.getContentResolver().insert(ThemesColumns.CONTENT_URI, values);
    }

    private static void insertLegacyIconPackInternal(Context context, PackageInfo pi,
            Map<String, Boolean> capabilities, boolean isProcessing) {
        PackageManager pm = context.getPackageManager();
        CharSequence labelName = pm.getApplicationLabel(pi.applicationInfo);
        if (labelName == null) labelName = context.getString(R.string.unknown_app_name);

        ContentValues values = new ContentValues();
        values.put(ThemesColumns.PKG_NAME, pi.packageName);
        values.put(ThemesColumns.TITLE, labelName.toString());
        values.put(ThemesColumns.AUTHOR, "");
        values.put(ThemesColumns.DATE_CREATED, System.currentTimeMillis());
        values.put(ThemesColumns.LAST_UPDATE_TIME, pi.lastUpdateTime);
        values.put(ThemesColumns.INSTALL_TIME, pi.firstInstallTime);
        values.put(ThemesColumns.IS_LEGACY_ICONPACK, 1);
        values.put(ThemesColumns.TARGET_API, pi.applicationInfo.targetSdkVersion);
        values.put(ThemesColumns.INSTALL_STATE, isProcessing ? InstallState.INSTALLING :
                InstallState.INSTALLED);

        // Insert theme capabilities
        insertCapabilities(capabilities, values);

        context.getContentResolver().insert(ThemesColumns.CONTENT_URI, values);
    }

    public static void updatePackage(Context context, String pkgName, boolean isProcessing)
            throws NameNotFoundException {
        if (SYSTEM_DEFAULT.equals(pkgName)) {
            updateSystemPackageInternal(context);
        } else {
            PackageInfo pi = context.getPackageManager().getPackageInfo(pkgName, 0);
            Map<String, Boolean> capabilities = getCapabilities(context, pkgName);
            if (pi.themeInfo != null) {
                updatePackageInternal(context, pi, capabilities, isProcessing);
            } else if (pi.isLegacyIconPackApk) {
                updateLegacyIconPackInternal(context, pi, capabilities, isProcessing);
            }
        }
    }

    private static void updatePackageInternal(Context context, PackageInfo pi,
            Map<String, Boolean> capabilities, boolean isProcessing) {
        ThemeInfo info = pi.themeInfo;
        boolean isPresentableTheme = ThemePackageHelper.isPresentableTheme(capabilities);
        final int oldInstallState =
                ProviderUtils.getInstallStateForTheme(context, pi.packageName);
        final int newState = isProcessing ? InstallState.UPDATING : InstallState.INSTALLED;

        ContentValues values = new ContentValues();
        values.put(ThemesColumns.PKG_NAME, pi.packageName);
        values.put(ThemesColumns.TITLE, info.name);
        values.put(ThemesColumns.AUTHOR, info.author);
        values.put(ThemesColumns.DATE_CREATED, System.currentTimeMillis());
        values.put(ThemesColumns.PRESENT_AS_THEME, isPresentableTheme);
        values.put(ThemesColumns.IS_LEGACY_THEME, false);
        values.put(ThemesColumns.IS_DEFAULT_THEME,
                ThemeUtils.getDefaultThemePackageName(context).equals(pi.packageName) ? 1 : 0);
        values.put(ThemesColumns.LAST_UPDATE_TIME, pi.lastUpdateTime);
        values.put(ThemesColumns.INSTALL_TIME, pi.firstInstallTime);
        values.put(ThemesColumns.TARGET_API, pi.applicationInfo.targetSdkVersion);
        values.put(ThemesColumns.INSTALL_STATE, newState);

        // Insert theme capabilities
        insertCapabilities(capabilities, values);

        String where = ThemesColumns.PKG_NAME + "=?";
        String[] args = { pi.packageName };
        context.getContentResolver().update(ThemesColumns.CONTENT_URI, values, where, args);

        // Broadcast that the theme is installed if the previous state was INSTALLING and
        // the new state is INSTALLED.
        if (newState == ThemesColumns.InstallState.INSTALLED) {
            if (oldInstallState == ThemesColumns.InstallState.INSTALLING) {
                ProviderUtils.sendThemeInstalledBroadcast(context, pi.packageName);
            } else if (oldInstallState == ThemesColumns.InstallState.UPDATING) {
                ProviderUtils.sendThemeUpdatedBroadcast(context, pi.packageName);
                // We should reapply any components that are currently applied for this theme.
                reapplyInstalledComponentsForTheme(context, pi.packageName);
            }
        }
    }

    private static void updateSystemPackageInternal(Context context) {
        ContentValues values = new ContentValues();
        values.put(ThemesColumns.IS_DEFAULT_THEME,
                SYSTEM_DEFAULT == ThemeUtils.getDefaultThemePackageName(context) ? 1 : 0);
        String where = ThemesColumns.PKG_NAME + "=?";
        String[] args = { SYSTEM_DEFAULT };
        context.getContentResolver().update(ThemesColumns.CONTENT_URI, values, where, args);
    }

    private static void updateLegacyIconPackInternal(Context context, PackageInfo pi,
            Map<String, Boolean> capabilities, boolean isProcessing) {
        PackageManager pm = context.getPackageManager();
        CharSequence labelName = pm.getApplicationLabel(pi.applicationInfo);
        if (labelName == null) labelName = context.getString(R.string.unknown_app_name);

        ContentValues values = new ContentValues();
        values.put(ThemesColumns.PKG_NAME, pi.packageName);
        values.put(ThemesColumns.TITLE, labelName.toString());
        values.put(ThemesColumns.DATE_CREATED, System.currentTimeMillis());
        values.put(ThemesColumns.LAST_UPDATE_TIME, pi.lastUpdateTime);
        values.put(ThemesColumns.INSTALL_TIME, pi.firstInstallTime);
        values.put(ThemesColumns.TARGET_API, pi.applicationInfo.targetSdkVersion);
        values.put(ThemesColumns.INSTALL_STATE,
                isProcessing ? InstallState.UPDATING : InstallState.INSTALLED);

        String where = ThemesColumns.PKG_NAME + "=?";
        String[] args = { pi.packageName };
        context.getContentResolver().update(ThemesColumns.CONTENT_URI, values, where, args);

        final int oldInstallState =
                ProviderUtils.getInstallStateForTheme(context, pi.packageName);
        final int newState = isProcessing ? InstallState.UPDATING : InstallState.INSTALLED;
        if (newState == ThemesColumns.InstallState.INSTALLED) {
            if (oldInstallState == ThemesColumns.InstallState.UPDATING) {
                // We should reapply any components that are currently applied for this theme.
                reapplyInstalledComponentsForTheme(context, pi.packageName);
            }
        }
    }

    public static void removePackage(Context context, String pkgToRemove) {
        // Check currently applied components (fonts, wallpapers etc) and verify the theme is still
        // installed. If it is not installed, we need to set the component back to the default theme
        ThemeChangeRequest.Builder builder = new ThemeChangeRequest.Builder();
        Map<String, String> defaultComponents = ThemeUtils.getDefaultComponents(context);

        Cursor mixnmatch = context.getContentResolver().query(MixnMatchColumns.CONTENT_URI, null,
                null, null, null);
        while (mixnmatch.moveToNext()) {
            String mixnmatchKey = mixnmatch.getString(mixnmatch
                    .getColumnIndex(MixnMatchColumns.COL_KEY));
            String component = ThemesContract.MixnMatchColumns
                    .mixNMatchKeyToComponent(mixnmatchKey);
            String pkg = mixnmatch.getString(mixnmatch.getColumnIndex(MixnMatchColumns.COL_VALUE));
            if (pkgToRemove.equals(pkg)) {
                builder.setComponent(component, defaultComponents.get(component));
            }
        }

        // Check for any per-app themes components using this theme
        final Configuration config = context.getResources().getConfiguration();
        final ThemeConfig themeConfig = config != null ? config.themeConfig : null;
        if (themeConfig != null) {
            final Map<String, ThemeConfig.AppTheme> themes = themeConfig.getAppThemes();
            final String defaultOverlayPkgName
                    = defaultComponents.get(ThemesColumns.MODIFIES_OVERLAYS);
            for (String appPkgName : themes.keySet()) {
                if (ThemeUtils.isPerAppThemeComponent(appPkgName) &&
                        pkgToRemove.equals(themes.get(appPkgName).getOverlayPkgName())) {
                    builder.setAppOverlay(appPkgName, defaultOverlayPkgName);
                }
            }
        }
        mixnmatch.close();

        builder.setRequestType(RequestType.THEME_REMOVED);
        ThemeManager manager = (ThemeManager) context.getSystemService(Context.THEME_SERVICE);
        manager.requestThemeChange(builder.build(), false);

        // Delete the theme from the db
        String selection = ThemesColumns.PKG_NAME + "= ?";
        String[] selectionArgs = { pkgToRemove };
        final ContentResolver resolver = context.getContentResolver();
        if (resolver.delete(ThemesColumns.CONTENT_URI, selection, selectionArgs) > 0) {
            ProviderUtils.sendThemeRemovedBroadcast(context, pkgToRemove);
        }
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
            boolean hasComponent = hasThemeComponent(themeContext, folderName);
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

    public static boolean hasThemeComponent(Context themeContext, String component) {
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
    // which has icons, wallpaper, and overlays
    public static boolean isPresentableTheme(Map<String, Boolean> implementMap) {
        return implementMap != null &&
                hasThemeComponent(implementMap, ThemesColumns.MODIFIES_LAUNCHER) &&
                hasThemeComponent(implementMap, ThemesColumns.MODIFIES_OVERLAYS);
    }

    private static boolean hasThemeComponent(Map<String, Boolean> componentMap, String component) {
        return componentMap.containsKey(component) && componentMap.get(component);
    }

    private static void reapplyInstalledComponentsForTheme(Context context, String pkgName) {
        ThemeChangeRequest.Builder builder = new ThemeChangeRequest.Builder();
        Configuration config = context.getResources().getConfiguration();
        if (config != null && config.themeConfig != null) {
            // Other packages such as wallpaper can be changed outside of themes
            // and are not tracked well by the provider. We only care to apply resources that may crash
            // the system if they are not reapplied.
            ThemeConfig themeConfig = config.themeConfig;
            if (pkgName.equals(themeConfig.getFontPkgName())) {
                builder.setFont(pkgName);
            }
            if (pkgName.equals(themeConfig.getIconPackPkgName())) {
                builder.setIcons(pkgName);
            }
            if (pkgName.equals(themeConfig.getOverlayPkgName())) {
                builder.setOverlay(pkgName);
            }
            if (pkgName.equals(themeConfig.getOverlayPkgNameForApp(SYSTEMUI_STATUS_BAR_PKG))) {
                builder.setStatusBar(pkgName);
            }
            if (pkgName.equals(themeConfig.getOverlayPkgNameForApp(SYSTEMUI_NAVBAR_PKG))) {
                builder.setNavBar(pkgName);
            }

            // Check if there are any per-app overlays using this theme
            final Map<String, ThemeConfig.AppTheme> themes = themeConfig.getAppThemes();
            for (String appPkgName : themes.keySet()) {
                if (ThemeUtils.isPerAppThemeComponent(appPkgName)) {
                    builder.setAppOverlay(appPkgName, pkgName);
                }
            }
        }

        CmLockPatternUtils lockPatternUtils = new CmLockPatternUtils(context);
        if (lockPatternUtils.isThirdPartyKeyguardEnabled()) {
            String[] projection = {MixnMatchColumns.COL_VALUE};
            String selection = MixnMatchColumns.COL_KEY + "=?";
            String[] selectionArgs = {MixnMatchColumns.KEY_LIVE_LOCK_SCREEN};
            Cursor cursor = context.getContentResolver().query(MixnMatchColumns.CONTENT_URI,
                    projection, selection, selectionArgs, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    String appliedPkgName = cursor.getString(0);
                    if (pkgName.equals(appliedPkgName)) {
                        builder.setLiveLockScreen(pkgName);
                    }
                }
                cursor.close();
            }
        }

        builder.setRequestType(RequestType.THEME_UPDATED);
        ThemeManager manager = (ThemeManager) context.getSystemService(Context.THEME_SERVICE);
        manager.requestThemeChange(builder.build(), false);
    }
}
