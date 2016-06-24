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

import android.app.DownloadManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.provider.MediaStore;
import android.provider.Settings;

import java.util.List;

public class IconPreviewGenerator {
    private ComponentName[] mIconComponents;

    private Context mContext;

    public IconPreviewGenerator(Context context) {
        mContext = context;
    }

    public IconItems generateIconItems(String pkgName) {
        IconItems items = new IconItems();
        IconPreviewHelper helper = new IconPreviewHelper(mContext, pkgName);

        final ComponentName[] components = getIconComponents(mContext);
        BitmapDrawable drawable;
        drawable = (BitmapDrawable) helper.getIcon(components[0]);
        items.icon1 = drawable.getBitmap();
        drawable = (BitmapDrawable) helper.getIcon(components[1]);
        items.icon2 = drawable.getBitmap();
        drawable = (BitmapDrawable) helper.getIcon(components[2]);
        items.icon3 = drawable.getBitmap();
        return items;
    }

    private ComponentName[] getIconComponents(Context context) {
        if (mIconComponents == null || mIconComponents.length == 0) {
            mIconComponents = new ComponentName[3];

            PackageManager pm = context.getPackageManager();

            if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                // Device does not have telephony so use settings and download manager icons
                mIconComponents[0] = getSettingsComponentName(context);
                mIconComponents[1] = getDownloadManagerComponentName(context);
            } else {
                // Device has telephony so use dialer and mms icons
                mIconComponents[0] = getDefaultDialerComponentName(context);
                mIconComponents[1] = getDefaultMessagingComponentName(context);
            }

            if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                // Device does not have a camera so use themes icon
                mIconComponents[2] = getDefaultThemesComponentName(context);
            } else {
                // Device does have a camera so use default camera icon
                mIconComponents[2] = getDefaultCameraComponentName(context);
            }
        }

        return mIconComponents;
    }

    private ComponentName getDefaultComponentNameForIntent(Context context, Intent intent) {
        final PackageManager pm = context.getPackageManager();
        ComponentName cn = null;
        ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (info != null) {
            // If we get the resolver activity, check for at least one possible match
            if ("android".equals(info.activityInfo.packageName)) {
                List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
                if (infos.size() > 0) {
                    info = infos.get(0);
                } else {
                    return null;
                }
            }
            cn = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
        }

        return cn;
    }

    private ComponentName getDefaultCameraComponentName(Context context) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        return getDefaultComponentNameForIntent(context, intent);
    }

    private ComponentName getDefaultDialerComponentName(Context context) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        return getDefaultComponentNameForIntent(context, intent);
    }

    private ComponentName getDefaultMessagingComponentName(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_APP_MESSAGING);
        return getDefaultComponentNameForIntent(context, intent);
    }

    private ComponentName getDefaultThemesComponentName(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory("cyanogenmod.intent.category.APP_THEMES");
        return getDefaultComponentNameForIntent(context, intent);
    }

    private ComponentName getDownloadManagerComponentName(Context context) {
        Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        return getDefaultComponentNameForIntent(context, intent);
    }

    private ComponentName getSettingsComponentName(Context context) {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        return getDefaultComponentNameForIntent(context, intent);
    }

    public class IconItems {
        public Bitmap icon1;
        public Bitmap icon2;
        public Bitmap icon3;
    }
}
