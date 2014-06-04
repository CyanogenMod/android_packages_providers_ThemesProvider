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
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.ThemesContract.ThemesColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/*
 * Copies images from the theme APK to the local provider's cache
 */
public class CopyImageService extends IntentService {
    public static final String ACTION_INSERT = "org.cyanogenmod.themes.provider.action.insert";
    public static final String ACTION_DELETE = "org.cyanogenmod.themes.provider.action.delete";
    public static final String EXTRA_PKG_NAME = "extra_pkg_name";

    private static final String TAG = CopyImageService.class.getName();
    private static final String IMAGES_PATH =
            "/data/org.cyanogenmod.themes.provider/files/images/";
    private static final String WALLPAPER_PATH =
            "/data/org.cyanogenmod.themes.provider/files/wallpapers/";

    private static final String WALLPAPER_PREVIEW = "images/wallpaper_preview";
    private static final String LOCKSCREEN_PREVIEW = "images/lockscreen_preview";
    private static final String STYLES_PREVIEW = "images/styles_preview";

    private static final String EXT_JPG = ".jpg";
    private static final String EXT_PNG = ".png";

    public CopyImageService() {
        super(CopyImageService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getExtras() == null || intent.getExtras().getString(EXTRA_PKG_NAME) == null) {
            Log.e(TAG, "No package name or extras provided");
            return;
        }

        String pkgName = intent.getExtras().getString(EXTRA_PKG_NAME);
        if (ACTION_INSERT.equals(intent.getAction())) {
            createPreviewImages(this, pkgName);
            insertPreviewValuesIntoDb(pkgName);
        } else if (ACTION_DELETE.equals(intent.getAction())) {
            deletePreviewImages(pkgName);
        }
    }

    public static void createPreviewImages(Context context, String pkgName) {
        // Presently this is just mocked up. IE We expect the theme APK to
        // provide the bitmap.
        Context themeContext = null;
        try {
            themeContext = context.createPackageContext(pkgName,
                    Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Error getting themeContext", e);
            return;
        }

        // This is for testing only. We copy some assets from APK and put into
        // internal storage
        AssetManager assetManager = themeContext.getAssets();
        try {
            InputStream homescreen = getPreviewAsset(assetManager, WALLPAPER_PREVIEW);

            InputStream lockscreen = getPreviewAsset(assetManager, LOCKSCREEN_PREVIEW);

            File dataDir = context.getFilesDir(); // Environment.getDataDirectory();
            File imgDir = new File(dataDir, "images");
            File wpDir = new File(dataDir, "wallpapers");
            imgDir.mkdir();
            wpDir.mkdir();

            File homescreenOut = new File(imgDir, pkgName + ".homescreen.jpg");
            File lockscreenOut = new File(imgDir, pkgName + ".lockscreen.jpg");

            FileOutputStream out = new FileOutputStream(homescreenOut);
            Bitmap bmp = BitmapUtils.loadBitmapWithBackouts(context, homescreen, 1);
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.close();

            out = new FileOutputStream(lockscreenOut);
            bmp = BitmapUtils.loadBitmapWithBackouts(context, lockscreen, 1);
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.close();
        } catch (IOException e) {
            Log.e(TAG, "ThemesOpenHelper could not copy preview image");
        }

        //Copy Style preview
        try {
            InputStream stylepreview = getPreviewAsset(assetManager, STYLES_PREVIEW);
            File dataDir = context.getFilesDir();
            File imgDir = new File(dataDir, "images");
            imgDir.mkdir();

            File styleOut = new File(imgDir, pkgName + ".stylepreview.jpg");

            FileOutputStream out = new FileOutputStream(styleOut);
            Bitmap bmp = BitmapUtils.loadBitmapWithBackouts(context, stylepreview, 1);
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.close();
        } catch (IOException e) {
            Log.e(TAG, "ThemesOpenHelper could not copy style image data");
        }
    }

    private void insertPreviewValuesIntoDb(String pkgName) {
        String homescreen = getHomeScreenPreviewPath(pkgName);
        String lockscreen = getLockScreenPreviewPath(pkgName);
        String stylePreview = getStylesPreviewPath(pkgName);
        String wallpaper = getWallpaperPreviewPath(pkgName);

        Uri hsUri = Uri.parse(homescreen);
        Uri lsUri = Uri.parse(lockscreen);
        Uri wpUri = Uri.parse(wallpaper);
        Uri styleUri = Uri.parse(stylePreview);

        String where = ThemesColumns.PKG_NAME + "=?";
        String[] selectionArgs = { pkgName };

        ContentValues values = new ContentValues();
        values.put(ThemesColumns.HOMESCREEN_URI, hsUri.toString());
        values.put(ThemesColumns.LOCKSCREEN_URI, lsUri.toString());
        values.put(ThemesColumns.STYLE_URI, styleUri.toString());
        values.put(ThemesColumns.WALLPAPER_URI, "file:///android_asset/wallpapers/wallpaper1.jpg");

        getContentResolver().update(ThemesColumns.CONTENT_URI, values,
                where, selectionArgs);
    }

    private void deletePreviewImages(String pkgName) {
        File home = new File(getHomeScreenPreviewPath(pkgName));
        home.delete();

        File lockscreen = new File(getLockScreenPreviewPath(pkgName));
        lockscreen.delete();

        File style = new File(getStylesPreviewPath(pkgName));
        style.delete();

        File wallpaper = new File(getWallpaperPreviewPath(pkgName));
        wallpaper.delete();
    }

    private static String getHomeScreenPreviewPath(String pkgName) {
        return Environment.getDataDirectory().getPath()
                + IMAGES_PATH + pkgName
                + ".homescreen.jpg";
    }

    private static String getLockScreenPreviewPath(String pkgName) {
        return Environment.getDataDirectory().getPath()
                + IMAGES_PATH + pkgName
                + ".lockscreen.jpg";
    }

    private static String getStylesPreviewPath(String pkgName) {
        return Environment.getDataDirectory().getPath()
                + IMAGES_PATH + pkgName
                + ".stylepreview.jpg";
    }

    private static String getWallpaperPreviewPath(String pkgName) {
        return ContentResolver.SCHEME_FILE + "://"
                + Environment.getDataDirectory().getPath()
                + WALLPAPER_PATH + pkgName
                + ".wallpaper1.jpg";
    }

    private static InputStream getPreviewAsset(AssetManager am, String preview) throws IOException {
        InputStream is = null;
        try {
            is = am.open(preview + EXT_JPG);
        } catch (IOException e) {
            // we'll try and fallback to PNG
        }
        if (is == null) is = am.open(preview + EXT_PNG);

        return is;
    }
}
