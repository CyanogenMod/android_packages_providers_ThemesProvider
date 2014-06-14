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
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BootAnimationPreviewGenerator {
    public static final String SYSTEM_BOOT_ANI_PATH = "/system/media/bootanimation.zip";
    public static final String THEME_BOOT_ANI_PATH = "bootanimation/bootanimation.zip";

    private Context mContext;

    public BootAnimationPreviewGenerator(Context context) {
        mContext = context;
    }

    public Bitmap generateBootAnimationPreview(String pkgName)
            throws IOException, PackageManager.NameNotFoundException {
        ZipInputStream zis;
        String previewName;
        if (ThemeConfig.SYSTEM_DEFAULT.equals(pkgName)) {
            previewName = getPreviewFrameEntryName(new FileInputStream(SYSTEM_BOOT_ANI_PATH));
            zis = new ZipInputStream(new FileInputStream(SYSTEM_BOOT_ANI_PATH));
        } else {
            final Context themeCtx = mContext.createPackageContext(pkgName, 0);
            previewName = getPreviewFrameEntryName(themeCtx.getAssets().open(THEME_BOOT_ANI_PATH));
            zis = new ZipInputStream(themeCtx.getAssets().open(THEME_BOOT_ANI_PATH));
        }
        ZipEntry ze;
        Bitmap bmp = null;
        while ((ze = zis.getNextEntry()) != null) {
            final String entryName = ze.getName();
            if (entryName.equals(previewName)) {
                bmp = BitmapFactory.decodeStream(zis);
                break;
            }
        }
        zis.close();

        View v = View.inflate(mContext, R.layout.bootanimation_preview, null);
        ImageView iv = (ImageView) v.findViewById(R.id.preview);
        iv.setImageBitmap(bmp);
        return LayoutRenderUtils.renderViewToBitmap(v);
    }

    private String getPreviewFrameEntryName(InputStream is) throws IOException {
        ZipInputStream zis = (is instanceof ZipInputStream) ? (ZipInputStream) is
                : new ZipInputStream(new BufferedInputStream(is));
        ZipEntry ze;
        String previewName = null;
        long max = 0;
        while ((ze = zis.getNextEntry()) != null) {
            final String entryName = ze.getName();
            if (entryName.contains("/")
                    && (entryName.endsWith(".png") || entryName.endsWith(".jpg"))) {
                if(ze.getSize() > max) {
                    previewName = entryName;
                    max = ze.getSize();
                }
            }
        }
        zis.close();

        return previewName;
    }
}
