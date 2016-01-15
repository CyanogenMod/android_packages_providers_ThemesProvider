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
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.widget.FrameLayout;
import org.cyanogenmod.themes.provider.view.BatteryMeterView;

import org.cyanogenmod.themes.provider.R;

public class SystemUiPreviewGenerator {
    public static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String WIFI_DRAWABLE = "stat_sys_wifi_signal_3_fully";
    private static final String SIGNAL_DRAWABLE = "stat_sys_signal_3_fully";
    private static final String BLUETOOTH_DRAWABLE = "stat_sys_data_bluetooth_connected";
    private static final String STATUS_BAR_BATTERY_WIDTH = "status_bar_battery_width";
    private static final String STATUS_BAR_BATTERY_HEIGHT = "status_bar_battery_height";
    private static final String STATUS_BAR_CLOCK_COLOR = "status_bar_clock_color";
    private static final String SYSTEM_BAR_BACKGROUND = "system_bar_background";
    private static final String STATUS_BAR_BACKGROUND_OPAQUE = "status_bar_background_opaque";
    private static final String NAVIGATION_BAR_BACKGROUND_OPAQUE
            = "navigation_bar_background_opaque";
    private static final String IC_SYSBAR_BACK = "ic_sysbar_back";
    private static final String IC_SYSBAR_HOME = "ic_sysbar_home";
    private static final String IC_SYSBAR_RECENT = "ic_sysbar_recent";
    private static final String STATUS_BAR_ICON_SIZE = "status_bar_icon_size";

    // Style used for tinting of wifi and signal icons
    private static final String DUAL_TONE_LIGHT_THEME = "DualToneLightTheme";

    private Context mContext;

    public SystemUiPreviewGenerator(Context context) {
        mContext = context;
    }

    /**
     * Loads the necessary resources for the given theme.
     * @param pkgName Package name for the theme to use
     * @return
     * @throws NameNotFoundException
     */
    public SystemUiItems generateSystemUiItems(String pkgName) throws NameNotFoundException {
        final PackageManager pm = mContext.getPackageManager();
        final Context themeContext = mContext.createApplicationContext(
                pm.getApplicationInfo(SYSTEMUI_PACKAGE, 0), pkgName, 0);
        final Resources res = themeContext.getResources();
        // Set the theme for use when tinting the signal icons
        themeContext.setTheme(res.getIdentifier(DUAL_TONE_LIGHT_THEME, "style", SYSTEMUI_PACKAGE));

        int iconSize = res.getDimensionPixelSize(res.getIdentifier(STATUS_BAR_ICON_SIZE, "dimen",
                        SYSTEMUI_PACKAGE));
        SystemUiItems items = new SystemUiItems();

        // Generate bluetooth icon
        Drawable d = themeContext.getDrawable(res.getIdentifier(BLUETOOTH_DRAWABLE, "drawable",
                SYSTEMUI_PACKAGE));
        items.bluetoothIcon = renderDrawableToBitmap(d, iconSize);

        // Generate wifi icon
        d = themeContext.getDrawable(res.getIdentifier(WIFI_DRAWABLE, "drawable",
                SYSTEMUI_PACKAGE));
        items.wifiIcon = renderDrawableToBitmap(d, iconSize);

        // Generate cell signal icon
        d = themeContext.getDrawable(res.getIdentifier(SIGNAL_DRAWABLE, "drawable",
                SYSTEMUI_PACKAGE));
        items.signalIcon = renderDrawableToBitmap(d, iconSize);

        // Retrieve the color used for the clock
        items.clockColor = themeContext.getColor(res.getIdentifier(STATUS_BAR_CLOCK_COLOR, "color",
                SYSTEMUI_PACKAGE));

        // wifi margin no longer used in systemui
        items.wifiMarginEnd = 0;
        generateBatteryPreviews(res, items);
        generateBackgroundPreviews(res, items);

        // Generate navigation bar icons
        items.navbarBack = BitmapFactory.decodeResource(res, res.getIdentifier(IC_SYSBAR_BACK,
                "drawable", SYSTEMUI_PACKAGE));
        items.navbarHome = BitmapFactory.decodeResource(res, res.getIdentifier(IC_SYSBAR_HOME,
                "drawable", SYSTEMUI_PACKAGE));
        items.navbarRecent = BitmapFactory.decodeResource(res, res.getIdentifier(IC_SYSBAR_RECENT,
                "drawable", SYSTEMUI_PACKAGE));

        return items;
    }

    private Bitmap renderDrawableToBitmap(Drawable d, int iconSize) {
        if (d instanceof VectorDrawable) {
            return renderVectorDrawableToBitmap((VectorDrawable) d, iconSize);
        } else if (d instanceof BitmapDrawable) {
            return ((BitmapDrawable) d).getBitmap();
        }

        return null;
    }

    private Bitmap renderVectorDrawableToBitmap(VectorDrawable d, int iconSize) {
        Bitmap bmp = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        d.setBounds(0, 0, iconSize, iconSize);
        d.draw(canvas);

        return bmp;
    }

    /**
     * Generates the various battery types using the provided resources.
     * @param res
     * @param items
     */
    private void generateBatteryPreviews(Resources res, SystemUiItems items) {
        FrameLayout view = new FrameLayout(mContext);
        BatteryMeterView battery = new BatteryMeterView(mContext, res);
        view.addView(battery,
                new FrameLayout.LayoutParams(mContext.getResources().getDimensionPixelSize(
                        R.dimen.status_bar_battery_width),
                        mContext.getResources().getDimensionPixelSize(
                                R.dimen.status_bar_battery_height)));
        battery.setMode(BatteryMeterView.BatteryMeterMode.BATTERY_METER_ICON_PORTRAIT);
        items.batteryPortrait = LayoutRenderUtils.renderViewToBitmap(view);
        battery.setMode(BatteryMeterView.BatteryMeterMode.BATTERY_METER_ICON_LANDSCAPE);
        items.batteryLandscape = LayoutRenderUtils.renderViewToBitmap(view);
        battery.setMode(BatteryMeterView.BatteryMeterMode.BATTERY_METER_CIRCLE);
        items.batteryCircle = LayoutRenderUtils.renderViewToBitmap(view);
    }

    private void generateBackgroundPreviews(Resources res, SystemUiItems items) {
        int width = Math.max(res.getDisplayMetrics().widthPixels,
                res.getDisplayMetrics().heightPixels);
        int height = mContext.getResources().getDimensionPixelSize(
                R.dimen.status_bar_battery_height);
        Drawable defaultBackground = res.getDrawable(
                res.getIdentifier(SYSTEM_BAR_BACKGROUND, "drawable", SYSTEMUI_PACKAGE));
        defaultBackground.setBounds(0, 0, width, height);

        Drawable statusbarBackground = null;
        int resId = res.getIdentifier(STATUS_BAR_BACKGROUND_OPAQUE, "color", SYSTEMUI_PACKAGE);
        if (resId != 0) {
            statusbarBackground = new ColorDrawable(res.getColor(resId));
            statusbarBackground.setBounds(0, 0, width, height);
        }
        items.statusbarBackground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(items.statusbarBackground);
        if (statusbarBackground != null) {
            statusbarBackground.draw(canvas);
        } else {
            defaultBackground.draw(canvas);
        }

        Drawable navbarBackground = null;
        resId = res.getIdentifier(NAVIGATION_BAR_BACKGROUND_OPAQUE, "color", SYSTEMUI_PACKAGE);
        if (resId != 0) {
            navbarBackground = new ColorDrawable(res.getColor(resId));
            navbarBackground.setBounds(0, 0, width, height);
        }
        items.navbarBackground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(items.navbarBackground);
        if (navbarBackground != null) {
            navbarBackground.draw(canvas);
        } else {
            defaultBackground.draw(canvas);
        }
    }

    public class SystemUiItems {
        /**
         * Status bar items
         */
        public Bitmap bluetoothIcon;
        public Bitmap wifiIcon;
        public Bitmap signalIcon;
        public Bitmap batteryPortrait;
        public Bitmap batteryLandscape;
        public Bitmap batteryCircle;
        public Bitmap statusbarBackground;
        public int clockColor;
        public int wifiMarginEnd;

        /**
         * Navigation bar items
         */
        public Bitmap navbarBackground;
        public Bitmap navbarBack;
        public Bitmap navbarHome;
        public Bitmap navbarRecent;
    }
}
