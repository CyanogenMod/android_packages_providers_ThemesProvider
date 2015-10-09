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

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.ThemeConfig;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.FileUtils;
import android.provider.ThemesContract.ThemesColumns;
import android.provider.ThemesContract.PreviewColumns;
import android.util.Log;
import org.cyanogenmod.themes.provider.util.BootAnimationPreviewGenerator;
import org.cyanogenmod.themes.provider.util.IconPreviewGenerator;
import org.cyanogenmod.themes.provider.util.IconPreviewGenerator.IconItems;
import org.cyanogenmod.themes.provider.util.LiveLockScreenPreviewGenerator;
import org.cyanogenmod.themes.provider.util.LiveLockScreenPreviewGenerator.LiveLockScreenItems;
import org.cyanogenmod.themes.provider.util.PreviewUtils;
import org.cyanogenmod.themes.provider.util.StylePreviewGenerator;
import org.cyanogenmod.themes.provider.util.StylePreviewGenerator.StyleItems;
import org.cyanogenmod.themes.provider.util.SystemUiPreviewGenerator;
import org.cyanogenmod.themes.provider.util.SystemUiPreviewGenerator.SystemUiItems;
import org.cyanogenmod.themes.provider.util.WallpaperPreviewGenerator;
import org.cyanogenmod.themes.provider.util.WallpaperPreviewGenerator.WallpaperItem;
import org.cyanogenmod.themes.provider.util.WallpaperPreviewGenerator.WallpaperItems;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/*
 * Copies images from the theme APK to the local provider's cache
 */
public class PreviewGenerationService extends IntentService {
    public static final String ACTION_INSERT = "org.cyanogenmod.themes.provider.action.insert";
    public static final String ACTION_UPDATE = "org.cyanogenmod.themes.provider.action.update";
    public static final String EXTRA_PKG_NAME = "extra_pkg_name";

    private static final String TAG = PreviewGenerationService.class.getName();

    public PreviewGenerationService() {
        super(PreviewGenerationService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getExtras() == null || intent.getExtras().getString(EXTRA_PKG_NAME) == null) {
            Log.e(TAG, "No package name or extras provided");
            return;
        }

        final Bundle extras = intent.getExtras();
        String pkgName = extras.getString(EXTRA_PKG_NAME);
        boolean hasSystemUi = false;
        boolean hasIcons = false;
        boolean hasWallpaper = false;
        boolean hasStyles = false;
        boolean hasBootanimation = false;
        boolean hasLiveLockScreen = false;
        boolean isSystemTheme = ThemeConfig.SYSTEM_DEFAULT.equals(pkgName);
        Cursor c = queryTheme(this, pkgName);
        if (c != null) {
            if (c.moveToFirst()) {
                // mods_status_bar was added in version 7 of the database so we need to make sure
                // it exists when trying to get the int value from the row.
                final int sysUiIndex = c.getColumnIndex(ThemesColumns.MODIFIES_STATUS_BAR);
                hasSystemUi = sysUiIndex >= 0 && c.getInt(sysUiIndex) == 1;
                hasIcons = c.getInt(c.getColumnIndex(ThemesColumns.MODIFIES_ICONS)) == 1;
                hasWallpaper = c.getInt(c.getColumnIndex(ThemesColumns.MODIFIES_LAUNCHER)) == 1 ||
                        c.getInt(c.getColumnIndex(ThemesColumns.MODIFIES_LOCKSCREEN)) == 1;
                hasStyles = c.getInt(c.getColumnIndex(ThemesColumns.MODIFIES_OVERLAYS)) == 1;
                hasBootanimation =
                        c.getInt(c.getColumnIndex(ThemesColumns.MODIFIES_BOOT_ANIM)) == 1;
                hasLiveLockScreen =
                        c.getInt(c.getColumnIndex(ThemesColumns.MODIFIES_LIVE_LOCK_SCREEN)) == 1;
            }
            c.close();
        }
        final String action = intent.getAction();
        if (ACTION_INSERT.equals(action) || ACTION_UPDATE.equals(action)) {
            PackageInfo info = null;
            try {
                if (!isSystemTheme ) {
                    info = getPackageManager().getPackageInfo(pkgName, 0);
                }
            } catch (NameNotFoundException e) {
                Log.e(TAG, "Unable to get package info for " + pkgName, e);
            }
            if (isSystemTheme || info != null) {
                String filesDir = this.getFilesDir().getAbsolutePath();
                String themePreviewsDir =
                        PreviewUtils.getPreviewsDir(filesDir) + File.separator + pkgName;
                clearThemePreviewsDir(themePreviewsDir);

                SystemUiItems items = null;
                try {
                    items = !hasSystemUi ? null :
                            new SystemUiPreviewGenerator(this).generateSystemUiItems(pkgName);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to create statusbar previews for " + pkgName, e);
                }

                IconItems iconItems = null;
                if (hasIcons) {
                    try {
                        iconItems = new IconPreviewGenerator(this).generateIconItems(pkgName);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to create icon previews for " + pkgName, e);
                    }
                }

                WallpaperItems wallpaperItems = null;
                if (hasWallpaper) {
                    try {
                        wallpaperItems = new WallpaperPreviewGenerator(this)
                                .generateWallpaperPreviews(info);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to create wallpaper previews for " + pkgName, e);
                    }
                }

                StyleItems styleItems = null;
                if (hasStyles) {
                    try {
                        styleItems = new StylePreviewGenerator(this).generateStylePreviews(pkgName);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to create style previews for " + pkgName, e);
                    }
                }

                Bitmap bootAnim = null;
                if (hasBootanimation) {
                    try {
                        bootAnim = new BootAnimationPreviewGenerator(this)
                                .generateBootAnimationPreview(pkgName);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to create boot animation preview for " + pkgName, e);
                    }
                }

                LiveLockScreenItems liveLockScreenItems = null;
                if (hasLiveLockScreen) {
                    try {
                        liveLockScreenItems = new LiveLockScreenPreviewGenerator(this)
                                .generateLiveLockScreenPreview(pkgName);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to create live lock screen preview for " + pkgName, e);
                    }
                }
                insertPreviewItemsIntoDb(pkgName, items, iconItems, wallpaperItems, styleItems,
                        liveLockScreenItems, bootAnim);
            }
        }
    }

    private void insertPreviewItemsIntoDb(String pkgName, SystemUiItems items, IconItems icons,
            WallpaperItems wallpaperItems, StyleItems styleItems,
            LiveLockScreenItems liveLockScreenItems, Bitmap bootAnim) {
        String[] projection = {ThemesColumns._ID};
        String selection = ThemesColumns.PKG_NAME + "=?";
        String[] selectionArgs = { pkgName };

        final ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(ThemesColumns.CONTENT_URI, projection, selection,
                selectionArgs, null);

        if (cursor != null) {
            cursor.moveToFirst();
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(ThemesColumns._ID));
            cursor.close();

            List<ContentValues> themeValues = new ArrayList<ContentValues>();
            ContentValues values = null;
            String filesDir = this.getFilesDir().getAbsolutePath();
            String path = null;
            clearThemeFromPreviewDB(resolver, pkgName);

            if (items != null) {
                path = PreviewUtils.compressAndSavePng(items.statusbarBackground, filesDir, pkgName,
                        PreviewColumns.STATUSBAR_BACKGROUND);
                values = createPreviewEntryString(id, PreviewColumns.STATUSBAR_BACKGROUND,
                        path);
                themeValues.add(values);

                path = PreviewUtils.compressAndSavePng(items.bluetoothIcon, filesDir, pkgName,
                        PreviewColumns.STATUSBAR_BLUETOOTH_ICON);
                values = createPreviewEntryString(id, PreviewColumns.STATUSBAR_BLUETOOTH_ICON,
                        path);
                themeValues.add(values);

                path = PreviewUtils.compressAndSavePng(items.wifiIcon, filesDir, pkgName,
                        PreviewColumns.STATUSBAR_WIFI_ICON);
                values = createPreviewEntryString(id, PreviewColumns.STATUSBAR_WIFI_ICON, path);
                themeValues.add(values);

                path = PreviewUtils.compressAndSavePng(items.signalIcon, filesDir, pkgName,
                        PreviewColumns.STATUSBAR_SIGNAL_ICON);
                values = createPreviewEntryString(id, PreviewColumns.STATUSBAR_SIGNAL_ICON,
                        path);
                themeValues.add(values);

                path = PreviewUtils.compressAndSavePng(items.batteryPortrait, filesDir, pkgName,
                        PreviewColumns.STATUSBAR_BATTERY_PORTRAIT);
                values = createPreviewEntryString(id, PreviewColumns.STATUSBAR_BATTERY_PORTRAIT,
                        path);
                themeValues.add(values);

                path = PreviewUtils.compressAndSavePng(items.batteryLandscape, filesDir, pkgName,
                        PreviewColumns.STATUSBAR_BATTERY_LANDSCAPE);
                values = createPreviewEntryString(id,
                        PreviewColumns.STATUSBAR_BATTERY_LANDSCAPE, path);
                themeValues.add(values);

                path = PreviewUtils.compressAndSavePng(items.batteryCircle, filesDir, pkgName,
                        PreviewColumns.STATUSBAR_BATTERY_CIRCLE);
                values = createPreviewEntryString(id, PreviewColumns.STATUSBAR_BATTERY_CIRCLE,
                        path);
                themeValues.add(values);

                values = createPreviewEntryInt(id, PreviewColumns.STATUSBAR_CLOCK_TEXT_COLOR,
                        items.clockColor);
                themeValues.add(values);

                values = createPreviewEntryInt(id,
                        PreviewColumns.STATUSBAR_WIFI_COMBO_MARGIN_END, items.wifiMarginEnd);
                themeValues.add(values);

                path = PreviewUtils.compressAndSavePng(items.navbarBackground, filesDir, pkgName,
                        PreviewColumns.NAVBAR_BACKGROUND);
                values = createPreviewEntryString(id, PreviewColumns.NAVBAR_BACKGROUND, path);
                themeValues.add(values);

                path = PreviewUtils.compressAndSavePng(items.navbarBack, filesDir, pkgName,
                        PreviewColumns.NAVBAR_BACK_BUTTON);
                values = createPreviewEntryString(id, PreviewColumns.NAVBAR_BACK_BUTTON, path);
                themeValues.add(values);

                path = PreviewUtils.compressAndSavePng(items.navbarHome, filesDir, pkgName,
                        PreviewColumns.NAVBAR_HOME_BUTTON);
                values = createPreviewEntryString(id, PreviewColumns.NAVBAR_HOME_BUTTON, path);
                themeValues.add(values);

                path = PreviewUtils.compressAndSavePng(items.navbarRecent, filesDir, pkgName,
                        PreviewColumns.NAVBAR_RECENT_BUTTON);
                values = createPreviewEntryString(id, PreviewColumns.NAVBAR_RECENT_BUTTON,
                        path);
                themeValues.add(values);
            }
            if (icons != null) {
                path = PreviewUtils.compressAndSavePng(icons.icon1, filesDir, pkgName,
                        PreviewColumns.ICON_PREVIEW_1);
                values = createPreviewEntryString(id, PreviewColumns.ICON_PREVIEW_1, path);
                themeValues.add(values);

                path = PreviewUtils.compressAndSavePng(icons.icon2, filesDir, pkgName,
                        PreviewColumns.ICON_PREVIEW_2);
                values = createPreviewEntryString(id, PreviewColumns.ICON_PREVIEW_2, path);
                themeValues.add(values);

                path = PreviewUtils.compressAndSavePng(icons.icon3, filesDir, pkgName,
                        PreviewColumns.ICON_PREVIEW_3);
                values = createPreviewEntryString(id, PreviewColumns.ICON_PREVIEW_3, path);
                themeValues.add(values);
            }
            if (wallpaperItems != null) {
                for (int i = 0; i < wallpaperItems.wallpapers.size(); i++) {
                    WallpaperItem wallpaperItem = wallpaperItems.wallpapers.get(i);
                    if (wallpaperItem == null) continue;

                    if (wallpaperItem.assetPath != null) {
                        path = wallpaperItem.assetPath;
                        values = createPreviewEntryString(id, i,
                                PreviewColumns.WALLPAPER_FULL, path);
                        themeValues.add(values);
                    }

                    if (wallpaperItem.previewPath != null) {
                        values = createPreviewEntryString(id, i,
                                PreviewColumns.WALLPAPER_PREVIEW, wallpaperItem.previewPath);
                        themeValues.add(values);
                    }

                    if (wallpaperItem.thumbnailPath != null) {
                        values = createPreviewEntryString(id, i,
                                PreviewColumns.WALLPAPER_THUMBNAIL, wallpaperItem.thumbnailPath);
                        themeValues.add(values);
                    }
                }

                if (wallpaperItems.lockscreen != null) {
                    if (wallpaperItems.lockscreen.previewPath != null) {
                        values = createPreviewEntryString(id,
                                PreviewColumns.LOCK_WALLPAPER_PREVIEW,
                                wallpaperItems.lockscreen.previewPath);
                        themeValues.add(values);
                    }

                    if (wallpaperItems.lockscreen.thumbnailPath != null) {
                        values = createPreviewEntryString(id,
                                PreviewColumns.LOCK_WALLPAPER_THUMBNAIL,
                                wallpaperItems.lockscreen.thumbnailPath);
                        themeValues.add(values);
                    }
                }
            }
            if (styleItems != null) {
                path = PreviewUtils.compressAndSavePng(styleItems.thumbnail, filesDir, pkgName,
                        PreviewColumns.STYLE_THUMBNAIL);
                values = createPreviewEntryString(id, PreviewColumns.STYLE_THUMBNAIL, path);
                themeValues.add(values);

                path = PreviewUtils.compressAndSavePng(styleItems.preview, filesDir, pkgName,
                        PreviewColumns.STYLE_PREVIEW);
                values = createPreviewEntryString(id, PreviewColumns.STYLE_PREVIEW, path);
                themeValues.add(values);
            }
            if (liveLockScreenItems != null) {
                path = PreviewUtils.compressAndSaveJpg(liveLockScreenItems.thumbnail, filesDir,
                        pkgName, PreviewColumns.LIVE_LOCK_SCREEN_THUMBNAIL);
                values = createPreviewEntryString(id, PreviewColumns.LIVE_LOCK_SCREEN_THUMBNAIL,
                        path);
                themeValues.add(values);

                path = PreviewUtils.compressAndSaveJpg(liveLockScreenItems.preview, filesDir,
                        pkgName, PreviewColumns.LIVE_LOCK_SCREEN_PREVIEW);
                values = createPreviewEntryString(id, PreviewColumns.LIVE_LOCK_SCREEN_PREVIEW,
                        path);
                themeValues.add(values);
            }
            if (bootAnim != null) {
                path = PreviewUtils.compressAndSavePng(bootAnim, filesDir, pkgName,
                        PreviewColumns.BOOTANIMATION_THUMBNAIL);
                values = createPreviewEntryString(id, PreviewColumns.BOOTANIMATION_THUMBNAIL,
                        path);
                themeValues.add(values);
            }

            if (!themeValues.isEmpty()) {
                selection = PreviewColumns.THEME_ID + "=? AND " + PreviewColumns.COMPONENT_ID +
                        "=? AND " + PreviewColumns.COL_KEY + "=?";
                for (ContentValues contentValues : themeValues) {
                    selectionArgs = new String[]{String.valueOf(id),
                            contentValues.getAsString(PreviewColumns.COMPONENT_ID),
                            contentValues.getAsString(PreviewColumns.COL_KEY)};
                    // Try an update first, if that returns 0 then we need to insert these values
                    if (resolver.update(PreviewColumns.CONTENT_URI, contentValues, selection,
                            selectionArgs) == 0) {
                        resolver.insert(PreviewColumns.CONTENT_URI, contentValues);
                    }
                }
            }
        }
    }

    private static ContentValues createPreviewEntryInt(int id, String key, int value) {
        return createPreviewEntryInt(id, 0, key, value);
    }

    private static ContentValues createPreviewEntryInt(int id, int componentId, String key,
            int value) {
        ContentValues values = new ContentValues();
        values.put(PreviewColumns.THEME_ID, id);
        values.put(PreviewColumns.COMPONENT_ID, componentId);
        values.put(PreviewColumns.COL_KEY, key);
        values.put(PreviewColumns.COL_VALUE, value);

        return values;
    }

    private static ContentValues createPreviewEntryString(int id, String key, String value) {
        return createPreviewEntryString(id, 0, key, value);
    }

    private static ContentValues createPreviewEntryString(int id, int componentId, String key,
            String value) {
        ContentValues values = new ContentValues();
        values.put(PreviewColumns.THEME_ID, id);
        values.put(PreviewColumns.COMPONENT_ID, componentId);
        values.put(PreviewColumns.COL_KEY, key);
        values.put(PreviewColumns.COL_VALUE, value);

        return values;
    }

    public static void clearThemePreviewsDir(String path) {
        File directory = new File(path);
        FileUtils.deleteContents(directory);
        directory.delete();
    }

    private static void clearThemeFromPreviewDB(ContentResolver resolver, String pkgName) {
        String selection = ThemesColumns.PKG_NAME + "=?";
        String[] selectionArgs = new String[]{ String.valueOf(pkgName) };
        resolver.delete(PreviewColumns.CONTENT_URI, selection, selectionArgs);
    }

    private static Cursor queryTheme(Context context, String pkgName) {
        String selection = ThemesColumns.PKG_NAME + "=?";
        String[] selectionArgs = { pkgName };
        return context.getContentResolver().query(ThemesColumns.CONTENT_URI, null,
                selection, selectionArgs, null);
    }
}
