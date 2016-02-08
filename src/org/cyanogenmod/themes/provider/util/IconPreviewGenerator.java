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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

public class IconPreviewGenerator {
    private static final ComponentName COMPONENT_DIALER =
            new ComponentName("com.android.dialer", "com.android.dialer.DialtactsActivity");
    private static final ComponentName COMPONENT_DIALERNEXT =
            new ComponentName("com.cyngn.dialer", "com.android.dialer.DialtactsActivity");
    private static final ComponentName COMPONENT_MESSAGING =
            new ComponentName("com.android.messaging",
                    "com.android.messaging.ui.conversationlist.ConversationListActivity");
    private static final ComponentName COMPONENT_CAMERANEXT =
            new ComponentName("com.cyngn.cameranext", "com.android.camera.CameraLauncher");
    private static final ComponentName COMPONENT_CAMERA =
            new ComponentName("com.android.camera2", "com.android.camera.CameraLauncher");
    private static final ComponentName COMPONENT_BROWSER =
            new ComponentName("com.android.browser", "com.android.browser.BrowserActivity");
    private static final ComponentName COMPONENT_SETTINGS =
            new ComponentName("com.android.settings", "com.android.settings.Settings");
    private static final ComponentName COMPONENT_CALENDAR =
            new ComponentName("com.android.calendar", "com.android.calendar.AllInOneActivity");
    private static final ComponentName COMPONENT_GALERY =
            new ComponentName("com.android.gallery3d", "com.android.gallery3d.app.GalleryActivity");

    private static final String CAMERA_NEXT_PACKAGE = "com.cyngn.cameranext";
    private static final String DIALER_NEXT_PACKAGE = "com.cyngn.dialer";

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
            mIconComponents = new ComponentName[]{COMPONENT_DIALER, COMPONENT_MESSAGING,
                    COMPONENT_CAMERA, COMPONENT_BROWSER};

            PackageManager pm = context.getPackageManager();

            // if device does not have telephony replace dialer and mms
            if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                mIconComponents[0] = COMPONENT_CALENDAR;
                mIconComponents[1] = COMPONENT_GALERY;
            } else {
                // decide on which dialer icon to use
                try {
                    if (pm.getPackageInfo(DIALER_NEXT_PACKAGE, 0) != null) {
                        mIconComponents[0] = COMPONENT_DIALERNEXT;
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    // default to COMPONENT_DIALER
                }
            }

            if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                mIconComponents[2] = COMPONENT_SETTINGS;
            } else {
                // decide on which camera icon to use
                try {
                    if (pm.getPackageInfo(CAMERA_NEXT_PACKAGE, 0) != null) {
                        mIconComponents[2] = COMPONENT_CAMERANEXT;
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    // default to COMPONENT_CAMERA
                }
            }

        }

        return mIconComponents;
    }
    public class IconItems {
        public Bitmap icon1;
        public Bitmap icon2;
        public Bitmap icon3;
    }
}
