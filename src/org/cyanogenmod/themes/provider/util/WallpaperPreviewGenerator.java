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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ThemeUtils;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.ThemeConfig;
import android.graphics.Bitmap;

import org.cyanogenmod.themes.provider.R;

import java.io.File;
import java.io.IOException;

public class WallpaperPreviewGenerator {
    private static final String WALLPAPER_ASSET_PATH = "wallpapers";
    private static final String LOCKSCREEN_ASSET_PATH = "lockscreen";
    private Context mContext;
    private int mPreviewSize;
    private int mThumbnailSize;

    public WallpaperPreviewGenerator(Context context) {
        mContext = context;
        final Resources res = context.getResources();
        mPreviewSize = res.getDimensionPixelSize(R.dimen.wallpaper_preview_size);
        mThumbnailSize = res.getDimensionPixelSize(R.dimen.wallpaper_thumbnail_size);
    }

    public WallpaperItems generateWallpaperPreviews(PackageInfo themeInfo)
            throws NameNotFoundException, IOException {
        WallpaperItems items = new WallpaperItems();
        if (themeInfo == null) {
            Resources res = mContext.getPackageManager().getThemedResourcesForApplication("android",
                    ThemeConfig.SYSTEM_DEFAULT);
            items.wpPreview = items.lsPreview = BitmapUtils.decodeResource(res,
                    com.android.internal.R.drawable.default_wallpaper, mPreviewSize, mPreviewSize);
        } else {
            final Context themeContext = mContext.createPackageContext(themeInfo.packageName, 0);
            final AssetManager assets = themeContext.getAssets();
            String path = ThemeUtils.getWallpaperPath(assets);
            if (path != null) {
                items.wpPreview = BitmapUtils.getBitmapFromAsset(themeContext, path,
                        mPreviewSize, mPreviewSize);
            }
            path = ThemeUtils.getLockscreenWallpaperPath(assets);
            if (path != null) {
                items.lsPreview = BitmapUtils.getBitmapFromAsset(themeContext, path,
                        mPreviewSize, mPreviewSize);
            }
        }
        if (items.wpPreview != null) {
            items.wpThumbnail = Bitmap.createScaledBitmap(items.wpPreview, mThumbnailSize,
                    mThumbnailSize, true);
        }
        if (items.lsPreview != null) {
            items.lsThumbnail = Bitmap.createScaledBitmap(items.lsPreview, mThumbnailSize,
                    mThumbnailSize, true);
        }
        return items;
    }

    public class WallpaperItems {
        // Wallpaper items
        public Bitmap wpThumbnail;
        public Bitmap wpPreview;

        // Lockscreen wallpaper items
        public Bitmap lsThumbnail;
        public Bitmap lsPreview;
    }
}
