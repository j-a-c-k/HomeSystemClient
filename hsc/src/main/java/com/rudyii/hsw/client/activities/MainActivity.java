package com.rudyii.hsw.client.activities;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.rudyii.hsw.client.R;
import com.rudyii.hsw.client.helpers.ToastDrawer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.rudyii.hsw.client.BuildConfig.COMPATIBLE_SERVER_VERSION;
import static com.rudyii.hsw.client.BuildConfig.SERVER_DOWNLOAD_URL;
import static com.rudyii.hsw.client.HomeSystemClientApplication.TAG;
import static com.rudyii.hsw.client.HomeSystemClientApplication.getAppContext;
import static com.rudyii.hsw.client.HomeSystemClientApplication.getToken;
import static com.rudyii.hsw.client.helpers.NotificationChannelsBuilder.NOTIFICATION_CHANNEL_MUTED;
import static com.rudyii.hsw.client.helpers.ShortcutsBuilder.buildDynamicShortcuts;
import static com.rudyii.hsw.client.helpers.Utils.DELAYED_ARM_DELAY_SECS;
import static com.rudyii.hsw.client.helpers.Utils.NOTIFICATION_TYPE_ALL;
import static com.rudyii.hsw.client.helpers.Utils.NOTIFICATION_TYPE_MOTION_DETECTED;
import static com.rudyii.hsw.client.helpers.Utils.NOTIFICATION_TYPE_MUTE;
import static com.rudyii.hsw.client.helpers.Utils.NOTIFICATION_TYPE_VIDEO_RECORDED;
import static com.rudyii.hsw.client.helpers.Utils.buildDataForMainActivityFrom;
import static com.rudyii.hsw.client.helpers.Utils.getActiveServerAlias;
import static com.rudyii.hsw.client.helpers.Utils.getActiveServerKey;
import static com.rudyii.hsw.client.helpers.Utils.getCurrentTimeAndDateDoubleDotsDelimFrom;
import static com.rudyii.hsw.client.helpers.Utils.getHourlyReportMutedStateForServer;
import static com.rudyii.hsw.client.helpers.Utils.getLooper;
import static com.rudyii.hsw.client.helpers.Utils.getNotificationMutedForServer;
import static com.rudyii.hsw.client.helpers.Utils.getNotificationTypeForServer;
import static com.rudyii.hsw.client.helpers.Utils.getServersList;
import static com.rudyii.hsw.client.helpers.Utils.registerUserDataOnServer;
import static com.rudyii.hsw.client.helpers.Utils.retrievePermissions;
import static com.rudyii.hsw.client.helpers.Utils.saveHourlyReportMutedStateForServer;
import static com.rudyii.hsw.client.helpers.Utils.saveNotificationMutedForServer;
import static com.rudyii.hsw.client.helpers.Utils.saveNotificationTypeForServer;
import static com.rudyii.hsw.client.helpers.Utils.stringIsEmptyOrNull;
import static com.rudyii.hsw.client.helpers.Utils.switchActiveServerTo;
import static com.rudyii.hsw.client.helpers.Utils.systemIsOnDarkMode;
import static com.rudyii.hsw.client.providers.DatabaseProvider.getStringValueFromSettings;
import static com.rudyii.hsw.client.providers.FirebaseDatabaseProvider.getRootReference;
import static java.util.Objects.requireNonNull;

public class MainActivity extends AppCompatActivity {
    private static final String HSC_STATUSES_UPDATED = "HSC_STATUSES_UPDATED";
    private final Random random = new Random();
    private final MainActivityBroadcastReceiver mainActivityBroadcastReceiver = new MainActivityBroadcastReceiver();
    private SwitchCompat systemMode, systemState;
    @SuppressWarnings("FieldCanBeLocal")
    private ImageButton buttonResendHourlyReport, buttonUsageStats, buttonSystemLog, buttonNotificationType;
    private TextView armedModeText, armedStateText;
    private Button armLater;
    private boolean buttonsChangedInternally, buttonNotificationTypeMuted, buttonResendHourlyReportMuted, delayedArmInProgress;
    private Handler serverLastPingHandler;
    private Runnable serverLastPingRunnable;
    private ColorStateList defaultTextColor;
    private DatabaseReference infoRef, statusesRef;
    private ValueEventListener infoValueEventListener, statusesValueEventListener;
    private long serverLastPing;
    private boolean systemIsInDarkMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        systemIsInDarkMode = systemIsOnDarkMode();
        setContentView(R.layout.activity_main);

        TextView serverLastPingTextValue = findViewById(R.id.textViewServerVersion);
        defaultTextColor = serverLastPingTextValue.getTextColors();

        buttonResendHourlyReport = findViewById(R.id.buttonResendHourly);
        if (systemIsInDarkMode) {
            buttonResendHourlyReport.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_hourly_inverted));
        }
        resolveHourlyReportIcon();
        buttonResendHourlyReport.setOnClickListener(v -> {
            if (buttonResendHourlyReportMuted) {
                new ToastDrawer().showToast(getResources().getString(R.string.text_resend_hourly_request_muted));
            } else {
                getRootReference().child("requests/resendHourly").setValue(random.nextInt(999));
                new ToastDrawer().showToast(getResources().getString(R.string.text_resend_hourly_request_text));
            }
        });
        buttonResendHourlyReport.setOnLongClickListener(v -> {
            muteUnmuteHourlyReporting();
            return true;
        });

        buttonUsageStats = findViewById(R.id.buttonUsageChart);
        if (systemIsInDarkMode) {
            buttonUsageStats.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_chart_inverted));
        }
        buttonUsageStats.setOnClickListener(v -> startActivity(new Intent(getApplicationContext(), UsageChartActivity.class)));
        buttonUsageStats.setOnLongClickListener(v -> {
            AlertDialog.Builder cleanupLog = new AlertDialog.Builder(MainActivity.this);
            cleanupLog.setTitle(getResources().getString(R.string.dialog_cleanup_usage_stats_title));
            cleanupLog.setMessage(getResources().getString(R.string.dialog_are_you_sure_cant_undo_alert_message));

            cleanupLog.setPositiveButton(getResources().getString(R.string.dialog_yes), (dialogInterface, i) -> getRootReference().child("/usageStats").removeValue());

            cleanupLog.setNegativeButton(getResources().getString(R.string.dialog_no), (dialogInterface, i) -> {

            });

            cleanupLog.show();

            return true;
        });

        buttonSystemLog = findViewById(R.id.buttonSystemLog);
        if (systemIsInDarkMode) {
            buttonSystemLog.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_log_inverted));
        }
        buttonSystemLog.setOnClickListener(v -> startActivity(new Intent(getApplicationContext(), SystemLogActivity.class)));
        buttonSystemLog.setOnLongClickListener(v -> {
            AlertDialog.Builder cleanupLog = new AlertDialog.Builder(MainActivity.this);
            cleanupLog.setTitle(getResources().getString(R.string.dialog_cleanup_log_title));
            cleanupLog.setMessage(getResources().getString(R.string.dialog_are_you_sure_cant_undo_alert_message));

            cleanupLog.setPositiveButton(getResources().getString(R.string.dialog_yes), (dialogInterface, i) -> getRootReference().child("/log").removeValue());

            cleanupLog.setNegativeButton(getResources().getString(R.string.dialog_no), (dialogInterface, i) -> {

            });

            cleanupLog.show();

            return true;
        });

        buttonNotificationType = findViewById(R.id.buttonNotificationType);
        resolveNotificationType();
        buttonNotificationType.setOnClickListener(v -> {
            if (buttonNotificationTypeMuted) {
                new ToastDrawer().showToast(getResources().getString(R.string.text_toast_notification_muted));
            } else {
                switchNotificationTypes();
                drawToastWithNotificationTypeInfo();
            }
        });
        buttonNotificationType.setOnLongClickListener(v -> {
            muteUnmuteButtonNotificationType();
            return true;
        });

        systemMode = findViewById(R.id.switchSystemMode);
        systemMode.setTextOn(getString(R.string.toggle_button_text_system_mode_state_automatic));
        systemMode.setTextOff(getString(R.string.toggle_button_text_system_mode_manual));
        systemMode.setOnCheckedChangeListener((buttonView, isChecked) -> calculateSystemStateBasedOn(systemMode, systemState));

        systemState = findViewById(R.id.switchSystemState);
        systemState.setTextOn(getString(R.string.toggle_button_text_system_state_armed));
        systemState.setTextOff(getString(R.string.toggle_button_text_system_state_disarmed));
        systemState.setOnCheckedChangeListener((buttonView, isChecked) -> calculateSystemStateBasedOn(systemMode, systemState));

        armLater = findViewById(R.id.buttonDelayedArm);
        armLater.setOnClickListener(v -> {
            if (systemMode.isChecked()) {
                new ToastDrawer().showLongToast(getString(R.string.text_toast_automatic_mode_selected));
            } else if (delayedArmInProgress) {
                new ToastDrawer().showLongToast(getString(R.string.text_toast_delayed_arm_in_progress));
            } else {
                delayedArmInProgress = true;

                AsyncTask.execute(() -> {
                    Context context = getAppContext();
                    String delayedArmSecondsRaw = getStringValueFromSettings(DELAYED_ARM_DELAY_SECS);
                    int delayedArmSeconds = stringIsEmptyOrNull(delayedArmSecondsRaw) ? 60 : Integer.parseInt(delayedArmSecondsRaw);
                    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_MUTED)
                            .setSmallIcon(R.drawable.ic_stat_notification)
                            .setContentTitle(
                                    getString(R.string.notif_text_delayed_arm) +
                                            delayedArmSeconds +
                                            getString(R.string.text_seconds))
                            .setProgress(delayedArmSeconds, 0, false);

                    NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    requireNonNull(mNotificationManager).notify(4918151, mBuilder.build());

                    int countUp = 1;
                    while (countUp < delayedArmSeconds) {
                        try {
                            Thread.sleep(1000L);
                            int percent = countUp * 100 / delayedArmSeconds;
                            mBuilder.setOngoing(true)
                                    .setContentInfo(percent + "%")
                                    .setProgress(100, percent, false);
                            requireNonNull(mNotificationManager).notify(4918151, mBuilder.build());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        countUp++;
                    }

                    Handler handler = new Handler(getLooper());
                    handler.post(() -> {
                        systemState.setChecked(true);
                        calculateSystemStateBasedOn(systemMode, systemState);
                        mNotificationManager.cancel(4918151);
                        delayedArmInProgress = false;
                    });
                });
            }
        });

        requestPermissions();
    }

    private void muteUnmuteButtonNotificationType() {
        String activeServer = getActiveServerAlias();
        buttonNotificationTypeMuted = Boolean.parseBoolean(getNotificationMutedForServer(activeServer));

        buttonNotificationTypeMuted = !buttonNotificationTypeMuted;

        saveNotificationMutedForServer(activeServer, "" + buttonNotificationTypeMuted);
        resolveNotificationType();

        registerUserDataOnServer(getActiveServerKey(), activeServer, getToken());
    }

    private void muteUnmuteHourlyReporting() {
        String activeServer = getActiveServerAlias();
        buttonResendHourlyReportMuted = Boolean.parseBoolean(getHourlyReportMutedStateForServer(activeServer));
        Drawable icon;

        if (buttonResendHourlyReportMuted) {
            buttonResendHourlyReportMuted = false;
            if (systemIsInDarkMode) {
                icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_hourly_inverted);
            } else {
                icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_hourly);
            }
        } else {
            buttonResendHourlyReportMuted = true;
            if (systemIsInDarkMode) {
                icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_muted_inverted);
            } else {
                icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_muted);
            }
        }

        buttonResendHourlyReport.setImageDrawable(icon);
        saveHourlyReportMutedStateForServer(activeServer, "" + buttonResendHourlyReportMuted);
        registerUserDataOnServer(getActiveServerKey(), activeServer, getToken());
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter statusesUpdatedIntentFilter = new IntentFilter();
        statusesUpdatedIntentFilter.addAction(HSC_STATUSES_UPDATED);
        registerReceiver(mainActivityBroadcastReceiver, statusesUpdatedIntentFilter);

        subscribeFirebaseListeners();

        buildServersList();

        buildHandlers();
    }

    @Override
    protected void onPause() {
        super.onPause();

        serverLastPingHandler.removeCallbacks(serverLastPingRunnable);
        unregisterReceiver(mainActivityBroadcastReceiver);

        unsubscribeFirebaseListeners();
    }

    private void subscribeFirebaseListeners() {
        infoRef = getRootReference().child("/info");
        infoValueEventListener = buildInfoValueEventListener();
        infoRef.addValueEventListener(infoValueEventListener);

        statusesRef = getRootReference().child("/statuses");
        statusesValueEventListener = buildStatusesValueEventListener();
        statusesRef.addValueEventListener(buildStatusesValueEventListener());
    }

    private void unsubscribeFirebaseListeners() {
        infoRef.removeEventListener(infoValueEventListener);
        statusesRef.removeEventListener(statusesValueEventListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        switch (itemId) {
            case R.id.settings:
                startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
                break;

            case R.id.download_server:
                Uri serverUrl = Uri.parse(SERVER_DOWNLOAD_URL);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(serverUrl);
                startActivity(intent);
                break;

            case R.id.share_server:
                startActivity(new Intent(getApplicationContext(), ServerSharingActivity.class));
                break;

            case R.id.about:
                startActivity(new Intent(getApplicationContext(), AboutActivity.class));
                break;
        }

        return true;
    }

    private void switchNotificationTypes() {
        if (buttonNotificationTypeMuted) {
            return;
        }

        String activeServer = getActiveServerAlias();
        String notificationType = getNotificationTypeForServer(activeServer);
        Drawable icon;

        switch (notificationType) {
            case NOTIFICATION_TYPE_MOTION_DETECTED:
                if (systemIsInDarkMode) {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_on_video_recorded_inverted);
                } else {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_on_video_recorded);
                }
                saveNotificationTypeForServer(activeServer, NOTIFICATION_TYPE_VIDEO_RECORDED);
                break;

            case NOTIFICATION_TYPE_VIDEO_RECORDED:
                if (systemIsInDarkMode) {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_on_motion_and_video_recorded_inverted);
                } else {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_on_motion_and_video_recorded);
                }
                saveNotificationTypeForServer(activeServer, NOTIFICATION_TYPE_ALL);
                break;

            case NOTIFICATION_TYPE_ALL:
                if (systemIsInDarkMode) {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_on_motion_inverted);
                } else {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_on_motion);
                }
                saveNotificationTypeForServer(activeServer, NOTIFICATION_TYPE_MOTION_DETECTED);
                break;
            default:
                icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.image_warning);
                break;
        }

        buttonNotificationType.setImageDrawable(icon);
        registerUserDataOnServer(getActiveServerKey(), activeServer, getToken());
    }

    private void resolveHourlyReportIcon() {
        String activeServer = getActiveServerAlias();
        buttonResendHourlyReportMuted = Boolean.parseBoolean(getHourlyReportMutedStateForServer(activeServer));
        Drawable icon;

        if (buttonResendHourlyReportMuted) {
            buttonResendHourlyReportMuted = true;
            if (systemIsInDarkMode) {
                icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_muted_inverted);
            } else {
                icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_muted);
            }
        } else {
            buttonResendHourlyReportMuted = false;
            if (systemIsInDarkMode) {
                icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_hourly_inverted);
            } else {
                icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_hourly);
            }
        }

        buttonResendHourlyReport.setImageDrawable(icon);
    }

    private void resolveNotificationType() {
        String activeServer = getActiveServerAlias();
        String notificationType = getNotificationTypeForServer(activeServer);
        buttonNotificationTypeMuted = Boolean.parseBoolean(getNotificationMutedForServer(activeServer));
        Drawable icon;

        switch (notificationType) {
            case NOTIFICATION_TYPE_MOTION_DETECTED:
                if (systemIsInDarkMode) {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_on_motion_inverted);
                } else {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_on_motion);
                }
                break;

            case NOTIFICATION_TYPE_VIDEO_RECORDED:
                if (systemIsInDarkMode) {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_on_video_recorded_inverted);
                } else {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_on_video_recorded);
                }
                break;

            case NOTIFICATION_TYPE_ALL:
                if (systemIsInDarkMode) {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_on_motion_and_video_recorded_inverted);
                } else {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_on_motion_and_video_recorded);
                }
                break;

            case NOTIFICATION_TYPE_MUTE:
                if (systemIsInDarkMode) {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_muted_inverted);
                } else {
                    icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_muted);
                }
                buttonNotificationTypeMuted = true;
                break;

            default:
                icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.image_warning);
                break;
        }

        if (buttonNotificationTypeMuted) {
            if (systemIsInDarkMode) {
                icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_muted_inverted);
            } else {
                icon = ContextCompat.getDrawable(getApplicationContext(), R.mipmap.button_muted);
            }
        }

        buttonNotificationType.setImageDrawable(icon);
    }

    private void drawToastWithNotificationTypeInfo() {
        String activeServer = getActiveServerAlias();
        String notificationType = getNotificationTypeForServer(activeServer);

        switch (notificationType) {
            case NOTIFICATION_TYPE_MOTION_DETECTED:
                new ToastDrawer().showLongToast(getResources().getString(R.string.text_toast_notification_type_motion_detected));
                break;

            case NOTIFICATION_TYPE_VIDEO_RECORDED:
                new ToastDrawer().showLongToast(getResources().getString(R.string.text_toast_notification_type_video_recorded));
                break;

            case NOTIFICATION_TYPE_ALL:
                new ToastDrawer().showLongToast(getResources().getString(R.string.text_toast_notification_type_both));
                break;
        }
    }

    private void calculateSystemStateBasedOn(SwitchCompat systemMode, SwitchCompat systemState) {
        Map<String, String> stateRequest = new HashMap<>();

        if (buttonsChangedInternally) {
            return;
        }

        if (systemMode.isChecked()) {
            systemState.setChecked(false);
            systemState.setEnabled(false);

            stateRequest.put("armedMode", "AUTOMATIC");
            stateRequest.put("armedState", "AUTO");

            getRootReference().child("requests/state").setValue(stateRequest);

        } else if (!systemMode.isChecked() && systemState.isChecked()) {
            systemMode.setChecked(false);
            systemState.setEnabled(true);

            stateRequest.put("armedMode", "MANUAL");
            stateRequest.put("armedState", "ARMED");

            getRootReference().child("requests/state").setValue(stateRequest);

        } else if (!systemMode.isChecked() && !systemState.isChecked()) {
            systemMode.setChecked(false);
            systemState.setEnabled(true);

            stateRequest.put("armedMode", "MANUAL");
            stateRequest.put("armedState", "DISARMED");

            getRootReference().child("requests/state").setValue(stateRequest);
        }
    }

    private void refreshFirebaseListeners() {
        unsubscribeFirebaseListeners();
        subscribeFirebaseListeners();
    }

    private ValueEventListener buildInfoValueEventListener() {
        return new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                @SuppressWarnings("unchecked") final Map<String, Object> info = (Map<String, Object>) dataSnapshot.getValue();

                if (info == null) {
                    return;
                }

                String serverVersion = info.get("serverVersion").toString();
                serverLastPing = (long) info.get("ping");
                Long serverUptime = (long) info.get("uptime");

                TextView serverVersionTextValue = findViewById(R.id.textViewServerVersionValue);
                if (COMPATIBLE_SERVER_VERSION.hashCode() > serverVersion.hashCode()) {
                    serverVersionTextValue.setTextColor(getApplicationContext().getColor(R.color.red));
                } else {
                    serverVersionTextValue.setTextColor(defaultTextColor);
                }
                serverVersionTextValue.setText(serverVersion);

                TextView serverLastPingTextValue = findViewById(R.id.textViewServerLastPingValue);
                serverLastPingTextValue.setText(calculatePing(serverLastPing));

                TextView serverUptimeTextValue = findViewById(R.id.textViewServerUptimeValue);
                serverUptimeTextValue.setText(calculateUptime(serverUptime));

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
    }

    private ValueEventListener buildStatusesValueEventListener() {
        return new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                @SuppressWarnings("unchecked") final Map<String, Object> state = (Map<String, Object>) dataSnapshot.getValue();

                if (state == null) {
                    return;
                }

                String armedMode = state.get("armedMode").toString();
                String armedState = state.get("armedState").toString();
                HashMap<String, Object> buttonsState = buildDataForMainActivityFrom(armedMode, armedState);

                buttonsChangedInternally = true;
                updateModeStateButtons(buttonsState);
                buttonsChangedInternally = false;


                armedModeText = findViewById(R.id.textViewForSwitchSystemMode);
                armedModeText.setText(buttonsState.get("systemModeText").toString());
                if ("auto".equalsIgnoreCase(armedMode)) {
                    armedModeText.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.red));
                } else {
                    armedModeText.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.green));
                }

                armedStateText = findViewById(R.id.textViewForSwitchSystemState);
                armedStateText.setText(buttonsState.get("systemStateText").toString());
                if ("armed".equalsIgnoreCase(armedState)) {
                    armedStateText.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.red));
                } else if ("disarmed".equalsIgnoreCase(armedState)) {
                    armedStateText.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.green));
                } else {
                    armedStateText.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.blue));
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
    }

    private String calculatePing(long serverLastPing) {
        return getCurrentTimeAndDateDoubleDotsDelimFrom(serverLastPing);
    }

    private String calculateUptime(Long serverUptime) {
        long days = TimeUnit.MILLISECONDS.toDays(serverUptime);
        long hours = TimeUnit.MILLISECONDS.toHours(serverUptime) % 24L;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(serverUptime) % 60L;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(serverUptime) % 60L;

        StringBuilder builder = new StringBuilder();
        builder.append(days);

        if (days == 1) {
            builder.append(getResources().getString(R.string.text_day));
        } else if (days > 1) {
            builder.append(getResources().getString(R.string.text_days));
        }

        builder.append(String.format(Locale.getDefault(), "%01d:", hours))
                .append(String.format(Locale.getDefault(), "%02d:", minutes))
                .append(String.format(Locale.getDefault(), "%02d", seconds));

        return builder.toString();
    }

    private void updateModeStateButtons(HashMap<String, Object> statusesData) {
        systemMode.setChecked((boolean) statusesData.get("systemModeChecked"));
        systemMode.setEnabled(true);

        systemState.setChecked((boolean) statusesData.get("systemStateChecked"));
        systemState.setEnabled((boolean) statusesData.get("systemStateEnabled"));
    }

    private void buildHandlers() {
        serverLastPingHandler = new Handler();
        serverLastPingRunnable = new Runnable() {
            @Override
            public void run() {
                TextView serverLastPingTextValue = findViewById(R.id.textViewServerLastPingValue);
                if (serverLastPing > 0 && System.currentTimeMillis() - serverLastPing > 300000) {
                    serverLastPingTextValue.setTextColor(getApplicationContext().getColor(R.color.red));
                    if (serverLastPingTextValue.getVisibility() == View.VISIBLE) {
                        serverLastPingTextValue.setVisibility(View.INVISIBLE);
                    } else {
                        serverLastPingTextValue.setVisibility(View.VISIBLE);
                    }
                } else {
                    serverLastPingTextValue.setTextColor(defaultTextColor);
                    serverLastPingTextValue.setVisibility(View.VISIBLE);
                }
                serverLastPingHandler.postDelayed(this, 1000);
            }
        };

        serverLastPingHandler.postDelayed(serverLastPingRunnable, 1000);
    }

    private void buildServersList() {
        Spinner serversList = findViewById(R.id.spinnerServerList);
        ArrayAdapter<String> serversArray = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_spinner_item, getServersList());
        serversArray.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        serversList.setAdapter(serversArray);

        final int[] currentItem = new int[1];
        try {
            currentItem[0] = serversArray.getPosition(getActiveServerAlias());
        } catch (IndexOutOfBoundsException e) {
            Log.w(TAG, "No paired servers");
        }

        serversList.setSelection(currentItem[0]);
        serversList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View convertView, int selected, long current) {
                String selectedServerName = (String) parent.getItemAtPosition(selected);

                if (currentItem[0] != selected) {
                    currentItem[0] = selected;
                    switchActiveServerTo(selectedServerName);
                    ((TextView) convertView).setText(selectedServerName);
                    ((TextView) convertView).setTextColor(getAppContext().getColor(R.color.textColor));

                    refreshFirebaseListeners();
                    resolveHourlyReportIcon();
                    resolveNotificationType();
                    registerUserDataOnServer(getActiveServerKey(), selectedServerName, getToken());

                    buildDynamicShortcuts();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void requestPermissions() {

        ArrayList<String> permissionsToBeRequested = new ArrayList<>(Arrays.asList(retrievePermissions()));

        if (permissionsToBeRequested.size() > 0) {
            String[] permissionsArray = new String[permissionsToBeRequested.size()];
            permissionsToBeRequested.toArray(permissionsArray);
            requestPermissions(permissionsArray, random.nextInt(999));
        }
    }

    public class MainActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            buttonsChangedInternally = true;

            @SuppressWarnings("unchecked") HashMap<String, Object> statusesData = (HashMap<String, Object>) intent.getSerializableExtra(HSC_STATUSES_UPDATED);

            if (statusesData == null) {
                return;
            }

            requireNonNull(armedModeText).setText((String) statusesData.get("systemModeText"));
            requireNonNull(armedModeText).setTextColor((int) statusesData.get("systemModeTextColor"));

            requireNonNull(armedStateText).setText((String) statusesData.get("systemStateText"));
            requireNonNull(armedStateText).setTextColor((int) statusesData.get("systemStateTextColor"));

            updateModeStateButtons(statusesData);

            buttonsChangedInternally = false;
        }
    }
}
