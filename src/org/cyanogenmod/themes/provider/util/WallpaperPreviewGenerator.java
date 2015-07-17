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

import android.provider.ThemesContract;
import android.text.TextUtils;
import org.cyanogenmod.themes.provider.R;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

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
        WallpaperItem item = null;
        Bitmap preview = null;
        String baseDir = mContext.getFilesDir().getAbsolutePath();
        String pkgName;
        if (themeInfo == null) {
            pkgName = ThemeConfig.SYSTEM_DEFAULT;
            Resources res = mContext.getPackageManager().getThemedResourcesForApplication("android",
                    ThemeConfig.SYSTEM_DEFAULT);
            if (preview != null) {
                preview.recycle();
            }
            preview = BitmapUtils.decodeResource(res,
                    com.android.internal.R.drawable.default_wallpaper, mPreviewSize, mPreviewSize);
            item = createWallpaperItems(0, baseDir, null, pkgName, preview);
            if (item != null) {
                items.wallpapers.add(item);
                items.lockscreen = item;
            }
        } else {
            final Context themeContext = mContext.createPackageContext(themeInfo.packageName, 0);
            final AssetManager assets = themeContext.getAssets();
            pkgName = themeInfo.packageName;
            // Get all wallpapers
            List<String> paths = ThemeUtils.getWallpaperPathList(assets);
            int id = 0;
            for (String path : paths) {
                if (!TextUtils.isEmpty(path)) {
                    if (preview != null) {
                        preview.recycle();
                    }
                    preview = BitmapUtils.getBitmapFromAsset(themeContext, path,
                            mPreviewSize, mPreviewSize);
                    item = createWallpaperItems(id, baseDir, path, pkgName, preview);
                    if (item != null) {
                        items.wallpapers.add(item);
                        id++;
                    }
                }
            }
            // Get the lockscreen
            String path = ThemeUtils.getLockscreenWallpaperPath(assets);
            if (!TextUtils.isEmpty(path)) {
                if (preview != null) {
                    preview.recycle();
                }
                preview = BitmapUtils.getBitmapFromAsset(themeContext, path,
                        mPreviewSize, mPreviewSize);
                items.lockscreen = createWallpaperItems(0, baseDir, path, pkgName, preview);
            }
        }
        return items;
    }

    private WallpaperItem createWallpaperItems(int id, String baseDir, String assetPath,
            String pkgName, Bitmap preview) {
        if (TextUtils.isEmpty(assetPath) && preview == null) {
            return null;
        }

        String componentID =  String.format("%03d", id);
        WallpaperItem item = new WallpaperItem();

        item.assetPath = assetPath;
        if (preview != null) {
            String fileName = ThemesContract.PreviewColumns.WALLPAPER_PREVIEW + componentID;
            item.previewPath = PreviewUtils.compressAndSaveJpg(preview, baseDir, pkgName, fileName);
            Bitmap thumbnail = Bitmap.createScaledBitmap(preview, mThumbnailSize, mThumbnailSize,
                    true);
            fileName = ThemesContract.PreviewColumns.WALLPAPER_THUMBNAIL + componentID;
            item.thumbnailPath = PreviewUtils.compressAndSavePng(thumbnail, baseDir, pkgName,
                    fileName);
        }
        return item;
    }

    public class WallpaperItem {
        public String assetPath;
        public String previewPath;
        public String thumbnailPath;
    }

    public class WallpaperItems {
        // Wallpaper items
        public List<WallpaperItem> wallpapers;

        // Lockscreen wallpaper item
        public WallpaperItem lockscreen;

        public WallpaperItems() {
            wallpapers = new LinkedList<WallpaperItem>();
            lockscreen = null;
        }
    }
}
