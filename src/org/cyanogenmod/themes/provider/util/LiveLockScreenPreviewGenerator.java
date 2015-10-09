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
package org.cyanogenmod.themes.provider.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ThemeConfig;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import org.cyanogenmod.themes.provider.R;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LiveLockScreenPreviewGenerator {
    private static final String LIVE_LOCK_SCREEN_PREVIEW_PATH = "live-lockscreen/preview";
    private static final String PNG_EXT = ".png";
    private static final String JPG_EXT = ".jpg";
    private static final String JPEG_EXT = ".jpeg";

    private Context mContext;

    public LiveLockScreenPreviewGenerator(Context context) {
        mContext = context;
    }

    public LiveLockScreenItems generateLiveLockScreenPreview(String pkgName)
            throws IOException, PackageManager.NameNotFoundException {
        final Context themeCtx = mContext.createPackageContext(pkgName, 0);
        final InputStream is = getPreviewInputStream(themeCtx);
        Bitmap bmp = null;
        if (is != null) {
            bmp = BitmapFactory.decodeStream(is);
        }

        LiveLockScreenItems items = null;
        if (bmp != null) {
            items = new LiveLockScreenItems();
            items.preview = bmp;
            items.thumbnail = Bitmap.createScaledBitmap(bmp, bmp.getWidth() / 4,
                    bmp.getHeight() / 4, true);
        }

        return items;
    }

    private InputStream getPreviewInputStream(Context themeContext) throws IOException {
        InputStream is = null;
        try {
            is = themeContext.getAssets().open(LIVE_LOCK_SCREEN_PREVIEW_PATH + PNG_EXT);
        } catch (FileNotFoundException e) {
        }
        if (is == null) {
            try {
                is = themeContext.getAssets().open(LIVE_LOCK_SCREEN_PREVIEW_PATH + JPG_EXT);
            } catch (FileNotFoundException e) {
            }
        }
        if (is == null) {
            try {
                is = themeContext.getAssets().open(LIVE_LOCK_SCREEN_PREVIEW_PATH + JPEG_EXT);
            } catch (FileNotFoundException e) {
            }
        }
        return is;
    }

    public class LiveLockScreenItems {
        public Bitmap thumbnail;
        public Bitmap preview;
    }
}
