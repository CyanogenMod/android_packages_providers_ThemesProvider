/*
 * Copyright (C) 2015 The CyanogenMod Project
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
import android.graphics.Bitmap;
import android.os.FileUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

public class PreviewUtils {
    private static final String TAG = PreviewUtils.class.getSimpleName();

    public static final String PREVIEWS_DIR = "previews";

    public static boolean dirExists(String dirPath) {
        final File dir = new File(dirPath);
        return dir.exists() && dir.isDirectory();
    }

    public static void createDirIfNotExists(String dirPath) {
        if (!dirExists(dirPath)) {
            File dir = new File(dirPath);
            if (dir.mkdir()) {
                FileUtils.setPermissions(dir, FileUtils.S_IRWXU |
                        FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH, -1, -1);
            }
        }
    }

    public static String getPreviewsDir(String baseDir) {
        return baseDir + File.separator + PREVIEWS_DIR;
    }

    public static void ensureCorrectPreviewPermissions(Context context) {
        File previewsDir = new File(getPreviewsDir(context.getFilesDir().getAbsolutePath()));
        if (previewsDir.exists()) {
            ensureCorrectPreviewPermissions(previewsDir);
        }
    }

    private static void ensureCorrectPreviewPermissions(File file) {
        int mode = 0;
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                ensureCorrectPreviewPermissions(f);
            }
            mode = FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH;
        } else {
            mode = FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IROTH;
        }
        FileUtils.setPermissions(file, mode, -1, -1);
    }

    private static String saveCompressedImage(byte[] image, String baseDir, String pkgName,
            String fileName) {
        if (image == null) return null;
        // Create relevant directories
        String previewsDir = PreviewUtils.getPreviewsDir(baseDir);
        String pkgDir = previewsDir + File.separator + pkgName;
        String filePath = pkgDir + File.separator + fileName;
        PreviewUtils.createDirIfNotExists(previewsDir);
        PreviewUtils.createDirIfNotExists(pkgDir);

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

    public static String compressAndSavePng(Bitmap bmp, String baseDir, String pkgName,
            String fileName) {
        byte[] image = BitmapUtils.getBitmapBlobPng(bmp);
        return saveCompressedImage(image, baseDir, pkgName, fileName);
    }

    public static String compressAndSaveJpg(Bitmap bmp, String baseDir, String pkgName,
            String fileName) {
        byte[] image = BitmapUtils.getBitmapBlobJpg(bmp);
        return saveCompressedImage(image, baseDir, pkgName, fileName);
    }
}
