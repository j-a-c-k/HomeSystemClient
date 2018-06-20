package com.rudyii.hsw.client.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.rudyii.hsw.client.R;
import com.rudyii.hsw.client.helpers.ToastDrawer;
import com.rudyii.hsw.client.listeners.StatusesListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.rudyii.hsw.client.HomeSystemClientApplication.HSC_SERVER_CHANGED;
import static com.rudyii.hsw.client.HomeSystemClientApplication.TAG;
import static com.rudyii.hsw.client.helpers.Utils.NOTIFICATION_TYPE_ALL;
import static com.rudyii.hsw.client.helpers.Utils.NOTIFICATION_TYPE_MOTION_DETECTED;
import static com.rudyii.hsw.client.helpers.Utils.NOTIFICATION_TYPE_VIDEO_RECORDED;
import static com.rudyii.hsw.client.helpers.Utils.buildDataForMainActivityFrom;
import static com.rudyii.hsw.client.helpers.Utils.getActiveServerAlias;
import static com.rudyii.hsw.client.helpers.Utils.getActiveServerKey;
import static com.rudyii.hsw.client.helpers.Utils.getCurrentTimeAndDateDoubleDotsDelimFrom;
import static com.rudyii.hsw.client.helpers.Utils.getNotificationTypeForServer;
import static com.rudyii.hsw.client.helpers.Utils.getServersList;
import static com.rudyii.hsw.client.helpers.Utils.registerUserDataOnServer;
import static com.rudyii.hsw.client.helpers.Utils.retrievePermissions;
import static com.rudyii.hsw.client.helpers.Utils.saveNotificationTypeForServer;
import static com.rudyii.hsw.client.helpers.Utils.switchActiveServerTo;
import static com.rudyii.hsw.client.providers.FirebaseDatabaseProvider.getRootReference;

public class MainActivity extends AppCompatActivity {
    private Random random = new Random();
    private Switch systemMode, systemState, switchPorts;
    private ImageButton buttonResendHourlyReport, buttonUsageStats, buttonSystemLog, buttonNotificationType;
    private TextView armedModeText, armedStateText;
    private boolean buttonsChangedInternally;
    private MainActivityBroadcastReceiver mainActivityBroadcastReceiver = new MainActivityBroadcastReceiver();
    private Handler serverLastPingHandler;
    private Runnable serverLastPingRunnable;
    private ColorStateList defaultTextColor;
    private DatabaseReference infoRef, statusesRef;
    private ValueEventListener infoValueEventListener, statusesValueEventListener;
    private long serverLastPing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        TextView serverLastPingTextValue = (TextView) findViewById(R.id.textViewServerVersion);
        defaultTextColor = serverLastPingTextValue.getTextColors();

        buttonResendHourlyReport = (ImageButton) findViewById(R.id.buttonResendHourly);
        buttonResendHourlyReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getRootReference().child("requests/resendHourly").setValue(random.nextInt(999));
                new ToastDrawer().showToast(getResources().getString(R.string.text_resend_hourly_request_text));
            }
        });
        buttonResendHourlyReport.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                new ToastDrawer().showToast(getResources().getString(R.string.text_resend_hourly_text));
                return true;
            }
        });

        buttonUsageStats = (ImageButton) findViewById(R.id.buttonUsageChart);
        buttonUsageStats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), UsageChartActivity.class));
            }
        });
        buttonUsageStats.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                AlertDialog.Builder cleanupLog = new AlertDialog.Builder(MainActivity.this);
                cleanupLog.setTitle(getResources().getString(R.string.dialog_cleanup_usage_stats_title));
                cleanupLog.setMessage(getResources().getString(R.string.dialog_are_you_sure_cant_undo_alert_message));

                cleanupLog.setPositiveButton(getResources().getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        getRootReference().child("/usageStats").removeValue();
                    }
                });

                cleanupLog.setNegativeButton(getResources().getString(R.string.dialog_no), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });

                cleanupLog.show();

                return true;
            }
        });

        buttonSystemLog = (ImageButton) findViewById(R.id.buttonSystemLog);
        buttonSystemLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), SystemLogActivity.class));
            }
        });
        buttonSystemLog.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                AlertDialog.Builder cleanupLog = new AlertDialog.Builder(MainActivity.this);
                cleanupLog.setTitle(getResources().getString(R.string.dialog_cleanup_log_title));
                cleanupLog.setMessage(getResources().getString(R.string.dialog_are_you_sure_cant_undo_alert_message));

                cleanupLog.setPositiveButton(getResources().getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        getRootReference().child("/log").removeValue();
                    }
                });

                cleanupLog.setNegativeButton(getResources().getString(R.string.dialog_no), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });

                cleanupLog.show();

                return true;
            }

        });

        buttonNotificationType = (ImageButton) findViewById(R.id.buttonNotificationType);
        resolveNotificationType();
        buttonNotificationType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchNotificationTypes();
            }
        });
        buttonNotificationType.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                drawToastWithNotificationTypeInfo();
                return true;
            }
        });
        switchPorts = (Switch) findViewById(R.id.switchPorts);
        switchPorts.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonsChangedInternally) {
                    return;
                }

                if (isChecked) {
                    getRootReference().child("requests/portsOpen").setValue(true);
                } else {
                    getRootReference().child("requests/portsOpen").setValue(false);
                }
            }
        });

        systemMode = (Switch) findViewById(R.id.switchSystemMode);
        systemMode.setTextOn(getString(R.string.toggle_button_text_system_mode_state_automatic));
        systemMode.setTextOff(getString(R.string.toggle_button_text_system_mode_manual));
        systemMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                calculateSystemStateBasedOn(systemMode, systemState);
            }
        });

        systemState = (Switch) findViewById(R.id.switchSystemState);
        systemState.setTextOn(getString(R.string.toggle_button_text_system_state_armed));
        systemState.setTextOff(getString(R.string.toggle_button_text_system_state_disarmed));
        systemState.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                calculateSystemStateBasedOn(systemMode, systemState);
            }
        });

        requestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter statusesUpdatedIntentFilter = new IntentFilter();
        statusesUpdatedIntentFilter.addAction(StatusesListener.HSC_STATUSES_UPDATED);
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

            case R.id.about:
                startActivity(new Intent(getApplicationContext(), AboutActivity.class));
                break;
        }

        return true;
    }

    private void switchNotificationTypes() {
        String activeServer = getActiveServerAlias();
        String notificationType = getNotificationTypeForServer(activeServer);
        Drawable icon = null;

        switch (notificationType) {
            case NOTIFICATION_TYPE_MOTION_DETECTED:
                icon = getDrawable(R.mipmap.button_on_video_recorded);
                saveNotificationTypeForServer(activeServer, NOTIFICATION_TYPE_VIDEO_RECORDED);
                break;

            case NOTIFICATION_TYPE_VIDEO_RECORDED:
                icon = getDrawable(R.mipmap.button_on_motion_and_video_recorded);
                saveNotificationTypeForServer(activeServer, NOTIFICATION_TYPE_ALL);
                break;

            case NOTIFICATION_TYPE_ALL:
                icon = getDrawable(R.mipmap.button_on_motion);
                saveNotificationTypeForServer(activeServer, NOTIFICATION_TYPE_MOTION_DETECTED);
                break;
            default:
                icon = getDrawable(R.mipmap.image_warning);
                break;
        }

        buttonNotificationType.setImageDrawable(icon);
        registerUserDataOnServer(getActiveServerKey(), activeServer);
    }

    private void resolveNotificationType() {
        String activeServer = getActiveServerAlias();
        String notificationType = getNotificationTypeForServer(activeServer);
        Drawable icon = null;

        switch (notificationType) {
            case NOTIFICATION_TYPE_MOTION_DETECTED:
                icon = getDrawable(R.mipmap.button_on_motion);
                break;

            case NOTIFICATION_TYPE_VIDEO_RECORDED:
                icon = getDrawable(R.mipmap.button_on_video_recorded);
                break;

            case NOTIFICATION_TYPE_ALL:
                icon = getDrawable(R.mipmap.button_on_motion_and_video_recorded);
                break;
            default:
                icon = getDrawable(R.mipmap.image_warning);
                break;
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

    private void calculateSystemStateBasedOn(Switch systemMode, Switch systemState) {
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

    public void refreshFirebaseListeners() {
        unsubscribeFirebaseListeners();
        subscribeFirebaseListeners();
    }

    private ValueEventListener buildInfoValueEventListener() {
        return new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                final Map<String, Object> info = (Map<String, Object>) dataSnapshot.getValue();

                if (info == null) {
                    return;
                }

                String serverVersion = info.get("serverVersion").toString();
                serverLastPing = (long) info.get("ping");
                Long serverUptime = (long) info.get("uptime");

                TextView serverVersionTextValue = (TextView) findViewById(R.id.textViewServerVersionValue);
                serverVersionTextValue.setText(serverVersion);

                TextView serverLastPingTextValue = (TextView) findViewById(R.id.textViewServerLastPingValue);
                serverLastPingTextValue.setText(calculatePing(serverLastPing));

                TextView serverUptimeTextValue = (TextView) findViewById(R.id.textViewServerUptimeValue);
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
                final Map<String, Object> state = (Map<String, Object>) dataSnapshot.getValue();

                if (state == null) {
                    return;
                }

                String armedMode = state.get("armedMode").toString();
                String armedState = state.get("armedState").toString();
                Boolean portsOpen = Boolean.valueOf(state.get("portsOpen").toString());
                HashMap<String, Object> buttonsState = buildDataForMainActivityFrom(armedMode, armedState, portsOpen);

                buttonsChangedInternally = true;
                updateModeStateButtons(buttonsState);
                switchPorts.setChecked(portsOpen);
                buttonsChangedInternally = false;


                armedModeText = (TextView) findViewById(R.id.textViewForSwitchSystemMode);
                armedModeText.setText(buttonsState.get("systemModeText").toString());
                if ("auto".equalsIgnoreCase(armedMode)) {
                    armedModeText.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.red));
                } else {
                    armedModeText.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.green));
                }

                armedStateText = (TextView) findViewById(R.id.textViewForSwitchSystemState);
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

        builder.append(String.format("%01d:", hours))
                .append(String.format("%02d:", minutes))
                .append(String.format("%02d", seconds));

        return builder.toString();
    }

    private void updateModeStateButtons(HashMap<String, Object> statusesData) {
        systemMode.setChecked((boolean) statusesData.get("systemModeChecked"));
        systemState.setChecked((boolean) statusesData.get("systemStateChecked"));
        systemState.setEnabled((boolean) statusesData.get("systemStateEnabled"));
    }

    private void buildHandlers() {
        serverLastPingHandler = new Handler();
        serverLastPingRunnable = new Runnable() {
            @Override
            public void run() {
                TextView serverLastPingTextValue = (TextView) findViewById(R.id.textViewServerLastPingValue);
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
        Spinner serversList = (Spinner) findViewById(R.id.spinnerServerList);
        ArrayAdapter<String> serversArray = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_item, getServersList());
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
                    refreshFirebaseListeners();

                    Intent intent = new Intent();
                    intent.setAction(HSC_SERVER_CHANGED);
                    sendBroadcast(intent);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void requestPermissions() {
        ArrayList<String> permissionsToBeRequested = new ArrayList<>();

        permissionsToBeRequested.addAll(Arrays.asList(retrievePermissions()));

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

            HashMap<String, Object> statusesData = (HashMap<String, Object>) intent.getSerializableExtra("HSC_STATUSES_UPDATED");

            if (statusesData == null) {
                return;
            }

            armedModeText.setText((String) statusesData.get("systemModeText"));
            armedModeText.setTextColor((int) statusesData.get("systemModeTextColor"));

            armedStateText.setText((String) statusesData.get("systemStateText"));
            armedStateText.setTextColor((int) statusesData.get("systemStateTextColor"));

            switchPorts.setChecked((boolean) statusesData.get("portsState"));

            updateModeStateButtons(statusesData);

            buttonsChangedInternally = false;
        }
    }
}
