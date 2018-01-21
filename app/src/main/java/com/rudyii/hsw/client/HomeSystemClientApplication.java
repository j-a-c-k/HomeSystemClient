package com.rudyii.hsw.client;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static com.rudyii.hsw.client.helpers.Utils.getActiveServerAlias;
import static com.rudyii.hsw.client.helpers.Utils.getCurrentFcmToken;
import static com.rudyii.hsw.client.helpers.Utils.registerTokenOnServers;

/**
 * Created by j-a-c on 16.12.2017.
 */

public class HomeSystemClientApplication extends Application {
    public static String HSC_SERVER_CHANGED = "com.rudyii.hsw.client.HSC_SERVER_CHANGED";
    public static String TAG = "HSClient";
    private static Context appContext;

    public static Context getAppContext() {
        return appContext;
    }

    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "HomeSystemClientApplication created");

        appContext = getApplicationContext();

        registerTokenOnServers(getCurrentFcmToken());

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(HSC_SERVER_CHANGED);
        registerReceiver(new ServerChangedReceiver(), intentFilter);

        buildDynamicShortcuts();
    }

    private void buildDynamicShortcuts() {
        if (Build.VERSION.SDK_INT >= 25) {
            ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
            shortcutManager.removeAllDynamicShortcuts();
            List<ShortcutInfo> scInfoFromXml = shortcutManager.getDynamicShortcuts();

            ShortcutInfo serverName = new ShortcutInfo.Builder(this, "serverName")
                    .setShortLabel(getActiveServerAlias())
                    .setIcon(Icon.createWithResource(this, R.mipmap.shortcut_server))
                    .setIntent(new Intent(Intent.ACTION_MAIN, Uri.EMPTY))
                    .build();

            List<ShortcutInfo> scAllShortcuts = new ArrayList<>();

            scAllShortcuts.add(serverName);
            scAllShortcuts.addAll(scInfoFromXml);

            shortcutManager.setDynamicShortcuts(scAllShortcuts);


        }
    }

    private class ServerChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            buildDynamicShortcuts();
        }
    }
}
