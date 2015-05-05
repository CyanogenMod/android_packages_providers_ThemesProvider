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

            List<ContentValues> themeValues = new ArrayList<ContentValues>();
            ContentValues values = null;
            if (items != null) {
                values = createPreviewEntryBlob(id, PreviewColumns.KEY_STATUSBAR_BACKGROUND,
                        getBitmapBlobPng(items.statusbarBackground));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_STATUSBAR_BLUETOOTH_ICON,
                        getBitmapBlobPng(items.bluetoothIcon));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_STATUSBAR_WIFI_ICON,
                        getBitmapBlobPng(items.wifiIcon));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_STATUSBAR_SIGNAL_ICON,
                        getBitmapBlobPng(items.signalIcon));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_STATUSBAR_BATTERY_PORTRAIT,
                        getBitmapBlobPng(items.batteryPortrait));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_STATUSBAR_BATTERY_LANDSCAPE,
                        getBitmapBlobPng(items.batteryLandscape));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_STATUSBAR_BATTERY_CIRCLE,
                        getBitmapBlobPng(items.batteryCircle));
                themeValues.add(values);

                values = createPreviewEntryInt(id, PreviewColumns.KEY_STATUSBAR_CLOCK_TEXT_COLOR,
                        items.clockColor);
                themeValues.add(values);

                values = createPreviewEntryInt(id,
                        PreviewColumns.KEY_STATUSBAR_WIFI_COMBO_MARGIN_END, items.wifiMarginEnd);
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_NAVBAR_BACKGROUND,
                        getBitmapBlobPng(items.navbarBackground));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_NAVBAR_BACK_BUTTON,
                        getBitmapBlobPng(items.navbarBack));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_NAVBAR_HOME_BUTTON,
                        getBitmapBlobPng(items.navbarHome));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_NAVBAR_RECENT_BUTTON,
                        getBitmapBlobPng(items.navbarRecent));
                themeValues.add(values);
            }
            if (icons != null) {
                values = createPreviewEntryBlob(id, PreviewColumns.KEY_ICON_PREVIEW_1,
                        getBitmapBlobPng(icons.icon1));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_ICON_PREVIEW_2,
                        getBitmapBlobPng(icons.icon2));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_ICON_PREVIEW_3,
                        getBitmapBlobPng(icons.icon3));
                themeValues.add(values);
            }
            if (wallpaperItems != null) {
                values = createPreviewEntryBlob(id, PreviewColumns.KEY_WALLPAPER_PREVIEW,
                        getBitmapBlobJpg(wallpaperItems.wpPreview));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_WALLPAPER_THUMBNAIL,
                        getBitmapBlobPng(wallpaperItems.wpThumbnail));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_LOCK_WALLPAPER_PREVIEW,
                        getBitmapBlobJpg(wallpaperItems.lsPreview));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_LOCK_WALLPAPER_THUMBNAIL,
                        getBitmapBlobPng(wallpaperItems.lsThumbnail));
                themeValues.add(values);
            }
            if (styleItems != null) {
                values = createPreviewEntryBlob(id, PreviewColumns.KEY_STYLE_THUMBNAIL,
                        getBitmapBlobPng(styleItems.thumbnail));
                themeValues.add(values);

                values = createPreviewEntryBlob(id, PreviewColumns.KEY_STYLE_PREVIEW,
                        getBitmapBlobPng(styleItems.preview));
                themeValues.add(values);
            }
            if (bootAnim != null) {
                values = createPreviewEntryBlob(id, PreviewColumns.KEY_BOOTANIMATION_THUMBNAIL,
                        getBitmapBlobPng(bootAnim));
                themeValues.add(values);
            }

            if (!themeValues.isEmpty()) {
                selection = PreviewColumns.THEME_ID + "=? AND " + PreviewColumns.COL_KEY + "=?";
                for (ContentValues contentValues : themeValues) {
                    selectionArgs = new String[]{String.valueOf(id),
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

    private static ContentValues createPreviewEntryBlob(int id, String key, byte[] value) {
        ContentValues values = new ContentValues();
        values.put(PreviewColumns.THEME_ID, id);
        values.put(PreviewColumns.COL_KEY, key);
        values.put(PreviewColumns.COL_VALUE, value);

        return values;
    }

    private static ContentValues createPreviewEntryInt(int id, String key, int value) {
        ContentValues values = new ContentValues();
        values.put(PreviewColumns.THEME_ID, id);
        values.put(PreviewColumns.COL_KEY, key);
        values.put(PreviewColumns.COL_VALUE, value);

        return values;
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

    private static Cursor queryTheme(Context context, String pkgName) {
        String selection = ThemesColumns.PKG_NAME + "=?";
        String[] selectionArgs = { pkgName };
        return context.getContentResolver().query(ThemesColumns.CONTENT_URI, null,
                selection, selectionArgs, null);
    }
}
