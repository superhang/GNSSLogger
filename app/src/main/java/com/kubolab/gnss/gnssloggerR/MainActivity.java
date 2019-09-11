package com.kubolab.gnss.gnssloggerR;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.design.widget.TabLayout.TabLayoutOnPageChangeListener;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;

/** The activity for the application. */
public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_REQUEST_ID = 1;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.BODY_SENSORS
    };
    private static final int NUMBER_OF_FRAGMENTS = 4;
    private static final int FRAGMENT_INDEX_SETTING = 0;
    private static final int FRAGMENT_INDEX_LOGGER = 1;
    private static final int FRAGMENT_INDEX_LOGGER2 = 2;
    private static final int FRAGMENT_INDEX_LOGGER3 = 3;

    private GnssContainer mGnssContainer;
    private UiLogger mUiLogger;
    private FileLogger mFileLogger;
    private Fragment[] mFragments;
    private GnssNavigationDataBase mGnssNavigationDataBase;
    private  SensorContainer mSensorContainer;
    private static MainActivity instance = null;

    public boolean GNSSRegister = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //アクションバーのロゴ設定  动作栏的标志设置
        getSupportActionBar().setDisplayShowHomeEnabled(true); //アイコン表示 图标视图
        getSupportActionBar().setIcon(R.mipmap.ic_launcher);

        requestPermissionAndSetupFragments(this);
        instance = this;
    }
    @Override
    protected void onStart() {
        super.onStart();
        if(hasPermissions(this)) {
            mGnssContainer.registerAll();
            GNSSRegister = true;
            if(SettingsFragment.useDeviceSensor){
                mSensorContainer.registerSensor();
            }
        }
    }
    @Override
    protected void onStop() {
        super.onStop();
        if(hasPermissions(this)) {
            mGnssContainer.unregisterAll();
            GNSSRegister = false;
            if(SettingsFragment.useDeviceSensor){
                mSensorContainer.unregisterSensor();
            }
        }
        //データベースは今のところクリアする.  数据库目前清除
        SQLiteDatabase NavDB;
        SQLiteManager hlpr = new SQLiteManager(getApplicationContext());
        NavDB = hlpr.getWritableDatabase();
        deleteDatabase(NavDB.getPath());
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to one of the
     * sections/tabs/pages.
     */
    public class ViewPagerAdapter extends FragmentStatePagerAdapter {

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case FRAGMENT_INDEX_SETTING:
                    return mFragments[FRAGMENT_INDEX_SETTING];
                case FRAGMENT_INDEX_LOGGER:
                    return mFragments[FRAGMENT_INDEX_LOGGER];
                case FRAGMENT_INDEX_LOGGER2:
                    return mFragments[FRAGMENT_INDEX_LOGGER2];
                case FRAGMENT_INDEX_LOGGER3:
                    return mFragments[FRAGMENT_INDEX_LOGGER3];
                default:
                    throw new IllegalArgumentException("Invalid section: " + position);
            }
        }

        @Override
        public int getCount() {
            // Show total pages.
            return 4;
        }

        Drawable myDrawable;
        String title;

        @Override
        public CharSequence getPageTitle(int position) {
            //Locale locale = Locale.getDefault();
            /*switch (position) {
                case 0:
                    title =  "Setting";
                    myDrawable = getResources().getDrawable(R.drawable.icon_101930_256);
                    break;
                case 1:
                    title = "Monitor&Log";
                    myDrawable = getResources().getDrawable(R.drawable.icon_160240_256);
                    break;
                case 2:
                    title = "skyplot.png";
                    myDrawable = getResources().getDrawable(R.drawable.icon_146290_256);
                    break;
                default:
                    break;
            }
            SpannableStringBuilder sb = new SpannableStringBuilder("   " + title);
            try {
                myDrawable.setBounds(2, 2, myDrawable.getIntrinsicWidth()/10, myDrawable.getIntrinsicHeight()/10);
                ImageSpan span = new ImageSpan(myDrawable, DynamicDrawableSpan.ALIGN_BASELINE);
                sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception e) {
                Log.e("Drawable Error","Span Draw Error");
            }*/
            return null;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == LOCATION_REQUEST_ID) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //SettingsFragment.PermissionOK = true;
                setupFragments();
                mGnssContainer.registerAll();
            }
        }
    }

    private void setupFragments() {
        mUiLogger = new UiLogger(getApplicationContext());
        mFileLogger = new FileLogger(getApplicationContext());
        mSensorContainer = new SensorContainer(getApplicationContext() ,mUiLogger, mFileLogger);
        mGnssContainer = new GnssContainer(getApplicationContext(), mUiLogger, mFileLogger);
        mGnssNavigationDataBase = new GnssNavigationDataBase(getApplicationContext());
        mFragments = new Fragment[NUMBER_OF_FRAGMENTS];
        SettingsFragment settingsFragment = new SettingsFragment();
        settingsFragment.setSensorContainer(mSensorContainer);
        settingsFragment.setGpsContainer(mGnssContainer);
        settingsFragment.setFileLogger(mFileLogger);
        settingsFragment.setUILogger(mUiLogger);
        settingsFragment.setGnssContainer(mGnssContainer);
        mFragments[FRAGMENT_INDEX_SETTING] = settingsFragment;

        //GNSS data的activity
        LoggerFragment loggerFragment = new LoggerFragment();
        loggerFragment.setUILogger(mUiLogger);
        loggerFragment.setFileLogger(mFileLogger);
        mFragments[FRAGMENT_INDEX_LOGGER] = loggerFragment;

        Logger2Fragment logger2Fragment = new Logger2Fragment();
        logger2Fragment.setUILogger(mUiLogger);
        logger2Fragment.setFileLogger(mFileLogger);
        mFragments[FRAGMENT_INDEX_LOGGER2] = logger2Fragment;

        //传感器
        Logger3Fragment logger3Fragment = new Logger3Fragment();
        logger3Fragment.setUILogger(mUiLogger);
        logger3Fragment.setFileLogger(mFileLogger);
        mFragments[FRAGMENT_INDEX_LOGGER3] = logger3Fragment;

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        // The viewpager that will host the section contents.
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
            viewPager.setOffscreenPageLimit(3);
        ViewPagerAdapter adapter = new ViewPagerAdapter(getFragmentManager());
            viewPager.setAdapter(adapter);
        tabLayout.setTabsFromPagerAdapter(adapter);

        // Set a listener via setOnTabSelectedListener(OnTabSelectedListener) to be notified when any
        // tab's selection state has been changed.
        tabLayout.setOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(viewPager));

        // Use a TabLayout.TabLayoutOnPageChangeListener to forward the scroll and selection changes to
        // this layout
        viewPager.addOnPageChangeListener(new TabLayoutOnPageChangeListener(tabLayout));
        //TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        TabLayout.Tab tab1 = tabLayout.getTabAt(0);
        View tab1View = inflater.inflate(R.layout.main_tab1, null);
        tab1.setCustomView(tab1View);

        TabLayout.Tab tab2 = tabLayout.getTabAt(1);
        View tab2View = inflater.inflate(R.layout.main_tab2, null);
        tab2.setCustomView(tab2View);

        TabLayout.Tab tab3 = tabLayout.getTabAt(2);
        View tab3View = inflater.inflate(R.layout.main_tab3, null);
        tab3.setCustomView(tab3View);

        TabLayout.Tab tab4 = tabLayout.getTabAt(3);
        View tab4View = inflater.inflate(R.layout.main_tab4, null);
        tab4.setCustomView(tab4View);
    }

    private boolean hasPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT < VERSION_CODES.M) {
            // Permissions granted at install time. 在安装时授予的权限。
            return true;
        }
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissionAndSetupFragments(final Activity activity) {
        if (hasPermissions(activity)) {
            //已授权
            setupFragments();
        } else {
            ActivityCompat.requestPermissions(activity, REQUIRED_PERMISSIONS, LOCATION_REQUEST_ID);
        }
    }

    public static MainActivity getInstance(){
        return instance;
    }
}
//
