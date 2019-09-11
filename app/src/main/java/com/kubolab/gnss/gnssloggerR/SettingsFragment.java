package com.kubolab.gnss.gnssloggerR;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioButton;

import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static android.location.GnssMeasurementsEvent.Callback.STATUS_LOCATION_DISABLED;
import static android.location.GnssMeasurementsEvent.Callback.STATUS_NOT_SUPPORTED;
import static android.location.GnssMeasurementsEvent.Callback.STATUS_READY;

/**
 * The UI fragment showing a set of configurable settings for the client to request GPS data.
 */
public class SettingsFragment extends Fragment {

//    private UiLogger mUiLogger;
//private TextView mSensorSpecView;
private TextView mAccSpecView;
    private TextView mGyroSpecView;
    private TextView mMagSpecView;
    private TextView mPressSpecView;

    private TextView mAccAvView;
    private TextView mGyroAvView;
    private TextView mMagAvView;
    private TextView mPressAvView;

    public static final String TAG = ":SettingsFragment";
    public static String SAVE_LOCATION = "G_RitZ_Logger";
    public static String FILE_PREFIX = "/" + SAVE_LOCATION + "/RINEXOBS";
    public static String FILE_PREFIXSUB = "/" + SAVE_LOCATION + "/KML";
    public static String FILE_PREFIXACCAZI = "/" + SAVE_LOCATION + "/CSV";
    public static String FILE_PREFIXNMEA = "/" + SAVE_LOCATION + "/NMEA";
    public static String FILE_PREFIXNAV = "/" + SAVE_LOCATION + "/RINEXNAV";
    public static String FILE_NAME = "AndroidOBS";

    public static boolean useDualFreq = false;


    public static boolean CarrierPhase = false;
    public static boolean useQZ = false;
    public static boolean useGL = false;
    public static boolean useGA = false;
    public static boolean useBD = false;
    public static boolean useSB = false;
    public static boolean usePseudorangeSmoother = false;
    public static boolean usePseudorangeRate = false;
    public static boolean useKalmanFilter = false;
    public static boolean GNSSClockSync = false;
    public static boolean useDeviceSensor = false;
    public static boolean ResearchMode = false;
    public static boolean SMOOTHER_RATE_RESET_FLAG_FILE = false;
    public static boolean SMOOTHER_RATE_RESET_FLAG_UI = false;
    public static boolean SendMode = false;
    public static int GNSSMeasurementReadyMode = 10;
    private GnssContainer mGpsContainer;
    private SensorContainer mSensorContainer;
    private FileLogger mFileLogger;
    private UiLogger mUiLogger;
    private GnssContainer mGnssContainer;
    private TextView EditSaveLocation;
    private TextView mRawDataIsOk;
    private TextView mLocationIsOk;
    private TextView FTPDirectory;
    public static boolean FIRST_CHECK = false;
    public static String FTP_SERVER_DIRECTORY = "";

    public static boolean PermissionOK = false;
    public static boolean registerGNSS = false;
    public static boolean FileLoggingOK = false;
    public static boolean EnableLogging = false;

    public static boolean EnableSensorLog = false;

    public static boolean RINEXNAVLOG = false;

    //RINEX記述モード
    public static boolean RINEX303 = false;

    //計測時間
    public static int timer = 0;
    public static boolean enableTimer = false;
    public static int interval = 1;


    public void setGpsContainer(GnssContainer value) {
        mGpsContainer = value;
    }
    public void setSensorContainer(SensorContainer value){ mSensorContainer = value; }

    private final SettingsFragment.UIFragmentSettingComponent mUiSettingComponent = new SettingsFragment.UIFragmentSettingComponent();

    public void setUILogger(UiLogger value) {
        mUiLogger = value;
    }

    public void setFileLogger(FileLogger value) {
        mFileLogger = value;
    }

    public void setGnssContainer(GnssContainer value){
        mGnssContainer = value;
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false /* attachToRoot */);
    }

    public  void onViewCreated(View view, Bundle savedInstanceState){

        mRawDataIsOk = (TextView) view.findViewById(R.id.rawDataIsOk);
        mLocationIsOk = (TextView) view.findViewById(R.id.locationIsOk);

        mAccSpecView = (TextView) view.findViewById(R.id.accSpecView);
        mGyroSpecView = (TextView) view.findViewById(R.id.gyroSpecView);
        mMagSpecView = (TextView) view.findViewById(R.id.magSpecView);
        mPressSpecView = (TextView) view.findViewById(R.id.pressSpecView);

        mAccAvView = (TextView) view.findViewById(R.id.accAvView);
        mGyroAvView = (TextView) view.findViewById(R.id.gyroAvView);
        mMagAvView = (TextView) view.findViewById(R.id.magAvView);
        mPressAvView = (TextView) view.findViewById(R.id.pressAvView);

        //ダミーラジオボタン、スイッチの初期設定
        final RadioButton rbrinex303 = (RadioButton) view.findViewById(R.id.RINEXMODE303);
        final RadioButton rbrinex211 = (RadioButton) view.findViewById(R.id.RINEXMODE211);
        final TextView RINEXDescription = (TextView) view.findViewById(R.id.RINEXDescription);
        //rbrinex303.setEnabled(false);
        rbrinex303.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    RINEX303 = true;
                    rbrinex211.setChecked(false);
                    RINEXDescription.setText("RINEX3.03 mode can log all satellites");
                }
            }
        });
        rbrinex211.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    RINEX303 = false;
                    rbrinex303.setChecked(false);
                    RINEXDescription.setText("RINEX2.11 mode can log only GPS");
                }
            }
        });

        final CheckBox PseudorangeSmoother = (CheckBox) view.findViewById(R.id.checkBoxPseSmoother);
        PseudorangeSmoother.setEnabled(false);
        final CheckBox CarrierPhaseChkBox = (CheckBox) view.findViewById(R.id.checkBox);

        final CheckBox useDualF = (CheckBox) view.findViewById(R.id.DualButton);                     // 2周波観測

        final CheckBox useQZSS = (CheckBox) view.findViewById(R.id.useQZS);
        final CheckBox useGLO = (CheckBox) view.findViewById(R.id.useGLO);
        final CheckBox useGAL = (CheckBox) view.findViewById(R.id.useGAL);
        final CheckBox useBDS = (CheckBox) view.findViewById(R.id.useBDS);
        final CheckBox useSBS = (CheckBox) view.findViewById(R.id.useSBS);
        useSBS.setEnabled(false);
        //useBDS.setEnabled(false);
        CarrierPhaseChkBox.setChecked(false);
        CarrierPhaseChkBox.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                CarrierPhase = CarrierPhaseChkBox.isChecked();
                if(CarrierPhaseChkBox.isChecked()){
                    PseudorangeSmoother.setEnabled(true);
                }else {
                    PseudorangeSmoother.setEnabled(false);
                    PseudorangeSmoother.setChecked(false);
                }
            }

        });
        final TextView Smootherdescription = (TextView) view.findViewById(R.id.SmootherDescription);

        PseudorangeSmoother.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                usePseudorangeSmoother = PseudorangeSmoother.isChecked();
                if(PseudorangeSmoother.isChecked()){
                    AlertDialog.Builder alertDialogBuilder =new AlertDialog.Builder(getContext());
                    alertDialogBuilder.setTitle("WARINING");
                    alertDialogBuilder.setMessage("Pseudorange Smoother is Beta function.\nWhich observation amount should be used for correction?");
                    alertDialogBuilder.setPositiveButton("Carrier Phase (for Static)", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            usePseudorangeRate = false;
                            SMOOTHER_RATE_RESET_FLAG_FILE = true;
                            SMOOTHER_RATE_RESET_FLAG_UI = true;
                            Smootherdescription.setText("Smoothed pseudorange with carrier phase");
                        }
                    });
                    alertDialogBuilder.setNegativeButton("Doppler Shift (for Kinematic)", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            usePseudorangeRate = true;
                            SMOOTHER_RATE_RESET_FLAG_FILE = true;
                            SMOOTHER_RATE_RESET_FLAG_UI = true;
                            Smootherdescription.setText("Smoothed pseudorange with doppler shift");
                        }
                    });
                    alertDialogBuilder.show();
                }
            }
        });

        useDualF.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                useDualFreq = useDualF.isChecked();
            }

        });                                                                                           //2周波観測

        useQZSS.setChecked(false);
        useQZSS.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                useQZ = useQZSS.isChecked();
            }

        });

        useGLO.setChecked(false);
        useGLO.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                useGL = useGLO.isChecked();
            }

        });

        useGAL.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                useGA = useGAL.isChecked();
            }

        });

        useBDS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useBD = useBDS.isChecked();
            }
        });

        useSBS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useSB = useSBS.isChecked();
            }
        });

        final Switch registerSensor = (Switch) view.findViewById(R.id.register_sensor);
        registerSensor.setChecked(false);
        registerSensor.setOnCheckedChangeListener(
                new OnCheckedChangeListener() {

                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                        if (isChecked) {
                            mSensorContainer.registerSensor();
                            useDeviceSensor = true;
                        } else {
                            mSensorContainer.unregisterSensor();
                            useDeviceSensor = false;
                            Logger2Fragment.deviceAzimuth = 0;
                        }
                    }
                });

        final CheckBox RINEXNAVCheck = (CheckBox) view.findViewById(R.id.outputRINESNAV);
        RINEXNAVCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RINEXNAVLOG = RINEXNAVCheck.isChecked();
            }
        });

        final CheckBox outPutSensor = (CheckBox) view.findViewById(R.id.outputSensor);
        outPutSensor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnableSensorLog = outPutSensor.isChecked();
            }
        });

        Date now = new Date();
        int observation = now.getYear() - 100;
        final TextView FileExtension = (TextView) view.findViewById(R.id.FileExtension);
        FileExtension.setText("$log/RINEX/\"prefix\"." + observation + "o");
        final TextView FileExtensionNav = (TextView) view.findViewById(R.id.fileExtensionNav);
        FileExtensionNav.setText("$log/RINEX/\"prefix\"." + observation + "n");

//        FTPDirectory = (TextView) view.findViewById(R.id.FTPDirectory);

        EditSaveLocation = (TextView) view.findViewById(R.id.EditSaveLocation);
        EditSaveLocation.setText("(Current Time)");
        EditSaveLocation.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if(s.toString() == ""){
                    EditSaveLocation.setText("(Current Time)");
                }else{
                    FILE_NAME = s.toString();
                }
            }
        });

        final Switch ResearchModeSwitch = (Switch) view.findViewById(R.id.ResearchMode);
        //リリース時
        //if(BuildConfig.DEBUG == false) {
            //ResearchModeSwitch.setEnabled(false);
        //}
        ResearchModeSwitch.setChecked(false);
        ResearchModeSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    final EditText editView = new EditText(getContext());
                    editView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    new AlertDialog.Builder(getContext())
                            .setTitle("Please Enter a Password")
                            //setViewにてビューを設定します。
                            .setView(editView)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    //入力した文字をトースト出力する
                             //       if(editView.getText().toString().indexOf("aiueo1") != -1){
                               //         Toast.makeText(getContext(),
                                 //               "Research mode turned ON",
                                   //             Toast.LENGTH_LONG).show();
                                        ResearchMode = true;
                                        //rbrinex303.setEnabled(true);
                                        //useGAL.setEnabled(true);
                                        outPutSensor.setEnabled(true);
                                        RINEXNAVCheck.setEnabled(true);
                                        //useBDS.setEnabled(true);
                                        useSBS.setEnabled(true);
                              //      }else {
                              //          Toast.makeText(getContext(),
                               //                 "Password is incorrect",
                                //                Toast.LENGTH_LONG).show();
                                //        ResearchModeSwitch.setChecked(false);
                           //         }
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    ResearchModeSwitch.setChecked(false);
                                }
                            })
                            .show();
                } else {
                    ResearchMode = false;
                    //rbrinex303.setEnabled(false);
                    //rbrinex303.setChecked(false);
                    //rbrinex211.setChecked(true);
                    //useGAL.setEnabled(false);
                    outPutSensor.setEnabled(false);
                    RINEXNAVCheck.setEnabled(false);
                    //useBDS.setEnabled(false);
                    useSBS.setEnabled(false);
                    //RINEX303 = false;
                    ResearchMode = true;
                }
            }

        });
        UiLogger currentUiLogger = mUiLogger;
        if (currentUiLogger != null) {
            //Log.d("mUILogger","Pointer OK");
            currentUiLogger.setUISettingComponent(mUiSettingComponent);
        }
        GnssContainer currentGnssContainer = mGnssContainer;
        if(currentGnssContainer != null){
            currentGnssContainer.setUISettingComponent(mUiSettingComponent);
        }
        if(FIRST_CHECK == false) {
            CheckGNSSMeasurementsReady(GNSSMeasurementReadyMode, FIRST_CHECK);
            FIRST_CHECK = true;
        }else{
            CheckGNSSMeasurementsReady(GNSSMeasurementReadyMode, FIRST_CHECK);
        }
    }

    private void CheckGNSSMeasurementsReady(int status, boolean FIRST_CHECK){
        if(status == STATUS_NOT_SUPPORTED){
            if(FIRST_CHECK == false) {
                new AlertDialog.Builder(getContext())
                        .setTitle("DEVICE NOT SUPPORTED")
                        .setMessage("This device is not suppored please check supported device list\nhttps://developer.android.com/guide/topics/sensors/gnss.html")
                        .setPositiveButton("OK", null)
                        .show();
            }
            //MainActivity.getInstance().finishAndRemoveTask();
            mRawDataIsOk.setText("Unavailable");
        }
        if(status == STATUS_LOCATION_DISABLED){
            if(FIRST_CHECK == false) {
                new AlertDialog.Builder(getContext())
                        .setTitle("LOCATION DISABLED")
                        .setMessage("Location is disabled. \nplease turn on your GPS Setting")
                        .setPositiveButton("OK", null)
                        .show();
            }
            mLocationIsOk.setText("Unavailable");
        }
        if(status == STATUS_READY){
            //Log.d("GNSSStatus","GNSSMeasurements Status Ready");
            Toast.makeText(getContext(),"GNSS Measurements Ready",Toast.LENGTH_SHORT).show();
        }
    }


    public class UIFragmentSettingComponent {

        //private static final int MAX_LENGTH = 12000;
        //private static final int LOWER_THRESHOLD = (int) (MAX_LENGTH * 0.5);
        public synchronized void SettingFTPDirectory(final String DirectoryName){
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                        //FTPDirectory.setText(DirectoryName);
                        }
                    });
        }

        public synchronized void SettingTextFragment(final String FileName) {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            if(EditSaveLocation.getText().toString().indexOf("Current Time") != -1){
                                FILE_NAME = FileName;
                            }
                        }
                    });
        }

        /*public synchronized void Lockout(final boolean status) {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            EditSaveLocation.setEnabled(status);

                        }
                    });
        }*/

        public synchronized void SettingFragmentSensorSpec(final String SensorSpec[]) {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            mAccSpecView.setText(SensorSpec[0]);
                            mGyroSpecView.setText(SensorSpec[1]);
                            mMagSpecView.setText(SensorSpec[2]);
                            mPressSpecView.setText(SensorSpec[3]);
                        }
                    });
        }

        public synchronized void SettingFragmentSensorAvairable(final String SensorAvairable[]) {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            mAccAvView.setText(SensorAvairable[0]);
                            mGyroAvView.setText(SensorAvairable[1]);
                            mMagAvView.setText(SensorAvairable[2]);
                            mPressAvView.setText(SensorAvairable[3]);
                        }
                    });
        }


        /*public void startActivity(Intent intent) {
            getActivity().startActivity(intent);
        }*/


    }
}