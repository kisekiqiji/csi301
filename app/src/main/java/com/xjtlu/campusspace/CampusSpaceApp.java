package com.xjtlu.campusspace;

import android.app.Application;

import com.xjtlu.campusspace.core.DemoClock;

/** Restores the persisted demo clock before any screen or repository reads the time. */
public class CampusSpaceApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        DemoClock.restore(this);
    }
}
