package com.rudyii.hsw.client.activities;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.rudyii.hsw.client.R;

/**
 * Created by Jack on 29.12.2017.
 */

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        String applicationVersion = "0";
        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);

            applicationVersion = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        TextView aboutTextView = (TextView) findViewById(R.id.aboutTextView);
        aboutTextView.setText(String.format(getResources().getString(R.string.text_about), applicationVersion));
    }
}
