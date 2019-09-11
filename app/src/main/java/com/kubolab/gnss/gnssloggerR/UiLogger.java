package com.kubolab.gnss.gnssloggerR;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import com.kubolab.gnss.gnssloggerR.Mathutil;

import org.apache.commons.math3.util.MathUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Arrays;
import java.util.List;

/**
 * A class representing a UI logger for the application. Its responsibility is show information in
 * the UI.
 */
public class UiLogger implements GnssListener {

    private static final long EARTH_RADIUS_METERS = 6371000;
    private static final double GPS_L1_FREQ = 154.0 * 10.23e6;  //1575.42MHz
    private static final double GPS_L5_FREQ=115.0 * 10.23e6; //1176.45MHz
    private static final double SPEED_OF_LIGHT = 299792458.0; //[m/s]
    private static final double GPS_L1_WAVELENGTH = SPEED_OF_LIGHT/GPS_L1_FREQ;
    private static final double GPS_L5_WAVELENGTH = SPEED_OF_LIGHT/GPS_L5_FREQ;

    private static final int USED_COLOR = Color.rgb(0x4a, 0x5f, 0x70);
    private double trueAzimuth;
    private double Declination;
    private int[] GLONASSFREQ = {1,-4,5,6,1,-4,5,6,-2,-7,0,-1,-2,-7,0,-1,4,-3,3,2,4,-3,3,2};
    private int leapseconds = 18;
    private final Context mContext;
    final float TOLERANCE_MHZ = 1e8f;
    //private double LAST_CARRIER_PHASE = 0;
    //private double DIFF_CARRIER_PHASE = 0;
    //private double SMOOTHED_PSEUDORANGE = 0.0;
    private double SMOOTHER_RATE = 0.01;
    private double[] CURRENT_SMOOTHER_RATE = new double[300];
    private double[] LAST_DELTARANGE = new double[300];
    private double[] LAST_SMOOTHED_PSEUDORANGE = new double[300];
    //private int SMOOTHER_RATE_MAX = 10;

    private boolean gnssStatusReady = false;
    private boolean initialize = false;

    int MaxSatelliteIndex = 36;
    String array[][] = new String[MaxSatelliteIndex][4];

    public static boolean RINEXIONOK = false;

    //Navigation Message用
    //private int[][] NavSatelliteSvid = new int[5][300];
    List<String> NavSatelliteSvid = new ArrayList<>();


    public UiLogger(Context context) {
        this.mContext = context;
        if(initialize == false){
            Arrays.fill(LAST_DELTARANGE,0.0);
            Arrays.fill(CURRENT_SMOOTHER_RATE,1.0);
            Arrays.fill(LAST_SMOOTHED_PSEUDORANGE,0.0);
            initialize = true;
            Log.d("UiLogger","Initialize complete");
        }
    }

    private LoggerFragment.UIFragmentComponent mUiFragmentComponent;

    public synchronized LoggerFragment.UIFragmentComponent getUiFragmentComponent() {
        return mUiFragmentComponent;
    }

    public synchronized void setUiFragmentComponent(LoggerFragment.UIFragmentComponent value) {
        mUiFragmentComponent = value;
    }

    private Logger2Fragment.UIFragment2Component mUiFragment2Component;

    public synchronized Logger2Fragment.UIFragment2Component getUiFragment2Component() {
        return mUiFragment2Component;
    }

    public synchronized void setUiFragment2Component(Logger2Fragment.UIFragment2Component value) {
        mUiFragment2Component = value;
    }

    private Logger3Fragment.UIFragment3Component mUiFragment3Component;

    public synchronized Logger3Fragment.UIFragment3Component getUiFragment3Component() {
        return mUiFragment3Component;
    }

    public synchronized void setUiFragment3Component(Logger3Fragment.UIFragment3Component value) {
        mUiFragment3Component = value;
    }

    private SettingsFragment.UIFragmentSettingComponent mUISettingComponent;

    public synchronized SettingsFragment.UIFragmentSettingComponent getUISettingComponent() {
        return mUISettingComponent;
    }

    public synchronized void setUISettingComponent(SettingsFragment.UIFragmentSettingComponent value) {
        mUISettingComponent = value;
    }


    @Override
    public void onProviderEnabled(String provider) {
        logLocationEvent("onProviderEnabled: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        logLocationEvent("onProviderDisabled: " + provider);
    }

    @Override
    public void onLocationChanged(Location location) {
        trueAzimuth = location.getBearing();
        //磁気偏角を計算
        double Longitude = location.getLongitude();
        double Latitude = location.getLatitude();
        double deltaPhi = Latitude - 37;
        double deltaLamda = Longitude - 138;
        Declination = 757.201 + 18.750*deltaPhi - 6.761*deltaLamda - 0.059*Math.pow(deltaPhi,2) - 0.014 * deltaPhi * deltaLamda - 0.579 * Math.pow(deltaLamda,2);
        Declination = Declination / 100;
        BigDecimal x = new BigDecimal(Declination);
        x = x.setScale(1,BigDecimal.ROUND_HALF_UP);
        Declination = x.doubleValue();

        //location.getTime();
        //LoggerFragment.UIFragmentComponent component = getUiFragmentComponent();
        //component.LocationTextFragment("Google(" + location.getProvider() + ")",String.format("%f",location.getLatitude()),String.format("%f",location.getLongitude()),String.format("%f",location.getAltitude()),0);

        //logLocationEvent("onLocationChanged: " + location);
    }

    @Override
    public void onLocationStatusChanged(String provider, int status, Bundle extras) {
        /*String message =
                String.format(
                        "onStatusChanged: provider=%s, status=%s, extras=%s",
                        provider, locationStatusToString(status), extras);
        logLocationEvent(message);*/
    }

    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
        LoggerFragment.UIFragmentComponent component = getUiFragmentComponent();
        if(SettingsFragment.SMOOTHER_RATE_RESET_FLAG_UI){
            Arrays.fill(LAST_DELTARANGE,0.0);
            Arrays.fill(CURRENT_SMOOTHER_RATE,1.0);
            Arrays.fill(LAST_SMOOTHED_PSEUDORANGE,0.0);
            SettingsFragment.SMOOTHER_RATE_RESET_FLAG_UI = false;
        }
        array = gnssMessageToString(event,event.getClock());
        component.logTextFragment("", "", array);
        String GNSSStr = gnssClockToString(event.getClock());
        component.GNSSClockLog(GNSSStr);
        //logMeasurementEvent("onGnsssMeasurementsReceived: " + measurements);
    }

    @Override
    public void onGnssMeasurementsStatusChanged(int status) {
        if(gnssMeasurementsStatusToString(status) != "READY"){
            gnssStatusReady = false;
        }else {
            gnssStatusReady = true;
        }
        //UIFragmentSettingComponent component = getUISettingComponent();
        //component.SettingErrorFragment(status);
        SettingsFragment.GNSSMeasurementReadyMode = status;
        //logMeasurementEvent("onStatusChanged: " + gnssMeasurementsStatusToString(status));
    }

    @Override
    public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
        LoggerFragment.UIFragmentComponent component = getUiFragmentComponent();
        //logNavigationMessageEvent("onGnssNavigationMessageReceived: " + event);
        if(event.getStatus() != GnssNavigationMessage.STATUS_PARITY_PASSED && event.getStatus() != GnssNavigationMessage.STATUS_PARITY_REBUILT){
            component.NavigationIONText("PARITY CHECK FAILED","#FF0000",0);
            component.NavigationIONText("PARITY CHECK FAILED","#FF0000",1);
            component.NavigationIONText("PARITY CHECK FAILED","#FF0000",2);
            Log.i("Navigation Parity",String.valueOf(event.getStatus()));
            return;
        }
        GnssNavigationConv mGnssNavigationConv = new GnssNavigationConv();
        SQLiteDatabase NavDB;
        SQLiteManager hlpr = new SQLiteManager(mContext);
        NavDB = hlpr.getWritableDatabase();
        int state = mGnssNavigationConv.onNavMessageReported(event.getSvid(),event.getType(),event.getMessageId(),event.getSubmessageId(),event.getData(),mContext);
        if(state == 0){
            component.NavigationIONText("UPDATE FAILED","#FF0000",0);
            component.NavigationIONText("UPDATE FAILED","#FF0000",1);
            component.NavigationIONText("UPDATE FAILED","#FF0000",2);
        }else if(state == 1 || RINEXIONOK){
            component.NavigationIONText("VALID","#40FF00",0);
            component.NavigationIONText("VALID","#40FF00",1);
            component.NavigationIONText("VALID","#40FF00",2);
        }else {
            component.NavigationIONText("SYNCHRONIZING","#FF8000",0);
            component.NavigationIONText("SYNCHRONIZING","#FF8000",1);
            component.NavigationIONText("SYNCHRONIZING","#FF8000",2);
        }
        NavDB.close();
    }

    @Override
    public void onGnssNavigationMessageStatusChanged(int status) {
        //logNavigationMessageEvent("onStatusChanged: " + getGnssNavigationMessageStatus(status));
    }

    @Override
    public void onGnssStatusChanged(GnssStatus gnssStatus) {
        Logger2Fragment.UIFragment2Component component2 = getUiFragment2Component();
        if(gnssStatusReady == false){
            return;
        }
        String[] SVID = new String[50];
        float[][] pos = new float[50][2];
        int maxSat = gnssStatus.getSatelliteCount();
        for(int i = 0;i < maxSat;i++){
            if(gnssStatus.getConstellationType(i) == GnssStatus.CONSTELLATION_GPS) {
                SVID[i] ="G" + String.format("%02d",gnssStatus.getSvid(i));
                pos[i][0] = gnssStatus.getAzimuthDegrees(i);
                pos[i][1] = gnssStatus.getElevationDegrees(i);
            }else if(gnssStatus.getConstellationType(i) == GnssStatus.CONSTELLATION_QZSS && SettingsFragment.useQZ){
                SVID[i] ="J" + String.format("%02d",gnssStatus.getSvid(i) - 192);
                pos[i][0] = gnssStatus.getAzimuthDegrees(i);
                pos[i][1] = gnssStatus.getElevationDegrees(i);
            }else if(gnssStatus.getConstellationType(i) == GnssStatus.CONSTELLATION_GLONASS && SettingsFragment.useGL){
                SVID[i] ="R" + String.format("%02d",gnssStatus.getSvid(i));
                pos[i][0] = gnssStatus.getAzimuthDegrees(i);
                pos[i][1] = gnssStatus.getElevationDegrees(i);
            }else if(gnssStatus.getConstellationType(i) == GnssStatus.CONSTELLATION_GALILEO && SettingsFragment.useGA){
                SVID[i] ="E" + String.format("%02d",gnssStatus.getSvid(i));
                pos[i][0] = gnssStatus.getAzimuthDegrees(i);
                pos[i][1] = gnssStatus.getElevationDegrees(i);
            }else if(gnssStatus.getConstellationType(i) == GnssStatus.CONSTELLATION_BEIDOU && SettingsFragment.useBD){
                SVID[i] ="C" + String.format("%02d",gnssStatus.getSvid(i));
                pos[i][0] = gnssStatus.getAzimuthDegrees(i);
                pos[i][1] = gnssStatus.getElevationDegrees(i);
            }else if(gnssStatus.getConstellationType(i) == GnssStatus.CONSTELLATION_SBAS && SettingsFragment.useSB){
                SVID[i] ="S" + String.format("%02d",gnssStatus.getSvid(i));
                pos[i][0] = gnssStatus.getAzimuthDegrees(i);
                pos[i][1] = gnssStatus.getElevationDegrees(i);
            }else if(gnssStatus.getConstellationType(i) == GnssStatus.CONSTELLATION_UNKNOWN && SettingsFragment.ResearchMode){
                SVID[i] ="U" + String.format("%02d",gnssStatus.getSvid(i));
                pos[i][0] = gnssStatus.getAzimuthDegrees(i);
                pos[i][1] = gnssStatus.getElevationDegrees(i);
            }
        }
        component2.log2TextFragment(SVID,pos,maxSat);
        //logStatusEvent("onGnssStatusChanged: " + gnssStatusToString(gnssStatus));
    }

    @Override
    public void onNmeaReceived(long timestamp, String s) {
        /*logNmeaEvent(String.format("onNmeaReceived: timestamp=%d, %s", timestamp, s));*/
    }

    @Override
    public void onListenerRegistration(String listener, boolean result) {
        logEvent("Registration", String.format("add%sListener: %b", listener, result), USED_COLOR);
    }

    public void onSensorListener(String listener,double azimuth,float accZ , float altitude){
        Logger2Fragment.UIFragment2Component component2 = getUiFragment2Component();
        Logger3Fragment.UIFragment3Component component3 = getUiFragment3Component();

        double TrueAzimuth = azimuth + Declination;
        if(TrueAzimuth >= 360){
            TrueAzimuth = 360 - TrueAzimuth;
        }
        if(component2 != null) {
            component2.log2SensorFragment(azimuth);
        }
        if(component3 != null) {
            component3.log3TextFragment(listener);
        }
        if(SettingsFragment.ResearchMode) {
            logText("Sensor", listener + "\nDeclination : " + Declination + "\nTrueAzimuth : " + Math.abs(TrueAzimuth), USED_COLOR);
            //Log.d("Device Sensor",listener);
        }else {
            logText("Sensor", listener, USED_COLOR);
            //Log.d("Device Sensor",listener);
        }
    }

    public void SensorSpec(String sensorSpec[]) {
        SettingsFragment.UIFragmentSettingComponent component = getUISettingComponent();
        if (component != null) {
            component.SettingFragmentSensorSpec(sensorSpec);
        }
    }

    public void SensorAvairable(String sensorAvairable[]) {
        SettingsFragment.UIFragmentSettingComponent component = getUISettingComponent();
        if (component != null) {
            component.SettingFragmentSensorAvairable(sensorAvairable);
        }
    }


    public void onSensorRawListener(String sensorRaw[]) {
        Logger3Fragment.UIFragment3Component component = getUiFragment3Component();
        if (component != null) {
            component.log3SensorRawFragment(sensorRaw);
        }
    }

    private void logMeasurementEvent(String event) {
        logEvent("Measurement", event, USED_COLOR);
    }

    private void logNavigationMessageEvent(String event) {
        logEvent("NavigationMsg", event, USED_COLOR);
    }

    private void logStatusEvent(String event) {
        logEvent("Status", event, USED_COLOR);
    }

    private void logNmeaEvent(String event) {
        logEvent("Nmea", event, USED_COLOR);
    }

    private void logEvent(String tag, String message, int color) {
        String composedTag = GnssContainer.TAG + tag;
        Log.d(composedTag, message);
        logText(tag, message, color);
    }

    private void logText(String tag, String text, int color) {
        LoggerFragment.UIFragmentComponent component = getUiFragmentComponent();
        if (component != null) {
            if(tag == "Sensor"){
                //component.SensorlogTextFragment(text,color);
            }
            else{
                //component.logTextFragment(tag, text, color);
            }
        }
    }

    private void SublogText(String tag, String text, int color) {
        LoggerFragment.UIFragmentComponent component = getUiFragmentComponent();
        if (component != null) {
            if(tag == "Sensor"){
                //component.SensorlogTextFragment(text,color);
            }
            else{
                //component.logTextFragment(tag, text, color);
            }
        }
    }

    private String locationStatusToString(int status) {
        switch (status) {
            case LocationProvider.AVAILABLE:
                return "AVAILABLE";
            case LocationProvider.OUT_OF_SERVICE:
                return "OUT_OF_SERVICE";
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                return "TEMPORARILY_UNAVAILABLE";
            default:
                return "<Unknown>";
        }
    }

    private String gnssMeasurementsStatusToString(int status) {
        switch (status) {
            case GnssMeasurementsEvent.Callback.STATUS_NOT_SUPPORTED:
                return "NOT_SUPPORTED";
            case GnssMeasurementsEvent.Callback.STATUS_READY:
                return "READY";
            case GnssMeasurementsEvent.Callback.STATUS_LOCATION_DISABLED:
                return "GNSS_LOCATION_DISABLED";
            default:
                return "<Unknown>";
        }
    }

    private String getGnssNavigationMessageStatus(int status) {
        switch (status) {
            case GnssNavigationMessage.STATUS_UNKNOWN:
                return "Status Unknown";
            case GnssNavigationMessage.STATUS_PARITY_PASSED:
                return "READY";
            case GnssNavigationMessage.STATUS_PARITY_REBUILT:
                return "Status Parity Rebuilt";
            default:
                return "<Unknown>";
        }
    }

    private String gnssStatusToString(GnssStatus gnssStatus) {

        StringBuilder builder = new StringBuilder("SATELLITE_STATUS | [Satellites:\n");
        for(int i = 0; i < gnssStatus.getSatelliteCount(); i++) {
            builder
                    .append("Type = ")
                    .append(getConstellationName(gnssStatus.getConstellationType(i)))
                    .append(", ");
            builder.append("Svid = ").append(gnssStatus.getSvid(i)).append(", ");
            //builder.append("Elevation = ").append(gnssStatus.getElevationDegrees(i)).append(", ");
            //builder.append("Azimuth = ").append(gnssStatus.getAzimuthDegrees(i)).append(", ");
            //builder.append("hasEphemeris = ").append(gnssStatus.hasEphemerisData(i)).append(", ");
            builder.append("usedInFix = ").append(gnssStatus.usedInFix(i)).append("\n");
        }
        builder.append("]");
        return builder.toString();
    }

    private String gnssClockToString(GnssClock gnssClock){
        String ClockStr = "";
        if(gnssStatusReady == false){
            return "GNSS Measurements NOT READY or SUPPORTED";
        }
        if(gnssClock.getHardwareClockDiscontinuityCount() == -1){
            ClockStr = "WARING!! HARDWARE Clock may broken";
        }else{
            double tRxSeconds;
            double TimeNanos = gnssClock.getTimeNanos();
            double FullBiasNanos = gnssClock.getFullBiasNanos();
            double BiasNanos = gnssClock.getBiasNanos();
            double weekNumber = Math.floor(- (gnssClock.getFullBiasNanos() * 1e-9 / 604800));
            double weekNumberNanos = weekNumber * 604800 * 1e9;
            if (gnssClock.hasBiasNanos() == false) {
                tRxSeconds = (gnssClock.getTimeNanos() - gnssClock.getFullBiasNanos() - weekNumberNanos) * 1e-9;
            } else {
                tRxSeconds = (gnssClock.getTimeNanos() - gnssClock.getFullBiasNanos() - gnssClock.getBiasNanos() - weekNumberNanos) * 1e-9;
            }
            String DeviceName = Build.DEVICE;
            FileLogger.GPSWStoGPST gpswStoGPST = new FileLogger.GPSWStoGPST();
            FileLogger.ReturnValue value = gpswStoGPST.method(weekNumber,tRxSeconds);
            ClockStr = String.format("%d / %d / %d / %d : %d : %f",value.Y,value.M,value.D,value.h,value.m,value.s);
            SettingsFragment.UIFragmentSettingComponent component = getUISettingComponent();
            component.SettingTextFragment(String.format("%d_%d_%d_%d_%d",value.Y,value.M,value.D,value.h,value.m));
            Calendar Start = Calendar.getInstance();
            Calendar End = Calendar.getInstance();
            Start.set(Calendar.YEAR,value.Y);
            Start.set(Calendar.MONTH,1);
            Start.set(Calendar.DATE,1);
            End.set(Calendar.YEAR,value.Y);
            End.set(Calendar.MONTH,value.M);
            End.set(Calendar.DATE,value.D);
            int spent = calcspent(Start,End);
            SettingsFragment.FTP_SERVER_DIRECTORY = String.format("%4d/brdc/brdc%03d0.%2dn.Z",value.Y,spent,value.Y - 2000);
            component.SettingFTPDirectory(String.format("%4d/brdc/brdc%03d0.%2dn.Z",value.Y,spent,value.Y - 2000));
            final Calendar calendar = Calendar.getInstance();
            Log.d("GNSSClock",String.valueOf(gnssClock.getBiasUncertaintyNanos()));
        }
        return ClockStr;
    }

    private String[][] gnssMessageToString(GnssMeasurementsEvent event, GnssClock gnssClock){
        String[][] array = new String[MaxSatelliteIndex][5];
        //builder.append("GNSSClock = ").append(event.getClock().toString()).append("\n");
        //double GPSWeek = Math.floor((double) (gnssClock.getTimeNanos()) * 1e-9 / 604800);
        //long GPSWeekNanos = (long) GPSWeek * (long) (604800 * 1e9);
        //double tRxNanos = 0;
        //if (gnssClock.hasBiasNanos() == false) {
            //tRxNanos = (gnssClock.getTimeNanos() - gnssClock.getFullBiasNanos());
        //} else {
            //tRxNanos = (gnssClock.getTimeNanos() - gnssClock.getFullBiasNanos() - gnssClock.getBiasNanos());
        //}
        //double tRxSeconds = tRxNanos * 1e-9;
        //雷神は値がひっくり返っているため補正する必要あり
        //builder.append("GPSWeek = ").append(GPSWeek).append("\n");
        //builder.append("GPSWeekSec = ").append(tRxSeconds).append("\n");
        //builder.append("GPSWeek = ").append(Math.floor(gnssClock.getFullBiasNanos())).append("\n");
        //builder.append("GPSWeekNanos = ").append(tRxNanos * 1e-9).append("\n");
        //builder.append("FullBiasSeconds = ").append((double)(gnssClock.getFullBiasNanos() * 1e-9)).append("\n");
        //builder.append("TimeSeconds = ").append((double)(gnssClock.getTimeNanos()* 1e-9)).append("\n");
        //builder.append("BiasSeconds = ").appen
        // kfleavfesthoszdeoxdgilojfgytd((double)(gnssClock.getBiasNanos()* 1e-9)).append("\n");
        if(gnssStatusReady == false){
            return array;
        }
        int arrayRow = 0;
        boolean CheckClockSync = false;
        for (GnssMeasurement measurement : event.getMeasurements()) {
        if((measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS && SettingsFragment.useQZ) || (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS) || ((measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS) && (SettingsFragment.useGL == true)) || ((measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO)&&(SettingsFragment.useGA)) || (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU && SettingsFragment.useBD)) {
            double weekNumber = Math.floor(-(gnssClock.getFullBiasNanos() * 1e-9 / 604800));
            //Log.d("WeekNumber",String.valueOf(weekNumber));
            double weekNumberNanos = weekNumber * 604800 * 1e9;
            //Log.d("WeekNumberNanos",String.valueOf(weekNumberNanos));
            //double tRxNanos = gnssClock.getTimeNanos() - gnssClock.getFullBiasNanos() - weekNumberNanos;
            double tRxNanos = gnssClock.getTimeNanos() - gnssClock.getFullBiasNanos() - weekNumberNanos;
            if (gnssClock.hasBiasNanos()) {
                tRxNanos = tRxNanos - gnssClock.getBiasNanos();
            }
            if (measurement.getTimeOffsetNanos() != 0) {
                tRxNanos = tRxNanos - measurement.getTimeOffsetNanos();
            }
            double tRxSeconds = tRxNanos * 1e-9;
            double tTxSeconds = measurement.getReceivedSvTimeNanos() * 1e-9;
            if ((measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS)) {
                double tRxSeconds_GLO = tRxSeconds % 86400;
                double tTxSeconds_GLO = tTxSeconds - 10800 + leapseconds;
                if (tTxSeconds_GLO < 0) {
                    tTxSeconds_GLO = tTxSeconds_GLO + 86400;
                }
                tRxSeconds = tRxSeconds_GLO;
                tTxSeconds = tTxSeconds_GLO;
            }
            if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU) {
                double tRxSeconds_BDS = tRxSeconds;
                double tTxSeconds_BDS = tTxSeconds + leapseconds - 4;
                if (tTxSeconds_BDS > 604800) {
                    tTxSeconds_BDS = tTxSeconds_BDS - 604800;
                }
                /*Log.i("PRN", String.format("%s%2d", getConstellationName(measurement.getConstellationType()), measurement.getSvid()));
                Log.i("tRxSeconds", String.valueOf(tRxSeconds_BDS));
                Log.i("tTxSeconds", String.valueOf(tTxSeconds_BDS));//53333*/
                tRxSeconds = tRxSeconds_BDS;
                tTxSeconds = tTxSeconds_BDS;
            }

            /*急場の変更！！*/
            String DeviceName = Build.DEVICE;
            //Log.d("DEVICE",DeviceName);
            /*急場の変更！！*/
            //GPS週のロールオーバーチェック
            double prSeconds = tRxSeconds - tTxSeconds;
            boolean iRollover = prSeconds > 604800 / 2;
            if (iRollover) {
                double delS = Math.round(prSeconds / 604800) * 604800;
                double prS = prSeconds - delS;
                double maxBiasSeconds = 10;
                if (prS > maxBiasSeconds) {
                    Log.e("RollOver", "Rollover Error");
                    iRollover = true;
                } else {
                    tRxSeconds = tRxSeconds - delS;
                    prSeconds = tRxSeconds - tTxSeconds;
                    iRollover = false;
                }
            }
            Log.i("PRN", String.format("%s%2d", getConstellationName(measurement.getConstellationType()), measurement.getSvid()));
            Log.i("tRxSeconds", String.valueOf(tRxSeconds));
            Log.i("tTxSeconds", String.valueOf(tTxSeconds));
            //Log.d("tRxSeconds",tRxStr);
            //Log.d("tTxSeconds",tTxStr);
            double prm = prSeconds * 2.99792458e8; //コード擬似距離

            if (SettingsFragment.useDualFreq) {
                if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 115.0 * 10.23e6f, TOLERANCE_MHZ)) {
                    if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS) {
                        //Log.d("QZSS","QZSS Detected");
                        array[arrayRow][0] = "J" + String.format("%02d  ", measurement.getSvid() - 192);
                    } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS) {
                        array[arrayRow][0] = "R" + String.format("%02d  ", measurement.getSvid());
                    } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS) {
                        array[arrayRow][0] = "G" + String.format("%02d  ", measurement.getSvid());
                    } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO) {
                        array[arrayRow][0] = "E" + String.format("%02d  ", measurement.getSvid());
                    } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU) {
                        array[arrayRow][0] = "C" + String.format("%02d  ", measurement.getSvid());
                    }
                }
                //Log.d("STATE",String.valueOf(measurement.getState());
                if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 115.0 * 10.23e6f, TOLERANCE_MHZ)) {
                    if (iRollover) {
                        array[arrayRow][1] = "ROLLOVER_ERROR";
                        prm = 0.0;
                    } else if (prSeconds < 0 || prSeconds > 0.5) {
                        array[arrayRow][1] = "INVALID_VALUE";
                        prm = 0.0;
                    } else if (getStateName(measurement.getState()) == "1") {
                        array[arrayRow][1] = String.format("%14.3f", prm);
                        CheckClockSync = true;
                    } else {
                        array[arrayRow][1] = getStateName(measurement.getState());
                    }
                }
            /*builder.append("GNSSClock = ").append(event.getClock().getTimeNanos()).append("\n");
            builder.append("Svid = ").append(measurement.getSvid()).append(", ");
            builder.append("Cn0DbHz = ").append(measurement.getCn0DbHz()).append(", ");
            builder.append("PseudoRange = ").append(prm).append("\n");
            builder.append("tRxSeconds = ").append(tRxSeconds).append("\n");
            builder.append("tTxSeconds = ").append(tTxSeconds).append("\n");*/
                //builder.append("FullCarrierCycles = ").append(measurement.getCarrierCycles() + measurement.getCarrierPhase()).append("\n");

                if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 115.0 * 10.23e6f, TOLERANCE_MHZ)) {
                    if (SettingsFragment.CarrierPhase == true) {
                        Log.i("Carrier Freq", String.valueOf(measurement.getCarrierFrequencyHz()));
                        Log.i("Carrier Frequ", String.valueOf(measurement.hasCarrierFrequencyHz()));
                        if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_CYCLE_SLIP) {
                            // 周波数　メモ  float abc=measurement.getCarrierFrequencyHz();
                            //  array[arrayRow][2] = String.valueOf(abc);
                            array[arrayRow][2] = "Cycle slip";
                        } else if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_RESET) {
                            array[arrayRow][2] = "RESET";
                        } else if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_UNKNOWN) {
                            array[arrayRow][2] = "UNKNOWN";
                        } else {
                            if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS || measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO || measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS) {
                                if (measurement.hasCarrierPhase() && measurement.hasCarrierCycles()) {
                                    array[arrayRow][2] = String.format("%14.3f", measurement.getCarrierCycles() + measurement.getCarrierPhase());
                                } else {
                                    array[arrayRow][2] = String.format("%14.3f", measurement.getAccumulatedDeltaRangeMeters() / GPS_L5_WAVELENGTH);
                                }
                            } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS) {
                                if (measurement.getSvid() <= 24) {
                                    if (measurement.hasCarrierPhase() && measurement.hasCarrierCycles()) {
                                        array[arrayRow][2] = String.format("%14.3f", measurement.getCarrierCycles() + measurement.getCarrierPhase());
                                    } else {
                                        array[arrayRow][2] = String.format("%14.3f", measurement.getAccumulatedDeltaRangeMeters() / GLONASSG1WAVELENGTH(measurement.getSvid()));
                                    }
                                } else {
                                    array[arrayRow][2] = "NOT_SUPPORTED";
                                }
                            } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU) {
                                if (measurement.getSvid() < 30) {
                                    if (measurement.hasCarrierPhase() && measurement.hasCarrierCycles()) {
                                        array[arrayRow][2] = String.format("%14.3f", measurement.getCarrierCycles() + measurement.getCarrierPhase());
                                    } else {
                                        array[arrayRow][2] = String.format("%14.3f", measurement.getAccumulatedDeltaRangeMeters() / BEIDOUWAVELENGTH(measurement.getSvid()));
                                    }
                                } else {
                                    array[arrayRow][2] = "NOT_SUPPORTED";
                                }
                            }
                        }
                        int index = measurement.getSvid();
                        if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS) {
                            index = index + 64;
                        }
                        if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU) {
                            index = index + 200;
                        }
                        if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO) {
                            index = index + 235;
                        }
                        if (!SettingsFragment.usePseudorangeRate && measurement.getAccumulatedDeltaRangeState() != GnssMeasurement.ADR_STATE_VALID) {
                            CURRENT_SMOOTHER_RATE[index] = 1.0;
                        }
                        if (SettingsFragment.usePseudorangeSmoother && prm != 0.0) {
                            if (index < 300) {
                                if (SettingsFragment.usePseudorangeRate) {
                                    LAST_SMOOTHED_PSEUDORANGE[index] = CURRENT_SMOOTHER_RATE[index] * prm + (1 - CURRENT_SMOOTHER_RATE[index]) * (LAST_SMOOTHED_PSEUDORANGE[index] + measurement.getPseudorangeRateMetersPerSecond());
                                    array[arrayRow][1] = String.format("%14.3f[FIX_PR]", LAST_SMOOTHED_PSEUDORANGE[index], " ", " ");
                                } else {
                                    if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_VALID) {
                                        LAST_SMOOTHED_PSEUDORANGE[index] = CURRENT_SMOOTHER_RATE[index] * prm + (1 - CURRENT_SMOOTHER_RATE[index]) * (LAST_SMOOTHED_PSEUDORANGE[index] + measurement.getAccumulatedDeltaRangeMeters() - LAST_DELTARANGE[index]);
                                        LAST_DELTARANGE[index] = measurement.getAccumulatedDeltaRangeMeters();
                                        CURRENT_SMOOTHER_RATE[index] = CURRENT_SMOOTHER_RATE[index] - SMOOTHER_RATE;
                                        if (CURRENT_SMOOTHER_RATE[index] <= 0) {
                                            CURRENT_SMOOTHER_RATE[index] = SMOOTHER_RATE;
                                        }
                                        array[arrayRow][1] = String.format("%14.3f[FIX_CF]", LAST_SMOOTHED_PSEUDORANGE[index], " ", " ");
                                    }
                                }
                            }
                        }
                    } else {
                        array[arrayRow][2] = "0";
                    }
                }
                if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 115.0 * 10.23e6f, TOLERANCE_MHZ)) {
                    array[arrayRow][3] = String.format("%2.1f", measurement.getCn0DbHz());
                }

                //array[arrayRow][3] = String.format("%2.1f",measurement.getCn0DbHz());
                //arrayRow++;
                if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 115.0 * 10.23e6f, TOLERANCE_MHZ)) {
                    //array[arrayRow][3] = String.format("%2.1f",measurement.getCn0DbHz());
                    //array[arrayRow][3] = String.format("%2.1f",measurement.getCarrierFrequencyHz()/1000000);
                    array[arrayRow][4] = getCarrierFrequencyLabel(measurement.getCarrierFrequencyHz());
                    arrayRow++;
                    // array[arrayRow][4]=String.format("%-8.3f",measurement.getCarrierFrequencyHz()/1000000);
                    // arrayRow++;
                }

            }
            else{
                if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6f, TOLERANCE_MHZ)) {
                    if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS) {
                        //Log.d("QZSS","QZSS Detected");
                        array[arrayRow][0] = "J" + String.format("%02d  ", measurement.getSvid() - 192);
                    } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS) {
                        array[arrayRow][0] = "R" + String.format("%02d  ", measurement.getSvid());
                    } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS) {
                        array[arrayRow][0] = "G" + String.format("%02d  ", measurement.getSvid());
                    } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO) {
                        array[arrayRow][0] = "E" + String.format("%02d  ", measurement.getSvid());
                    } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU) {
                        array[arrayRow][0] = "C" + String.format("%02d  ", measurement.getSvid());
                    }
                    //Log.d("STATE",String.valueOf(measurement.getState());
                    if (iRollover) {
                        array[arrayRow][1] = "ROLLOVER_ERROR";
                        prm = 0.0;
                    } else if (prSeconds < 0 || prSeconds > 0.5) {
                        array[arrayRow][1] = "INVALID_VALUE";
                        prm = 0.0;
                    } else if (getStateName(measurement.getState()) == "1") {
                        array[arrayRow][1] = String.format("%14.3f", prm);
                        CheckClockSync = true;
                    } else {
                        array[arrayRow][1] = getStateName(measurement.getState());
                    }
            /*builder.append("GNSSClock = ").append(event.getClock().getTimeNanos()).append("\n");
            builder.append("Svid = ").append(measurement.getSvid()).append(", ");
            builder.append("Cn0DbHz = ").append(measurement.getCn0DbHz()).append(", ");
            builder.append("PseudoRange = ").append(prm).append("\n");
            builder.append("tRxSeconds = ").append(tRxSeconds).append("\n");
            builder.append("tTxSeconds = ").append(tTxSeconds).append("\n");*/
                    //builder.append("FullCarrierCycles = ").append(measurement.getCarrierCycles() + measurement.getCarrierPhase()).append("\n");
                    if (SettingsFragment.CarrierPhase == true) {
                        //Log.i("Carrier Freq",String.valueOf(measurement.getCarrierFrequencyHz()));
                        if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_CYCLE_SLIP) {
                            array[arrayRow][2] = "CYCLE_SLIP";
                        } else if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_RESET) {
                            array[arrayRow][2] = "RESET";
                        } else if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_UNKNOWN) {
                            array[arrayRow][2] = "UNKNOWN";
                        } else {
                            if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS || measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO || measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS) {
                                if (measurement.hasCarrierPhase() && measurement.hasCarrierCycles()) {
                                    array[arrayRow][2] = String.format("%14.3f", measurement.getCarrierCycles() + measurement.getCarrierPhase());
                                } else {
                                    array[arrayRow][2] = String.format("%14.3f", measurement.getAccumulatedDeltaRangeMeters() / GPS_L1_WAVELENGTH);
                                }
                            } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS) {
                                if (measurement.getSvid() <= 24) {
                                    if (measurement.hasCarrierPhase() && measurement.hasCarrierCycles()) {
                                        array[arrayRow][2] = String.format("%14.3f", measurement.getCarrierCycles() + measurement.getCarrierPhase());
                                    } else {
                                        array[arrayRow][2] = String.format("%14.3f", measurement.getAccumulatedDeltaRangeMeters() / GLONASSG1WAVELENGTH(measurement.getSvid()));
                                    }
                                } else {
                                    array[arrayRow][2] = "NOT_SUPPORTED";
                                }
                            } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU) {
                                if (measurement.getSvid() < 30) {
                                    if (measurement.hasCarrierPhase() && measurement.hasCarrierCycles()) {
                                        array[arrayRow][2] = String.format("%14.3f", measurement.getCarrierCycles() + measurement.getCarrierPhase());
                                    } else {
                                        array[arrayRow][2] = String.format("%14.3f", measurement.getAccumulatedDeltaRangeMeters() / BEIDOUWAVELENGTH(measurement.getSvid()));
                                    }
                                } else {
                                    array[arrayRow][2] = "NOT_SUPPORTED";
                                }
                            }
                        }
                        int index = measurement.getSvid();
                        if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS) {
                            index = index + 64;
                        }
                        if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU) {
                            index = index + 200;
                        }
                        if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO) {
                            index = index + 235;
                        }
                        if (!SettingsFragment.usePseudorangeRate && measurement.getAccumulatedDeltaRangeState() != GnssMeasurement.ADR_STATE_VALID) {
                            CURRENT_SMOOTHER_RATE[index] = 1.0;
                        }
                        if (SettingsFragment.usePseudorangeSmoother && prm != 0.0) {
                            if (index < 300) {
                                if (SettingsFragment.usePseudorangeRate) {
                                    LAST_SMOOTHED_PSEUDORANGE[index] = CURRENT_SMOOTHER_RATE[index] * prm + (1 - CURRENT_SMOOTHER_RATE[index]) * (LAST_SMOOTHED_PSEUDORANGE[index] + measurement.getPseudorangeRateMetersPerSecond());
                                    array[arrayRow][1] = String.format("%14.3f[FIX_PR]", LAST_SMOOTHED_PSEUDORANGE[index], " ", " ");
                                } else {
                                    if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_VALID) {
                                        LAST_SMOOTHED_PSEUDORANGE[index] = CURRENT_SMOOTHER_RATE[index] * prm + (1 - CURRENT_SMOOTHER_RATE[index]) * (LAST_SMOOTHED_PSEUDORANGE[index] + measurement.getAccumulatedDeltaRangeMeters() - LAST_DELTARANGE[index]);
                                        LAST_DELTARANGE[index] = measurement.getAccumulatedDeltaRangeMeters();
                                        CURRENT_SMOOTHER_RATE[index] = CURRENT_SMOOTHER_RATE[index] - SMOOTHER_RATE;
                                        if (CURRENT_SMOOTHER_RATE[index] <= 0) {
                                            CURRENT_SMOOTHER_RATE[index] = SMOOTHER_RATE;
                                        }
                                        array[arrayRow][1] = String.format("%14.3f[FIX_CF]", LAST_SMOOTHED_PSEUDORANGE[index], " ", " ");
                                    }
                                }
                            }
                        }
                    } else {
                        array[arrayRow][2] = "0";
                    }
                    array[arrayRow][3] = String.format("%2.1f", measurement.getCn0DbHz());

                    array[arrayRow][4] = getCarrierFrequencyLabel(measurement.getCarrierFrequencyHz());

                    arrayRow++;
                }
            }
        }
    }
    if(CheckClockSync){
        SettingsFragment.GNSSClockSync = true;
    }else{
        SettingsFragment.GNSSClockSync = false;
    }
        return array;
}
    public static String getCarrierFrequencyLabel(float carrierFrequencyhz) {
        final float TOLERANCE_MHZ = 100000000f;

        if  (Mathutil.fuzzyEquals(carrierFrequencyhz, 154.0 * 10.23e6f, TOLERANCE_MHZ)) {

            return "L1";
        } //else if (Mathutil.fuzzyEquals(carrierFrequencyMhz, 1176.45f, TOLERANCE_MHZ)) {
          // return "L5";
     //  }
      else {
           return "L5";
      }
}
    private void logLocationEvent(String event) {
        logEvent("Location", event, USED_COLOR);
    }

    private String getStateName(int id){
        switch (id){
            case GnssMeasurement.STATE_BIT_SYNC:
                return "BIT_SYNC";
            case GnssMeasurement.STATE_SUBFRAME_SYNC:
                return "SUBFRAME_SYNC";
            case GnssMeasurement.STATE_SYMBOL_SYNC:
                return "SYMBOL_SYNC";
            case GnssMeasurement.STATE_MSEC_AMBIGUOUS:
                return "MSEC_AMBIGUOUS";
            case GnssMeasurement.STATE_CODE_LOCK:
                return "CODE_LOCK";
            case GnssMeasurement.STATE_UNKNOWN:
                return "UNKNOWN";
            case GnssMeasurement.STATE_TOW_DECODED:
                return "TOW_DECODED";
            case GnssMeasurement.STATE_BDS_D2_BIT_SYNC:
                return "BDS_D2_BIT_SYNC";
            case GnssMeasurement.STATE_GAL_E1B_PAGE_SYNC:
                return "GAL_E1B_PAGE_SYNC";
            case GnssMeasurement.STATE_BDS_D2_SUBFRAME_SYNC:
                return "BDS_D2_SUBFRAME_SYNC";
            case GnssMeasurement.STATE_GAL_E1BC_CODE_LOCK:
                return "GAL_E1BC_CODE_LOCK";
            case GnssMeasurement.STATE_GAL_E1C_2ND_CODE_LOCK:
                return "GAL_E1C_2ND_CODE_LOCK";
            case GnssMeasurement.STATE_GLO_STRING_SYNC:
                return "GLO_STRING_SYNC";
            case GnssMeasurement.STATE_GLO_TOD_DECODED:
                return "GLO_TOD_DECODED";
            case GnssMeasurement.STATE_SBAS_SYNC:
                return "SBAS_SYNC";
            default:
                return "1";
        }
    }

    private String getConstellationName(int id) {
        switch (id) {
            case 1:
                return "GPS";
            case 2:
                return "SBAS";
            case 3:
                return "GLONASS";
            case 4:
                return "QZSS";
            case 5:
                return "BEIDOU";
            case 6:
                return "GALILEO";
            default:
                return "UNKNOWN";
        }
    }

    private double GLONASSG1WAVELENGTH(int svid){
        return SPEED_OF_LIGHT/((1602 + GLONASSFREQ[svid - 1] * 0.5625) * 10e5);
    }

    private double BEIDOUWAVELENGTH(int svid){
        return SPEED_OF_LIGHT/(1561.098 * 10e5);
    }

    private static int calcspent(Calendar Start , Calendar End){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Log.d("DATE",String.valueOf(Start) + String.valueOf(End));
        return  (int)((End.getTimeInMillis() - Start.getTimeInMillis() + (1000 * 60 * 60 * 24)) / (1000 * 60 * 60 * 24));
    }

    private static String getNAVType(int type){
        switch (type){
            case GnssNavigationMessage.TYPE_GPS_L1CA:
                return "GPS_L1CA";
            case GnssNavigationMessage.TYPE_GLO_L1CA:
                return "GLO_L1CA";
            case GnssNavigationMessage.TYPE_GPS_L5CNAV:
                return "GPS_L5CNAV";
            default:
                return "UNKNOWN";
        }
    }
}

