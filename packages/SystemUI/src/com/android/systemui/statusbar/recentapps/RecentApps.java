/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.recentapps;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.wimax.WimaxHelper;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.systemui.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RecentApps extends FrameLayout {
    private static final String TAG = "RecentApps";

    private static final FrameLayout.LayoutParams WIDGET_LAYOUT_PARAMS = new FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT, // width = match_parent
                                        ViewGroup.LayoutParams.WRAP_CONTENT  // height = wrap_content
                                        );

    private static final LinearLayout.LayoutParams BUTTON_LAYOUT_PARAMS = new LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT, // width = wrap_content
                                        ViewGroup.LayoutParams.MATCH_PARENT, // height = match_parent
                                        1.0f                                    // weight = 1
                                        );

    private static final int LAYOUT_SCROLL_BUTTON_THRESHOLD = 6;
    private static int NUM_BUTTONS = 8;
    private static int MAX_RECENT_TASKS = NUM_BUTTONS * 2; // allow for some discards

    private Context mContext;
    private LayoutInflater mInflater;
    private RecentAppsBroadcastReceiver mBroadcastReceiver = null;
    private RecentAppsSettingsObserver mObserver = null;

    private HorizontalScrollView mScrollView;
    private TextView[] mIcons = new TextView[NUM_BUTTONS];
    
    public RecentApps(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // get an initial width
        updateButtonLayoutWidth();
    }

    public void setupRecentApps() {
        Log.i(TAG, "Clearing all recent apps in list");

        // remove all views from the layout
        removeAllViews();

        // unregister our content receiver
        if(mBroadcastReceiver != null) {
            mContext.unregisterReceiver(mBroadcastReceiver);
        }
        // unobserve our content
        if(mObserver != null) {
            mObserver.unobserve();
        }

        // clear the button instances
        //PowerButton.unloadAllButtons();

        Log.i(TAG, "Setting up recent apps");

        String buttons = Settings.System.getString(mContext.getContentResolver(), Settings.System.WIDGET_BUTTONS);
        Log.i(TAG, "Button list: " + buttons);
        
        mIcons[0] = (TextView) findViewById(com.android.internal.R.id.button0);
        mIcons[1] = (TextView) findViewById(com.android.internal.R.id.button1);
        mIcons[2] = (TextView) findViewById(com.android.internal.R.id.button2);
        mIcons[3] = (TextView) findViewById(com.android.internal.R.id.button3);
        mIcons[4] = (TextView) findViewById(com.android.internal.R.id.button4);
        mIcons[5] = (TextView) findViewById(com.android.internal.R.id.button5);
        mIcons[6] = (TextView) findViewById(com.android.internal.R.id.button6);
        mIcons[7] = (TextView) findViewById(com.android.internal.R.id.button7);

        // create a linearlayout to hold our buttons
        LinearLayout ll = new LinearLayout(mContext);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setGravity(Gravity.CENTER_HORIZONTAL);

        int buttonCount = 0;

        final PackageManager pm = mContext.getPackageManager();
        final ActivityManager am = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RecentTaskInfo> recentTasks =
                am.getRecentTasks(MAX_RECENT_TASKS, ActivityManager.RECENT_IGNORE_UNAVAILABLE);

        ActivityInfo homeInfo = 
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .resolveActivityInfo(pm, 0);

        IconUtilities iconUtilities = new IconUtilities(getContext());
        
        int index = 0;
        int numTasks = recentTasks.size();
        for (int i = 0; i < numTasks && (index < NUM_BUTTONS); ++i) {
            final ActivityManager.RecentTaskInfo info = recentTasks.get(i);

            Intent intent = new Intent(info.baseIntent);
            if (info.origActivity != null) {
                intent.setComponent(info.origActivity);
            }

            // Skip the current home activity.
            if (homeInfo != null) {
                if (homeInfo.packageName.equals(
                        intent.getComponent().getPackageName())
                        && homeInfo.name.equals(
                                intent.getComponent().getClassName())) {
                    continue;
                }
            }

            intent.setFlags((intent.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            final ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
            if (resolveInfo != null) {
                final ActivityInfo activityInfo = resolveInfo.activityInfo;
                final String title = activityInfo.loadLabel(pm).toString();
                Drawable icon = activityInfo.loadIcon(pm);

                if (title != null && title.length() > 0 && icon != null) {
	                //View buttonView = mInflater.inflate(R.layout.power_widget_button, null, false);
                    final TextView buttonView = mIcons[index];
                    buttonView.setText(title);
                    icon = iconUtilities.createIconDrawable(icon);
                    buttonView.setCompoundDrawables(null, icon, null, null);
                    //buttonView.setBackgroundDrawable(icon);
                    buttonView.setTag(intent);
                    buttonView.setVisibility(View.VISIBLE);
                    buttonView.setPressed(false);
                    buttonView.clearFocus();
                    //ll.addView(buttonView, BUTTON_LAYOUT_PARAMS);
                    ++index;
                    buttonCount++;
                }
            }
        }


        // we determine if we're using a horizontal scroll view based on a threshold of button counts
//        if(buttonCount > LAYOUT_SCROLL_BUTTON_THRESHOLD) {
//            // we need our horizontal scroll view to wrap the linear layout
//            mScrollView = new HorizontalScrollView(mContext);
//            // make the fading edge the size of a button (makes it more noticible that we can scroll
//            mScrollView.setFadingEdgeLength(mContext.getResources().getDisplayMetrics().widthPixels / LAYOUT_SCROLL_BUTTON_THRESHOLD);
//            mScrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
//            mScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
//            // set the padding on the linear layout to the size of our scrollbar, so we don't have them overlap
//            ll.setPadding(ll.getPaddingLeft(), ll.getPaddingTop(), ll.getPaddingRight(), mScrollView.getVerticalScrollbarWidth());
//            mScrollView.addView(ll, WIDGET_LAYOUT_PARAMS);
//            updateScrollbar();
//            addView(mScrollView, WIDGET_LAYOUT_PARAMS);
//        } else {
//            // not needed, just add the linear layout
//            addView(ll, WIDGET_LAYOUT_PARAMS);
//        }

        // set up a broadcast receiver for our intents, based off of what our power buttons have been loaded
        setupBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        // we add this so we can update views and such if the settings for our widget change
        filter.addAction(Settings.SETTINGS_CHANGED);
        // we need to re-setup our widget on boot complete to make sure it is visible if need be
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        // we need to detect orientation changes and update the static button width value appropriately
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        // register the receiver
        mContext.registerReceiver(mBroadcastReceiver, filter);
        // register our observer
        if(mObserver != null) {
            mObserver.observe();
        }
    }

    public void updateRecentApps() {
    //
    }

    public void setupSettingsObserver(Handler handler) {
        if(mObserver == null) {
            mObserver = new RecentAppsSettingsObserver(handler);
        }
    }
    
    public void setGlobalButtonOnClickListener(View.OnClickListener listener) {
//        PowerButton.setGlobalOnClickListener(listener);
    }

    public void setGlobalButtonOnLongClickListener(View.OnLongClickListener listener) {
//        PowerButton.setGlobalOnLongClickListener(listener);
    }

    private void setupBroadcastReceiver() {
        if(mBroadcastReceiver == null) {
            mBroadcastReceiver = new RecentAppsBroadcastReceiver();
        }
    }

    private void updateButtonLayoutWidth() {
        // use our context to set a valid button width
        BUTTON_LAYOUT_PARAMS.width = mContext.getResources().getDisplayMetrics().widthPixels / LAYOUT_SCROLL_BUTTON_THRESHOLD;
    }

    private void updateVisibility() {
        // now check if we need to display the widget still
        boolean displayPowerRecentApps = Settings.System.getInt(mContext.getContentResolver(),
                   Settings.System.EXPANDED_VIEW_WIDGET, 1) == 1;
        if(!displayPowerRecentApps) {
            setVisibility(View.GONE);
        } else {
            setVisibility(View.VISIBLE);
        }
    }

    private void updateScrollbar() {
        if (mScrollView == null) return;
        boolean hideScrollBar = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.EXPANDED_HIDE_SCROLLBAR, 0) == 1;
        mScrollView.setHorizontalScrollBarEnabled(!hideScrollBar);
    }

    // our own broadcast receiver :D
    private class RecentAppsBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
                setupRecentApps();
                updateVisibility();
            } else if(intent.getAction().equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                updateButtonLayoutWidth();
                setupRecentApps();
            } else {
                // handle the intent through our power buttons
                //PowerButton.handleOnReceive(context, intent);
            }

            // update our widget
            updateRecentApps();
        }
    };

    // our own settings observer :D
    private class RecentAppsSettingsObserver extends ContentObserver {
        public RecentAppsSettingsObserver(Handler handler) {
            super(handler);
        }

        public void observe() {
            ContentResolver resolver = mContext.getContentResolver();
			/*
            // watch for display widget
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.EXPANDED_VIEW_WIDGET),
                            false, this);

            // watch for scrollbar hiding
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.EXPANDED_HIDE_SCROLLBAR),
                            false, this);

            // watch for haptic feedback
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.EXPANDED_HAPTIC_FEEDBACK),
                            false, this);

            // watch for changes in buttons
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.WIDGET_BUTTONS),
                            false, this);

            // watch for changes in color
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.EXPANDED_VIEW_WIDGET_COLOR),
                            false, this);

            // watch for power-button specifc stuff that has been loaded
            for(Uri uri : PowerButton.getAllObservedUris()) {
                resolver.registerContentObserver(uri, false, this);
            }*/
        }

        public void unobserve() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChangeUri(Uri uri, boolean selfChange) {
            /*ContentResolver resolver = mContext.getContentResolver();
            Resources res = mContext.getResources();

            // first check if our widget buttons have changed
            if(uri.equals(Settings.System.getUriFor(Settings.System.WIDGET_BUTTONS))) {
                setupRecentApps();
            // now check if we change visibility
            } else if(uri.equals(Settings.System.getUriFor(Settings.System.EXPANDED_VIEW_WIDGET))) {
                updateVisibility();
            // now check for scrollbar hiding
            } else if(uri.equals(Settings.System.getUriFor(Settings.System.EXPANDED_HIDE_SCROLLBAR))) {
                updateScrollbar();
            }

            // do whatever the individual buttons must
            PowerButton.handleOnChangeUri(uri);

            // something happened so update the widget
            updateRecentApps();*/
        }
    }
}
