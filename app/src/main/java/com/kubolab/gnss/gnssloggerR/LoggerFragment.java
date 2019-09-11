package com.kubolab.gnss.gnssloggerR;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.github.aakira.expandablelayout.ExpandableRelativeLayout;

import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.CheckedOutputStream;

//gnss data界面的activity
/**  The UI fragment that hosts a logging view. */
public class LoggerFragment extends Fragment {

    private TextView mLogView;
    private TextView mGNSSClockView;
    private TextView mLocationProvider;
    private TextView mLocationLatitude;
    private TextView mLocationLongitude;
    private TextView mLocationAltitude;
    private TextView gnssNavigationDebugView;
    private TextView gnssNavigationDebugTitle;
    ExpandableRelativeLayout expandableLayoutNav;
    //航法メッセージ用ビュー定義
    private TextView IONTitle,IONCORR,TimeCorr,TimeTitle,leapseconds,leapsecondstitle;

    private ScrollView mScrollView;
    private FileLogger mFileLogger;
    private UiLogger mUiLogger;
    private ViewGroup mTable;
    private Button startLog;
    private Button sendFile;
    private boolean FileLogging;
    private Spinner interval_spinner;

    //セーブ中ダイアログの表示
    private View MainView;
    private PopupWindow Progress_Popup;

    private TextView EditTimer;

    //表用ID
    int Rid[][]={{R.id.textView1_1,R.id.textView1_2,R.id.textView1_3,R.id.textView1_4,R.id.textView1_5},{R.id.textView2_1,R.id.textView2_2,R.id.textView2_3,R.id.textView2_4,R.id.textView2_5},
            {R.id.textView3_1,R.id.textView3_2,R.id.textView3_3,R.id.textView3_4,R.id.textView3_5},{R.id.textView4_1,R.id.textView4_2,R.id.textView4_3,R.id.textView4_4,R.id.textView4_5},
            {R.id.textView5_1,R.id.textView5_2,R.id.textView5_3,R.id.textView5_4,R.id.textView5_5},{R.id.textView6_1,R.id.textView6_2,R.id.textView6_3,R.id.textView6_4,R.id.textView6_5},
            {R.id.textView7_1,R.id.textView7_2,R.id.textView7_3,R.id.textView7_4,R.id.textView7_5},{R.id.textView8_1,R.id.textView8_2,R.id.textView8_3,R.id.textView8_4,R.id.textView8_5},
            {R.id.textView9_1,R.id.textView9_2,R.id.textView9_3,R.id.textView9_4,R.id.textView9_5},{R.id.textView10_1,R.id.textView10_2,R.id.textView10_3,R.id.textView10_4,R.id.textView10_5},
            {R.id.textView11_1,R.id.textView11_2,R.id.textView11_3,R.id.textView11_4,R.id.textView11_5},{R.id.textView12_1,R.id.textView12_2,R.id.textView12_3,R.id.textView12_4,R.id.textView12_5},
            {R.id.textView13_1,R.id.textView13_2,R.id.textView13_3,R.id.textView13_4,R.id.textView13_5},{R.id.textView14_1,R.id.textView14_2,R.id.textView14_3,R.id.textView14_4,R.id.textView14_5},
            {R.id.textView15_1,R.id.textView15_2,R.id.textView15_3,R.id.textView15_4,R.id.textView15_5},{R.id.textView16_1,R.id.textView16_2,R.id.textView16_3,R.id.textView16_4,R.id.textView16_5},
            {R.id.textView17_1,R.id.textView17_2,R.id.textView17_3,R.id.textView17_4,R.id.textView17_5},{R.id.textView18_1,R.id.textView18_2,R.id.textView18_3,R.id.textView18_4,R.id.textView18_5},
            {R.id.textView19_1,R.id.textView19_2,R.id.textView19_3,R.id.textView19_4,R.id.textView19_5},{R.id.textView20_1,R.id.textView20_2,R.id.textView20_3,R.id.textView20_4,R.id.textView20_5},
            {R.id.textView21_1,R.id.textView21_2,R.id.textView21_3,R.id.textView21_4,R.id.textView21_5},{R.id.textView22_1,R.id.textView22_2,R.id.textView22_3,R.id.textView22_4,R.id.textView22_5},
            {R.id.textView23_1,R.id.textView23_2,R.id.textView23_3,R.id.textView23_4,R.id.textView23_5},{R.id.textView24_1,R.id.textView24_2,R.id.textView24_3,R.id.textView24_4,R.id.textView24_5},
            {R.id.textView25_1,R.id.textView25_2,R.id.textView25_3,R.id.textView25_4,R.id.textView25_5},{R.id.textView26_1,R.id.textView26_2,R.id.textView26_3,R.id.textView26_4,R.id.textView26_5},
            {R.id.textView27_1,R.id.textView27_2,R.id.textView27_3,R.id.textView27_4,R.id.textView27_5},{R.id.textView28_1,R.id.textView28_2,R.id.textView28_3,R.id.textView28_4,R.id.textView28_5},
            {R.id.textView29_1,R.id.textView29_2,R.id.textView29_3,R.id.textView29_4,R.id.textView29_5},{R.id.textView30_1,R.id.textView30_2,R.id.textView30_3,R.id.textView30_4,R.id.textView30_5},
            {R.id.textView31_1,R.id.textView31_2,R.id.textView31_3,R.id.textView31_4,R.id.textView31_5},{R.id.textView32_1,R.id.textView32_2,R.id.textView32_3,R.id.textView32_4,R.id.textView32_5},
            {R.id.textView33_1,R.id.textView33_2,R.id.textView33_3,R.id.textView33_4,R.id.textView33_5},{R.id.textView34_1,R.id.textView34_2,R.id.textView34_3,R.id.textView34_4,R.id.textView34_5},
            {R.id.textView35_1,R.id.textView35_2,R.id.textView35_3,R.id.textView35_4,R.id.textView35_5},{R.id.textView36_1,R.id.textView36_2,R.id.textView36_3,R.id.textView36_4,R.id.textView36_5}};

    int MaxSatelliteIndex = 36;
    TextView mTextView[][] = new TextView[MaxSatelliteIndex][5];

    private final UIFragmentComponent mUiComponent = new UIFragmentComponent();
    private CheckedOutputStream mFileWriter;

    public void setUILogger(UiLogger value) {
        mUiLogger = value;
    }

    public void setFileLogger(FileLogger value) {
        mFileLogger = value;
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
       return inflater.inflate(R.layout.fragment_log, container, false /* attachToRoot */);
    }

    public void onViewCreated(final View view, Bundle savedInstanceState){
        //mLogView = (TextView) newView.findViewById(R.id.lo);
        /*mLocationProvider = (TextView) view.findViewById(R.id.location_prov);
        mLocationLatitude = (TextView) view.findViewById(R.id.location_lat);
        mLocationLongitude = (TextView) view.findViewById(R.id.location_lon);
        mLocationAltitude = (TextView) view.findViewById(R.id.location_alt);*/
        //gnssNavigationDebugView = (TextView) view.findViewById(R.id.gnssNavigationDebugView);
        gnssNavigationDebugTitle = (TextView) view.findViewById(R.id.gnssNavigationDebugTitle);
        expandableLayoutNav = (ExpandableRelativeLayout) view.findViewById(R.id.expandableLayoutNav);
        MainView = view;

        IONCORR = (TextView) view.findViewById(R.id.IONCORR);
        IONTitle = (TextView) view.findViewById(R.id.IONTitle);
        TimeCorr = (TextView) view.findViewById(R.id.TimeCorr);
        TimeTitle = (TextView) view.findViewById(R.id.TimeTitle);
        leapseconds = (TextView) view.findViewById(R.id.leapseconds);
        leapsecondstitle = (TextView) view.findViewById(R.id.leapsecondstitle);
        interval_spinner = (Spinner) view.findViewById(R.id.interval_spinner);
        PopupWindow mPopupWindow = new PopupWindow(getActivity());
        //mPopupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.));
        //View popupView = getLayoutInflater().inflate(R.layout.popup_layout, null);
        final PopupWindow ION_popupWindow;
        ION_popupWindow = new PopupWindow(getActivity());
        View layout = (View)getActivity().getLayoutInflater().inflate(R.layout.popup_background, null);
        layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView text = (TextView)layout.findViewById(R.id.pouuptext);

        ION_popupWindow.setContentView(layout);
        ION_popupWindow.setWidth(800);
        ION_popupWindow.setHeight(1200);
        ION_popupWindow.setOutsideTouchable(true);
        ION_popupWindow.setFocusable(true);
        ION_popupWindow.setClippingEnabled(true);
        Button btn = (Button)layout.findViewById(R.id.ionpop_close);
        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ION_popupWindow.dismiss();
            }
        });

        IONCORR.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                //Log.i("Touch","Touch Event Received");
                GnssNavigationDataBase gnd = new GnssNavigationDataBase(getActivity());
                text.setText(gnd.getIonosphericDataStr());
                ION_popupWindow.showAtLocation(view,Gravity.CENTER,0,0);
                //Log.i("Touch",String.valueOf(ION_popupWindow.isShowing()));
            }
        });
        TimeCorr.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //Log.i("Touch","Touch Event Received");
                GnssNavigationDataBase gnd = new GnssNavigationDataBase(getActivity());
                text.setText(gnd.getTimeSystemDataStr());
                ION_popupWindow.showAtLocation(view,Gravity.CENTER,0,0);
                //Log.i("Touch",String.valueOf(ION_popupWindow.isShowing()));
            }
        });
        leapseconds.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //Log.i("Touch","Touch Event Received");
                GnssNavigationDataBase gnd = new GnssNavigationDataBase(getActivity());
                text.setText(gnd.getLeapSecondsDataStr());
                ION_popupWindow.showAtLocation(view,Gravity.CENTER,0,0);
                //Log.i("Touch",String.valueOf(ION_popupWindow.isShowing()));
            }
        });
        //mScrollView = (ScrollView) newView.findViewById(R.id.log_scroll);
        mTable = (ViewGroup) view.findViewById(R.id.TableLayout);
        //表の初期化
        for(int i = 0;i < MaxSatelliteIndex;i++){
            for(int j = 0;j < 5;j++){
                Log.d("Array", i + "," + j);
                mTextView[i][j]=(TextView) view.findViewById(Rid[i][j]);
                //Log.d("Array", i + "," + j);
            }
        }
        EditTimer = (TextView) view.findViewById(R.id.editTimer);
        EditTimer.setText("0");
        EditTimer.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if(SettingsFragment.enableTimer == false) {
                    String inputStr = s.toString();
                    if (inputStr == "") {
                        SettingsFragment.timer = 0;
                        SettingsFragment.enableTimer = false;
                    } else {
                        try {
                            int timer = Integer.parseInt(inputStr);
                            if(timer > 0) {
                                SettingsFragment.timer = Integer.parseInt(inputStr) + 1;
                                Log.i("Timer", String.valueOf(SettingsFragment.timer));
                            }else {
                                SettingsFragment.timer = 0;
                                SettingsFragment.enableTimer = false;
                            }
                        } catch (NumberFormatException e) {
                            Log.e("ParseInt", "Number format Exception!!!");
                            SettingsFragment.timer = 0;
                            SettingsFragment.enableTimer = false;
                        }
                    }
                }
            }
        });

        interval_spinner.setSelection(0);

        interval_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //Log.i("Spinner Pos",String.valueOf(position));
                switch (position){
                    case 0:
                        SettingsFragment.interval = 1;
                        Log.i("interval",String.valueOf(SettingsFragment.interval));
                        break;
                    case 1:
                        SettingsFragment.interval = 10;
                        Log.i("interval",String.valueOf(SettingsFragment.interval));
                        break;
                    case 2:
                        SettingsFragment.interval = 15;
                        Log.i("interval",String.valueOf(SettingsFragment.interval));
                        break;
                    case 3:
                        SettingsFragment.interval = 30;
                        Log.i("interval",String.valueOf(SettingsFragment.interval));
                        break;
                        default:
                            SettingsFragment.interval = 1;
                            break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        UiLogger currentUiLogger = mUiLogger;
        if (currentUiLogger != null) {
            currentUiLogger.setUiFragmentComponent(mUiComponent);
        }
        FileLogger currentFileLogger = mFileLogger;
        if (currentFileLogger != null) {
            currentFileLogger.setUiComponent(mUiComponent);
        }

        startLog = (Button) view.findViewById(R.id.start_logs);

        startLog.setText("ClockSync...");
        startLog.setEnabled(false);
//        startLog.setEnabled(true);

        mGNSSClockView = (TextView) view.findViewById(R.id.GNSSClockView);

        FileLogging = false;

        gnssNavigationDebugTitle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(expandableLayoutNav.isExpanded()){
                    expandableLayoutNav.collapse();
                    IONCORR.setVisibility(View.GONE);
                    IONTitle.setVisibility(View.GONE);
                    TimeCorr.setVisibility(View.GONE);
                    TimeTitle.setVisibility(View.GONE);
                    leapseconds.setVisibility(View.GONE);
                    leapsecondstitle.setVisibility(View.GONE);
                    //expandableLayoutNav.initLayout();
                }else {
                    expandableLayoutNav.expand();
                    //expandableLayoutNav.initLayout();
                    IONCORR.setVisibility(View.VISIBLE);
                    IONTitle.setVisibility(View.VISIBLE);
                    TimeCorr.setVisibility(View.VISIBLE);
                    TimeTitle.setVisibility(View.VISIBLE);
                    leapseconds.setVisibility(View.VISIBLE);
                    leapsecondstitle.setVisibility(View.VISIBLE);
                }
            }
        });

        startLog.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(SettingsFragment.EnableLogging == false) {
                            //startLog.setEnabled(false);
                            //sendFile.setEnabled(true);
                            Toast.makeText(getContext(), "Starting log...", Toast.LENGTH_LONG).show();
                            mFileLogger.startNewLog();
                            FileLogging = true;
                            SettingsFragment.EnableLogging = true;
                            EditTimer.setEnabled(false);
                            interval_spinner.setEnabled(false);
                            startLog.setText("End Log");
                            if(SettingsFragment.timer > 0){
                                SettingsFragment.enableTimer = true;
                            }
                        }else {
                            //startLog.setEnabled(true);
                            //sendFile.setEnabled(false);
                            if(SettingsFragment.enableTimer){
                                Toast.makeText(getContext(), "Timer stopped by user", Toast.LENGTH_LONG).show();
                                SettingsFragment.enableTimer = false;
                                SettingsFragment.timer = 0;
                                EditTimer.setText("0");
                            }
                            Toast.makeText(getContext(), "Sending file...", Toast.LENGTH_LONG).show();
                            mFileLogger.send();
                            FileLogging = false;
                            SettingsFragment.EnableLogging = false;
                            EditTimer.setEnabled(true);
                            interval_spinner.setEnabled(true);
                            startLog.setText("Start Log");
                        }
                    }
                });
    }

    /**
     * A facade for UI and Activity related operations that are required for {@link GnssListener}s.
     */
    public class UIFragmentComponent {

        //private static final int MAX_LENGTH = 12000;
        //private static final int LOWER_THRESHOLD = (int) (MAX_LENGTH * 0.5);

        public synchronized void logTextFragment(final String tag, final String text, final String[][] array) {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            if(FileLogging == false) {
                                if (SettingsFragment.GNSSClockSync == true) {
                                    startLog.setEnabled(true);
                                    startLog.setText("Start Log");
                                } else {
                                    startLog.setEnabled(false);
                                    startLog.setText("ClockSync...");
                                }
                            }
                            for(int i = 0;i < MaxSatelliteIndex;i++){
                                for(int j = 0;j < 5;j++){
                                    if(j == 2){
                                        if(array[i][j] == "0") {
                                            mTextView[i][j].setTextColor(Color.parseColor("#FF9900"));
                                            mTextView[i][j].setText("Carrier Phase OFF");
                                        }else if(array[i][j] == "CYCLE_SLIP"){
                                            mTextView[i][j].setTextColor(Color.RED);
                                            mTextView[i][j].setText("CYCLE_SLIP");
                                        }else {
                                            mTextView[i][j].setTextColor(Color.BLACK);
                                            mTextView[i][j].setText(array[i][j]);
                                        }
                                    }else if(j == 3){
                                        if(array[i][j] != null) {
                                            float dBHz = 0.0f;
                                            try{
                                                dBHz = Float.parseFloat(array[i][j]);
                                            }catch (NumberFormatException e){
                                                Log.e("dBHz","parseFloat Error" + array[i][j]);
                                            }
                                            if (dBHz < 12.0) {
                                                mTextView[i][j].setTextColor(Color.RED);
                                                mTextView[i][j].setText(array[i][j]);
                                            } else if (dBHz < 29.0) {
                                                mTextView[i][j].setTextColor(Color.parseColor("#FF9900"));
                                                mTextView[i][j].setText(array[i][j]);
                                            } else {
                                                mTextView[i][j].setTextColor(Color.GREEN);
                                                mTextView[i][j].setText(array[i][j]);
                                            }
                                        }else{
                                            mTextView[i][j].setText(array[i][j]);
                                        }
                                    }else {
                                        mTextView[i][j].setText(array[i][j]);
                                    }
                                }
                            }
                        }
                    });
        }

        public synchronized void GNSSClockLog(final String ClockData) {

            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            mGNSSClockView.setText(ClockData);
                            /*Editable editable = mLogView.getEditableText();
                            int length = editable.length();
                            if (length > MAX_LENGTH) {
                                editable.delete(0, length - LOWER_THRESHOLD);
                            }*/
                        }
                    });
        }

        public synchronized void NavigationIONText(final String NavMessage,final String ColorCode, final int tag) {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            if(tag == 0) {
                                IONCORR.setTextColor(Color.parseColor(ColorCode));
                                IONCORR.setText(NavMessage);
                            }else if(tag == 1){
                                TimeCorr.setTextColor(Color.parseColor(ColorCode));
                                TimeCorr.setText(NavMessage);
                            }else if(tag == 2){
                                leapseconds.setTextColor(Color.parseColor(ColorCode));
                                leapseconds.setText(NavMessage);
                            }
                        }
                    });
        }

        /*public synchronized void LocationTextFragment(final String provider, final String latitude, final String longitude,final String altitude,int color) {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            mLocationProvider.setText(provider);
                            mLocationLatitude.setText(latitude);
                            mLocationLongitude.setText(longitude);
                            mLocationAltitude.setText(altitude);
                        }
                    });
        }*/

        public synchronized void RefreshTimer(){
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            if(SettingsFragment.timer == 0){
                                Toast.makeText(getContext(), "Sending file...", Toast.LENGTH_LONG).show();
                                mFileLogger.send();
                                FileLogging = false;
                                SettingsFragment.EnableLogging = false;
                                EditTimer.setEnabled(true);
                                startLog.setText("Start Log");
                                SettingsFragment.enableTimer = false;
                                EditTimer.setText(String.valueOf(SettingsFragment.timer));
                            }else {
                                EditTimer.setText(String.valueOf(SettingsFragment.timer));
                            }
                        }
                    });
        }

        public synchronized void ShowProgressWindow(boolean visible){
            //final PopupWindow progress_popupWindow;
            View layout = (View)getActivity().getLayoutInflater().inflate(R.layout.progress_background, null);
            layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            if(visible){
                if(Progress_Popup == null) {
                    if(MainView != null) {
                        Progress_Popup = new PopupWindow(getActivity());
                        Progress_Popup.setContentView(layout);
                        Progress_Popup.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
                        Progress_Popup.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
                        Progress_Popup.setOutsideTouchable(false);
                        Progress_Popup.showAtLocation(MainView, Gravity.CENTER, 0, 0);
                        Log.i("progress","show");
                    }else {
                        Log.i("MainView","null");
                    }
                }
            }else {
                //Log.i("progress","dismiss");
                //Progress_Popup.isShowing()
                if(false){
                    Progress_Popup.dismiss();
                    Log.i("progress","dismiss");
                }else {
                    //final PopupWindow oldWindow = progress_popupWindow;
                    new Timer().schedule(new TimerTask(){
                        @Override
                        public void run() {
                            getActivity().runOnUiThread(new Runnable(){
                                public void run() {
                                    Progress_Popup.dismiss();
                                    Log.i("progress","delaydismiss");
                                }
                            });
                        }
                    }, 500);
                }
                Progress_Popup = null;
            }
        }

        public void startActivity(Intent intent) {
            getActivity().startActivity(intent);
        }
    }
}
