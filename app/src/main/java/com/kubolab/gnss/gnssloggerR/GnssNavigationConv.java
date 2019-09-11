package com.kubolab.gnss.gnssloggerR;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Debug;
import android.util.Log;
import android.location.GnssNavigationMessage;

import java.util.concurrent.TimeUnit;


/**
 * Created by KuboLab on 2017/12/04.
 */
//テスト
public class GnssNavigationConv {
    private static final byte IONOSPHERIC_PARAMETERS_PAGE_18_SV_ID = 56;

    private static final int WORD_SIZE_BITS = 30;
    private static final int WORD_PADDING_BITS = 2;
    private static final int BYTE_AS_BITS = 8;
    private static final int GPS_CYCLE_WEEKS = 1024;
    private static final int IODE_TO_IODC_MASK = 0xFF;

    public static final int SUBFRAME_1 = (1 << 0);
    public static final int SUBFRAME_2 = (1 << 1);
    public static final int SUBFRAME_3 = (1 << 2);
    public static final int SUBFRAME_4 = (1 << 3);
    public static final int SUBFRAME_5 = (1 << 4);

    private static final double POW_2_4 = Math.pow(2, 4);
    private static final double POW_2_11 = Math.pow(2, 11);
    private static final double POW_2_12 = Math.pow(2, 12);
    private static final double POW_2_14 = Math.pow(2, 14);
    private static final double POW_2_16 = Math.pow(2, 16);
    private static final double POW_2_NEG_5 = Math.pow(2, -5);
    private static final double POW_2_NEG_19 = Math.pow(2, -19);
    private static final double POW_2_NEG_24 = Math.pow(2, -24);
    private static final double POW_2_NEG_27 = Math.pow(2, -27);
    private static final double POW_2_NEG_29 = Math.pow(2, -29);
    private static final double POW_2_NEG_30 = Math.pow(2, -30);
    private static final double POW_2_NEG_31 = Math.pow(2, -31);
    private static final double POW_2_NEG_33 = Math.pow(2, -33);
    private static final double POW_2_NEG_43 = Math.pow(2, -43);
    private static final double POW_2_NEG_55 = Math.pow(2, -55);

    private static final long INTEGER_RANGE = 0xFFFFFFFFL;
    // 3657 is the number of days between the unix epoch and GPS epoch as the GPS epoch started on
    // Jan 6, 1980
    private static final long GPS_EPOCH_AS_UNIX_EPOCH_MS = TimeUnit.DAYS.toMillis(3657);
    // A GPS Cycle is 1024 weeks, or 7168 days
    private static final long GPS_CYCLE_MS = TimeUnit.DAYS.toMillis(7168);

    /** Maximum possible number of GPS satellites */
    public static final int MAX_NUMBER_OF_SATELLITES = 32;

    private static final int L1_CA_MESSAGE_LENGTH_BYTES = 40;

    private static final int IODC1_INDEX = 82;
    private static final int IODC1_LENGTH = 2;
    private static final int IODC2_INDEX = 210;
    private static final int IODC2_LENGTH = 8;
    private static final int WEEK_INDEX = 60;
    private static final int WEEK_LENGTH = 10;
    private static final int URA_INDEX = 72;
    private static final int URA_LENGTH = 4;
    private static final int SV_HEALTH_INDEX = 76;
    private static final int SV_HEALTH_LENGTH = 6;
    private static final int TGD_INDEX = 196;
    private static final int TGD_LENGTH = 8;
    private static final int AF2_INDEX = 240;
    private static final int AF2_LENGTH = 8;
    private static final int AF1_INDEX = 248;
    private static final int AF1_LENGTH = 16;
    private static final int AF0_INDEX = 270;
    private static final int AF0_LENGTH = 22;
    private static final int IODE1_INDEX = 60;
    private static final int IODE_LENGTH = 8;
    private static final int TOC_INDEX = 218;
    private static final int TOC_LENGTH = 16;
    private static final int CRS_INDEX = 68;
    private static final int CRS_LENGTH = 16;
    private static final int DELTA_N_INDEX = 90;
    private static final int DELTA_N_LENGTH = 16;
    private static final int M0_INDEX8 = 106;
    private static final int M0_INDEX24 = 120;
    private static final int CUC_INDEX = 150;
    private static final int CUC_LENGTH = 16;
    private static final int E_INDEX8 = 166;
    private static final int E_INDEX24 = 180;
    private static final int CUS_INDEX = 210;
    private static final int CUS_LENGTH = 16;
    private static final int A_INDEX8 = 226;
    private static final int A_INDEX24 = 240;
    private static final int TOE_INDEX = 270;
    private static final int TOE_LENGTH = 16;
    private static final int IODE2_INDEX = 270;
    private static final int CIC_INDEX = 60;
    private static final int CIC_LENGTH = 16;
    private static final int O0_INDEX8 = 76;
    private static final int O0_INDEX24 = 90;
    private static final int O_INDEX8 = 196;
    private static final int O_INDEX24 = 210;
    private static final int ODOT_INDEX = 240;
    private static final int ODOT_LENGTH = 24;
    private static final int CIS_INDEX = 120;
    private static final int CIS_LENGTH = 16;
    private static final int I0_INDEX8 = 136;
    private static final int I0_INDEX24 = 150;
    private static final int CRC_INDEX = 180;
    private static final int CRC_LENGTH = 16;
    private static final int IDOT_INDEX = 278;
    private static final int IDOT_LENGTH = 14;
    private static final int A0_INDEX = 68;
    private static final int A_B_LENGTH = 8;
    private static final int A1_INDEX = 76;
    private static final int A2_INDEX = 90;
    private static final int A3_INDEX = 98;
    private static final int B0_INDEX = 106;
    private static final int B1_INDEX = 120;
    private static final int B2_INDEX = 128;
    private static final int B3_INDEX = 136;
    private static final int WN_LS_INDEX = 226;
    private static final int DELTA_T_LS_INDEX = 240;
    private static final int TOT_LS_INDEX = 218;
    private static final int DN_LS_INDEX = 256;
    private static final int WNF_LS_INDEX = 248;
    private static final int DELTA_TF_LS_INDEX = 270;
    private static final int I0UTC_INDEX8 = 210;
    private static final int I0UTC_INDEX24 = 180;
    private static final int I1UTC_INDEX = 150;


    public int onNavMessageReported(int prn, int type, int page, int subframe, byte[] rawData, Context mContext) {
        if(rawData == null || type != GnssNavigationMessage.TYPE_GPS_L1CA){
            return -2;
        }
        //Log.i("Navigation",String.valueOf(subframe));
        int state = -1;
        StringBuilder NavMessage = new StringBuilder();
            switch (subframe) {
                case 1:
                    //handleFirstSubframe(prn, rawData);
                    Log.i("Navigation",getNAVType(type) + String.valueOf(prn) + "First SubFrame");
                    break;
                case 2:
                    handleSecondSubframe(rawData);
                    Log.i("Navigation",getNAVType(type) + String.valueOf(prn) + "Second SubFrame");
                    break;
                case 3:
                    //handleThirdSubframe(prn, rawData);
                    Log.i("Navigation",getNAVType(type) + String.valueOf(prn) + "Third SubFrame");
                    break;
                case 4:
                    state = handleFourthSubframe(page,rawData,mContext);
                    Log.i("Navigation",getNAVType(type) + String.valueOf(prn) + "Forth SubFrame");
                    break;
                case 5:
                    break;
                default:
                    // invalid message id
                    throw new IllegalArgumentException("Invalid Subframe ID: " + subframe);
            }
            return state;
    }

    private void handleFirstSubframe(byte prn, byte[] rawData) {
        int iodc = extractBits(IODC1_INDEX, IODC1_LENGTH, rawData) << 8;
        iodc |= extractBits(IODC2_INDEX, IODC2_LENGTH, rawData);


        // the navigation message contains a modulo-1023 week number
        int week = extractBits(WEEK_INDEX, WEEK_LENGTH, rawData);
        int uraIndex = extractBits(URA_INDEX, URA_LENGTH, rawData);
        int svHealth = extractBits(SV_HEALTH_INDEX, SV_HEALTH_LENGTH, rawData);
        byte tgd = (byte) extractBits(TGD_INDEX, TGD_LENGTH, rawData);
        int toc = extractBits(TOC_INDEX, TOC_LENGTH, rawData);
        double tocScaled = toc * POW_2_4;
        byte af2 = (byte) extractBits(AF2_INDEX, AF2_LENGTH, rawData);
        short af1 = (short) extractBits(AF1_INDEX, AF1_LENGTH, rawData);
        int af0 = extractBits(AF0_INDEX, AF0_LENGTH, rawData);
        af0 = getTwoComplement(af0, AF0_LENGTH);
    }

    private void handleSecondSubframe(byte[] rawData) {
        StringBuilder SecondSubframe = new StringBuilder();
        double iode = extractBits(IODE1_INDEX, IODE_LENGTH, rawData);

        double crs = (short) extractBits(CRS_INDEX, CRS_LENGTH, rawData) * POW_2_NEG_5;

        double deltaN = (short) extractBits(DELTA_N_INDEX, DELTA_N_LENGTH, rawData) * POW_2_NEG_43 * Math.PI;

        double m0 = (int) buildUnsigned32BitsWordFrom8And24Words(M0_INDEX8, M0_INDEX24, rawData) * POW_2_NEG_31 * Math.PI;

        String broadcastorbit_1 = String.format("    %1.12E,%1.12E,%1.12E,%1.12E",iode,crs,deltaN,m0);
        Log.i("Navigation",broadcastorbit_1);

        double cuc = (short) extractBits(CUC_INDEX, CUC_LENGTH, rawData) * POW_2_NEG_29;

        // an unsigned 32 bit value
        double e = buildUnsigned32BitsWordFrom8And24Words(E_INDEX8, E_INDEX24, rawData) * POW_2_NEG_33;

        double cus = (short) extractBits(CUS_INDEX, CUS_LENGTH, rawData) * POW_2_NEG_29;

        // an unsigned 32 bit value
        double a = buildUnsigned32BitsWordFrom8And24Words(A_INDEX8, A_INDEX24, rawData) * POW_2_NEG_19;

        double toe = extractBits(TOE_INDEX, TOE_LENGTH, rawData) * POW_2_4;
        double toeScaled = toe * POW_2_4;

    }
/*
    private void handleThirdSubframe(byte prn, byte[] rawData) {

        int iode = extractBits(IODE2_INDEX, IODE_LENGTH, rawData);

        IntermediateEphemeris intermediateEphemeris =
                findIntermediateEphemerisToUpdate(prn, SUBFRAME_3, iode);
        if (intermediateEphemeris == null) {
            // A fully or partially decoded message is available , hence nothing to update
            return;
        }

        GpsEphemerisProto gpsEphemerisProto = intermediateEphemeris.getEphemerisObj();
        gpsEphemerisProto.iode = iode;

        short cic = (short) extractBits(CIC_INDEX, CIC_LENGTH, rawData);
        gpsEphemerisProto.cic = cic * POW_2_NEG_29;

        int o0 = (int) buildUnsigned32BitsWordFrom8And24Words(O0_INDEX8, O0_INDEX24, rawData);
        gpsEphemerisProto.omega0 = o0 * POW_2_NEG_31 * Math.PI;

        int o = (int) buildUnsigned32BitsWordFrom8And24Words(O_INDEX8, O_INDEX24, rawData);
        gpsEphemerisProto.omega = o * POW_2_NEG_31 * Math.PI;

        int odot = extractBits(ODOT_INDEX, ODOT_LENGTH, rawData);
        odot = getTwoComplement(odot, ODOT_LENGTH);;
        gpsEphemerisProto.omegaDot = o * POW_2_NEG_43 * Math.PI;

        short cis = (short) extractBits(CIS_INDEX, CIS_LENGTH, rawData);
        gpsEphemerisProto.cis = cis * POW_2_NEG_29;

        int i0 = (int) buildUnsigned32BitsWordFrom8And24Words(I0_INDEX8, I0_INDEX24, rawData);
        gpsEphemerisProto.i0 = i0 * POW_2_NEG_31 * Math.PI;

        short crc = (short) extractBits(CRC_INDEX, CRC_LENGTH, rawData);
        gpsEphemerisProto.crc = crc * POW_2_NEG_5;


        // a 14-bit two's complement number
        int idot = extractBits(IDOT_INDEX, IDOT_LENGTH, rawData);
        idot = getTwoComplement(idot, IDOT_LENGTH);
        gpsEphemerisProto.iDot = idot * POW_2_NEG_43 * Math.PI;

        updateDecodedState(prn, SUBFRAME_3, intermediateEphemeris);
    }*/

    int handleFourthSubframe(int page, byte[] rawData, Context mContext) {
        /*byte pageId = (byte) extractBits(62, 6, rawData);*/
        if (page != 18) {
            // We only care to decode ionospheric parameters for now
            Log.i("Navigation","PAGE No." + page + " Not found IONOSPHERIC DATA");
            return -2;
        }

        StringBuilder FourthSubframe = new StringBuilder();
        SQLiteDatabase NavDB;
        SQLiteManager hlpr = new SQLiteManager(mContext);
        NavDB = hlpr.getWritableDatabase();

        double[] alpha = new double[4];
        byte a0 = (byte) extractBits(A0_INDEX, A_B_LENGTH, rawData);

        alpha[0] = a0 * POW_2_NEG_30;
        byte a1 = (byte) extractBits(A1_INDEX, A_B_LENGTH, rawData);
        alpha[1] = a1 * POW_2_NEG_27;
        byte a2 = (byte) extractBits(A2_INDEX, A_B_LENGTH, rawData);
        alpha[2] = a2 * POW_2_NEG_24;
        byte a3 = (byte) extractBits(A3_INDEX, A_B_LENGTH, rawData);
        alpha[3] = a3 * POW_2_NEG_24;
        FourthSubframe.append(String.format("GPSA   %1.4E %1.4E %1.4E %1.4E       IONOSPHERIC CORR\n",alpha[0],alpha[1],alpha[2],alpha[3]));


        double[] beta = new double[4];
        byte b0 = (byte) extractBits(B0_INDEX, A_B_LENGTH, rawData);
        beta[0] = b0 * POW_2_11;
        byte b1 = (byte) extractBits(B1_INDEX, A_B_LENGTH, rawData);
        beta[1] = b1 * POW_2_14;
        byte b2 = (byte) extractBits(B2_INDEX, A_B_LENGTH, rawData);
        beta[2] = b2 * POW_2_16;
        byte b3 = (byte) extractBits(B3_INDEX, A_B_LENGTH, rawData);
        beta[3] = b3 * POW_2_16;
        FourthSubframe.append(String.format("GPSB   %1.4E %1.4E %1.4E %1.4E       IONOSPHERIC CORR\n",beta[0],beta[1],beta[2],beta[3]));

        double a0UTC =
                buildSigned32BitsWordFrom8And24WordsWith8bitslsb(I0UTC_INDEX8, I0UTC_INDEX24, rawData)
                        * Math.pow(2, -30);
        double a1UTC = getTwoComplement(extractBits(I1UTC_INDEX, 24, rawData), 24) * Math.pow(2, -50);
        short tot = (short)(extractBits(TOT_LS_INDEX, A_B_LENGTH, rawData) * POW_2_12);
        short wnt = (short)extractBits(WN_LS_INDEX, A_B_LENGTH, rawData);

        short tls = (short) extractBits(DELTA_T_LS_INDEX, A_B_LENGTH, rawData);
        short wnlsf = (short) extractBits(WNF_LS_INDEX, A_B_LENGTH, rawData);
        short dn = (short) extractBits(DN_LS_INDEX, A_B_LENGTH, rawData);
        short tlsf = (short) extractBits(DELTA_TF_LS_INDEX, A_B_LENGTH, rawData);
//tot > 0 && a1 > 0 && tls > 0
        if(true) {
            if (!hlpr.existTable(NavDB, "IONOSPHERIC")) {
                hlpr.createTable(NavDB, "IONOSPHERIC");
            }


            if (!hlpr.existColumn(NavDB, "IONOSPHERIC", "GPSA0")) {
                ContentValues values = new ContentValues();
                NavDB.execSQL("ALTER TABLE 'IONOSPHERIC' ADD COLUMN 'GPSA0'");
                NavDB.execSQL("ALTER TABLE 'IONOSPHERIC' ADD COLUMN 'GPSA1'");
                NavDB.execSQL("ALTER TABLE 'IONOSPHERIC' ADD COLUMN 'GPSA2'");
                NavDB.execSQL("ALTER TABLE 'IONOSPHERIC' ADD COLUMN 'GPSA3'");
                NavDB.execSQL("ALTER TABLE 'IONOSPHERIC' ADD COLUMN 'GPSB0'");
                NavDB.execSQL("ALTER TABLE 'IONOSPHERIC' ADD COLUMN 'GPSB1'");
                NavDB.execSQL("ALTER TABLE 'IONOSPHERIC' ADD COLUMN 'GPSB2'");
                NavDB.execSQL("ALTER TABLE 'IONOSPHERIC' ADD COLUMN 'GPSB3'");
                values.put("GPSA0", alpha[0]);
                values.put("GPSA1", alpha[1]);
                values.put("GPSA2", alpha[2]);
                values.put("GPSA3", alpha[3]);
                values.put("GPSB0", beta[0]);
                values.put("GPSB1", beta[1]);
                values.put("GPSB2", beta[2]);
                values.put("GPSB3", beta[3]);
                NavDB.insert("IONOSPHERIC", null, values);
                values.clear();
            } else {
                ContentValues values = new ContentValues();
                values.put("GPSA0", alpha[0]);
                values.put("GPSA1", alpha[1]);
                values.put("GPSA2", alpha[2]);
                values.put("GPSA3", alpha[3]);
                values.put("GPSB0", beta[0]);
                values.put("GPSB1", beta[1]);
                values.put("GPSB2", beta[2]);
                values.put("GPSB3", beta[3]);
                NavDB.update("IONOSPHERIC", values, null, null);
                values.clear();
            }
            //NavDB.close();
            if (!hlpr.existTable(NavDB, "UTC")) {
                hlpr.createTable(NavDB, "UTC");
            }

            FourthSubframe.append(String.format("GPUT %1.10E%1.10E %6d %6d         TIME SYSTEM CORR\n", a0UTC, a1UTC, tot, wnt));
            if (!hlpr.existColumn(NavDB, "UTC", "a0UTC")) {
                ContentValues values = new ContentValues();
                NavDB.execSQL("ALTER TABLE 'UTC' ADD COLUMN 'a0UTC'");
                NavDB.execSQL("ALTER TABLE 'UTC' ADD COLUMN 'a1UTC'");
                NavDB.execSQL("ALTER TABLE 'UTC' ADD COLUMN 'tot'");
                NavDB.execSQL("ALTER TABLE 'UTC' ADD COLUMN 'wnt'");
                values.put("a0UTC", a0UTC);
                values.put("a1UTC", a1UTC);
                values.put("tot", tot);
                values.put("wnt", wnt);
                NavDB.insert("UTC", null, values);
                values.clear();
            } else {
                ContentValues values = new ContentValues();
                values.put("a0UTC", a0UTC);
                values.put("a1UTC", a1UTC);
                values.put("tot", tot);
                values.put("wnt", wnt);
                NavDB.update("UTC", values, null, null);
                values.clear();
            }

            if (!hlpr.existTable(NavDB, "LEAPSECOND")) {
                hlpr.createTable(NavDB, "LEAPSECOND");
            }

            FourthSubframe.append(String.format("%6d%6d%6d%6d                                   LEAP SECONDS\n", tls, tlsf, wnlsf, dn));
            if (!hlpr.existColumn(NavDB, "LEAPSECOND", "tls")) {
                ContentValues values = new ContentValues();
                NavDB.execSQL("ALTER TABLE 'LEAPSECOND' ADD COLUMN 'tls'");
                NavDB.execSQL("ALTER TABLE 'LEAPSECOND' ADD COLUMN 'wnlsf'");
                NavDB.execSQL("ALTER TABLE 'LEAPSECOND' ADD COLUMN 'dn'");
                NavDB.execSQL("ALTER TABLE 'LEAPSECOND' ADD COLUMN 'tlsf'");
                values.put("tls", tls);
                values.put("wnlsf", wnlsf);
                values.put("dn", dn);
                values.put("tlsf", tlsf);
                NavDB.insert("LEAPSECOND", null, values);
                values.clear();
            } else {
                ContentValues values = new ContentValues();
                values.put("tls", tls);
                values.put("wnlsf", wnlsf);
                values.put("dn", dn);
                values.put("tlsf", tlsf);
                NavDB.update("LEAPSECOND", values, null, null);
                values.clear();
            }
            Log.i("Navigation",FourthSubframe.toString());
            UiLogger.RINEXIONOK = true;
            return 1;
        }else {
            Log.i("Navigation",FourthSubframe.toString());
            return 0;
        }
    }

    private static int getTwoComplement(int value, int bits) {
        int msbMask = 1 << bits - 1;
        int msb = value & msbMask;
        if (msb == 0) {
            // the value is positive
            return value;
        }

        int valueBitMask = (1 << bits) - 1;
        int extendedSignMask = (int) INTEGER_RANGE - valueBitMask;
        return value | extendedSignMask;
    }

    private static int extractBits(int index, int length, byte[] rawData) {
        int result = 0;

        for (int i = 0; i < length; ++i) {
            int workingIndex = index + i;

            int wordIndex = workingIndex / WORD_SIZE_BITS;
            // account for 2 bit padding for every 30bit word
            workingIndex += (wordIndex + 1) * WORD_PADDING_BITS;
            int byteIndex = workingIndex / BYTE_AS_BITS;
            int byteOffset = workingIndex % BYTE_AS_BITS;

            byte raw = rawData[byteIndex];
            // account for zero-based indexing
            int shiftOffset = BYTE_AS_BITS - 1 - byteOffset;
            int mask = 1 << shiftOffset;
            int bit = raw & mask;
            bit >>= shiftOffset;

            // account for zero-based indexing
            result |= bit << length - 1 - i;
        }
        return result;
    }

    private static long buildUnsigned32BitsWordFrom8And24Words(int index8, int index24,
                                                               byte[] rawData) {
        long result = (long) extractBits(index8, 8, rawData) << 24;
        result |= extractBits(index24, 24, rawData);
        return result;
    }

    private static int buildSigned32BitsWordFrom8And24WordsWith8bitslsb(
            int index8, int index24, byte[] rawData) {
        int result = extractBits(index24, 24, rawData) << 8;
        result |= extractBits(index8, 8, rawData);
        return result;
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
