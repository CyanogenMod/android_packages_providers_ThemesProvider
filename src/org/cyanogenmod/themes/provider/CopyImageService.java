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

    public static final String EXTRA_PKG_NAME = "extra_pkg_name";

    private static final String TAG = CopyImageService.class.getName();
    private static final String IMAGES_PATH =
            "/data/org.cyanogenmod.themes.provider/files/images/";
    private static final String WALLPAPER_PATH =
            "/data/org.cyanogenmod.themes.provider/files/wallpapers/";

    public CopyImageService() {
        super(CopyImageService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        if (intent.getExtras() == null)
            return;

        String pkgName = intent.getExtras().getString(EXTRA_PKG_NAME);

        if (pkgName != null) {
            generate(this, pkgName);
        }

        String homescreen = Environment.getDataDirectory().getPath()
                + IMAGES_PATH + pkgName
                + ".homescreen.png";
        String lockscreen = Environment.getDataDirectory().getPath()
                + IMAGES_PATH + pkgName
                + ".lockscreen.png";
        String stylePreview = Environment.getDataDirectory().getPath()
                + IMAGES_PATH + pkgName
                + ".stylepreview.jpg";
        String wallpaper = ContentResolver.SCHEME_FILE + "://" + Environment.getDataDirectory().getPath()
                + WALLPAPER_PATH + pkgName
                + ".wallpaper1.jpg";
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

    public static void generate(Context context, String pkgName) {
        // Presently this is just mocked up. IE We expect the theme APK to
        // provide the bitmap.
        Context themeContext = null;
        try {
            themeContext = context.createPackageContext(pkgName,
                    Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        // This is for testing only. We copy some assets from APK and put into
        // internal storage
        AssetManager assetManager = themeContext.getAssets();
        try {
            InputStream homescreen = assetManager
                    .open("images/icons_wallpaper_straight.png");
            InputStream lockscreen = assetManager
                    .open("images/lockscreen_portrait.png");

            File dataDir = context.getFilesDir(); // Environment.getDataDirectory();
            File imgDir = new File(dataDir, "images");
            File wpDir = new File(dataDir, "wallpapers");
            imgDir.mkdir();
            wpDir.mkdir();

            File homescreenOut = new File(imgDir, pkgName + ".homescreen.png");
            File lockscreenOut = new File(imgDir, pkgName + ".lockscreen.png");

            FileOutputStream out = new FileOutputStream(homescreenOut);
            byte[] buffer = new byte[4096];
            int count = 0;
            while ((count = homescreen.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            out.close();

            out = new FileOutputStream(lockscreenOut);
            while ((count = lockscreen.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            out.close();
        } catch (IOException e) {
            Log.e(TAG, "ThemesOpenHelper could not copy test image data");
        }

        //Copy Style preview
        try {
            InputStream stylepreview = assetManager
                    .open("images/style.jpg");
            File dataDir = context.getFilesDir();
            File imgDir = new File(dataDir, "images");
            imgDir.mkdir();

            File styleOut = new File(imgDir, pkgName + ".stylepreview.jpg");

            byte[] buffer = new byte[4096];
            int count = 0;
            FileOutputStream out = new FileOutputStream(styleOut);
            while ((count = stylepreview.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            out.close();
        } catch (IOException e) {
            Log.e(TAG, "ThemesOpenHelper could not copy style image data");
        }
    }
}
