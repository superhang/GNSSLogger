package com.kubolab.gnss.gnssloggerR;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.apache.commons.math3.geometry.euclidean.twod.Line;
//sky plot
public class Logger2Fragment extends Fragment {

    private TextView mLogView;
    private TextView mSensorLogView;
    //private ImageView SkyplotBG;
    private ScrollView mScrollView;
    private FileLogger mFileLogger;
    private UiLogger mUiLogger;
    private int MaxCanvusWidth;
    private int MaxCanvusHeight;
    private float[][] SkyPlotPos = new float[50][2];
    private float[] NorthPos = new float[2];
    private String[] SkyPlotSvid = new String[50];
    private int msatNumber = 0;
    public static float deviceAzimuth;
    private Bitmap skyplotbg = null;

    private final Logger2Fragment.UIFragment2Component mUiComponent = new Logger2Fragment.UIFragment2Component();

    public void setUILogger(UiLogger value) {
        mUiLogger = value;
    }

    public void setFileLogger(FileLogger value) {
        mFileLogger = value;
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_log2, container, false /* attachToRoot */);
    }

    public  void onViewCreated(View view, Bundle savedInstanceState){
        FrameLayout frameLayout = (FrameLayout) view.findViewById(R.id.fragment);
        frameLayout.addView(new TestView(this.getActivity()));
        //SkyplotBG = (ImageView) view.findViewById(R.id.skyplotview);
        UiLogger currentUiLogger = mUiLogger;
        if (currentUiLogger != null) {
            currentUiLogger.setUiFragment2Component(mUiComponent);
        }
    }

    public class TestView extends SurfaceView implements SurfaceHolder.Callback, Runnable{
        Paint paint = new Paint();
        private boolean isAttached;
        private Thread mLooper;
        private SurfaceHolder mHolder;
        private long mTime =0;
        Activity activity = getActivity();
        public TestView(final Context context) {
            super(context);
            initialize();
        }
        private void initialize() {
            getHolder().setFormat(PixelFormat.TRANSLUCENT);
            getHolder().addCallback(this);
            setZOrderOnTop(true);
        }
        public void surfaceDestroyed(SurfaceHolder surfaceholder) {
            mLooper = null;
        }

        public void surfaceCreated(final SurfaceHolder surfaceholder) {
            mHolder = surfaceholder;
            mLooper = new Thread(this);
        }



        public void surfaceChanged(SurfaceHolder surfaceholder, int i, int j, int k) {
            if(mLooper != null) {
                //mTime = System.currentTimeMillis();
                if(!mLooper.isAlive()) {
                    mLooper.start();
                }
                //doDraw(surfaceholder);
            }
        }
        public void run() {
            while (mLooper != null) {
                Activity activity = getActivity();
                if (activity == null) {
                    try {
                        mLooper.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (IllegalStateException e){
                        e.printStackTrace();
                    }
                } else {
                    activity.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    doDraw(mHolder);
                                    //SkyplotBG.setRotation(-deviceAzimuth);
                                }
                            });
                    try {
                        mLooper.sleep(1000);
                    } catch (InterruptedException e) {
                         e.printStackTrace();
                    } catch (IllegalStateException e){
                        e.printStackTrace();
                    }
                }
            }
        }


        private void doDraw(SurfaceHolder holder) {
            Canvas canvas = holder.lockCanvas();
            if (canvas == null){ return; }
            try
            {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                draw(canvas);
            }
            finally
            {
                if ( canvas != null )
                {
                    holder.unlockCanvasAndPost(canvas);
                    canvas = null;
                }
            }
        }

        @Override
        public void draw(Canvas canvas){
            //canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            //canvas.drawColor(Color.WHITE);
            MaxCanvusWidth = canvas.getWidth();
            MaxCanvusHeight = canvas.getHeight();
            //背景の描画
            //点線
            Paint mDotPaint = new Paint();
            //普通の線&文字
            Paint mPaint = new Paint();
            mPaint.setStrokeWidth(1);
            mDotPaint.setPathEffect(new DashPathEffect(new float[]{5.0f,5.0f},0));
            mPaint.setStyle(Paint.Style.STROKE);
            mDotPaint.setStyle(Paint.Style.STROKE);
            mDotPaint.setStrokeWidth(1);
            mPaint.setColor(Color.BLACK);
            mDotPaint.setColor(Color.BLACK);
            mPaint.setTextSize(30);
            //サークルの作画
            float maxCircleRadius = (float) ((MaxCanvusWidth/2)*0.888);
            canvas.drawCircle(MaxCanvusWidth/2,MaxCanvusHeight/2 , maxCircleRadius , mPaint);
            canvas.drawCircle(MaxCanvusWidth/2,MaxCanvusHeight/2 , (maxCircleRadius/6)*5 , mDotPaint);
            canvas.drawCircle(MaxCanvusWidth/2,MaxCanvusHeight/2 , (maxCircleRadius/6)*4 , mDotPaint);
            canvas.drawCircle(MaxCanvusWidth/2,MaxCanvusHeight/2 , (maxCircleRadius/6)*3 , mDotPaint);
            canvas.drawCircle(MaxCanvusWidth/2,MaxCanvusHeight/2 , (maxCircleRadius/6)*2 , mDotPaint);
            canvas.drawCircle(MaxCanvusWidth/2,MaxCanvusHeight/2 , (maxCircleRadius/6)*1 , mDotPaint);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setTextSize(30);
            canvas.drawText("90",MaxCanvusWidth/2 - 15.0f,MaxCanvusHeight/2,mPaint);
            canvas.drawText("60",MaxCanvusWidth/2 - 15.0f,MaxCanvusHeight/2 - (maxCircleRadius/6)*2,mPaint);
            canvas.drawText("30",MaxCanvusWidth/2 - 15.0f,MaxCanvusHeight/2 - (maxCircleRadius/6)*4,mPaint);
            //線の作画
            for(int i = 0; i <= 360; i = i + 30){
                double LineDegree = i - deviceAzimuth - 90;
                if(LineDegree < 0){
                    LineDegree = LineDegree + 360;
                }
                canvas.drawLine(MaxCanvusWidth/2,MaxCanvusHeight/2 ,(float) (MaxCanvusWidth/2 + maxCircleRadius*Math.cos(Math.toRadians(LineDegree))), (float) (MaxCanvusHeight/2 + maxCircleRadius*Math.sin(Math.toRadians(LineDegree))), mDotPaint);
                mPaint.setStyle(Paint.Style.FILL);
                double coefficient = 1.0;
                double offsetX;
                double offsetY;
                double offsetXT;
                double offsetYT;
                if(LineDegree >= 90 && LineDegree < 180){
                    //第三象限
                    coefficient = 1.0;
                    offsetX = 50;
                    offsetY = 15;
                    offsetXT = 15;
                    offsetYT = 50;
                }else if(LineDegree >= 180 && LineDegree < 270){
                    //第二象限
                    coefficient = 1.0;
                    offsetX = 50;
                    offsetY = 0;
                    offsetXT = 50;
                    offsetYT = 15;
                }else if(LineDegree >= 270 && LineDegree < 360){
                    //第一象限
                    coefficient = 1.0;
                    offsetX = -15;
                    offsetY = 0;
                    offsetXT = 15;
                    offsetYT = -15;
                }else {
                    //第四象限
                    coefficient = 1.0;
                    offsetX = 0;
                    offsetY = 15;
                    offsetXT = -15;
                    offsetYT = 15;
                }
                if(i == 0 || i == 90 || i == 180 || i == 270){
                    mPaint.setTextSize(50);
                    switch (i){
                        case 0:
                            canvas.drawText("N",(float) (MaxCanvusWidth/2 + coefficient*maxCircleRadius*Math.cos(Math.toRadians(LineDegree)) - offsetXT),(float) (MaxCanvusHeight/2 + coefficient*maxCircleRadius*Math.sin(Math.toRadians(LineDegree)) + offsetYT),mPaint);
                            break;
                        case 90:
                            canvas.drawText("E",(float) (MaxCanvusWidth/2 + coefficient*maxCircleRadius*Math.cos(Math.toRadians(LineDegree)) - offsetXT),(float) (MaxCanvusHeight/2 + coefficient*maxCircleRadius*Math.sin(Math.toRadians(LineDegree)) + offsetYT),mPaint);
                            break;
                        case 180:
                            canvas.drawText("S",(float) (MaxCanvusWidth/2 + coefficient*maxCircleRadius*Math.cos(Math.toRadians(LineDegree)) - offsetXT),(float) (MaxCanvusHeight/2 + coefficient*maxCircleRadius*Math.sin(Math.toRadians(LineDegree)) + offsetYT),mPaint);
                            break;
                        case 270:
                            canvas.drawText("W",(float) (MaxCanvusWidth/2 + coefficient*maxCircleRadius*Math.cos(Math.toRadians(LineDegree)) - offsetXT),(float) (MaxCanvusHeight/2 + coefficient*maxCircleRadius*Math.sin(Math.toRadians(LineDegree)) + offsetYT),mPaint);
                            break;
                    }
                }else if(i != 360){
                    mPaint.setTextSize(30);
                    canvas.drawText(String.valueOf(i),(float) (MaxCanvusWidth/2 + coefficient*maxCircleRadius*Math.cos(Math.toRadians(LineDegree)) - offsetX),(float) (MaxCanvusHeight/2 + coefficient*maxCircleRadius*Math.sin(Math.toRadians(LineDegree)) + offsetY),mPaint);
                }
            }
                /*double offsetDegreeX = LineDegree;
                if(offsetDegreeX > 180){
                    offsetDegreeX = offsetDegreeX - 180;
                }
                double offsetDegreeY = -LineDegree + 270;
                if(offsetDegreeX > 90) {
                    offsetX = -50 * (offsetDegreeX / 180);
                    offsetXT = -30 * (offsetDegreeX / 180);
                }else {
                    offsetX = 50 * (offsetDegreeX / 180);
                    offsetXT = 30 * (offsetDegreeX / 180);
                }
                offsetY = 10*Math.sin(Math.toRadians(offsetDegreeY));
                offsetYT = -15*Math.sin(Math.toRadians(offsetDegreeY));*/
                /*if(i == 0 || i == 90 || i == 180 || i == 270){
                    mPaint.setTextSize(50);
                    switch (i){
                        case 0:
                            canvas.drawText("N",(float) (MaxCanvusWidth/2 + coefficient*maxCircleRadius*Math.cos(Math.toRadians(LineDegree)) - offsetXT),(float) (MaxCanvusHeight/2 + coefficient*maxCircleRadius*Math.sin(Math.toRadians(LineDegree)) + offsetYT),mPaint);
                            break;
                        case 90:
                            canvas.drawText("E",(float) (MaxCanvusWidth/2 + coefficient*maxCircleRadius*Math.cos(Math.toRadians(LineDegree)) - offsetXT),(float) (MaxCanvusHeight/2 + coefficient*maxCircleRadius*Math.sin(Math.toRadians(LineDegree)) + offsetYT),mPaint);
                            break;
                        case 180:
                            canvas.drawText("S",(float) (MaxCanvusWidth/2 + coefficient*maxCircleRadius*Math.cos(Math.toRadians(LineDegree)) - offsetXT),(float) (MaxCanvusHeight/2 + coefficient*maxCircleRadius*Math.sin(Math.toRadians(LineDegree)) + offsetYT),mPaint);
                            break;
                        case 270:
                            canvas.drawText("W",(float) (MaxCanvusWidth/2 + coefficient*maxCircleRadius*Math.cos(Math.toRadians(LineDegree)) - offsetXT),(float) (MaxCanvusHeight/2 + coefficient*maxCircleRadius*Math.sin(Math.toRadians(LineDegree)) + offsetYT),mPaint);
                            break;
                    }
                }else if(i != 360){
                    mPaint.setTextSize(30);
                    canvas.drawText(String.valueOf(i),(float) (MaxCanvusWidth/2 + coefficient*maxCircleRadius*Math.cos(Math.toRadians(LineDegree)) - offsetX),(float) (MaxCanvusHeight/2 + coefficient*maxCircleRadius*Math.sin(Math.toRadians(LineDegree)) + offsetY),mPaint);
                }
            }*/

            //衛星配置のプロット
            paint.setColor(Color.BLACK);
            paint.setTextSize(50);
            //paint.setStyle(Paint.Style.FILL);
            //paint.setAntiAlias(true);
            //衛星配置のプロット
            paint.setTypeface(Typeface.SERIF);
            for(int i = 0; i < msatNumber; i++){
                if(SkyPlotSvid[i] != null) {
                    if(SkyPlotSvid[i].indexOf("R") != -1) {
                        paint.setColor(Color.GREEN);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(1);
                        paint.setAntiAlias(true);
                        canvas.drawCircle(MaxCanvusWidth/2 + SkyPlotPos[i][0],MaxCanvusHeight/2 + SkyPlotPos[i][1] , 50 , paint);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setStrokeWidth(1);
                        paint.setTextSize(50);
                        paint.setColor(Color.GREEN);
                        //canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 100.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 50.0f, paint);
                        canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 50.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 15.0f, paint);
                    }
                    else if(SkyPlotSvid[i].indexOf("J") != -1){
                        paint.setColor(Color.MAGENTA);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(1);
                        paint.setAntiAlias(true);
                        //canvas.drawRect(MaxCanvusWidth/2 + SkyPlotPos[i][0] - 10,MaxCanvusHeight/2 + SkyPlotPos[i][1] - 10 , MaxCanvusWidth/2 + SkyPlotPos[i][0] + 10 ,MaxCanvusHeight/2 + SkyPlotPos[i][1] + 10, paint);
                        canvas.drawCircle(MaxCanvusWidth/2 + SkyPlotPos[i][0],MaxCanvusHeight/2 + SkyPlotPos[i][1] , 50 , paint);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setStrokeWidth(1);
                        paint.setTextSize(50);
                        paint.setColor(Color.MAGENTA);
                        //canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 100.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 50.0f, paint);
                        canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 50.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 15.0f, paint);
                    }else if(SkyPlotSvid[i].indexOf("G") != -1){
                        paint.setColor(Color.parseColor("#58D3F7"));
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(1);
                        paint.setAntiAlias(true);
                        //canvas.drawRect(MaxCanvusWidth/2 + SkyPlotPos[i][0] - 10,MaxCanvusHeight/2 + SkyPlotPos[i][1] - 10 , MaxCanvusWidth/2 + SkyPlotPos[i][0] + 10 ,MaxCanvusHeight/2 + SkyPlotPos[i][1] + 10, paint);
                        canvas.drawCircle(MaxCanvusWidth/2 + SkyPlotPos[i][0],MaxCanvusHeight/2 + SkyPlotPos[i][1] , 50 , paint);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setStrokeWidth(1);
                        paint.setTextSize(50);
                        paint.setColor(Color.parseColor("#58D3F7"));
                        //canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 100.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 50.0f, paint);
                        canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 50.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 15.0f, paint);
                    }else if(SkyPlotSvid[i].indexOf("E") != -1){
                        paint.setColor(Color.parseColor("#0101DF"));
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(1);
                        paint.setAntiAlias(true);
                        //canvas.drawRect(MaxCanvusWidth/2 + SkyPlotPos[i][0] - 10,MaxCanvusHeight/2 + SkyPlotPos[i][1] - 10 , MaxCanvusWidth/2 + SkyPlotPos[i][0] + 10 ,MaxCanvusHeight/2 + SkyPlotPos[i][1] + 10, paint);
                        canvas.drawCircle(MaxCanvusWidth/2 + SkyPlotPos[i][0],MaxCanvusHeight/2 + SkyPlotPos[i][1] , 50 , paint);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setStrokeWidth(1);
                        paint.setTextSize(50);
                        paint.setColor(Color.parseColor("#0101DF"));
                        //canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 100.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 50.0f, paint);
                        canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 50.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 15.0f, paint);
                    }else if(SkyPlotSvid[i].indexOf("C") != -1){
                        paint.setColor(Color.parseColor("#FF8000"));
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(1);
                        paint.setAntiAlias(true);
                        //canvas.drawRect(MaxCanvusWidth/2 + SkyPlotPos[i][0] - 10,MaxCanvusHeight/2 + SkyPlotPos[i][1] - 10 , MaxCanvusWidth/2 + SkyPlotPos[i][0] + 10 ,MaxCanvusHeight/2 + SkyPlotPos[i][1] + 10, paint);
                        canvas.drawCircle(MaxCanvusWidth/2 + SkyPlotPos[i][0],MaxCanvusHeight/2 + SkyPlotPos[i][1] , 50 , paint);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setStrokeWidth(1);
                        paint.setTextSize(50);
                        paint.setColor(Color.parseColor("#FF8000"));
                        //canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 100.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 50.0f, paint);
                        canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 50.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 15.0f, paint);
                    }else if(SkyPlotSvid[i].indexOf("S") != -1){
                        paint.setColor(Color.parseColor("#FFFF00"));
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(1);
                        paint.setAntiAlias(true);
                        //canvas.drawRect(MaxCanvusWidth/2 + SkyPlotPos[i][0] - 10,MaxCanvusHeight/2 + SkyPlotPos[i][1] - 10 , MaxCanvusWidth/2 + SkyPlotPos[i][0] + 10 ,MaxCanvusHeight/2 + SkyPlotPos[i][1] + 10, paint);
                        canvas.drawCircle(MaxCanvusWidth/2 + SkyPlotPos[i][0],MaxCanvusHeight/2 + SkyPlotPos[i][1] , 50 , paint);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setStrokeWidth(1);
                        paint.setTextSize(50);
                        paint.setColor(Color.parseColor("#FFFF00"));
                        //canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 100.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 50.0f, paint);
                        canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 50.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 15.0f, paint);
                    }else if(SkyPlotSvid[i].indexOf("U") != -1){
                        paint.setColor(Color.parseColor("#000000"));
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(1);
                        paint.setAntiAlias(true);
                        //canvas.drawRect(MaxCanvusWidth/2 + SkyPlotPos[i][0] - 10,MaxCanvusHeight/2 + SkyPlotPos[i][1] - 10 , MaxCanvusWidth/2 + SkyPlotPos[i][0] + 10 ,MaxCanvusHeight/2 + SkyPlotPos[i][1] + 10, paint);
                        canvas.drawCircle(MaxCanvusWidth/2 + SkyPlotPos[i][0],MaxCanvusHeight/2 + SkyPlotPos[i][1] , 50 , paint);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setStrokeWidth(1);
                        paint.setTextSize(50);
                        paint.setColor(Color.parseColor("#000000"));
                        //canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 100.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 50.0f, paint);
                        canvas.drawText(SkyPlotSvid[i], MaxCanvusWidth / 2 + SkyPlotPos[i][0] - 50.0f, MaxCanvusHeight / 2 + SkyPlotPos[i][1] + 15.0f, paint);
                    }
                }
            }

        }
    }

    public class UIFragment2Component {

        private static final int MAX_LENGTH = 12000;
        private static final int LOWER_THRESHOLD = (int) (MAX_LENGTH * 0.5);

        public synchronized void log2TextFragment(final String[] svid, final float[][] pos, final int satnumber) {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            final TestView testview = new TestView((Context)activity);
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            for(int i = 0;i < satnumber;i++){
                                //まずは仰角を変換
                                double Altitude = 1 - pos[i][1]/90;
                                Altitude = Altitude * (MaxCanvusWidth/2);
                                float azimuth = pos[i][0];
                                //Log.d("Azimuth",String.valueOf(azimuth));
                                azimuth = azimuth - 90;
                                if(azimuth < 0){
                                    azimuth = azimuth + 360;
                                }
                                if(SettingsFragment.useDeviceSensor) {
                                    float gnssAzimuth = azimuth;
                                    gnssAzimuth = azimuth - deviceAzimuth;
                                    if (gnssAzimuth < 0) {
                                        gnssAzimuth = gnssAzimuth + 360;
                                    }
                                    azimuth = gnssAzimuth;
                                }
                                SkyPlotPos[i][0] = (float) (0.888 * Altitude * Math.cos(Math.toRadians(azimuth)));
                                SkyPlotPos[i][1] = (float) (0.888 * Altitude * Math.sin(Math.toRadians(azimuth)));
                                SkyPlotSvid[i] = svid[i];
                            }
                            float DevAzimuth = -90;
                            if(SettingsFragment.useDeviceSensor) {
                                DevAzimuth = -deviceAzimuth;
                                DevAzimuth = DevAzimuth - 90;
                                if (DevAzimuth < -360) {
                                    DevAzimuth = DevAzimuth + 360;
                                }
                            }
                            msatNumber = satnumber;
                        }
                    });
        }

        public synchronized void log2SensorFragment(final double azimuth) {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            //final TestView testview = new TestView((Context)activity);
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            deviceAzimuth = (float) azimuth;
                        }
                    });
        }
        public void startActivity(Intent intent) {
            getActivity().startActivity(intent);
        }
    }

}
