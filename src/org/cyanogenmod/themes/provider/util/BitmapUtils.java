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
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;
import android.util.TypedValue;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class BitmapUtils {
    private static final String TAG = "BitmapUtils";

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

    public static Bitmap decodeFile(String path, int reqWidth, int reqHeight) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(path);
            return decodeStream(fis, reqWidth, reqHeight);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Unable to open resource in path" + path, e);
        } finally {
            closeSilently(fis);
        }
        return null;
    }

    public static Bitmap decodeStream(InputStream is, int reqWidth, int reqHeight) {
        BitmapFactory.Options opts = new BitmapFactory.Options();

        // Determine insample size
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, opts);
        opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight);

        // Decode the bitmap, regionally if necessary
        Bitmap bitmap = null;
        opts.inJustDecodeBounds = false;
        Rect rect = getCropRectIfNecessary(opts, reqWidth, reqHeight);
        try {
            if (rect != null) {
                BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is, false);
                bitmap = decoder.decodeRegion(rect, opts);
            } else {
                bitmap = BitmapFactory.decodeStream(is, null, opts);
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to decode bitmap from stream", e);
        }
        return bitmap;
    }

    public static Bitmap decodeResource(Resources res, int resId, int reqWidth, int reqHeight) {
        BitmapFactory.Options opts = new BitmapFactory.Options();

        // Determine insample size
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, opts);
        opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight);

        // Decode the bitmap, regionally if necessary
        Bitmap bitmap = null;
        opts.inJustDecodeBounds = false;
        Rect rect = getCropRectIfNecessary(opts, reqWidth, reqHeight);

        InputStream stream = null;
        try {
            if (rect != null) {
                stream = res.openRawResource(resId, new TypedValue());
                if (stream == null) return null;
                BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(stream, false);
                bitmap = decoder.decodeRegion(rect, opts);
            } else {
                bitmap = BitmapFactory.decodeResource(res, resId, opts);
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to open resource " + resId, e);
        } finally {
            closeSilently(stream);
        }
        return bitmap;
    }


    public static Bitmap decodeByteArray(byte[] buffer, int reqWidth, int reqHeight) {
        BitmapFactory.Options opts = new BitmapFactory.Options();

        // Determine insample size
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(buffer, 0, buffer.length, opts);
        opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight);

        // Decode the bitmap, regionally if necessary
        Bitmap bitmap = null;
        opts.inJustDecodeBounds = false;
        Rect rect = getCropRectIfNecessary(opts, reqWidth, reqHeight);
        try {
            if (rect != null) {
                BitmapRegionDecoder decoder =
                        BitmapRegionDecoder.newInstance(buffer, 0, buffer.length, false);
                bitmap = decoder.decodeRegion(rect, opts);
            } else {
                bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.length, opts);
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to decode bitmap from stream", e);
        }
        return bitmap;
    }

    public static Bitmap getBitmapFromAsset(Context ctx, String path, int reqWidth, int reqHeight) {
        if (ctx == null || path == null)
            return null;

        Bitmap bitmap = null;
        try {
            AssetManager assets = ctx.getAssets();
            InputStream is = assets.open(path);
            bitmap = decodeStream(is, reqWidth, reqHeight);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }


    /**
     * For excessively large images with an awkward ratio we
     * will want to crop them
     * @return
     */
    public static Rect getCropRectIfNecessary(
            BitmapFactory.Options options,int reqWidth, int reqHeight) {
        final int width = options.outWidth;
        final int height = options.outHeight;
        Rect rect = new Rect(0, 0, width, height);
        // Determine downsampled size
        int targetWidth = reqWidth * options.inSampleSize;
        int targetHeight = reqHeight * options.inSampleSize;

        if (targetHeight < height) {
            rect.top = (height - targetHeight) / 2;
            rect.bottom = rect.top + targetHeight;
        }
        if (targetWidth < width) {
            rect.left = (width - targetWidth) / 2;
            rect.right = rect.left + targetWidth;
        }
        return rect;
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        return calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight);
    }

    // Modified from original source:
    // http://developer.android.com/training/displaying-bitmaps/load-bitmap.html
    public static int calculateInSampleSize(
            int decodeWidth, int decodeHeight, int reqWidth, int reqHeight) {
        // Raw height and width of image
        int inSampleSize = 1;

        if (decodeHeight > reqHeight || decodeWidth > reqWidth) {
            final int halfHeight = decodeHeight / 2;
            final int halfWidth = decodeWidth / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight &&
                    (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
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
