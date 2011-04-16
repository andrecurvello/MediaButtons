/*
 * Copyright (C) 2011 Cory Williams
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

package com.github.mediabuttons;

import java.util.Hashtable;

import com.github.mediabuttons.R;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.RemoteViews;

public class Widget extends AppWidgetProvider {
	public final static String TAG = "MediaButtons";
	public final static String BROADCAST_MEDIA_BUTTON =
		"com.github.mediabuttons.Widget.BROADCAST_MEDIA_BUTTON";
	
	@Override
    public void onUpdate(Context context, AppWidgetManager manager,
    		int[] appWidgetIds) {
        Log.i(TAG, "Updating for " + appWidgetIds.length + " widgets");
        if (appWidgetIds.length == 0) {
            Log.i(TAG, "No widgets to update?");
            return;
        }
        SharedPreferences prefs =
        	context.getSharedPreferences(Configure.PREFS_NAME, 0);
        for (int id: appWidgetIds) {
        	String pref_name = Configure.ACTION_PREF_PREFIX + id;
            int action_index = prefs.getInt(pref_name, 0);
            updateWidget(context, manager, id, action_index);
        }
    }
	
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        Log.i(TAG, "Deleting " + appWidgetIds.length + " widgets");
        SharedPreferences.Editor prefs =
            context.getSharedPreferences(Configure.PREFS_NAME, 0).edit();
        for (int id: appWidgetIds) {
            String pref_name = Configure.ACTION_PREF_PREFIX + id;
            prefs.remove(pref_name);
            mViews.remove(id);
        }
    }

	public static void updateWidget(Context context, AppWidgetManager manager,
			int id, int action_index) {
		Log.i(TAG, "Updating widget " + id + " with action " + action_index);
		
		int keyCode = Configure.sKeyCode[action_index];
        Intent intent = new Intent(BROADCAST_MEDIA_BUTTON);
        intent.setClass(context, Widget.class);
        // The URI is not the right fit for the keycode data, but the URI
        // has to be unique so that PendingIntent doesn't override the intents
        // we create for each keycode.
        intent.setData(Uri.parse("http://" + keyCode));
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
        		context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews views = new RemoteViews(context.getPackageName(),
        		R.layout.widget);
        if (action_index == Configure.PLAY_PAUSE_ACTION) {
        	AudioManager audioManager = (AudioManager)
        			context.getSystemService(Context.AUDIO_SERVICE);
        	setPlayPauseIcon(views, audioManager.isMusicActive());
            mViews.put(id, views);
        } else {
        	views.setImageViewResource(R.id.button, Configure.sImageResource[action_index]);
        }
        views.setOnClickPendingIntent(R.id.button, pendingIntent);
        
        manager.updateAppWidget(id, views);
	}
	
	public static void setPlayPauseIcon(RemoteViews views, boolean isPlaying) {
		if (isPlaying) {
			views.setImageViewResource(R.id.button, R.drawable.pause);
		} else {
			views.setImageViewResource(R.id.button, R.drawable.play);
		}
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		super.onReceive(context, intent);
	    Log.i(TAG, "Got intent " + intent.getAction());
		if (intent.getAction().equals(BROADCAST_MEDIA_BUTTON)) {
			int keycode = Integer.parseInt(intent.getData().getHost());
			long upTime = SystemClock.uptimeMillis();
			long downTime = upTime - 1;
			
			Log.i(TAG, "Got keycode " + keycode);
			
            KeyEvent downKeyEvent = new KeyEvent(
            	downTime, downTime, KeyEvent.ACTION_DOWN, keycode, 0);
			Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downKeyEvent);
            context.sendOrderedBroadcast(downIntent, null);
            
            KeyEvent upKeyEvent = new KeyEvent(
            	downTime, upTime, KeyEvent.ACTION_UP, keycode, 0);
			Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upKeyEvent);
            context.sendOrderedBroadcast(upIntent, null);
            
            if (keycode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                setupWidgetList(context);
            	musicPlaying = null; // Force an update the first time.
    			mContext = context;
            	mUpdateRepeat = 5;
            	mHandler.removeCallbacks(mUpdateButton);
            	mHandler.postDelayed(mUpdateButton, 300);
            }
		}
	}
	
	private void setupWidgetList(Context context) {
        if (mViews.size() > 0) {
            return;
        }
        
        Log.i(TAG, "Repopulating the list of widgets.");
        AppWidgetManager manager = AppWidgetManager.getInstance(mContext);
        ComponentName component = new ComponentName(context, Widget.class);
        int[] widgetIds = manager.getAppWidgetIds(component);
        onUpdate(context, manager, widgetIds);
    }

    private static Context mContext = null;
	private static Hashtable<Integer, RemoteViews> mViews = new Hashtable<Integer, RemoteViews>();
	
	private static Boolean musicPlaying = null;
	private static int mUpdateRepeat = 0;
	private static Handler mHandler = new Handler();
	private static Runnable mUpdateButton = new Runnable() {
		public void run() {
			if (mContext == null) {
				Log.e(TAG, "Unable to run play/pause handler because context is null");
				return;
			}
			Log.i(TAG, "Play/pause handler called for " + mViews.size() + " widgets");
			AudioManager audioManager = (AudioManager)
					mContext.getSystemService(Context.AUDIO_SERVICE);
			boolean isActive = audioManager.isMusicActive();
			if (musicPlaying == null || musicPlaying != isActive) {
				musicPlaying = isActive;
				AppWidgetManager manager = AppWidgetManager.getInstance(mContext);
				for (int id: mViews.keySet()) {
					RemoteViews views = mViews.get(id);
					setPlayPauseIcon(views, isActive);
					manager.updateAppWidget(id, views);
				}
			}
			if (--mUpdateRepeat > 0) {
				mHandler.postDelayed(this, 1000);
			}
		}
	};
}