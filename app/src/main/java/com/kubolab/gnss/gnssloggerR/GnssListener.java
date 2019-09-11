package com.kubolab.gnss.gnssloggerR;

import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.OnNmeaMessageListener;
import android.os.Bundle;

/**  A class representing an interface for logging GPS information. */
public interface GnssListener {

    /** @see LocationListener#onProviderEnabled(String) */
    void onProviderEnabled(String provider);
    /** @see LocationListener#onProviderDisabled(String) */
    void onProviderDisabled(String provider);
    /** @see LocationListener#onLocationChanged(Location) */
    void onLocationChanged(Location location);
    /** @see LocationListener#onStatusChanged(String, int, Bundle) */
    void onLocationStatusChanged(String provider, int status, Bundle extras);
    /**
     * @see android.location.GnssMeasurementsEvent.Callback#
     * onGnssMeasurementsReceived(GnssMeasurementsEvent)
     */
    void onGnssMeasurementsReceived(GnssMeasurementsEvent event);
    /** @see GnssMeasurementsEvent.Callback#onStatusChanged(int) */
    void onGnssMeasurementsStatusChanged(int status);
    /**
     * @see GnssNavigationMessage.Callback#
     * onGnssNavigationMessageReceived(GnssNavigationMessage)
     */
    void onGnssNavigationMessageReceived(GnssNavigationMessage event);
    /** @see GnssNavigationMessage.Callback#onStatusChanged(int) */
    void onGnssNavigationMessageStatusChanged(int status);
    /** @see GnssStatus.Callback#onSatelliteStatusChanged(GnssStatus) */
    void onGnssStatusChanged(GnssStatus gnssStatus);
    /** Called when the listener is registered to listen to GNSS events */
    void onListenerRegistration(String listener, boolean result);
    /** @see OnNmeaMessageListener#onNmeaMessage(String, long) */
    void onNmeaReceived(long l, String s);
    //void onSensorListener(String listener,float Azimuth,float accZ);
}
