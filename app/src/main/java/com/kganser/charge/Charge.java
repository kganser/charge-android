package com.kganser.charge;

import android.app.Application;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

public class Charge extends Application {
    private Tracker tracker;
    public synchronized Tracker getTracker() {
        if (tracker == null) {
            GoogleAnalytics ga = GoogleAnalytics.getInstance(this);
            ga.enableAutoActivityReports(this);
            tracker = ga.newTracker("UA-27434499-3");
            tracker.enableAdvertisingIdCollection(true);
            tracker.enableExceptionReporting(true);
            tracker.enableAutoActivityTracking(true);
        }
        return tracker;
    }
}
