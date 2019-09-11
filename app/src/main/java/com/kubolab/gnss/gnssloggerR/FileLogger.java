package com.kubolab.gnss.gnssloggerR;

import android.content.Context;
import android.content.Intent;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.os.SystemClock;
import com.kubolab.gnss.gnssloggerR.LoggerFragment.UIFragmentComponent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Calendar;

//aiueo
public class FileLogger implements GnssListener {

    private static final String TAG = "FileLogger";
    private static final String ERROR_WRITING_FILE = "Problem writing to file.";
    private static final String COMMENT_START = "# ";
    private static final char RECORD_DELIMITER = ',';
    private static final String VERSION_TAG = "Version: ";
    private static final String FILE_VERSION = "1.4.0.0, Platform: N";
    private static final double GPS_L1_FREQ = 154.0 * 10.23e6;  //1575.42MHz
    private static final double GPS_L5_FREQ=115.0 * 10.23e6; //1176.45MHz
    private static final double SPEED_OF_LIGHT = 299792458.0; //[m/s]
    private static final double GPS_L1_WAVELENGTH = SPEED_OF_LIGHT/GPS_L1_FREQ;
    private static final double GPS_L5_WAVELENGTH = SPEED_OF_LIGHT/GPS_L5_FREQ;

    private static final int MAX_FILES_STORED = 100;
    private static final int MINIMUM_USABLE_FILE_SIZE_BYTES = 1000;

    private final Context mContext;

    private final Object mFileLock = new Object();
    private  final Object mFileSubLock = new Object();
    private final Object mFileAccAzLock = new Object();
    private final Object mFileNmeaLock = new Object();
    private final Object mFileNavLock = new Object();
    private BufferedWriter mFileWriter;
    private BufferedWriter mFileSubWriter;
    private BufferedWriter mFileAccAzWriter;
    private BufferedWriter mFileNmeaWriter;
    private BufferedWriter mFileNavWriter;
    private File mFile;
    private File mFileSub;
    private File mFileAccAzi;
    private File mFileNmea;
    private File mFileNav;
    private boolean firsttime;
    private UIFragmentComponent mUiComponent;
    //private LoggerFragment mloggerFragment;
    private boolean notenoughsat = false;
    private boolean firstOBSforAcc = true;
    private ArrayList<Integer> UsedInFixList = new ArrayList<Integer>() ;
    private ArrayList<String> arrayList1= new ArrayList<>();
    private ArrayList<Double>longitudekml=new ArrayList<>();
    private ArrayList<Double>latitudekml=new ArrayList<>();
    private ArrayList<Double>altitudekml=new ArrayList<>();
    private ArrayList<String> gpstkml=new ArrayList<String>();
    private boolean RINEX_NAV_ION_OK = false;
    private int gnsstimeclock_a;
    private int gnsstimeclock_b;
    private int gnsstimeclock_c;
    private double gnsstimeclock_d;
    private int gnsstimeclock_e;
    private int gnsstimeclock_f;
    final float TOLERANCE_MHZ = 1e8f;
    //GLONASS系の補正情報
    private int[] GLONASSFREQ = {1,-4,5,6,1,-4,5,6,-2,-7,0,-1,-2,-7,0,-1,4,-3,3,2,4,-3,3,2};
    private int leapseconds = 18;

    private double[] CURRENT_SMOOTHER_RATE = new double[300];
    private double[] LAST_DELTARANGE = new double[300];
    private double[] LAST_SMOOTHED_PSEUDORANGE = new double[300];
    private double SMOOTHER_RATE = 0.01;
    private boolean initialize = false;

    private double constFullBiasNanos = 0.0;

    //インターバル変数
    private int localintervaltime = 1;

    public synchronized UIFragmentComponent getUiComponent() {
        return mUiComponent;
    }

    public synchronized void setUiComponent(UIFragmentComponent value) {
        mUiComponent = value;
    }
    public FileLogger(Context context) {
        this.mContext = context;
        if(initialize == false){
            Arrays.fill(LAST_DELTARANGE,0.0);
            Arrays.fill(CURRENT_SMOOTHER_RATE,1.0);
            Arrays.fill(LAST_SMOOTHED_PSEUDORANGE,0.0);
            initialize = true;
            Log.d("FileLogger","Initialize complete");
        }
    }

    /**
     * Start a new file logging process.
     */
    public void startNewLog() {
        synchronized (mFileSubLock){
            File baseSubDirectory;
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                baseSubDirectory = new File(Environment.getExternalStorageDirectory(), SettingsFragment.FILE_PREFIXSUB);
                baseSubDirectory.mkdirs();
            } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                logError("Cannot write to external storage.");
                return;
            } else {
                logError("Cannot read external storage.");
                return;
            }
            Date currentTime = Calendar.getInstance().getTime();
            Date now = new Date();
            int observation = now.getYear() - 100;
            String fileNameSub = String.format(SettingsFragment.FILE_NAME + ".kml", SettingsFragment.FILE_PREFIXSUB);
            File currentFileSub = new File(baseSubDirectory, fileNameSub);
            String currentFileSubPath = currentFileSub.getAbsolutePath();
            BufferedWriter currentFileSubWriter;
            try {
                currentFileSubWriter = new BufferedWriter(new FileWriter(currentFileSub));
            } catch (IOException e) {
                logException("Could not open subobservation file: " + currentFileSubPath, e);
                return;
            }

            // サブ観測ファイルへのヘッダ書き出し
            try {
                currentFileSubWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                currentFileSubWriter.newLine();
                currentFileSubWriter.write("<kml xmlns=\"http://earth.google.com/kml/2.0\">");
                currentFileSubWriter.newLine();
                currentFileSubWriter.write("<Document>");
                currentFileSubWriter.newLine();
                currentFileSubWriter.write("<Placemark>");
                currentFileSubWriter.newLine();
                currentFileSubWriter.write("  <name>Rover Track</name>");
                currentFileSubWriter.newLine();
                currentFileSubWriter.write("  <Style>");
                currentFileSubWriter.newLine();
                currentFileSubWriter.write("    <LineStyle>");
                currentFileSubWriter.newLine();
                currentFileSubWriter.write("      <color>aa00FFFF</color>");
                currentFileSubWriter.newLine();
                currentFileSubWriter.write("    </LineStyle>");
                currentFileSubWriter.newLine();
                currentFileSubWriter.write("  </Style>");
                currentFileSubWriter.newLine();
                currentFileSubWriter.write("  <LineString>");
                currentFileSubWriter.newLine();
                currentFileSubWriter.write("    <coordinates>");
                currentFileSubWriter.newLine();
            } catch (IOException e) {
                Toast.makeText(mContext, "Count not initialize Sub observation file", Toast.LENGTH_SHORT).show();
                logException("Count not initialize subobservation file: " + currentFileSubPath, e);
                return;
            }

            if (mFileSubWriter != null) {
                try {
                    mFileSubWriter.close();
                } catch (IOException e) {
                    logException("Unable to close sub observation file streams.", e);
                    return;
                }
            }
            mFileSub = currentFileSub;
            mFileSubWriter = currentFileSubWriter;
            Toast.makeText(mContext, "File opened: " + currentFileSubPath, Toast.LENGTH_SHORT).show();

            // To make sure that files do not fill up the external storage:
            // - Remove all empty files
            FileFilter filter = new FileToDeleteFilter(mFileSub);
            for (File existingFile : baseSubDirectory.listFiles(filter)) {
                existingFile.delete();
            }
            // - Trim the number of files with data
            File[] existingFiles = baseSubDirectory.listFiles();
            int filesToDeleteCount = existingFiles.length - MAX_FILES_STORED;
            if (filesToDeleteCount > 0) {
                Arrays.sort(existingFiles);
                for (int i = 0; i < filesToDeleteCount; ++i) {
                    existingFiles[i].delete();
                }
            }
        }
        synchronized (mFileAccAzLock){
            if(SettingsFragment.ResearchMode) {
                File baseAccAziDirectory;
                String state = Environment.getExternalStorageState();
                if (Environment.MEDIA_MOUNTED.equals(state)) {
                    baseAccAziDirectory = new File(Environment.getExternalStorageDirectory(), SettingsFragment.FILE_PREFIXACCAZI);
                    baseAccAziDirectory.mkdirs();
                } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                    logError("Cannot write to external storage.");
                    return;
                } else {
                    logError("Cannot read external storage.");
                    return;
                }
// csvファイル書き出し Fileクラスで用意されているgetAbsolutePathは絶対パスによるファイルの位置と名前を取得できる
                Date now = new Date();
                String fileNameAccAzi = String.format(SettingsFragment.FILE_NAME + ".csv", SettingsFragment.FILE_PREFIXSUB);
                File currentFileAccAzi = new File(baseAccAziDirectory, fileNameAccAzi);
                String currentFileAccAziPath = currentFileAccAzi.getAbsolutePath();
                BufferedWriter currentFileAccAziWriter;
                try {
                    currentFileAccAziWriter = new BufferedWriter(new FileWriter(currentFileAccAzi));
                } catch (IOException e) {
                    logException("Could not open subobservation file: " + currentFileAccAziPath, e);
                    return;
                }

                // サブ観測ファイルへのヘッダ書き出し
                try {
                    if(SettingsFragment.EnableSensorLog) {
                       // currentFileAccAziWriter.write("Android Acc\nEast,North ");
                       // currentFileAccAziWriter.newLine();
                    }else {
                        currentFileAccAziWriter.write("PseudorangeRate,PseudorangeRate (Carrier Phase),PseudorangeRate (Doppler) ");
                        currentFileAccAziWriter.newLine();
                    }
                } catch (IOException e) {
                    Toast.makeText(mContext, "Count not initialize Sub observation file", Toast.LENGTH_SHORT).show();
                    logException("Count not initialize subobservation file: " + currentFileAccAziPath, e);
                    return;
                }
//csvファイル閉じる
                if (mFileAccAzWriter != null) {
                    try {
                        mFileAccAzWriter.close();
                    } catch (IOException e) {
                        logException("Unable to close sub observation file streams.", e);
                        return;
                    }
                }

                mFileAccAzi = currentFileAccAzi;
                mFileAccAzWriter = currentFileAccAziWriter;
                Toast.makeText(mContext, "File opened: " + currentFileAccAziPath, Toast.LENGTH_SHORT).show();

                // To make sure that files do not fill up the external storage:
                // - Remove all empty files
                FileFilter filter = new FileToDeleteFilter(mFileAccAzi);
                for (File existingFile : baseAccAziDirectory.listFiles(filter)) {
                    existingFile.delete();
                }
                // - Trim the number of files with data
                File[] existingFiles = baseAccAziDirectory.listFiles();
                int filesToDeleteCount = existingFiles.length - MAX_FILES_STORED;
                if (filesToDeleteCount > 0) {
                    Arrays.sort(existingFiles);
                    for (int i = 0; i < filesToDeleteCount; ++i) {
                        existingFiles[i].delete();
                    }
                }
            }
        }

        synchronized (mFileLock) {
            File baseDirectory;
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                baseDirectory = new File(Environment.getExternalStorageDirectory(), SettingsFragment.FILE_PREFIX);
                baseDirectory.mkdirs();
            } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                logError("Cannot write to external storage.");
                return;
            } else {
                logError("Cannot read external storage.");
                return;
            }

            Date now = new Date();
            int observation = now.getYear() - 100;
            String fileName = String.format(SettingsFragment.FILE_NAME + "." + observation + "o", SettingsFragment.FILE_PREFIX);
            File currentFile = new File(baseDirectory, fileName);
            String currentFilePath = currentFile.getAbsolutePath();
            BufferedWriter currentFileWriter;
            try {
                currentFileWriter = new BufferedWriter(new FileWriter(currentFile));
            } catch (IOException e) {
                logException("Could not open observation file: " + currentFilePath, e);
                return;
            }

            // initialize the contents of the file
            try {

                //RINEX ver3.03
                if(SettingsFragment.RINEX303){
                    //RINEX Version Type
                    currentFileWriter.write(String.format("     %3.2f           OBSERVATION DATA    M                   RINEX VERSION / TYPE",3.03));
                    currentFileWriter.newLine();
                    currentFileWriter.write("G = GPS  R = GLONASS  E = GALILEO  J = QZSS  C = BDS        COMMENT             ");
                    currentFileWriter.newLine();
                    currentFileWriter.write("S = SBAS payload  M = Mixed                                 COMMENT             ");
                    currentFileWriter.newLine();
                    String PGM = String.format("%-20s", "GRitz Logger");
                    String RUNBY = String.format("%-20s", Build.MODEL);
                    String DATE = String.format("%-20s", now.getTime());
                    currentFileWriter.write(PGM + RUNBY + DATE + "UTC PGM / RUN BY / DATE");
                    currentFileWriter.newLine();
                    currentFileWriter.write("                                                            MARKER NAME         ");
                    currentFileWriter.newLine();
                    currentFileWriter.write("                                                            MARKER NUMBER       ");
                    currentFileWriter.newLine();
                    currentFileWriter.write("                                                            OBSERVER / AGENCY   ");
                    currentFileWriter.newLine();
                    currentFileWriter.write("                                                            REC # / TYPE / VERS ");
                    currentFileWriter.newLine();
                    currentFileWriter.write("                                                            ANT # / TYPE        ");
                    currentFileWriter.newLine();
                    String X = String.format("%14.4f", 0.0);
                    String Y = String.format("%14.4f", 0.0);
                    String Z = String.format("%14.4f", 0.0);
                    currentFileWriter.write(X + Y + Z + "                  " + "APPROX POSITION XYZ");
                    currentFileWriter.newLine();
                    currentFileWriter.write("        0.0000        0.0000        0.0000                  ANTENNA: DELTA H/E/N");
                    currentFileWriter.newLine();
                        currentFileWriter.write("     0                                                      RCV CLOCK OFFS APPL ");
                    currentFileWriter.newLine();
                    if(SettingsFragment.CarrierPhase){
                        currentFileWriter.write("G    6 C1C L1C S1C C5X L5X S5X                              SYS / # / OBS TYPES ");
                        currentFileWriter.newLine();
                    }else {
                        currentFileWriter.write("G    4 C1C S1C C5X S5X                                      SYS / # / OBS TYPES ");
                        currentFileWriter.newLine();
                    }
                    if(SettingsFragment.useGL){
                        if(SettingsFragment.CarrierPhase){
                            currentFileWriter.write("R    4 L1C C1C D1C S1C                                      SYS / # / OBS TYPES ");
                            currentFileWriter.newLine();
                        }else {
                            currentFileWriter.write("R    3 C1C D1C S1C                                          SYS / # / OBS TYPES ");
                            currentFileWriter.newLine();
                        }
                    }
                    if(SettingsFragment.useQZ){
                        if(SettingsFragment.CarrierPhase){
                            currentFileWriter.write("J    6 C1C L1C S1C C5X L5X S5X                              SYS / # / OBS TYPES ");
                            currentFileWriter.newLine();
                        }else {
                            currentFileWriter.write("J    4 C1C S1C C5X S5X                                      SYS / # / OBS TYPES ");
                            currentFileWriter.newLine();
                        }
                    }
                    if(SettingsFragment.useGA){
                        if(SettingsFragment.CarrierPhase){
                            currentFileWriter.write("E    6 C1X L1X S1X C5X L5X S5X                              SYS / # / OBS TYPES ");
                            currentFileWriter.newLine();
                        }else {
                            currentFileWriter.write("E    4 C1C S1C C5X S5X                                      SYS / # / OBS TYPES ");
                            currentFileWriter.newLine();
                        }
                    }
                    if(SettingsFragment.useBD){
                        if(SettingsFragment.CarrierPhase){
                            currentFileWriter.write("C    4 L2I C2I D2I S2I                                      SYS / # / OBS TYPES ");
                            currentFileWriter.newLine();
                        }else {
                            currentFileWriter.write("C    3 C2I D2I S2I                                          SYS / # / OBS TYPES ");
                            currentFileWriter.newLine();
                        }
                    }
                }//RINEX ver2.11
                else {
                    //RINEX Version Type
                    currentFileWriter.write("     2.11           OBSERVATION DATA    G (GPS)             RINEX VERSION / TYPE");
                    currentFileWriter.newLine();
                    //PGM RUNBY DATE
                    String PGM = String.format("%-20s", "AndroidGNSSReceiver");
                    String RUNBY = String.format("%-20s", "RITSUMEIKAN KUBOLAB");
                    String DATE = String.format("%-20s", now.getTime());
                    currentFileWriter.write(PGM + RUNBY + DATE + "PGM / RUN BY / DATE");
                    currentFileWriter.newLine();
                    //COMMENT
                    //String COMMENT = String.format("%-60s","Android Ver7.0 Nougat");
                    //currentFileWriter.write( COMMENT +  "COMMENT");
                    //currentFileWriter.newLine();
                    //MARKER NAME
                    String MARKERNAME = String.format("%-60s", Build.DEVICE);
                    currentFileWriter.write(MARKERNAME + "MARKER NAME");
                    currentFileWriter.newLine();
                    //MARKER NUMBER
                    //OBSERVER AGENCY
                    String OBSERVER = String.format("%-20s", "GRitzLogger");
                    String AGENCY = String.format("%-40s", "KUBOLAB");
                    currentFileWriter.write(OBSERVER + AGENCY + "OBSERVER / AGENCY");
                    currentFileWriter.newLine();
                    //REC TYPE VERS
                    String REC = String.format("%-20s", "0");
                    String TYPE = String.format("%-20s", "Android Receiver");
                    String VERS = String.format("%-20s", Build.VERSION.BASE_OS);
                    currentFileWriter.write(REC + TYPE + VERS + "REC # / TYPE / VERS");
                    currentFileWriter.newLine();
                    //ANT TYPE
                    String ANT = String.format("%-20s", "0");
                    String ANTTYPE = String.format("%-40s", "Android Anttena");
                    currentFileWriter.write(ANT + ANTTYPE + "ANT # / TYPE");
                    currentFileWriter.newLine();
                    //APPROX POSITION XYZ
                    String X = String.format("%14.4f", 0.0);
                    String Y = String.format("%14.4f", 0.0);
                    String Z = String.format("%14.4f", 0.0);
                    currentFileWriter.write(X + Y + Z + "                  " + "APPROX POSITION XYZ");
                    currentFileWriter.newLine();
                    //ANTENNA: DELTA H/E/N
                    String H = String.format("%14.4f", 0.0);
                    String E = String.format("%14.4f", 0.0);
                    String N = String.format("%14.4f", 0.0);
                    currentFileWriter.write(H + E + N + "                  " + "ANTENNA: DELTA H/E/N");
                    currentFileWriter.newLine();
                    //WAVELENGTH FACT L1/2
                    String WAVELENGTH = String.format("%-6d%-54d", 1, 0);
                    currentFileWriter.write(WAVELENGTH + "WAVELENGTH FACT L1/2");
                    currentFileWriter.newLine();
                    //# / TYPES OF OBSERV
                    if (SettingsFragment.CarrierPhase) {
                        String NUMBEROFOBS = String.format("%-6d", 6);
                        String OBSERV = String.format("%-54s", "    L1    C1    S1    L5    C5    S5");
                        currentFileWriter.write(NUMBEROFOBS + OBSERV + "# / TYPES OF OBSERV");
                        currentFileWriter.newLine();
                    } else {
                        String NUMBEROFOBS = String.format("%-6d", 2);
                        String OBSERV = String.format("%-54s", "    C1    S1");
                        currentFileWriter.write(NUMBEROFOBS + OBSERV + "# / TYPES OF OBSERV");
                        currentFileWriter.newLine();
                    }
                    //INTERVAL
                    String INTERVAL = String.format("%-60.3f", 1.0);
                    currentFileWriter.write(INTERVAL + "INTERVAL");
                    currentFileWriter.newLine();
                }
                firsttime = true;
                localintervaltime = SettingsFragment.interval;
            } catch (IOException e) {
                Toast.makeText(mContext, "Count not initialize observation file", Toast.LENGTH_SHORT).show();
                logException("Count not initialize file: " + currentFilePath, e);
                return;
            }

            if (mFileWriter != null) {
                try {
                    mFileWriter.close();
                } catch (IOException e) {
                    logException("Unable to close all file streams.", e);
                    return;
                }
            }

            mFile = currentFile;
            mFileWriter = currentFileWriter;
            Toast.makeText(mContext, "File opened: " + currentFilePath, Toast.LENGTH_SHORT).show();

            // To make sure that files do not fill up the external storage:
            // - Remove all empty files
            FileFilter filter = new FileToDeleteFilter(mFile);
            for (File existingFile : baseDirectory.listFiles(filter)) {
                existingFile.delete();
            }
            // - Trim the number of files with data
            File[] existingFiles = baseDirectory.listFiles();
            int filesToDeleteCount = existingFiles.length - MAX_FILES_STORED;
            if (filesToDeleteCount > 0) {
                Arrays.sort(existingFiles);
                for (int i = 0; i < filesToDeleteCount; ++i) {
                    existingFiles[i].delete();
                }
            }
        }

        //NMEAファイル
        synchronized (mFileNmeaLock){
                File baseNmeaDirectory;
                String state = Environment.getExternalStorageState();
                if (Environment.MEDIA_MOUNTED.equals(state)) {
                    baseNmeaDirectory = new File(Environment.getExternalStorageDirectory(), SettingsFragment.FILE_PREFIXNMEA);
                    baseNmeaDirectory.mkdirs();
                } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                    logError("Cannot write to external storage.");
                    return;
                } else {
                    logError("Cannot read external storage.");
                    return;
                }

                Date now = new Date();
                String fileNameNmea = String.format(SettingsFragment.FILE_NAME + ".nmea", SettingsFragment.FILE_PREFIXNMEA);
                File currentFileNmea = new File(baseNmeaDirectory, fileNameNmea);
                String currentFileNmeaPath = currentFileNmea.getAbsolutePath();
                BufferedWriter currentFileNmeaWriter;
                try {
                    currentFileNmeaWriter = new BufferedWriter(new FileWriter(currentFileNmea));
                } catch (IOException e) {
                    logException("Could not open NMEA file: " + currentFileNmea, e);
                    return;
                }

                // NMEAファイルへのヘッダ書き出し
/*
                try {
                    currentFileNmeaWriter.write("NMEA");
                    currentFileNmeaWriter.newLine();
                } catch (IOException e) {
                    Toast.makeText(mContext, "Count not initialize Sub observation file", Toast.LENGTH_SHORT).show();
                    logException("Count not initialize NMEA file: " + currentFileNmeaPath, e);
                    return;
                }
*/
                if (mFileNmeaWriter != null) {
                    try {
                        mFileNmeaWriter.close();
                    } catch (IOException e) {
                        logException("Unable to close NMEA file streams.", e);
                        return;
                    }
                }
                mFileNmea = currentFileNmea;
                mFileNmeaWriter = currentFileNmeaWriter;
                Toast.makeText(mContext, "File opened: " + currentFileNmeaPath, Toast.LENGTH_SHORT).show();

                // To make sure that files do not fill up the external storage:
                // - Remove all empty files
                FileFilter filter = new FileToDeleteFilter(mFileNmea);
                for (File existingFile : baseNmeaDirectory.listFiles(filter)) {
                    existingFile.delete();
                }
                // - Trim the number of files with data
                File[] existingFiles = baseNmeaDirectory.listFiles();
                int filesToDeleteCount = existingFiles.length - MAX_FILES_STORED;
                if (filesToDeleteCount > 0) {
                    Arrays.sort(existingFiles);
                    for (int i = 0; i < filesToDeleteCount; ++i) {
                        existingFiles[i].delete();
                    }
                }
        }
        //Nファイル書き出し
        if(SettingsFragment.RINEXNAVLOG) {
            synchronized (mFileNavLock) {
                File baseNmeaDirectory;
                String state = Environment.getExternalStorageState();
                if (Environment.MEDIA_MOUNTED.equals(state)) {
                    baseNmeaDirectory = new File(Environment.getExternalStorageDirectory(), SettingsFragment.FILE_PREFIXNAV);
                    baseNmeaDirectory.mkdirs();
                } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                    logError("Cannot write to external storage.");
                    return;
                } else {
                    logError("Cannot read external storage.");
                    return;
                }

                Date now = new Date();
                int observation = now.getYear() - 100;
                String fileNameNav = String.format(SettingsFragment.FILE_NAME + "." + observation + "n", SettingsFragment.FILE_PREFIXNAV);
                File currentFileNav = new File(baseNmeaDirectory, fileNameNav);
                String currentFileNavPath = currentFileNav.getAbsolutePath();
                BufferedWriter currentFileNavWriter;
                try {
                    currentFileNavWriter = new BufferedWriter(new FileWriter(currentFileNav));
                } catch (IOException e) {
                    logException("Could not open NMEA file: " + currentFileNav, e);
                    return;
                }
                try {
                    currentFileNavWriter.write("     3.03           N: GNSS NAV DATA    M: MIXED            RINEX VERSION / TYPE");
                    currentFileNavWriter.newLine();
                    currentFileNavWriter.write("                                                            PGM / RUN BY / DATE");
                    currentFileNavWriter.newLine();
                } catch (IOException e) {
                    Toast.makeText(mContext, "Count not initialize observation file", Toast.LENGTH_SHORT).show();
                    logException("Count not initialize file: " + currentFileNavPath, e);
                    return;
                }
/*
                try {
                    currentFileNmeaWriter.write("NMEA");
                    currentFileNmeaWriter.newLine();
                } catch (IOException e) {
                    Toast.makeText(mContext, "Count not initialize Sub observation file", Toast.LENGTH_SHORT).show();
                    logException("Count not initialize NMEA file: " + currentFileNmeaPath, e);
                    return;
                }
*/
                if (mFileNavWriter != null) {
                    try {
                        mFileNavWriter.close();
                    } catch (IOException e) {
                        logException("Unable to close Nav file streams.", e);
                        return;
                    }
                }
                mFileNav = currentFileNav;
                mFileNavWriter = currentFileNavWriter;
                Toast.makeText(mContext, "File opened: " + currentFileNavPath, Toast.LENGTH_SHORT).show();

                // To make sure that files do not fill up the external storage:
                // - Remove all empty files
                FileFilter filter = new FileToDeleteFilter(mFileNav);
                for (File existingFile : baseNmeaDirectory.listFiles(filter)) {
                    existingFile.delete();
                }
                // - Trim the number of files with data
                File[] existingFiles = baseNmeaDirectory.listFiles();
                int filesToDeleteCount = existingFiles.length - MAX_FILES_STORED;
                if (filesToDeleteCount > 0) {
                    Arrays.sort(existingFiles);
                    for (int i = 0; i < filesToDeleteCount; ++i) {
                        existingFiles[i].delete();
                    }
                }
            }
        }

    } // 各ファイル編集

    /**
     * Send the current log via email or other options selected from a pop menu shown to the user. A
     * new log is started when calling this function.
     */
    public void send() {
        //mUiComponent.ShowProgressWindow(true);
        if (mFile == null) {
            //mUiComponent.ShowProgressWindow(false);
            return;
        }
        if (mFileSub == null){
            //mUiComponent.ShowProgressWindow(false);
            return;
        }
        if(mFileAccAzi == null && SettingsFragment.ResearchMode){
            //mUiComponent.ShowProgressWindow(false);
            return;
        }
        if(mFileNmea == null){
            //mUiComponent.ShowProgressWindow(false);
            return;
        }
        if(mFileNav == null && SettingsFragment.RINEXNAVLOG){
            //mUiComponent.ShowProgressWindow(false);
            return;
        }
        try {
            mFileSubWriter.write("    </coordinates>\n  </LineString>\n</Placemark>\n<Folder>");
            // </coordinates></LineString></Placemark><Folder></Document></kml>
            mFileSubWriter.newLine();
            for (int i=0; i<arrayList1.size(); i++){
                mFileSubWriter.write(" <Placemark>\n");
                mFileSubWriter.write("<name>"+arrayList1.get(i)+"\"</name>\"");
                mFileSubWriter.newLine();
               // mFileSubWriter.write("<TimeStamp><when>"+arrayList1.get(i)+"</when></TimeStamp>");
                //mFileSubWriter.newLine();
                mFileSubWriter.write("<Style>\n<BalloonStyle><text><![CDATA[$[description]]]></text></BalloonStyle>\n" +
                                "    <LabelStyle><scale>0</scale></LabelStyle>\n" +
                                "    <IconStyle>\n" +
                                "      <scale>0.3</scale>\n" +
                                "      <color>ffFFFF00</color>\n" +
                                "      <Icon><href>http://maps.google.com/mapfiles/kml/pal2/icon26.png</href></Icon>\n" +
                                "    </IconStyle>\n" +
                                "  </Style>\n" +
                                "  <Point>" );
                mFileSubWriter.newLine();
                mFileSubWriter.write("<coordinates>"+longitudekml.get(i)+","+latitudekml.get(i)+","+altitudekml.get(i)+ "</coordinates>");
                mFileSubWriter.write("</Point>");
                mFileSubWriter.newLine();
                mFileSubWriter.write("</Placemark>");
            }mFileSubWriter.newLine();
            mFileSubWriter.write("</Folder>\n  </Document>\n  </kml>");
        }catch (IOException e){
            Toast.makeText(mContext, "ERROR_WRITINGFOTTER_FILE", Toast.LENGTH_SHORT).show();
            logException(ERROR_WRITING_FILE, e);
        }

        if (mFileWriter != null) {
            try {
                mFileWriter.close();
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,Uri.fromFile(mFile));
                mContext.sendBroadcast(mediaScanIntent);
                mFileWriter = null;
            } catch (IOException e) {
                logException("Unable to close all file streams.", e);
                //mUiComponent.ShowProgressWindow(false);
                return;
            }
        }

        if(mFileSubWriter != null) {
            try {
                mFileSubWriter.close();
                Intent mediaScanIntentSub = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,Uri.fromFile(mFileSub));
                mContext.sendBroadcast(mediaScanIntentSub);
                mFileSubWriter = null;
            } catch (IOException e) {
                logException("Unable to close subobservation file streams.", e);
                //mUiComponent.ShowProgressWindow(false);
                return;
            }
        }
        if(SettingsFragment.ResearchMode) {
            if (mFileAccAzWriter != null) {
                try {
                    mFileAccAzWriter.close();
                    Intent mediaScanIntentSensor = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,Uri.fromFile(mFileAccAzi));
                    mContext.sendBroadcast(mediaScanIntentSensor);
                    mFileAccAzWriter = null;
                } catch (IOException e) {
                    logException("Unable to close sensorlog file streams.", e);
                    //mUiComponent.ShowProgressWindow(false);
                    return;
                }
            }
        }
        if(mFileNmeaWriter != null) {
            try {
                mFileNmeaWriter.close();
                Intent mediaScanIntentSub = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,Uri.fromFile(mFileNmea));
                mContext.sendBroadcast(mediaScanIntentSub);
                mFileNmeaWriter = null;
            } catch (IOException e) {
                logException("Unable to close NMEA file streams.", e);
                //mUiComponent.ShowProgressWindow(false);
                return;
            }
        }
        if(SettingsFragment.RINEXNAVLOG){
            try {
                mFileNavWriter.close();
                Intent mediaScanIntentSub = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,Uri.fromFile(mFileNav));
                mContext.sendBroadcast(mediaScanIntentSub);
                mFileNavWriter = null;
                RINEX_NAV_ION_OK = false;
            } catch (IOException e) {
                logException("Unable to close NAV file streams.", e);
                //mUiComponent.ShowProgressWindow(false);
                return;
            }
        }
        //Log.i("progress","dismiss");
        //mUiComponent.ShowProgressWindow(false);
    } //各ファイル保存

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public void onLocationChanged(Location location ) {
        if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {

            synchronized (mFileSubLock) {
                if (mFileSubWriter == null) {
                    return;
                }
                else{
                    try {
                        String locationStream =
                                String.format(

                                        Locale.US,
                                       //KMLの中身
                                        "       %15.9f,%15.9f,%15.9f",
                                        location.getLongitude(),
                                        location.getLatitude(),
                                        location.getAltitude()

                                        );
                        longitudekml.add(location.getLongitude());
                        latitudekml.add(location.getLatitude());
                        altitudekml.add(location.getAltitude());
                        Calendar myCal= Calendar.getInstance();
                        DateFormat myFormat = new SimpleDateFormat("yyyy/MM/dd");
                        String myName = myFormat.format(myCal.getTime());
                        //mFileSubWriter.write(myName);
                        //mFileSubWriter.newLine();
                        String gnsstime=
                                String.format("%d,%d,%d,%d,%d,%13.7f",gnsstimeclock_f,gnsstimeclock_e,gnsstimeclock_a,gnsstimeclock_b,gnsstimeclock_c,gnsstimeclock_d);
                        arrayList1.add(gnsstime);
                        mFileSubWriter.write(locationStream);
                        mFileSubWriter.newLine();
                    }catch (IOException e){
                        Toast.makeText(mContext, "ERROR_WRITING_FILE", Toast.LENGTH_SHORT).show();
                        logException(ERROR_WRITING_FILE, e);
                    }
                }
            }
        }
    }

    @Override
    public void onLocationStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
        synchronized (mFileLock) {
            if (mFileWriter == null) {
                return;
            }
            GnssClock gnssClock = event.getClock();



            //平滑化方式が変更になれば係数を初期化
            if(SettingsFragment.SMOOTHER_RATE_RESET_FLAG_FILE){
                Arrays.fill(LAST_DELTARANGE,0.0);
                Arrays.fill(CURRENT_SMOOTHER_RATE,1.0);
                Arrays.fill(LAST_SMOOTHED_PSEUDORANGE,0.0);
                SettingsFragment.SMOOTHER_RATE_RESET_FLAG_FILE = false;
            }

            for (GnssMeasurement measurement : event.getMeasurements()) {
                try {
                    if(firsttime == true && measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS){
                        gnssClock = event.getClock();

                        double weekNumber = Math.floor(-(gnssClock.getFullBiasNanos() * 1e-9 / 604800));
                        double weekNumberNanos = weekNumber * 604800 * 1e9;
                        double tRxNanos = gnssClock.getTimeNanos() - gnssClock.getFullBiasNanos() - weekNumberNanos;
                        if (gnssClock.hasBiasNanos()) {
                            tRxNanos = tRxNanos - gnssClock.getBiasNanos();
                        }
                        //GPS週・週秒から年月日時分秒に変換
                        GPSWStoGPST gpswStoGPST = new GPSWStoGPST();
                        ReturnValue value = gpswStoGPST.method(weekNumber, tRxNanos * 1e-9);
                        if (measurement.getTimeOffsetNanos() != 0) {
                            tRxNanos = tRxNanos - measurement.getTimeOffsetNanos();
                        }
                        double tRxSeconds = tRxNanos * 1e-9;
                        double tTxSeconds = measurement.getReceivedSvTimeNanos() * 1e-9;
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

                        double prm = prSeconds * 2.99792458e8;  //コード擬似距離の計算
                        if (iRollover == false && prm > 0 && prSeconds < 0.5) {
                            if (SettingsFragment.RINEX303) {
                                mFileWriter.write(String.format("  %4d    %2d    %2d    %2d    %2d   %10.7f     GPS         TIME OF FIRST OBS   ", value.Y, value.M, value.D, value.h, value.m, value.s));
                                //mFileWriter.write(String.format("  %4d    %2d    %2d    %2d    %2d   %10.7f     GPS         TIME OF END OBS     ", value.Y, value.M, value.D, value.h, value.m, value.s));
                                mFileWriter.newLine();
                                mFileWriter.write(" 24 R01  1 R02 -4 R03  5 R04  6 R05  1 R06 -4 R07  5 R08  6 GLONASS SLOT / FRQ #");
                                mFileWriter.newLine();
                                mFileWriter.write("    R09 -2 R10 -7 R11  0 R12 -1 R13 -2 R14 -7 R15  0 R16 -1 GLONASS SLOT / FRQ #");
                                mFileWriter.newLine();
                                mFileWriter.write("    R17  4 R18 -3 R19  3 R20  2 R21  4 R22 -3 R23  3 R24  2 GLONASS SLOT / FRQ #");
                                mFileWriter.newLine();
                                mFileWriter.write("                                                            END OF HEADER       ");
                                mFileWriter.newLine();

                            } else {
                                String StartTimeOBS = String.format("%6d%6d%6d%6d%6d%13.7f     %3s         TIME OF FIRST OBS\n", value.Y, value.M, value.D, value.h, value.m, value.s, "GPS");
                                //END OF HEADER
                                String ENDOFHEADER = String.format("%73s", "END OF HEADER");
                                mFileWriter.write(StartTimeOBS + ENDOFHEADER);
                                mFileWriter.newLine();
                            }
                            //FullBiasNanosを固定する.
                            if (gnssClock.hasBiasNanos()) {
                                constFullBiasNanos = gnssClock.getFullBiasNanos() + gnssClock.getBiasNanos();
                            } else {
                                constFullBiasNanos = gnssClock.getFullBiasNanos();
                            }
                            firsttime = false;

                         }
                    }
                    else{

                    }
                } catch (IOException e) {
                    Toast.makeText(mContext, "ERROR_WRITING_FILE", Toast.LENGTH_SHORT).show();
                    logException(ERROR_WRITING_FILE, e);
                }
            }
            try {
                writeGnssMeasurementToFile(gnssClock,event);
                if(SettingsFragment.enableTimer){
                    if(true) {
                        SettingsFragment.timer = SettingsFragment.timer - 1;
                        getUiComponent().RefreshTimer();
                    }
                }
            } catch (IOException e){
                logException(ERROR_WRITING_FILE, e);
            }

        }
        firstOBSforAcc = true;
    }  //伝搬時間の計算、oファイルヘッダ下部

    @Override
    public void onGnssMeasurementsStatusChanged(int status) {}

    @Override
    public void onGnssNavigationMessageReceived(GnssNavigationMessage navigationMessage) {
        if(SettingsFragment.RINEXNAVLOG){
            synchronized (mFileNavLock){
                if(mFileNavWriter == null){
                    return;
                }
                /*try {
                    if(RINEX_NAV_ION_OK == false) {
                        StringBuilder NAV_ION = new StringBuilder();
                        GnssNavigationConv mGnssNavigationConv = new GnssNavigationConv();
                        StringBuilder ION = mGnssNavigationConv.onNavMessageReported((byte) navigationMessage.getSvid(),(byte)navigationMessage.getType(),navigationMessage.getMessageId(),navigationMessage.getSubmessageId(),navigationMessage.getData());
                        if(ION != null && ION.toString().indexOf("null") == -1) {
                            NAV_ION.append(ION);
                            Log.d("NAV",ION.toString());
                            mFileNavWriter.write(NAV_ION.toString());
                            RINEX_NAV_ION_OK = true;
                        }
                    }
                }catch (IOException e){
                    Toast.makeText(mContext, "ERROR_WRITING_FILE", Toast.LENGTH_SHORT).show();
                    logException(ERROR_WRITING_FILE, e);
                }*/
            }
        }
    }


    public void onSensorListener(String listener,float azimuth,float accZ,float altitude){
        synchronized (mFileAccAzLock) {
            if (mFileAccAzWriter == null || SettingsFragment.ResearchMode == false || !SettingsFragment.EnableSensorLog) {
                return;
            }
            else{
                if(listener == "") {
                    try {
                       // Calendar myCal= Calendar.getInstance();
                       // DateFormat myFormat = new SimpleDateFormat("MM/dd/hh:mm.ss");
                       // String myName = myFormat.format(myCal.getTime());
                        //csv ファイルの中身　歩行者の位置モデルの指揮　altitudeは気圧センサ

                        String SensorStream =
                                String.format("%f,%f,%f", (float) (accZ * Math.sin(azimuth)), (float) (accZ * Math.cos(azimuth)), altitude);
                        mFileAccAzWriter.write(SensorStream);
                       // String day=
                        //        String.format("%6d,%6d,%6d,%13.7f,\t",gnsstimeclock_a,gnsstimeclock_b,gnsstimeclock_c,gnsstimeclock_d,myName);
                      // mFileAccAzWriter.write(day);
                    //    String time=
//                               String.format("%13.7f",myName);
                      //  mFileAccAzWriter.write(myName);
                        mFileAccAzWriter.newLine();
                    } catch (IOException e) {
                        Toast.makeText(mContext, "ERROR_WRITING_FILE", Toast.LENGTH_SHORT).show();
                        logException(ERROR_WRITING_FILE, e);
                    }
                }
            }
        }
    }
    @Override
    public void onGnssNavigationMessageStatusChanged(int status) {
    }

    @Override
    public void  onGnssStatusChanged(GnssStatus gnssStatus) {
        try {
            writeUseInFixArray(gnssStatus);
        }catch (IOException e){
            Toast.makeText(mContext, "FATAL_ERROR_FOR_WRITING_ARRAY", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onNmeaReceived(long timestamp, String s) {
        synchronized (mFileNmeaLock) {
            if (mFileNmeaWriter == null) {
                return;
            }
            else{
                try {
                    String NmeaStream = String.format("%s", s);
                    mFileNmeaWriter.write(NmeaStream);
//                    mFileNmeaWriter.newLine();
                }catch (IOException e){
                    Toast.makeText(mContext, "ERROR_WRITING_FILE", Toast.LENGTH_SHORT).show();
                    logException(ERROR_WRITING_FILE, e);
                }
            }
        }
    }

    @Override
    public void onListenerRegistration(String listener, boolean result) {}

    private void writeUseInFixArray(GnssStatus gnssStatus) throws IOException{
        for (int i = 0; i < gnssStatus.getSatelliteCount(); i++) {
            if (gnssStatus.usedInFix(i) && gnssStatus.getConstellationType(i) == GnssStatus.CONSTELLATION_GPS) {
                if (UsedInFixList.indexOf(gnssStatus.getSvid(i)) == -1) {
                    UsedInFixList.add(gnssStatus.getSvid(i));
                }
            } else if(gnssStatus.getConstellationType(i) == GnssStatus.CONSTELLATION_GPS) {
                int index = UsedInFixList.indexOf(gnssStatus.getSvid(i));
                if (index != -1) {
                    UsedInFixList.remove(index);
                }
            }
        }
    }

    private boolean ReadUseInFixArray(int Svid) throws IOException{
        return UsedInFixList.indexOf(Svid) != -1;
    }

    private void writeGnssMeasurementToFile(GnssClock clock, GnssMeasurementsEvent event) throws IOException {
        if (localintervaltime < SettingsFragment.interval) {
            localintervaltime++;
            Log.i("interval", String.valueOf(localintervaltime) + "," + String.valueOf(SettingsFragment.interval));
            return;
        } else {
            localintervaltime = 1;
        }
        StringBuilder Time = new StringBuilder();
        StringBuilder Prn = new StringBuilder();
        StringBuilder Measurements = new StringBuilder();
        String SensorStream = "";
        boolean firstOBS = true;
        int satnumber = 0;


        //2周波観測
        if (SettingsFragment.useDualFreq) {

            //RINEX303
            if (SettingsFragment.RINEX303) {

                //GPS衛星
                String L5carrier_1 = "";
                String L5code_1 = "";
                String S5C_1 = "";
                String L5carrier_3 = "";
                String L5code_3 = "";
                String S5C_3 = "";
                String L5carrier_6 = "";
                String L5code_6 = "";
                String S5C_6 = "";
                String L5carrier_8 = "";
                String L5code_8 = "";
                String S5C_8 = "";
                String L5carrier_9 = "";
                String L5code_9 = "";
                String S5C_9 = "";
                String L5carrier_10 = "";
                String L5code_10 = "";
                String S5C_10 = "";
                String L5carrier_24 = "";
                String L5code_24 = "";
                String S5C_24 = "";
                String L5carrier_25 = "";
                String L5code_25 = "";
                String S5C_25 = "";
                String L5carrier_26 = "";
                String L5code_26 = "";
                String S5C_26 = "";
                String L5carrier_30 = "";
                String L5code_30 = "";
                String S5C_30 = "";
                String L5carrier_27 = "";
                String L5code_27 = "";
                String S5C_27 = "";
                String L5carrier_32 = "";
                String L5code_32 = "";
                String S5C_32 = "";

                // QZSS衛星
                String QZSS_L1_1 = "";
                String QZSS_C1_1 = "";
                String QZS1_1 = "";
                String QZSS_L1_2 = "";
                String QZSS_C1_2 = "";
                String QZS1_2 = "";
                String QZSS_L1_3 = "";
                String QZSS_C1_3 = "";
                String QZS1_3 = "";
                String QZSS_L1_4 = "";
                String QZSS_C1_4 = "";
                String QZS1_4 = "";

                // Galileo衛星
                String Galileo_L1_1 = "";
                String Galileo_C1_1 = "";
                String GalileoS1_1 = "";
                String Galileo_L1_2 = "";
                String Galileo_C1_2 = "";
                String GalileoS1_2 = "";
                String Galileo_L1_3 = "";
                String Galileo_C1_3 = "";
                String GalileoS1_3 = "";
                String Galileo_L1_4 = "";
                String Galileo_C1_4 = "";
                String GalileoS1_4 = "";
                String Galileo_L1_5 = "";
                String Galileo_C1_5 = "";
                String GalileoS1_5 = "";
                String Galileo_L1_6 = "";
                String Galileo_C1_6 = "";
                String GalileoS1_6 = "";
                String Galileo_L1_7 = "";
                String Galileo_C1_7 = "";
                String GalileoS1_7 = "";
                String Galileo_L1_8 = "";
                String Galileo_C1_8 = "";
                String GalileoS1_8 = "";
                String Galileo_L1_9 = "";
                String Galileo_C1_9 = "";
                String GalileoS1_9 = "";
                String Galileo_L1_10 = "";
                String Galileo_C1_10 = "";
                String GalileoS1_10 = "";
                String Galileo_L1_11 = "";
                String Galileo_C1_11 = "";
                String GalileoS1_11 = "";
                String Galileo_L1_12 = "";
                String Galileo_C1_12 = "";
                String GalileoS1_12 = "";
                String Galileo_L1_13 = "";
                String Galileo_C1_13 = "";
                String GalileoS1_13 = "";
                String Galileo_L1_14 = "";
                String Galileo_C1_14 = "";
                String GalileoS1_14 = "";
                String Galileo_L1_15 = "";
                String Galileo_C1_15 = "";
                String GalileoS1_15 = "";
                String Galileo_L1_18 = "";
                String Galileo_C1_18 = "";
                String GalileoS1_18 = "";
                String Galileo_L1_19 = "";
                String Galileo_C1_19 = "";
                String GalileoS1_19 = "";
                String Galileo_L1_20 = "";
                String Galileo_C1_20 = "";
                String GalileoS1_20 = "";
                String Galileo_L1_21 = "";
                String Galileo_C1_21 = "";
                String GalileoS1_21 = "";
                String Galileo_L1_22 = "";
                String Galileo_C1_22 = "";
                String GalileoS1_22 = "";
                String Galileo_L1_24 = "";
                String Galileo_C1_24 = "";
                String GalileoS1_24 = "";
                String Galileo_L1_25 = "";
                String Galileo_C1_25 = "";
                String GalileoS1_25 = "";
                String Galileo_L1_26 = "";
                String Galileo_C1_26 = "";
                String GalileoS1_26 = "";
                String Galileo_L1_27 = "";
                String Galileo_C1_27 = "";
                String GalileoS1_27 = "";
                String Galileo_L1_30 = "";
                String Galileo_C1_30 = "";
                String GalileoS1_30 = "";
                String Galileo_L1_31 = "";
                String Galileo_C1_31 = "";
                String GalileoS1_31 = "";
                String Galileo_L1_33 = "";
                String Galileo_C1_33 = "";
                String GalileoS1_33 = "";
                String Galileo_L1_36 = "";
                String Galileo_C1_36 = "";
                String GalileoS1_36 = "";

                String OBSTime = "";
                GnssClock gnssClock = event.getClock();
                double weekNumber = Math.floor(-(gnssClock.getFullBiasNanos() * 1e-9 / 604800));
                double weekNumberNanos = weekNumber * 604800 * 1e9;
                //FullBiasNanosがリセットされたら再計算
                if (constFullBiasNanos == 0.0) {
                    if (gnssClock.hasBiasNanos()) {
                        constFullBiasNanos = gnssClock.getFullBiasNanos() + gnssClock.getBiasNanos();
                    } else {
                        constFullBiasNanos = gnssClock.getFullBiasNanos();
                    }
                }
                //Log.d("ConstBias",String.valueOf(constFullBiasNanos%1e5));
                //Log.d("InstBias",String.valueOf((gnssClock.getFullBiasNanos()%1e5)));
                //Log.d("TimeNanosBias",String.valueOf(((gnssClock.getFullBiasNanos()%1e5) - (constFullBiasNanos%1e5))));
                double tRxNanos = gnssClock.getTimeNanos() - constFullBiasNanos - weekNumberNanos;
                //GPS週・週秒から年月日時分秒に変換
                GPSWStoGPST gpswStoGPST = new GPSWStoGPST();
                ReturnValue value = gpswStoGPST.method(weekNumber, tRxNanos * 1e-9);
                for (GnssMeasurement measurement : event.getMeasurements()) {
                    if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS || (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS && SettingsFragment.useGL) || (measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS && SettingsFragment.useQZ) || ((measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO) && (SettingsFragment.useGA)) || (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU && SettingsFragment.useBD)) {
                        double tRxSeconds = (tRxNanos - measurement.getTimeOffsetNanos()) * 1e-9;
                        double tTxSeconds = measurement.getReceivedSvTimeNanos() * 1e-9;
                        //GLONASS時刻への変換
                        if ((measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS)) {
                            double tRxSeconds_GLO = tRxSeconds % 86400;
                            double tTxSeconds_GLO = tTxSeconds - 10800 + leapseconds;
                            if (tTxSeconds_GLO < 0) {
                                tTxSeconds_GLO = tTxSeconds_GLO + 86400;
                            }
                            tRxSeconds = tRxSeconds_GLO;
                            tTxSeconds = tTxSeconds_GLO;
                        }
                        //Beidou時刻への変換
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

                        /*急場の変更！！*/
                        String DeviceName = Build.DEVICE;
                        //Log.d("DEVICE",DeviceName);
                        /*急場の変更！！*/
                        double prm = prSeconds * 2.99792458e8;
                        //コード擬似距離の計算
                        if (iRollover == false && prm > 0 && prSeconds < 0.5) {
                            if (firstOBS == true) {
                                OBSTime = String.format("> %4d %2d %2d %2d %2d%11.7f  0", value.Y, value.M, value.D, value.h, value.m, value.s);
                                SensorStream =
                                        String.format("%6d,%6d,%6d,%6d,%6d,%13.7f", value.Y, value.M, value.D, value.h, value.m, value.s);
                                //firstOBS = false;

                            }
                            //GPSのPRN番号と時刻用String
                            String prn = "";
                            if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 115.0 * 10.23e6, TOLERANCE_MHZ)) {
                                if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS) {
                                    prn = String.format("G%02d", measurement.getSvid());
                                } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS) {
                                    prn = String.format("R%02d", measurement.getSvid());
                                } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS) {
                                    prn = String.format("J%02d", measurement.getSvid() - 192);
                                } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO) {
                                    prn = String.format("E%02d", measurement.getSvid());
                                } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU) {
                                    prn = String.format("C%02d", measurement.getSvid());
                                }
                                // Prn.append(prn);
                                satnumber = satnumber + 1;
                            }
                            //Measurements.append(prn);

                            String C1C = String.format("%14.3f%s%s", prm, " ", " ");
                            String L1C = String.format("%14.3f%s%s", 0.0, " ", " ");

                            //搬送波の謎バイアスを補正したい
                            double ADR = measurement.getAccumulatedDeltaRangeMeters();
                            if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS || measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO || measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS) {
                                if (SettingsFragment.CarrierPhase == true) {
                                    if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_CYCLE_SLIP) {
                                        if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6, TOLERANCE_MHZ)) {
                                            L1C = String.format("%14.3f%s%s", ADR / GPS_L1_WAVELENGTH, "1", " ");
                                        } else {
                                            L1C = String.format("%14.3f%s%s", ADR / GPS_L5_WAVELENGTH, "1", " ");
                                        }
                                    } else {
                                        if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6, TOLERANCE_MHZ)) {
                                            L1C = String.format("%14.3f%s%s", ADR / GPS_L1_WAVELENGTH, " ", " ");
                                        } else {
                                            L1C = String.format("%14.3f%s%s", ADR / GPS_L5_WAVELENGTH, " ", " ");
                                        }
                                    }
                                }
                            } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS) {
                                if (measurement.getSvid() <= 24) {
                                    if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_CYCLE_SLIP) {
                                        L1C = String.format("%14.3f%s%s", ADR / GLONASSG1WAVELENGTH(measurement.getSvid()), "1", " ");
                                    } else {
                                        L1C = String.format("%14.3f%s%s", ADR / GLONASSG1WAVELENGTH(measurement.getSvid()), " ", " ");
                                    }
                                }
                            } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU) {
                                if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_CYCLE_SLIP) {
                                    L1C = String.format("%14.3f%s%s", ADR / BEIDOUWAVELENGTH(measurement.getSvid()), "1", " ");
                                } else {
                                    L1C = String.format("%14.3f%s%s", ADR / BEIDOUWAVELENGTH(measurement.getSvid()), " ", " ");
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
                            //Pseudorange Smoother
                            if (SettingsFragment.usePseudorangeSmoother && prm != 0.0) {
                                if (index < 300) {
                                    if (SettingsFragment.usePseudorangeRate) {
                                        LAST_SMOOTHED_PSEUDORANGE[index] = CURRENT_SMOOTHER_RATE[index] * prm + (1 - CURRENT_SMOOTHER_RATE[index]) * (LAST_SMOOTHED_PSEUDORANGE[index] + measurement.getPseudorangeRateMetersPerSecond());
                                        C1C = String.format("%14.3f%s%s", LAST_SMOOTHED_PSEUDORANGE[index], " ", " ");
                                    } else {
                                        if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_VALID) {
                                            LAST_SMOOTHED_PSEUDORANGE[index] = CURRENT_SMOOTHER_RATE[index] * prm + (1 - CURRENT_SMOOTHER_RATE[index]) * (LAST_SMOOTHED_PSEUDORANGE[index] + measurement.getAccumulatedDeltaRangeMeters() - LAST_DELTARANGE[index]);
                                            LAST_DELTARANGE[index] = measurement.getAccumulatedDeltaRangeMeters();
                                            CURRENT_SMOOTHER_RATE[index] = CURRENT_SMOOTHER_RATE[index] - SMOOTHER_RATE;
                                            if (CURRENT_SMOOTHER_RATE[index] <= 0) {
                                                CURRENT_SMOOTHER_RATE[index] = SMOOTHER_RATE;
                                            }
                                            C1C = String.format("%14.3f%s%s", LAST_SMOOTHED_PSEUDORANGE[index], " ", " ");
                                        }
                                    }
                                }
                            }
                            String D1C = String.format("%14.3f%s%s", -measurement.getPseudorangeRateMetersPerSecond() / GPS_L1_WAVELENGTH, " ", " ");

                            String S1C = String.format("%14.3f%s%s", measurement.getCn0DbHz(), " ", " ");


                            //変更点
                            if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS) {

                                if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6, TOLERANCE_MHZ)) {
                                    if (measurement.getSvid() == 1) {
                                        L5carrier_1 = L1C;
                                        L5code_1 = C1C;
                                        S5C_1 = S1C;
                                    }
                                    if (measurement.getSvid() == 3) {
                                        L5carrier_3 = L1C;
                                        L5code_3 = C1C;
                                        S5C_3 = S1C;
                                    }
                                    if (measurement.getSvid() == 6) {
                                        L5carrier_6 = L1C;
                                        L5code_6 = C1C;
                                        S5C_6 = S1C;
                                    }
                                    if (measurement.getSvid() == 8) {
                                        L5carrier_8 = L1C;
                                        L5code_8 = C1C;
                                        S5C_8 = S1C;
                                    }
                                    if (measurement.getSvid() == 9) {
                                        L5carrier_9 = L1C;
                                        L5code_9 = C1C;
                                        S5C_9 = S1C;
                                    }
                                    if (measurement.getSvid() == 10) {
                                        L5carrier_10 = L1C;
                                        L5code_10 = C1C;
                                        S5C_10 = S1C;
                                    }

                                    if (measurement.getSvid() == 24) {
                                        L5carrier_24 = L1C;
                                        L5code_24 = C1C;
                                        S5C_24 = S1C;
                                    }
                                    if (measurement.getSvid() == 25) {
                                        L5carrier_25 = L1C;
                                        L5code_25 = C1C;
                                        S5C_25 = S1C;
                                    }
                                    if (measurement.getSvid() == 26) {
                                        L5carrier_26 = L1C;
                                        L5code_26 = C1C;
                                        S5C_26 = S1C;
                                    }
                                    if (measurement.getSvid() == 27) {
                                        L5carrier_27 = L1C;
                                        L5code_27 = C1C;
                                        S5C_27 = S1C;
                                    }
                                    if (measurement.getSvid() == 30) {
                                        L5carrier_30 = L1C;
                                        L5code_30 = C1C;
                                        S5C_30 = S1C;
                                    }
                                    if (measurement.getSvid() == 32) {
                                        L5carrier_32 = L1C;
                                        L5code_32 = C1C;
                                        S5C_32 = S1C;
                                    }
                                }
                                if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 115.0 * 10.23e6, TOLERANCE_MHZ)) {

                                    if (L5carrier_1 != null && measurement.getSvid() == 1) {
                                        Measurements.append(prn + L5code_1 + L5carrier_1 + S5C_1 + C1C + L1C + S1C + '\n');
                                    }
                                    if (L5carrier_3 != null && measurement.getSvid() == 3) {
                                        Measurements.append(prn + L5code_3 + L5carrier_3 + S5C_3 + C1C + L1C + S1C + '\n');
                                    }
                                    if (L5carrier_6 != null && measurement.getSvid() == 6) {
                                        Measurements.append(prn + L5code_6 + L5carrier_6 + S5C_6 + C1C + L1C + S1C + '\n');
                                    }
                                    if (L5carrier_8 != null && measurement.getSvid() == 8) {
                                        Measurements.append(prn + L5code_8 + L5carrier_8 + S5C_8 + C1C + L1C + S1C + '\n');
                                    }
                                    if (L5carrier_9 != null && measurement.getSvid() == 9) {
                                        Measurements.append(prn + L5code_9 + L5carrier_9 + S5C_9 + C1C + L1C + S1C + '\n');
                                    }
                                    if (L5carrier_10 != null && measurement.getSvid() == 10) {
                                        Measurements.append(prn + L5code_10 + L5carrier_10 + S5C_10 + C1C + L1C + S1C + '\n');
                                    }
                                    if (L5carrier_24 != null && measurement.getSvid() == 24) {
                                        Measurements.append(prn + L5code_24 + L5carrier_24 + S5C_24 + C1C + L1C + S1C + '\n');
                                    }
                                    if (L5carrier_25 != null && measurement.getSvid() == 25) {
                                        Measurements.append(prn + L5code_25 + L5carrier_25 + S5C_25 + C1C + L1C + S1C + '\n');
                                    }
                                    if (L5carrier_26 != null && measurement.getSvid() == 26) {
                                        Measurements.append(prn + L5code_26 + L5carrier_26 + S5C_26 + C1C + L1C + S1C + '\n');
                                    }
                                    if (L5carrier_27 != null && measurement.getSvid() == 27) {
                                        Measurements.append(prn + L5code_27 + L5carrier_27 + S5C_27 + C1C + L1C + S1C + '\n');
                                    }
                                    if (L5carrier_30 != null && measurement.getSvid() == 30) {
                                        Measurements.append(prn + L5code_30 + L5carrier_30 + S5C_30 + C1C + L1C + S1C + '\n');
                                    }
                                    if (L5carrier_32 != null && measurement.getSvid() == 32) {
                                        Measurements.append(prn + L5code_32 + L5carrier_32 + S5C_32 + C1C + L1C + S1C + '\n');
                                    }

                                }
                            } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS) {

                                if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6, TOLERANCE_MHZ)) {
                                    if (measurement.getSvid() == 1 + 192) {
                                        QZSS_L1_1 = L1C;
                                        QZSS_C1_1 = C1C;
                                        QZS1_1 = S1C;
                                    }
                                    if (measurement.getSvid() == 2 + 192) {
                                        QZSS_L1_2 = L1C;
                                        QZSS_C1_2 = C1C;
                                        QZS1_2 = S1C;
                                    }
                                    if (measurement.getSvid() == 3 + 192) {
                                        QZSS_L1_3 = L1C;
                                        QZSS_C1_3 = C1C;
                                        QZS1_3 = S1C;
                                    }
                                    if (measurement.getSvid() == 4 + 192) {
                                        QZSS_L1_4 = L1C;
                                        QZSS_C1_4 = C1C;
                                        QZS1_4 = S1C;
                                    }
                                }
                                if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 115.0 * 10.23e6, TOLERANCE_MHZ)) {

                                    if (QZSS_L1_1 != null && measurement.getSvid() == 1 + 192) {
                                        Measurements.append(prn + QZSS_C1_1 + QZSS_L1_1 + QZS1_1 + C1C + L1C + S1C + '\n');
                                    }
                                    if (QZSS_L1_2 != null && measurement.getSvid() == 2 + 192) {
                                        Measurements.append(prn + QZSS_C1_2 + QZSS_L1_2 + QZS1_2 + C1C + L1C + S1C + '\n');
                                    }
                                    if (QZSS_L1_3 != null && measurement.getSvid() == 3 + 192) {
                                        Measurements.append(prn + QZSS_C1_3 + QZSS_L1_3 + QZS1_3 + C1C + L1C + S1C + '\n');
                                    }
                                    if (QZSS_L1_4 != null && measurement.getSvid() == 4 + 192) {
                                        Measurements.append(prn + QZSS_C1_4 + QZSS_L1_4 + QZS1_4 + C1C + L1C + S1C + '\n');
                                    }

                                }
                            } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO) {

                                if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6, TOLERANCE_MHZ)) {

                                    if (measurement.getSvid() == 1) {
                                        Galileo_L1_1 = L1C;
                                        Galileo_C1_1 = C1C;
                                        GalileoS1_1 = S1C;
                                    }
                                    if (measurement.getSvid() == 2) {
                                        Galileo_L1_2 = L1C;
                                        Galileo_C1_2 = C1C;
                                        GalileoS1_2 = S1C;
                                    }
                                    if (measurement.getSvid() == 3) {
                                        Galileo_L1_3 = L1C;
                                        Galileo_C1_3 = C1C;
                                        GalileoS1_3 = S1C;
                                    }
                                    if (measurement.getSvid() == 4) {
                                        Galileo_L1_4 = L1C;
                                        Galileo_C1_4 = C1C;
                                        GalileoS1_4 = S1C;
                                    }
                                    if (measurement.getSvid() == 5) {
                                        Galileo_L1_5 = L1C;
                                        Galileo_C1_5 = C1C;
                                        GalileoS1_5 = S1C;
                                    }
                                    if (measurement.getSvid() == 6) {
                                        Galileo_L1_6 = L1C;
                                        Galileo_C1_6 = C1C;
                                        GalileoS1_6 = S1C;
                                    }
                                    if (measurement.getSvid() == 7) {
                                        Galileo_L1_7 = L1C;
                                        Galileo_C1_7 = C1C;
                                        GalileoS1_7 = S1C;
                                    }
                                    if (measurement.getSvid() == 8) {
                                        Galileo_L1_8 = L1C;
                                        Galileo_C1_8 = C1C;
                                        GalileoS1_8 = S1C;
                                    }
                                    if (measurement.getSvid() == 9) {
                                        Galileo_L1_9 = L1C;
                                        Galileo_C1_9 = C1C;
                                        GalileoS1_9 = S1C;
                                    }
                                    if (measurement.getSvid() == 10) {
                                        Galileo_L1_10 = L1C;
                                        Galileo_C1_10 = C1C;
                                        GalileoS1_10 = S1C;
                                    }
                                    if (measurement.getSvid() == 11) {
                                        Galileo_L1_11 = L1C;
                                        Galileo_C1_11 = C1C;
                                        GalileoS1_11 = S1C;
                                    }
                                    if (measurement.getSvid() == 12) {
                                        Galileo_L1_12 = L1C;
                                        Galileo_C1_12 = C1C;
                                        GalileoS1_12 = S1C;
                                    }
                                    if (measurement.getSvid() == 13) {
                                        Galileo_L1_13 = L1C;
                                        Galileo_C1_13 = C1C;
                                        GalileoS1_13 = S1C;
                                    }
                                    if (measurement.getSvid() == 14) {
                                        Galileo_L1_14 = L1C;
                                        Galileo_C1_14 = C1C;
                                        GalileoS1_14 = S1C;
                                    }
                                    if (measurement.getSvid() == 15) {
                                        Galileo_L1_15 = L1C;
                                        Galileo_C1_15 = C1C;
                                        GalileoS1_15 = S1C;
                                    }
                                    if (measurement.getSvid() == 18) {
                                        Galileo_L1_18 = L1C;
                                        Galileo_C1_18 = C1C;
                                        GalileoS1_18 = S1C;
                                    }
                                    if (measurement.getSvid() == 19) {
                                        Galileo_L1_19 = L1C;
                                        Galileo_C1_19 = C1C;
                                        GalileoS1_19 = S1C;
                                    }
                                    if (measurement.getSvid() == 20) {
                                        Galileo_L1_20 = L1C;
                                        Galileo_C1_20 = C1C;
                                        GalileoS1_20 = S1C;
                                    }
                                    if (measurement.getSvid() == 21) {
                                        Galileo_L1_21 = L1C;
                                        Galileo_C1_21 = C1C;
                                        GalileoS1_21 = S1C;
                                    }
                                    if (measurement.getSvid() == 22) {
                                        Galileo_L1_22 = L1C;
                                        Galileo_C1_22 = C1C;
                                        GalileoS1_22 = S1C;
                                    }
                                    if (measurement.getSvid() == 24) {
                                        Galileo_L1_24 = L1C;
                                        Galileo_C1_24 = C1C;
                                        GalileoS1_24 = S1C;
                                    }
                                    if (measurement.getSvid() == 25) {
                                        Galileo_L1_25 = L1C;
                                        Galileo_C1_25 = C1C;
                                        GalileoS1_25 = S1C;
                                    }
                                    if (measurement.getSvid() == 26) {
                                        Galileo_L1_26 = L1C;
                                        Galileo_C1_26 = C1C;
                                        GalileoS1_26 = S1C;
                                    }
                                    if (measurement.getSvid() == 27) {
                                        Galileo_L1_27 = L1C;
                                        Galileo_C1_27 = C1C;
                                        GalileoS1_27 = S1C;
                                    }
                                    if (measurement.getSvid() == 30) {
                                        Galileo_L1_30 = L1C;
                                        Galileo_C1_30 = C1C;
                                        GalileoS1_30 = S1C;
                                    }
                                    if (measurement.getSvid() == 31) {
                                        Galileo_L1_31 = L1C;
                                        Galileo_C1_31 = C1C;
                                        GalileoS1_31 = S1C;
                                    }
                                    if (measurement.getSvid() == 33) {
                                        Galileo_L1_33 = L1C;
                                        Galileo_C1_33 = C1C;
                                        GalileoS1_33 = S1C;
                                    }
                                    if (measurement.getSvid() == 36) {
                                        Galileo_L1_36 = L1C;
                                        Galileo_C1_36 = C1C;
                                        GalileoS1_36 = S1C;
                                    }
                                }

                                if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 115.0 * 10.23e6, TOLERANCE_MHZ)) {

                                    if (Galileo_L1_1 != null && measurement.getSvid() == 1) {
                                        Measurements.append(prn + Galileo_C1_1 + Galileo_L1_1 + GalileoS1_1 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_2 != null && measurement.getSvid() == 2) {
                                        Measurements.append(prn + Galileo_C1_2 + Galileo_L1_2 + GalileoS1_2 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_3 != null && measurement.getSvid() == 3) {
                                        Measurements.append(prn + Galileo_C1_3 + Galileo_L1_3 + GalileoS1_3 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_4 != null && measurement.getSvid() == 4) {
                                        Measurements.append(prn + Galileo_C1_4 + Galileo_L1_4 + GalileoS1_4 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_5 != null && measurement.getSvid() == 5) {
                                        Measurements.append(prn + Galileo_C1_5 + Galileo_L1_5 + GalileoS1_5 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_6 != null && measurement.getSvid() == 6) {
                                        Measurements.append(prn + Galileo_C1_6 + Galileo_L1_6 + GalileoS1_6 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_7 != null && measurement.getSvid() == 7) {
                                        Measurements.append(prn + Galileo_C1_7 + Galileo_L1_7 + GalileoS1_7 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_8 != null && measurement.getSvid() == 8) {
                                        Measurements.append(prn + Galileo_C1_8 + Galileo_L1_8 + GalileoS1_8 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_9 != null && measurement.getSvid() == 9) {
                                        Measurements.append(prn + Galileo_C1_9 + Galileo_L1_9 + GalileoS1_9 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_10 != null && measurement.getSvid() == 10) {
                                        Measurements.append(prn + Galileo_C1_10 + Galileo_L1_10 + GalileoS1_10 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_11 != null && measurement.getSvid() == 11) {
                                        Measurements.append(prn + Galileo_C1_11 + Galileo_L1_11 + GalileoS1_11 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_12 != null && measurement.getSvid() == 12) {
                                        Measurements.append(prn + Galileo_C1_12 + Galileo_L1_12 + GalileoS1_12 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_13 != null && measurement.getSvid() == 13) {
                                        Measurements.append(prn + Galileo_C1_13 + Galileo_L1_13 + GalileoS1_13 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_14 != null && measurement.getSvid() == 14) {
                                        Measurements.append(prn + Galileo_C1_14 + Galileo_L1_14 + GalileoS1_14 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_15 != null && measurement.getSvid() == 15) {
                                        Measurements.append(prn + Galileo_C1_15 + Galileo_L1_15 + GalileoS1_15 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_18 != null && measurement.getSvid() == 18) {
                                        Measurements.append(prn + Galileo_C1_18 + Galileo_L1_18 + GalileoS1_18 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_19 != null && measurement.getSvid() == 19) {
                                        Measurements.append(prn + Galileo_C1_19 + Galileo_L1_19 + GalileoS1_19 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_20 != null && measurement.getSvid() == 20) {
                                        Measurements.append(prn + Galileo_C1_20 + Galileo_L1_20 + GalileoS1_20 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_21 != null && measurement.getSvid() == 21) {
                                        Measurements.append(prn + Galileo_C1_21 + Galileo_L1_21 + GalileoS1_21 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_22 != null && measurement.getSvid() == 22) {
                                        Measurements.append(prn + Galileo_C1_22 + Galileo_L1_22 + GalileoS1_22 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_24 != null && measurement.getSvid() == 24) {
                                        Measurements.append(prn + Galileo_C1_24 + Galileo_L1_24 + GalileoS1_24 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_25 != null && measurement.getSvid() == 25) {
                                        Measurements.append(prn + Galileo_C1_25 + Galileo_L1_25 + GalileoS1_25 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_26 != null && measurement.getSvid() == 26) {
                                        Measurements.append(prn + Galileo_C1_26 + Galileo_L1_26 + GalileoS1_26 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_27 != null && measurement.getSvid() == 27) {
                                        Measurements.append(prn + Galileo_C1_27 + Galileo_L1_27 + GalileoS1_27 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_30 != null && measurement.getSvid() == 30) {
                                        Measurements.append(prn + Galileo_C1_30 + Galileo_L1_30 + GalileoS1_30 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_31 != null && measurement.getSvid() == 31) {
                                        Measurements.append(prn + Galileo_C1_31 + Galileo_L1_31 + GalileoS1_31 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_33 != null && measurement.getSvid() == 33) {
                                        Measurements.append(prn + Galileo_C1_33 + Galileo_L1_33 + GalileoS1_33 + C1C + L1C + S1C + '\n');
                                    }
                                    if (Galileo_L1_36 != null && measurement.getSvid() == 36) {
                                        Measurements.append(prn + Galileo_C1_36 + Galileo_L1_36 + GalileoS1_36 + C1C + L1C + S1C + '\n');
                                    }

                                }
                            }
                        }

                    }
                }
                // Prn.insert(0, String.format("%3d", satnumber));
                //mFileWriter.write(OBSTime + Prn.toString() + "\n");
                mFileWriter.write(OBSTime + String.format("%3d", satnumber));
                mFileWriter.newLine();
                mFileWriter.write(Measurements.toString());
                mFileWriter.newLine();
                //衛星が１基も観測できていない場合, FullBiasNanos定数をリセットする.
                if (firstOBS) {
                    constFullBiasNanos = 0.0;
                }
                if (SettingsFragment.ResearchMode) {
                    mFileAccAzWriter.write(SensorStream);
                    mFileAccAzWriter.newLine();
                }
            }
            else {                                                                                         // RINEX 2.11
                String L1carrier_3 = "";
                String L1code_3 = "";
                String S5_3 = "";
                String L1carrier_10 = "";
                String L1code_10 = "";
                String S5_10 = "";
                String L1carrier_24 = "";
                String L1code_24 = "";
                String S5_24 = "";
                String L1carrier_25 = "";
                String L1code_25 = "";
                String S5_25 = "";
                String L1carrier_26 = "";
                String L1code_26 = "";
                String S5_26 = "";
                String L1carrier_32 = "";
                String L1code_32 = "";
                String S5_32 = "";  //仮
                for (GnssMeasurement measurement : event.getMeasurements()) {
                    if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS) {
                        GnssClock gnssClock = event.getClock();
                        double weekNumber = Math.floor(-(gnssClock.getFullBiasNanos() * 1e-9 / 604800));
                        double weekNumberNanos = weekNumber * 604800 * 1e9;
                        //FullBiasNanosがリセットされたら再計算
                        if (constFullBiasNanos == 0.0) {
                            if (gnssClock.hasBiasNanos()) {
                                constFullBiasNanos = gnssClock.getFullBiasNanos() + gnssClock.getBiasNanos();
                            } else {
                                constFullBiasNanos = gnssClock.getFullBiasNanos();
                            }
                        }
                        double tRxNanos = gnssClock.getTimeNanos() - constFullBiasNanos - weekNumberNanos;
                        if (measurement.getTimeOffsetNanos() != 0) {
                            tRxNanos = tRxNanos - measurement.getTimeOffsetNanos();
                        }
                        double tRxSeconds = tRxNanos * 1e-9;
                        double tTxSeconds = measurement.getReceivedSvTimeNanos() * 1e-9;
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

                        //GPS週・週秒から年月日時分秒に変換
                        GPSWStoGPST gpswStoGPST = new GPSWStoGPST();
                        ReturnValue value = gpswStoGPST.method(weekNumber, tRxSeconds);
                        /*急場の変更！！*/
                        String DeviceName = Build.DEVICE;
                        //Log.d("DEVICE",DeviceName);
                        /*急場の変更！！*/
                        double prm = prSeconds * 2.99792458e8;
                        //コード擬似距離の計算
                        if (iRollover == false && prm > 0 && prSeconds < 0.5) {
                            if (firstOBS == true) {
                                String OBSTime = String.format(" %2d %2d %2d %2d %2d%11.7f  0", value.Y - 2000, value.M, value.D, value.h, value.m, value.s);
                                SensorStream =
                                        String.format("%6d,%6d,%6d,%6d,%6d,%13.7f", value.Y, value.M, value.D, value.h, value.m, value.s);
                                //メモで
                                gnsstimeclock_a = value.D;
                                gnsstimeclock_b = value.h;
                                gnsstimeclock_c = value.m;
                                gnsstimeclock_d = value.s;
                                gnsstimeclock_e = value.M;
                                gnsstimeclock_f = value.Y;
                                Time.append(OBSTime);
                                firstOBS = false;
                            }
                            //GPSのPRN番号と時刻用String

                            //ここ

                            if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 115.0 * 10.23e6, TOLERANCE_MHZ)) {
                                String prn = String.format("G%2d", measurement.getSvid());

                                satnumber = satnumber + 1;
                                Prn.append(prn);
                            }
                            String PrmStrings = String.format("%14.3f%s%s", prm, " ", " ");
                            String DeltaRangeStrings = String.format("%14.3f%s%s", 0.0, " ", " ");
                            if (SettingsFragment.CarrierPhase == true) {
                                double ADR = measurement.getAccumulatedDeltaRangeMeters();
                                if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS || measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO || measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS) {
                                    if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_CYCLE_SLIP) {
                                        if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6, TOLERANCE_MHZ)) {
                                            DeltaRangeStrings = String.format("%14.3f%s%s", ADR / GPS_L1_WAVELENGTH, "1", " ");
                                        } else {
                                            DeltaRangeStrings = String.format("%14.3f%s%s", ADR / GPS_L5_WAVELENGTH, "1", " ");
                                        }
                                    } else {
                                        if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6, TOLERANCE_MHZ)) {
                                            DeltaRangeStrings = String.format("%14.3f%s%s", ADR / GPS_L1_WAVELENGTH, " ", " ");
                                        } else {
                                            DeltaRangeStrings = String.format("%14.3f%s%s", ADR / GPS_L5_WAVELENGTH, " ", " ");
                                        }
                                    }
                                } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS) {
                                    if (measurement.getSvid() <= 24) {
                                        if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_CYCLE_SLIP) {
                                            DeltaRangeStrings = String.format("%14.3f%s%s", ADR / GLONASSG1WAVELENGTH(measurement.getSvid()), "1", " ");
                                        } else {
                                            DeltaRangeStrings = String.format("%14.3f%s%s", ADR / GLONASSG1WAVELENGTH(measurement.getSvid()), " ", " ");
                                        }
                                    } else {
                                        DeltaRangeStrings = String.format("%14.3f%s%s", 0.0, " ", " ");
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
                            //Pseudorange Smoother
                            if (SettingsFragment.usePseudorangeSmoother && prm != 0.0) {
                                if (index < 300) {
                                    if (SettingsFragment.usePseudorangeRate) {
                                        LAST_SMOOTHED_PSEUDORANGE[index] = CURRENT_SMOOTHER_RATE[index] * prm + (1 - CURRENT_SMOOTHER_RATE[index]) * (LAST_SMOOTHED_PSEUDORANGE[index] + measurement.getPseudorangeRateMetersPerSecond());
                                        PrmStrings = String.format("%14.3f%s%s", LAST_SMOOTHED_PSEUDORANGE[index], " ", " ");
                                    } else {
                                        if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_VALID) {
                                            LAST_SMOOTHED_PSEUDORANGE[index] = CURRENT_SMOOTHER_RATE[index] * prm + (1 - CURRENT_SMOOTHER_RATE[index]) * (LAST_SMOOTHED_PSEUDORANGE[index] + measurement.getAccumulatedDeltaRangeMeters() - LAST_DELTARANGE[index]);
                                            LAST_DELTARANGE[index] = measurement.getAccumulatedDeltaRangeMeters();
                                            // A=measurement.hasCarrierFrequencyHz(),measurement.getCarrierFrequencyHz();
                                            CURRENT_SMOOTHER_RATE[index] = CURRENT_SMOOTHER_RATE[index] - SMOOTHER_RATE;
                                            if (CURRENT_SMOOTHER_RATE[index] <= 0) {
                                                CURRENT_SMOOTHER_RATE[index] = SMOOTHER_RATE;
                                            }
                                            PrmStrings = String.format("%14.3f%s%s", LAST_SMOOTHED_PSEUDORANGE[index], " ", " ");
                                        }
                                    }
                                }
                            }
                            //Fix用チェック
                            //  Calendar myCal= Calendar.getInstance();
                            //  DateFormat myFormat = new SimpleDateFormat("yyyy/MM/dd/hh:mm.ss");
                            //  String myName = myFormat.format(myCal.getTime());
                            String DbHz = String.format("%14.3f%s%s", measurement.getCn0DbHz(), " ", " ");
                            // String L1code=PrmStrings;
                            // String L1carrier=DeltaRangeStrings;
                            // String S5=DbHz;
                            ArrayList<Integer> dualcheck = new ArrayList<Integer>();
                            dualcheck.add(measurement.getSvid()); //衛星番号でチェックしたい
                            // int dual1;


                            // ArrayList<Integer> dualcheck = new ArrayList<Integer>() ;
                            // dualcheck.add(measurement.getSvid());


                            //仮　GPSの周波数指定
                            if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6, TOLERANCE_MHZ)) {
                                if (measurement.getSvid() == 3) {
                                    // ArrayList<String> L1carrier = new ArrayList<String>();
                                    // L1carrier.add(DeltaRangeStrings);
                                    L1carrier_3 = DeltaRangeStrings;
                                    L1code_3 = PrmStrings;
                                    S5_3 = DbHz;
                                }
                                if (measurement.getSvid() == 10) {
                                    // ArrayList<String> L1carrier = new ArrayList<String>();
                                    // L1carrier.add(DeltaRangeStrings);
                                    L1carrier_10 = DeltaRangeStrings;
                                    L1code_10 = PrmStrings;
                                    S5_10 = DbHz;
                                }
                                if (measurement.getSvid() == 24) {
                                    // ArrayList<String> L1carrier = new ArrayList<String>();
                                    // L1carrier.add(DeltaRangeStrings);
                                    L1carrier_24 = DeltaRangeStrings;
                                    L1code_24 = PrmStrings;
                                    S5_24 = DbHz;
                                }
                                if (measurement.getSvid() == 25) {
                                    // ArrayList<String> L1carrier = new ArrayList<String>();
                                    // L1carrier.add(DeltaRangeStrings);
                                    L1carrier_25 = DeltaRangeStrings;
                                    L1code_25 = PrmStrings;
                                    S5_25 = DbHz;
                                }
                                if (measurement.getSvid() == 26) {
                                    // ArrayList<String> L1carrier = new ArrayList<String>();
                                    // L1carrier.add(DeltaRangeStrings);
                                    L1carrier_26 = DeltaRangeStrings;
                                    L1code_26 = PrmStrings;
                                    S5_26 = DbHz;
                                }
                                if (measurement.getSvid() == 32) {
                                    // ArrayList<String> L1carrier = new ArrayList<String>();
                                    // L1carrier.add(DeltaRangeStrings);
                                    L1carrier_32 = DeltaRangeStrings;
                                    L1code_32 = PrmStrings;
                                    S5_32 = DbHz;
                                } else {
                                }
                                //   if(Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(),115.0 * 10.23e6, TOLERANCE_MHZ))
                                if (SettingsFragment.CarrierPhase) {
                                    //Measurements.append(DeltaRangeStrings + PrmStrings + DbHz );  //oFileの書き出し　コード擬似距離など

                                } else {
                                    //Measurements.append(PrmStrings + DbHz );   //oFileの書き出し　コード擬似距離など
                                }
                            }
                            if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 115.0 * 10.23e6, TOLERANCE_MHZ)) {

                                int checkflag = 1;
                                if (L1carrier_3 != null && measurement.getSvid() == 3 && checkflag == 1) {
                                    Measurements.append(L1carrier_3 + L1code_3 + S5_3 + DeltaRangeStrings + PrmStrings + DbHz);
                                    checkflag = 0;
                                }
                                if (L1carrier_10 != null && measurement.getSvid() == 10 && checkflag == 1) {
                                    Measurements.append(L1carrier_10 + L1code_10 + S5_10 + DeltaRangeStrings + PrmStrings + DbHz);
                                    checkflag = 0;
                                } //L1とL5を同時に書きたい　ofileの日付の横の重複もなくす　観測できていないところは空白にする
                                if (L1carrier_24 != null && measurement.getSvid() == 24 && checkflag == 1) {
                                    Measurements.append(L1carrier_24 + L1code_24 + S5_24 + DeltaRangeStrings + PrmStrings + DbHz);
                                    checkflag = 0;
                                }
                                if (L1carrier_25 != null && measurement.getSvid() == 25 && checkflag == 1) {
                                    Measurements.append(L1carrier_25 + L1code_25 + S5_25 + DeltaRangeStrings + PrmStrings + DbHz);
                                    checkflag = 0;
                                }
                                if (L1carrier_26 != null && measurement.getSvid() == 26 && checkflag == 1) {
                                    Measurements.append(L1carrier_26 + L1code_26 + S5_26 + DeltaRangeStrings + PrmStrings + DbHz);
                                    checkflag = 0;
                                }
                                if (L1carrier_32 != null && measurement.getSvid() == 32 && checkflag == 1) {
                                    Measurements.append(L1carrier_32 + L1code_32 + S5_32 + DeltaRangeStrings + PrmStrings + DbHz);
                                    checkflag = 0;
                                }
                            }

                        }
                    }
                }
                Prn.insert(0, String.format("%3d", satnumber));
                //oファイルの中身
                //onGnssMeasurementsReceived();

                //Calendar myCal= Calendar.getInstance();
                //DateFormat myFormat = new SimpleDateFormat("yyyy/MM/dd/hh:mm.ss");
                //String myName = myFormat.format(myCal.getTime());

                //mFileWriter.write("%"+myName);
                //mFileWriter.newLine();
                //"\t"+myName+
                mFileWriter.write(Time.toString() + Prn.toString() + "\n"); //ofileの各時刻の日付と観測できる衛星番号
                mFileWriter.write(Measurements.toString()); //各衛星の情報
                if (SettingsFragment.ResearchMode) {
                    mFileAccAzWriter.write(SensorStream);
                    mFileAccAzWriter.newLine();
                }
            }

        }
        else{    //2周波観測終了

            if(SettingsFragment.RINEX303){
                String OBSTime = "";
                GnssClock gnssClock = event.getClock();
                double weekNumber = Math.floor(-(gnssClock.getFullBiasNanos() * 1e-9 / 604800));
                double weekNumberNanos = weekNumber * 604800 * 1e9;
                //FullBiasNanosがリセットされたら再計算
                if(constFullBiasNanos == 0.0){
                    if(gnssClock.hasBiasNanos()) {
                        constFullBiasNanos = gnssClock.getFullBiasNanos() + gnssClock.getBiasNanos();
                    }else {
                        constFullBiasNanos = gnssClock.getFullBiasNanos();
                    }
                }
                //Log.d("ConstBias",String.valueOf(constFullBiasNanos%1e5));
                //Log.d("InstBias",String.valueOf((gnssClock.getFullBiasNanos()%1e5)));
                //Log.d("TimeNanosBias",String.valueOf(((gnssClock.getFullBiasNanos()%1e5) - (constFullBiasNanos%1e5))));
                double tRxNanos = gnssClock.getTimeNanos() - constFullBiasNanos - weekNumberNanos;
                //GPS週・週秒から年月日時分秒に変換
                GPSWStoGPST gpswStoGPST = new GPSWStoGPST();
                ReturnValue value = gpswStoGPST.method(weekNumber, tRxNanos * 1e-9);
                for (GnssMeasurement measurement : event.getMeasurements()) {
                    if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS || (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS && SettingsFragment.useGL) || (measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS&& SettingsFragment.useQZ) || ((measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO)&&(SettingsFragment.useGA))  || (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU && SettingsFragment.useBD)) {
                        double tRxSeconds = (tRxNanos - measurement.getTimeOffsetNanos()) * 1e-9;
                        double tTxSeconds = measurement.getReceivedSvTimeNanos() * 1e-9;
                        //GLONASS時刻への変換
                        if((measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS)) {
                            double tRxSeconds_GLO = tRxSeconds % 86400;
                            double tTxSeconds_GLO = tTxSeconds - 10800 + leapseconds;
                            if(tTxSeconds_GLO < 0){
                                tTxSeconds_GLO = tTxSeconds_GLO + 86400;
                            }
                            tRxSeconds = tRxSeconds_GLO;
                            tTxSeconds = tTxSeconds_GLO;
                        }
                        //Beidou時刻への変換
                        if(measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU){
                            double tRxSeconds_BDS = tRxSeconds;
                            double tTxSeconds_BDS = tTxSeconds + leapseconds - 4;
                            if(tTxSeconds_BDS > 604800){
                                tTxSeconds_BDS = tTxSeconds_BDS - 604800;
                            }
                    /*Log.i("PRN", String.format("%s%2d", getConstellationName(measurement.getConstellationType()), measurement.getSvid()));
                    Log.i("tRxSeconds", String.valueOf(tRxSeconds_BDS));
                    Log.i("tTxSeconds", String.valueOf(tTxSeconds_BDS));//53333*/
                            tRxSeconds = tRxSeconds_BDS;
                            tTxSeconds = tTxSeconds_BDS;
                        }
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

                        /*急場の変更！！*/
                        String DeviceName = Build.DEVICE;
                        //Log.d("DEVICE",DeviceName);
                        /*急場の変更！！*/
                        double prm = prSeconds * 2.99792458e8;
                        //コード擬似距離の計算
                        if (iRollover == false && prm > 0 && prSeconds < 0.5) {
                            if (firstOBS == true) {
                                OBSTime = String.format("> %4d %2d %2d %2d %2d%11.7f  0", value.Y, value.M, value.D, value.h, value.m, value.s);
                                SensorStream =
                                        String.format("%6d,%6d,%6d,%6d,%6d,%13.7f", value.Y, value.M, value.D, value.h, value.m, value.s);
                                //firstOBS = false;
                            }
                            //GPSのPRN番号と時刻用String
                            String prn = "";
                            if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6, TOLERANCE_MHZ)) {
                                if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS) {
                                    prn = String.format("G%02d", measurement.getSvid());
                                } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS) {
                                    prn = String.format("R%02d", measurement.getSvid());
                                } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS) {
                                    prn = String.format("J%02d", measurement.getSvid() - 192);
                                } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO) {
                                    prn = String.format("E%02d", measurement.getSvid());
                                } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU) {
                                    prn = String.format("C%02d", measurement.getSvid());
                                }
                                satnumber = satnumber + 1;
                            }

                            //Measurements.append(prn);
                            String C1C = String.format("%14.3f%s%s", prm, " ", " ");
                            String L1C = String.format("%14.3f%s%s", 0.0, " ", " ");
                            //搬送波の謎バイアスを補正したい
                            double ADR = measurement.getAccumulatedDeltaRangeMeters();
                            double CarrierCycles = measurement.getAccumulatedDeltaRangeMeters()/GPS_L1_WAVELENGTH;
                            double absCC = Math.abs(CarrierCycles);
                            if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6, TOLERANCE_MHZ)) {
                                if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS || measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO || measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS) {

                                    if (SettingsFragment.CarrierPhase == true) {

                                            if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_CYCLE_SLIP) {
                                                L1C = String.format("%14.3f%s%s", ADR / GPS_L1_WAVELENGTH, "1", " ");
                                            } else {
                                                L1C = String.format("%14.3f%s%s", ADR / GPS_L1_WAVELENGTH, " ", " ");
                                            }
                                    }
                                } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS) {
                                    if (measurement.getSvid() <= 24) {
                                        if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_CYCLE_SLIP) {
                                            L1C = String.format("%14.3f%s%s", ADR / GLONASSG1WAVELENGTH(measurement.getSvid()), "1", " ");
                                        } else {
                                            L1C = String.format("%14.3f%s%s", ADR / GLONASSG1WAVELENGTH(measurement.getSvid()), " ", " ");
                                        }
                                    }
                                } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU) {
                                    if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_CYCLE_SLIP) {
                                        L1C = String.format("%14.3f%s%s", ADR / BEIDOUWAVELENGTH(measurement.getSvid()), "1", " ");
                                    } else {
                                        L1C = String.format("%14.3f%s%s", ADR / BEIDOUWAVELENGTH(measurement.getSvid()), " ", " ");
                                    }
                                }
                            }

                            int index = measurement.getSvid();
                            if(measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS){
                                index = index + 64;
                            }
                            if(measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU){
                                index = index + 200;
                            }
                            if(measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO){
                                index = index + 235;
                            }
                            if(!SettingsFragment.usePseudorangeRate && measurement.getAccumulatedDeltaRangeState() != GnssMeasurement.ADR_STATE_VALID){
                                CURRENT_SMOOTHER_RATE[index] = 1.0;
                            }
                            //Pseudorange Smoother
                            if(SettingsFragment.usePseudorangeSmoother &&  prm != 0.0){
                                if(index < 300) {
                                    if(SettingsFragment.usePseudorangeRate){
                                        LAST_SMOOTHED_PSEUDORANGE[index] = CURRENT_SMOOTHER_RATE[index] * prm + (1 - CURRENT_SMOOTHER_RATE[index]) * (LAST_SMOOTHED_PSEUDORANGE[index] + measurement.getPseudorangeRateMetersPerSecond());
                                        C1C = String.format("%14.3f%s%s", LAST_SMOOTHED_PSEUDORANGE[index], " ", " ");
                                    }else {
                                        if(measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_VALID){
                                            LAST_SMOOTHED_PSEUDORANGE[index] = CURRENT_SMOOTHER_RATE[index] * prm + (1 - CURRENT_SMOOTHER_RATE[index]) * (LAST_SMOOTHED_PSEUDORANGE[index] + measurement.getAccumulatedDeltaRangeMeters() - LAST_DELTARANGE[index]);
                                            LAST_DELTARANGE[index] = measurement.getAccumulatedDeltaRangeMeters();
                                            CURRENT_SMOOTHER_RATE[index] = CURRENT_SMOOTHER_RATE[index] - SMOOTHER_RATE;
                                            if (CURRENT_SMOOTHER_RATE[index] <= 0) {
                                                CURRENT_SMOOTHER_RATE[index] = SMOOTHER_RATE;
                                            }
                                            C1C = String.format("%14.3f%s%s", LAST_SMOOTHED_PSEUDORANGE[index], " ", " ");
                                        }
                                    }
                                }
                            }
                            String D1C = String.format("%14.3f%s%s", -measurement.getPseudorangeRateMetersPerSecond() / GPS_L1_WAVELENGTH, " ", " ");
                            String S1C = String.format("%14.3f%s%s", measurement.getCn0DbHz(), " ", " ");
                            //Fix用チェック
                            if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6, TOLERANCE_MHZ)) {
                                if (SettingsFragment.CarrierPhase) {
                                    if(absCC > 1e4) {
                                        if (firstOBS) {
                                            Measurements.append(prn + L1C + C1C + S1C);
                                            firstOBS = false;
                                        } else {
                                            Measurements.append("\n" + prn + L1C + C1C + S1C);
                                        }
                                    }
                                } else {
                                    if (firstOBS) {
                                        Measurements.append(prn + C1C  + S1C);
                                        firstOBS = false;
                                    } else {
                                        Measurements.append("\n" + prn + C1C + S1C);
                                    }
                                }
                            }
                        }
                    }
                }
                mFileWriter.write(OBSTime + String.format("%3d", satnumber));
                mFileWriter.newLine();
                mFileWriter.write(Measurements.toString());
                mFileWriter.newLine();
                //衛星が１基も観測できていない場合, FullBiasNanos定数をリセットする.
                if(firstOBS){
                    constFullBiasNanos = 0.0;
                }
                if (SettingsFragment.ResearchMode) {
                    mFileAccAzWriter.write(SensorStream);
                    mFileAccAzWriter.newLine();
                }
            }else {
                for (GnssMeasurement measurement : event.getMeasurements()) {
                    if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS) {
                        GnssClock gnssClock = event.getClock();
                        double weekNumber = Math.floor(-(gnssClock.getFullBiasNanos() * 1e-9 / 604800));
                        double weekNumberNanos = weekNumber * 604800 * 1e9;
                        //FullBiasNanosがリセットされたら再計算
                        if(constFullBiasNanos == 0.0){
                            if(gnssClock.hasBiasNanos()) {
                                constFullBiasNanos = gnssClock.getFullBiasNanos() + gnssClock.getBiasNanos();
                            }else {
                                constFullBiasNanos = gnssClock.getFullBiasNanos();
                            }
                        }
                        double tRxNanos = gnssClock.getTimeNanos() - constFullBiasNanos - weekNumberNanos;
                        if (measurement.getTimeOffsetNanos() != 0) {
                            tRxNanos = tRxNanos - measurement.getTimeOffsetNanos();
                        }
                        double tRxSeconds = tRxNanos * 1e-9;
                        double tTxSeconds = measurement.getReceivedSvTimeNanos() * 1e-9;
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

                        //GPS週・週秒から年月日時分秒に変換
                        GPSWStoGPST gpswStoGPST = new GPSWStoGPST();
                        ReturnValue value = gpswStoGPST.method(weekNumber, tRxSeconds);
                        /*急場の変更！！*/
                        String DeviceName = Build.DEVICE;
                        //Log.d("DEVICE",DeviceName);
                        /*急場の変更！！*/
                        double prm = prSeconds * 2.99792458e8;
                        //コード擬似距離の計算
                        if (iRollover == false && prm > 0 && prSeconds < 0.5) {
                            if (firstOBS == true) {
                                String OBSTime = String.format(" %2d %2d %2d %2d %2d%11.7f  0", value.Y - 2000, value.M, value.D, value.h, value.m, value.s);
                                SensorStream =
                                        String.format("%6d,%6d,%6d,%6d,%6d,%13.7f", value.Y, value.M, value.D, value.h, value.m, value.s);
                                Time.append(OBSTime);
                                firstOBS = false;
                            }
                            //GPSのPRN番号と時刻用String
                            String prn = String.format("G%2d", measurement.getSvid());
                            satnumber = satnumber + 1;
                            Prn.append(prn);
                            String PrmStrings = String.format("%14.3f%s%s", prm, " ", " ");
                            String DeltaRangeStrings = String.format("%14.3f%s%s", 0.0, " ", " ");
                            if (SettingsFragment.CarrierPhase == true) {
                                double ADR = measurement.getAccumulatedDeltaRangeMeters();
                                double CarrierCycles = measurement.getAccumulatedDeltaRangeMeters()/GPS_L1_WAVELENGTH;
                                double absCC = Math.abs(CarrierCycles);
                                if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6, TOLERANCE_MHZ)) {
                                    if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS || measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO || measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS) {
                                        if(absCC > 1e4) {
                                            if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_CYCLE_SLIP) {
                                                DeltaRangeStrings = String.format("%14.3f%s%s", CarrierCycles, "1", " ");
                                            } else {
                                                DeltaRangeStrings = String.format("%14.3f%s%s", CarrierCycles, " ", " ");
                                            }
                                        } else{
                                            DeltaRangeStrings = "";
                                        }
                                    } else if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS) {
                                        if (measurement.getSvid() <= 24) {
                                            if (measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_CYCLE_SLIP) {
                                                DeltaRangeStrings = String.format("%14.3f%s%s", ADR / GLONASSG1WAVELENGTH(measurement.getSvid()), "1", " ");
                                            } else {
                                                DeltaRangeStrings = String.format("%14.3f%s%s", ADR / GLONASSG1WAVELENGTH(measurement.getSvid()), " ", " ");
                                            }
                                        } else {
                                            DeltaRangeStrings = String.format("%14.3f%s%s", 0.0, " ", " ");
                                        }
                                    }
                                }
                            }
                            int index = measurement.getSvid();
                            if(measurement.getConstellationType() == GnssStatus.CONSTELLATION_GLONASS){
                                index = index + 64;
                            }
                            if(measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU){
                                index = index + 200;
                            }
                            if(measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO){
                                index = index + 235;
                            }
                            if(!SettingsFragment.usePseudorangeRate && measurement.getAccumulatedDeltaRangeState() != GnssMeasurement.ADR_STATE_VALID){
                                CURRENT_SMOOTHER_RATE[index] = 1.0;
                            }
                            //Pseudorange Smoother
                            if(SettingsFragment.usePseudorangeSmoother &&  prm != 0.0){
                                if(index < 300) {
                                    if(SettingsFragment.usePseudorangeRate){
                                        LAST_SMOOTHED_PSEUDORANGE[index] = CURRENT_SMOOTHER_RATE[index] * prm + (1 - CURRENT_SMOOTHER_RATE[index]) * (LAST_SMOOTHED_PSEUDORANGE[index] + measurement.getPseudorangeRateMetersPerSecond());
                                        PrmStrings = String.format("%14.3f%s%s", LAST_SMOOTHED_PSEUDORANGE[index], " ", " ");
                                    }else {
                                        if(measurement.getAccumulatedDeltaRangeState() == GnssMeasurement.ADR_STATE_VALID){
                                            LAST_SMOOTHED_PSEUDORANGE[index] = CURRENT_SMOOTHER_RATE[index] * prm + (1 - CURRENT_SMOOTHER_RATE[index]) * (LAST_SMOOTHED_PSEUDORANGE[index] + measurement.getAccumulatedDeltaRangeMeters() - LAST_DELTARANGE[index]);
                                            LAST_DELTARANGE[index] = measurement.getAccumulatedDeltaRangeMeters();
                                            CURRENT_SMOOTHER_RATE[index] = CURRENT_SMOOTHER_RATE[index] - SMOOTHER_RATE;
                                            if (CURRENT_SMOOTHER_RATE[index] <= 0) {
                                                CURRENT_SMOOTHER_RATE[index] = SMOOTHER_RATE;
                                            }
                                            PrmStrings = String.format("%14.3f%s%s", LAST_SMOOTHED_PSEUDORANGE[index], " ", " ");
                                        }
                                    }
                                }
                            }
                            //Fix用チェック
                            String DbHz = String.format("%14.3f%s%s", measurement.getCn0DbHz(), " ", " ");

                            if (Mathutil.fuzzyEquals(measurement.getCarrierFrequencyHz(), 154.0 * 10.23e6, TOLERANCE_MHZ)) {
                                if (SettingsFragment.CarrierPhase) {
                                    Measurements.append(DeltaRangeStrings + PrmStrings + DbHz + "\n");
                                } else {
                                    Measurements.append(PrmStrings + DbHz + "\n");
                                }
                            }
                        }
                    }
                }
                Prn.insert(0, String.format("%3d", satnumber));
                mFileWriter.write(Time.toString() + Prn.toString() + "\n");
                mFileWriter.write(Measurements.toString());
                if (SettingsFragment.ResearchMode) {
                    mFileAccAzWriter.write(SensorStream);
                    mFileAccAzWriter.newLine();
                }
            }
        }
    }
    private void logException(String errorMessage, Exception e) {
        Log.e(GnssContainer.TAG + TAG, errorMessage, e);
        Toast.makeText(mContext, errorMessage, Toast.LENGTH_LONG).show();
    }

    private void logError(String errorMessage) {
        Log.e(GnssContainer.TAG + TAG, errorMessage);
        Toast.makeText(mContext, errorMessage, Toast.LENGTH_LONG).show();
    }

    /**
     * Implements a {@link FileFilter} to delete files that are not in the
     * {@link FileToDeleteFilter#mRetainedFiles}.
     */
    private static class FileToDeleteFilter implements FileFilter {
        private final List<File> mRetainedFiles;

        public FileToDeleteFilter(File... retainedFiles) {
            this.mRetainedFiles = Arrays.asList(retainedFiles);
        }

        /**
         * Returns {@code true} to delete the file, and {@code false} to keep the file.
         *
         * <p>Files are deleted if they are not in the {@link FileToDeleteFilter#mRetainedFiles} list.
         */
        @Override
        public boolean accept(File pathname) {
            if (pathname == null || !pathname.exists()) {
                return false;
            }
            if (mRetainedFiles.contains(pathname)) {
                return false;
            }
            return pathname.length() < MINIMUM_USABLE_FILE_SIZE_BYTES;
        }
    }

    private double GLONASSG1WAVELENGTH(int svid){
        return SPEED_OF_LIGHT/((1602 + GLONASSFREQ[svid - 1] * 0.5625) * 10e5);
    }

    private double BEIDOUWAVELENGTH(int svid){
        return SPEED_OF_LIGHT/(1561.098 * 10e5);
    }

    //GPS週秒からGPS時への変換
    public static class ReturnValue {
        public int Y;
        public int M;
        public int D;
        public int h;
        public int m;
        public double s;
    }

    public static class GPSWStoGPST {
        public ReturnValue method(double GPSW , double GPSWS) {
            ReturnValue value = new ReturnValue();
            //MJDおよびMDの計算
            double MD = (int)(GPSWS/86400);
            double MJD = 44244+GPSW*7+MD;
            //ユリウス日から年月日
            double JD = MJD + 2400000.5;
            double N = JD + 0.5;
            int Z = (int)N;
            double F = N - Z;
            double A;
            if(Z >= 2299161){
                int X = (int)((Z-1867216.25)/36524.25);
                A = Z + 1 + X - X/4;
            }
            else {
                A = Z;
            }
            double B = A + 1524;
            int C = (int)((B-122.1)/365.25);
            int K = (int)(365.25*C);
            int E = (int)((B-K)/30.6001);
            double D = B-K-(int)(30.6001*E)+F;
            int M;
            int Y;
            if(E < 13.5){
                M = E - 1;
            }
            else {
                M = E - 13;
            }
            if(M > 2.5){
                Y = C - 4716;
            }
            else{
                Y = C - 4715;
            }
            value.Y = Y;
            value.M = M;
            value.D = (int)D;

            //GPS週秒からGPS時刻へ
            double DS = GPSWS-MD*86400;
            int h = (int)(DS/3600);
            double hm = DS-h*3600;
            int m = (int)(hm/60);
            double s = hm - m * 60;

            value.h = h;
            value.m = m;
            value.s = s;

            return value;

            }

    }

}
