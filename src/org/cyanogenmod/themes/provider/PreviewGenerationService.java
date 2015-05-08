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
import android.os.FileUtils;
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
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/*
 * Copies images from the theme APK to the local provider's cache
 */
public class PreviewGenerationService extends IntentService {
    public static final String ACTION_INSERT = "org.cyanogenmod.themes.provider.action.insert";
    public static final String ACTION_UPDATE = "org.cyanogenmod.themes.provider.action.update";
    public static final String EXTRA_PKG_NAME = "extra_pkg_name";

    public static final String PREVIEWS_DIR = "previews";

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
            String filesDir = this.getFilesDir().getAbsolutePath();
            String themePreviewsDir =
                    filesDir + File.separator + PREVIEWS_DIR + File.separator + pkgName;
            String path = null;
            clearThemePreviewsDir(themePreviewsDir);

            if (items != null) {
                path = compressAndSavePng(items.statusbarBackground, filesDir, pkgName,
                        PreviewColumns.KEY_STATUSBAR_BACKGROUND);
                values = createPreviewEntryString(id, PreviewColumns.KEY_STATUSBAR_BACKGROUND,
                        path);
                themeValues.add(values);

                path = compressAndSavePng(items.bluetoothIcon, filesDir, pkgName,
                        PreviewColumns.KEY_STATUSBAR_BLUETOOTH_ICON);
                values = createPreviewEntryString(id, PreviewColumns.KEY_STATUSBAR_BLUETOOTH_ICON,
                        path);
                themeValues.add(values);

                path = compressAndSavePng(items.wifiIcon, filesDir, pkgName,
                        PreviewColumns.KEY_STATUSBAR_WIFI_ICON);
                values = createPreviewEntryString(id, PreviewColumns.KEY_STATUSBAR_WIFI_ICON, path);
                themeValues.add(values);

                path = compressAndSavePng(items.signalIcon, filesDir, pkgName,
                        PreviewColumns.KEY_STATUSBAR_SIGNAL_ICON);
                values = createPreviewEntryString(id, PreviewColumns.KEY_STATUSBAR_SIGNAL_ICON,
                        path);
                themeValues.add(values);

                path = compressAndSavePng(items.batteryPortrait, filesDir, pkgName,
                        PreviewColumns.KEY_STATUSBAR_BATTERY_PORTRAIT);
                values = createPreviewEntryString(id, PreviewColumns.KEY_STATUSBAR_BATTERY_PORTRAIT,
                        path);
                themeValues.add(values);

                path = compressAndSavePng(items.batteryLandscape, filesDir, pkgName,
                        PreviewColumns.KEY_STATUSBAR_BATTERY_LANDSCAPE);
                values = createPreviewEntryString(id,
                        PreviewColumns.KEY_STATUSBAR_BATTERY_LANDSCAPE, path);
                themeValues.add(values);

                path = compressAndSavePng(items.batteryCircle, filesDir, pkgName,
                        PreviewColumns.KEY_STATUSBAR_BATTERY_CIRCLE);
                values = createPreviewEntryString(id, PreviewColumns.KEY_STATUSBAR_BATTERY_CIRCLE,
                        path);
                themeValues.add(values);

                values = createPreviewEntryInt(id, PreviewColumns.KEY_STATUSBAR_CLOCK_TEXT_COLOR,
                        items.clockColor);
                themeValues.add(values);

                values = createPreviewEntryInt(id,
                        PreviewColumns.KEY_STATUSBAR_WIFI_COMBO_MARGIN_END, items.wifiMarginEnd);
                themeValues.add(values);

                path = compressAndSavePng(items.navbarBackground, filesDir, pkgName,
                        PreviewColumns.KEY_NAVBAR_BACKGROUND);
                values = createPreviewEntryString(id, PreviewColumns.KEY_NAVBAR_BACKGROUND, path);
                themeValues.add(values);

                path = compressAndSavePng(items.navbarBack, filesDir, pkgName,
                        PreviewColumns.KEY_NAVBAR_BACK_BUTTON);
                values = createPreviewEntryString(id, PreviewColumns.KEY_NAVBAR_BACK_BUTTON, path);
                themeValues.add(values);

                path = compressAndSavePng(items.navbarHome, filesDir, pkgName,
                        PreviewColumns.KEY_NAVBAR_HOME_BUTTON);
                values = createPreviewEntryString(id, PreviewColumns.KEY_NAVBAR_HOME_BUTTON, path);
                themeValues.add(values);

                path = compressAndSavePng(items.navbarRecent, filesDir, pkgName,
                        PreviewColumns.KEY_NAVBAR_RECENT_BUTTON);
                values = createPreviewEntryString(id, PreviewColumns.KEY_NAVBAR_RECENT_BUTTON,
                        path);
                themeValues.add(values);
            }
            if (icons != null) {
                path = compressAndSavePng(icons.icon1, filesDir, pkgName,
                        PreviewColumns.KEY_ICON_PREVIEW_1);
                values = createPreviewEntryString(id, PreviewColumns.KEY_ICON_PREVIEW_1, path);
                themeValues.add(values);

                path = compressAndSavePng(icons.icon2, filesDir, pkgName,
                        PreviewColumns.KEY_ICON_PREVIEW_2);
                values = createPreviewEntryString(id, PreviewColumns.KEY_ICON_PREVIEW_2, path);
                themeValues.add(values);

                path = compressAndSavePng(icons.icon3, filesDir, pkgName,
                        PreviewColumns.KEY_ICON_PREVIEW_3);
                values = createPreviewEntryString(id, PreviewColumns.KEY_ICON_PREVIEW_3, path);
                themeValues.add(values);
            }
            if (wallpaperItems != null) {
                path = compressAndSaveJpg(wallpaperItems.wpPreview, filesDir, pkgName,
                        PreviewColumns.KEY_WALLPAPER_PREVIEW);
                values = createPreviewEntryString(id, PreviewColumns.KEY_WALLPAPER_PREVIEW, path);
                themeValues.add(values);

                path = compressAndSavePng(wallpaperItems.wpThumbnail, filesDir, pkgName,
                        PreviewColumns.KEY_WALLPAPER_THUMBNAIL);
                values = createPreviewEntryString(id, PreviewColumns.KEY_WALLPAPER_THUMBNAIL, path);
                themeValues.add(values);

                path = compressAndSaveJpg(wallpaperItems.lsPreview, filesDir, pkgName,
                        PreviewColumns.KEY_LOCK_WALLPAPER_PREVIEW);
                values = createPreviewEntryString(id, PreviewColumns.KEY_LOCK_WALLPAPER_PREVIEW,
                        path);
                themeValues.add(values);

                path = compressAndSavePng(wallpaperItems.lsThumbnail, filesDir, pkgName,
                        PreviewColumns.KEY_LOCK_WALLPAPER_THUMBNAIL);
                values = createPreviewEntryString(id, PreviewColumns.KEY_LOCK_WALLPAPER_THUMBNAIL,
                        path);
                themeValues.add(values);
            }
            if (styleItems != null) {
                path = compressAndSavePng(styleItems.thumbnail, filesDir, pkgName,
                        PreviewColumns.KEY_STYLE_THUMBNAIL);
                values = createPreviewEntryString(id, PreviewColumns.KEY_STYLE_THUMBNAIL, path);
                themeValues.add(values);

                path = compressAndSavePng(styleItems.preview, filesDir, pkgName,
                        PreviewColumns.KEY_STYLE_PREVIEW);
                values = createPreviewEntryString(id, PreviewColumns.KEY_STYLE_PREVIEW, path);
                themeValues.add(values);
            }
            if (bootAnim != null) {
                path = compressAndSavePng(bootAnim, filesDir, pkgName,
                        PreviewColumns.KEY_BOOTANIMATION_THUMBNAIL);
                values = createPreviewEntryString(id, PreviewColumns.KEY_BOOTANIMATION_THUMBNAIL,
                        path);
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

    private static ContentValues createPreviewEntryInt(int id, String key, int value) {
        ContentValues values = new ContentValues();
        values.put(PreviewColumns.THEME_ID, id);
        values.put(PreviewColumns.COL_KEY, key);
        values.put(PreviewColumns.COL_VALUE, value);

        return values;
    }

    private static ContentValues createPreviewEntryString(int id, String key, String value) {
        ContentValues values = new ContentValues();
        values.put(PreviewColumns.THEME_ID, id);
        values.put(PreviewColumns.COL_KEY, key);
        values.put(PreviewColumns.COL_VALUE, value);

        return values;
    }

    private static String compressAndSavePng(Bitmap bmp, String baseDir, String pkgName,
                                               String fileName) {
        byte[] image = getBitmapBlobPng(bmp);
        return saveCompressedImage(image, baseDir, pkgName, fileName);
    }

    private static String compressAndSaveJpg(Bitmap bmp, String baseDir, String pkgName,
                                             String fileName) {
        byte[] image = getBitmapBlobJpg(bmp);
        return saveCompressedImage(image, baseDir, pkgName, fileName);
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

    private static String saveCompressedImage(byte[] image, String baseDir, String pkgName,
                                              String fileName) {
        // Create relevant directories
        String previewsDir = baseDir + File.separator + PREVIEWS_DIR;
        String pkgDir = previewsDir + File.separator + pkgName;
        String filePath = pkgDir + File.separator + fileName;
        createDirIfNotExists(previewsDir);
        createDirIfNotExists(pkgDir);

        // Save blob
        FileOutputStream outputStream;
        final File pkgPreviewDir = new File(pkgDir);
        try {
            File outFile = new File(pkgPreviewDir, fileName);
            outputStream = new FileOutputStream(outFile);
            outputStream.write(image);
            outputStream.close();
            FileUtils.setPermissions(outFile,
                    FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IROTH,
                    -1, -1);
        } catch (Exception e) {
            Log.w(TAG, "Unable to save preview " + pkgName + File.separator + fileName, e);
            filePath = null;
        }

        return filePath;
    }

    public static void clearThemePreviewsDir(String path) {
        File directory = new File(path);
        FileUtils.deleteContents(directory);
        directory.delete();
    }

    private static Cursor queryTheme(Context context, String pkgName) {
        String selection = ThemesColumns.PKG_NAME + "=?";
        String[] selectionArgs = { pkgName };
        return context.getContentResolver().query(ThemesColumns.CONTENT_URI, null,
                selection, selectionArgs, null);
    }

    private static boolean dirExists(String dirPath) {
        final File dir = new File(dirPath);
        return dir.exists() && dir.isDirectory();
    }

    private static void createDirIfNotExists(String dirPath) {
        if (!dirExists(dirPath)) {
            File dir = new File(dirPath);
            if (dir.mkdir()) {
                FileUtils.setPermissions(dir, FileUtils.S_IRWXU |
                        FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH, -1, -1);
            }
        }
    }
}
