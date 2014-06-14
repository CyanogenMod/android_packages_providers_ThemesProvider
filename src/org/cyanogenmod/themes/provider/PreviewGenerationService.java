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
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.ThemeConfig;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.os.Bundle;
import android.provider.ThemesContract.ThemesColumns;
import android.provider.ThemesContract.PreviewColumns;
import android.util.Log;
import org.cyanogenmod.themes.provider.util.BootAnimationPreviewGenerator;
import org.cyanogenmod.themes.provider.util.IconPreviewGenerator;
import org.cyanogenmod.themes.provider.util.IconPreviewGenerator.IconItems;
import org.cyanogenmod.themes.provider.util.StylePreviewGenerator;
import org.cyanogenmod.themes.provider.util.StylePreviewGenerator.StyleItems;
import org.cyanogenmod.themes.provider.util.SystemUiPreviewGenerator;
import org.cyanogenmod.themes.provider.util.SystemUiPreviewGenerator.SystemUiItems;
import org.cyanogenmod.themes.provider.util.WallpaperPreviewGenerator;
import org.cyanogenmod.themes.provider.util.WallpaperPreviewGenerator.WallpaperItems;

import java.io.ByteArrayOutputStream;

/*
 * Copies images from the theme APK to the local provider's cache
 */
public class PreviewGenerationService extends IntentService {
    public static final String ACTION_INSERT = "org.cyanogenmod.themes.provider.action.insert";
    public static final String ACTION_UPDATE = "org.cyanogenmod.themes.provider.action.update";
    public static final String EXTRA_PKG_NAME = "extra_pkg_name";
    public static final String EXTRA_HAS_SYSTEMUI = "extra_has_system_ui";
    public static final String EXTRA_HAS_ICONS = "extra_has_icons";
    public static final String EXTRA_HAS_WALLPAPER = "extra_has_wallpaper";
    public static final String EXTRA_HAS_STYLES = "extra_has_styles";
    public static final String EXTRA_HAS_BOOTANIMATION = "extra_has_bootanimation";

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
        boolean hasSystemUi = extras.getBoolean(EXTRA_HAS_SYSTEMUI, false);
        boolean hasIcons = extras.getBoolean(EXTRA_HAS_ICONS, false);
        boolean hasWallpaper = extras.getBoolean(EXTRA_HAS_WALLPAPER, false);
        boolean hasStyles = extras.getBoolean(EXTRA_HAS_STYLES, false);
        boolean hasBootanimation = extras.getBoolean(EXTRA_HAS_BOOTANIMATION, false);
        boolean isSystemTheme = ThemeConfig.SYSTEM_DEFAULT.equals(pkgName);
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
                insertPreviewItemsIntoDb(pkgName, items, iconItems, wallpaperItems, styleItems,
                        bootAnim);
            }
        }
    }

    private void insertPreviewItemsIntoDb(String pkgName, SystemUiItems items, IconItems icons,
                                          WallpaperItems wallpaperItems, StyleItems styleItems,
                                          Bitmap bootAnim) {
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

            ContentValues values = new ContentValues();
            values.put(PreviewColumns.THEME_ID, id);
            if (items != null) {
                values.put(PreviewColumns.STATUSBAR_BACKGROUND,
                        getBitmapBlobPng(items.statusbarBackground));
                values.put(PreviewColumns.STATUSBAR_BLUETOOTH_ICON,
                        getBitmapBlobPng(items.bluetoothIcon));
                values.put(PreviewColumns.STATUSBAR_WIFI_ICON,
                        getBitmapBlobPng(items.wifiIcon));
                values.put(PreviewColumns.STATUSBAR_SIGNAL_ICON,
                        getBitmapBlobPng(items.signalIcon));
                values.put(PreviewColumns.STATUSBAR_BATTERY_PORTRAIT,
                        getBitmapBlobPng(items.batteryPortrait));
                values.put(PreviewColumns.STATUSBAR_BATTERY_LANDSCAPE,
                        getBitmapBlobPng(items.batteryLandscape));
                values.put(PreviewColumns.STATUSBAR_BATTERY_CIRCLE,
                        getBitmapBlobPng(items.batteryCircle));
                values.put(PreviewColumns.STATUSBAR_CLOCK_TEXT_COLOR, items.clockColor);
                values.put(PreviewColumns.STATUSBAR_WIFI_COMBO_MARGIN_END, items.wifiMarginEnd);
                values.put(PreviewColumns.NAVBAR_BACKGROUND,
                        getBitmapBlobPng(items.navbarBackground));
                values.put(PreviewColumns.NAVBAR_BACK_BUTTON,
                        getBitmapBlobPng(items.navbarBack));
                values.put(PreviewColumns.NAVBAR_HOME_BUTTON,
                        getBitmapBlobPng(items.navbarHome));
                values.put(PreviewColumns.NAVBAR_RECENT_BUTTON,
                        getBitmapBlobPng(items.navbarRecent));
            }
            if (icons != null) {
                values.put(PreviewColumns.ICON_PREVIEW_1, getBitmapBlobPng(icons.icon1));
                values.put(PreviewColumns.ICON_PREVIEW_2, getBitmapBlobPng(icons.icon2));
                values.put(PreviewColumns.ICON_PREVIEW_3, getBitmapBlobPng(icons.icon3));
            }
            if (wallpaperItems != null) {
                values.put(PreviewColumns.WALLPAPER_PREVIEW,
                        getBitmapBlobJpg(wallpaperItems.wpPreview));
                values.put(PreviewColumns.WALLPAPER_THUMBNAIL,
                        getBitmapBlobPng(wallpaperItems.wpThumbnail));
                values.put(PreviewColumns.LOCK_WALLPAPER_PREVIEW,
                        getBitmapBlobJpg(wallpaperItems.lsPreview));
                values.put(PreviewColumns.LOCK_WALLPAPER_THUMBNAIL,
                        getBitmapBlobPng(wallpaperItems.lsThumbnail));
            }
            if (styleItems != null) {
                values.put(PreviewColumns.STYLE_THUMBNAIL, getBitmapBlobPng(styleItems.thumbnail));
                values.put(PreviewColumns.STYLE_PREVIEW, getBitmapBlobPng(styleItems.preview));
            }
            if (bootAnim != null) {
                values.put(PreviewColumns.BOOTANIMATION_THUMBNAIL, getBitmapBlobPng(bootAnim));
            }

            selection = PreviewColumns.THEME_ID + "=?";
            selectionArgs = new String[] { String.valueOf(id) };
            // Try an update first, if that returns 0 then we need to insert these values
            if (resolver.update(
                    PreviewColumns.CONTENT_URI, values, selection, selectionArgs) == 0) {
                resolver.insert(PreviewColumns.CONTENT_URI, values);
            }
        }
    }

    private static byte[] getBitmapBlobPng(Bitmap bmp) {
        return getBitmapBlob(bmp, CompressFormat.PNG, 100);
    }

    private static byte[] getBitmapBlobJpg(Bitmap bmp) {
        return getBitmapBlob(bmp, CompressFormat.JPEG, 80);
    }

    private static byte[] getBitmapBlob(Bitmap bmp, CompressFormat format, int quality) {
        if (bmp == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(format, quality, out);
        return out.toByteArray();
    }
}
