package com.ingchips;

import android.app.Application;

import com.tencent.bugly.crashreport.CrashReport;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CrashReport.initCrashReport(getApplicationContext(), "4fbdc243d3", true);
    }
}
