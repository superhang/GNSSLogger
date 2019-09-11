package com.kubolab.gnss.gnssloggerR;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.text.Html;
import android.text.Spanned;

/**
 * Created by KuboLab on 2018/02/12.
 */

public class GnssNavigationDataBase {
    public Context mContext;
    public GnssNavigationDataBase(final Context context){
        mContext = context;
    }

    public Spanned getIonosphericDataStr(){
        SQLiteDatabase NavDB;
        SQLiteManager hlpr = new SQLiteManager(mContext);
        NavDB = hlpr.getWritableDatabase();
        Spanned temp;
        //StringBuilder FourthSubframe = new StringBuilder();
        if(hlpr.existColumn(NavDB,"IONOSPHERIC", "GPSA0")) {
            double a0 = hlpr.searchIndex(NavDB, "IONOSPHERIC", "GPSA0");
            double a1 = hlpr.searchIndex(NavDB, "IONOSPHERIC", "GPSA1");
            double a2 = hlpr.searchIndex(NavDB, "IONOSPHERIC", "GPSA2");
            double a3 = hlpr.searchIndex(NavDB, "IONOSPHERIC", "GPSA3");

            double b0 = hlpr.searchIndex(NavDB, "IONOSPHERIC", "GPSB0");
            double b1 = hlpr.searchIndex(NavDB, "IONOSPHERIC", "GPSB1");
            double b2 = hlpr.searchIndex(NavDB, "IONOSPHERIC", "GPSB2");
            double b3 = hlpr.searchIndex(NavDB, "IONOSPHERIC", "GPSB3");
            //FourthSubframe.append(String.format("GPS_a0 %1.4E\nGPS_a1 %1.4E\nGPS_a2 %1.4E\nGPS_a3 %1.4E\n",a0,a1,a2,a3));
            //FourthSubframe.append(String.format("GPS_b0 %1.4E\nGPS_b1 %1.4E\nGPS_b2 %1.4E\nGPS_b3 %1.4E",b0,b1,b2,b3));
            String sampleHtml ="IONOSPHERIC CORR<br>GPSA<br>&alpha;<sub><small>0</small></sub>: " + String.format("%1.4E",a0) + "<br>&alpha;<sub><small>1</small></sub>: " + String.format("%1.4E",a1) + "<br>&alpha;<sub><small>2</small></sub>: " + String.format("%1.4E",a2) + "<br>&alpha;<sub><small>3</small></sub>: " + String.format("%1.4E",a3) +
                    "<br>GPSB<br>&beta;<sub><small>0</small></sub>: " + String.format("%1.4E",b0) + "<br>&beta;<sub><small>1</small></sub>: " + String.format("%1.4E",b1) + "<br>&beta;<sub><small>2</small></sub>: " + String.format("%1.4E",b2) + "<br>&beta;<sub><small>3</small></sub>: " + String.format("%1.4E",b3);
            temp = Html.fromHtml(sampleHtml);
        }else {
            temp = Html.fromHtml("DATA NOTFOUND");
        }
        NavDB.close();
        return temp;
    }

    public Spanned getTimeSystemDataStr(){
        SQLiteDatabase NavDB;
        SQLiteManager hlpr = new SQLiteManager(mContext);
        NavDB = hlpr.getWritableDatabase();
        Spanned temp;
        //StringBuilder FourthSubframe = new StringBuilder();
        if(hlpr.existColumn(NavDB,"UTC", "a0UTC")) {
            double a0UTC = hlpr.searchIndex(NavDB, "UTC", "a0UTC");
            double a1UTC = hlpr.searchIndex(NavDB, "UTC", "a1UTC");
            int tot = (int) hlpr.searchIndex(NavDB, "UTC", "tot");
            int wnt = (int) hlpr.searchIndex(NavDB, "UTC", "wnt");
            String sampleHtml ="GPUT<br>A<sub><small>0</small></sub>: " + String.format("%1.10E",a0UTC) + "<br>A<sub><small>1</small></sub>: " + String.format("%1.10E",a1UTC) + "<br>t<sub><small>ot</small></sub>: " + String.format("%6d",tot) + "<br>WN<sub><small>t</small></sub>: " + String.format("%6d",wnt);
            temp = Html.fromHtml(sampleHtml);
        }else {
            temp = Html.fromHtml("DATA NOTFOUND");
        }
        NavDB.close();
        return temp;
    }

    public Spanned getLeapSecondsDataStr(){
        SQLiteDatabase NavDB;
        SQLiteManager hlpr = new SQLiteManager(mContext);
        NavDB = hlpr.getWritableDatabase();
        //StringBuilder FourthSubframe = new StringBuilder();
        Spanned temp;
        if(hlpr.existColumn(NavDB,"LEAPSECOND", "tls")) {
            int tls = (int) hlpr.searchIndex(NavDB, "LEAPSECOND", "tls");
            int wnlsf = (int) hlpr.searchIndex(NavDB, "LEAPSECOND", "wnlsf");
            int dn = (int) hlpr.searchIndex(NavDB, "LEAPSECOND", "dn");
            int tlsf = (int) hlpr.searchIndex(NavDB, "LEAPSECOND", "tlsf");
            //FourthSubframe.append(String.format("LEAPSECONDS %6d %6d %6d %6d",tls,wnlsf,dn,tlsf));
            String sampleHtml ="LEAP SECONDS<br>&Delta;t<sub><small>LS</small></sub>: " + String.format("%6d",tls) + "<br>WN<sub><small>LSF</small></sub>: " + String.format("%6d",wnlsf) + "<br>DN: " + String.format("%6d",dn) + "<br>&Delta;t<sub><small>LSF</small></sub>: " + String.format("%6d",tlsf);
            temp = Html.fromHtml(sampleHtml);
        }else {
            //FourthSubframe.append("NOTFOUND DATA");
            temp = Html.fromHtml("DATA NOTFOUND");
        }
        NavDB.close();
        return temp;
    }
}
