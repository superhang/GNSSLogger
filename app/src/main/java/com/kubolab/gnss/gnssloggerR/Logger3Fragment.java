package com.kubolab.gnss.gnssloggerR;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
//传感器
public class Logger3Fragment extends Fragment {

    private FileLogger mFileLogger;
    private UiLogger mUiLogger;
    private TextView mSensorLogView;
    private TextView mSensorRawAccView;
    private TextView mSensorRawPressView;
    private TextView mSensorRawMagView;
    //private TextView mSensorRawMagUncalibratedView;
    private TextView mSensorRawGyroView;
    //private TextView mSensorRawGyroUncalibratedView;

    private final Logger3Fragment.UIFragment3Component mUiComponent = new Logger3Fragment.UIFragment3Component();

    public void setUILogger(UiLogger value) {
        mUiLogger = value;
    }

    public void setFileLogger(FileLogger value) {
        mFileLogger = value;
    }
    @Override
    public void onAttach(Context context){
        super.onAttach(context);
        UiLogger currentUiLogger = mUiLogger;
        if (currentUiLogger != null) {
            currentUiLogger.setUiFragment3Component(mUiComponent);
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_log3, container, false /* attachToRoot */);
    }

    public void onViewCreated(View view, Bundle savedInstanceState){
        FrameLayout frameLayout = (FrameLayout) view.findViewById(R.id.fragment);
        mSensorLogView = (TextView) view.findViewById(R.id.sensorview);
        mSensorRawAccView = (TextView) view.findViewById(R.id.sensorAccView);
        mSensorRawPressView = (TextView) view.findViewById(R.id.sensorPressView);
        mSensorRawMagView = (TextView) view.findViewById(R.id.sensorMagView);
        mSensorRawGyroView = (TextView) view.findViewById(R.id.sensorGyroView);
    }

    public class UIFragment3Component {

        private static final int MAX_LENGTH = 12000;
        private static final int LOWER_THRESHOLD = (int) (MAX_LENGTH * 0.5);

        public synchronized void log3TextFragment(final String SensorString) {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            mSensorLogView.setText(SensorString);
                        }
                    });
        }

        public synchronized void log3SensorRawFragment(final String SensorRawString[]) {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            mSensorRawAccView.setText(SensorRawString[0]);
//                            mSensorRawGyroUncalibratedView .setText(SensorRawString[1]);
                            mSensorRawGyroView.setText(SensorRawString[2]);
//                            mSensorRawMagUncalibratedView.setText(SensorRawString[3]);
                            mSensorRawMagView.setText(SensorRawString[4]);
                            mSensorRawPressView.setText(SensorRawString[5]);
                        }
                    });
        }

        public void startActivity(Intent intent) {
            getActivity().startActivity(intent);
        }
    }

}
