package com.rudyii.hsw.client.listeners;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import com.rudyii.hsw.client.R;
import com.rudyii.hsw.client.activities.MainActivity;

import java.util.HashMap;
import java.util.Objects;

import static com.rudyii.hsw.client.providers.DatabaseProvider.getStringValueFromSettings;

/**
 * Created by Jack on 18.12.2017.
 */

public class OfflineDeviceListener extends BroadcastReceiver {
    public static final String HSC_DEVICE_REBOOT = "com.rudyii.hsw.client.HSC_DEVICE_REBOOT";

    @Override
    public void onReceive(Context context, Intent intent) {
        @SuppressWarnings("unchecked") HashMap<String, Object> offlineDeviceData = (HashMap<String, Object>) intent.getSerializableExtra("HSC_DEVICE_REBOOT");
        String offlineDevice = (String) offlineDeviceData.get("cameraName");
        String serverName = (String) offlineDeviceData.get("serverName");

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(serverName + ": " + offlineDevice + context.getResources().getString(R.string.notif_text_camera_is_rebooting))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 500})
                .setSound(Uri.parse(getStringValueFromSettings("INFO_SOUND")), AudioManager.STREAM_NOTIFICATION);

        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Objects.requireNonNull(mNotificationManager).notify((int) System.currentTimeMillis(), mBuilder.build());
    }
}
