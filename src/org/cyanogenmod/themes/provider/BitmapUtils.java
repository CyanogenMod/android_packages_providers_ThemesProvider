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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class BitmapUtils {
    private static final String TAG = "BitmapUtils";
    private static final int BITMAP_LOAD_BACKOUT_ATTEMPTS = 5;

    /**
     * Returns the bitmap from the given uri loaded using the given options.
     * Returns null on failure.
     */
    public static Bitmap loadBitmap(Context context, InputStream is, BitmapFactory.Options o) {
        try {
            return BitmapFactory.decodeStream(is, null, o);
        } finally {
            closeSilently(is);
        }
    }

    /**
     * Loads a bitmap that has been downsampled using sampleSize from a given url.
     */
    public static Bitmap loadDownsampledBitmap(Context context, InputStream is, int sampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        options.inSampleSize = sampleSize;
        return loadBitmap(context, is, options);
    }

    /**
     * Loads a bitmap that is downsampled by at least the input sample size. In
     * low-memory situations, the bitmap may be downsampled further.
     */
    public static Bitmap loadBitmapWithBackouts(Context context, InputStream is, int sampleSize) {
        boolean noBitmap = true;
        int num_tries = 0;
        if (sampleSize <= 0) {
            sampleSize = 1;
        }
        Bitmap bmap = null;
        while (noBitmap) {
            try {
                // Try to decode, downsample if low-memory.
                bmap = loadDownsampledBitmap(context, is, sampleSize);
                noBitmap = false;
            } catch (java.lang.OutOfMemoryError e) {
                // Try with more downsampling before failing for good.
                if (++num_tries >= BITMAP_LOAD_BACKOUT_ATTEMPTS) {
                    throw e;
                }
                bmap = null;
                System.gc();
                sampleSize *= 2;
            }
        }
        return bmap;
    }

    public static void closeSilently(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException t) {
            Log.w(TAG, "close fail ", t);
        }
    }
}
