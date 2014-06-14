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
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.os.UserHandle;
import android.view.View;

import org.cyanogenmod.themes.provider.R;

public class StylePreviewGenerator {
    private Context mContext;

    public StylePreviewGenerator(Context context) {
        mContext = context;
    }

    public StyleItems generateStylePreviews(String pkgName) throws NameNotFoundException {
        StyleItems items = new StyleItems();
        Context themeContext = mContext.createPackageContextAsUser("android", pkgName, 0,
                UserHandle.CURRENT);
        View v = View.inflate(themeContext, R.layout.controls_thumbnail, null);
        items.thumbnail = LayoutRenderUtils.renderViewToBitmap(v);

        v = View.inflate(themeContext, R.layout.controls_preview, null);
        items.preview = LayoutRenderUtils.renderViewToBitmap(v);
        return items;
    }

    public class StyleItems {
        public Bitmap thumbnail;
        public Bitmap preview;
    }
}
