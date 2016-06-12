package com.voxlearning.androidtcpdumpgui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.voxlearning.androidtcpdumpgui.util.RootUtils;
import com.voxlearning.androidtcpdumpgui.util.Str;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

@SuppressWarnings("FieldCanBeLocal")
@SuppressLint({"DefaultLocale", "SetTextI18n"})
public class MainActivity extends AppCompatActivity {

    private TextView mTextViewInterface;
    private Spinner mSpinnerInterface;

    private TextView mTextViewFilter;
    private EditText mEditTextFilter;

    private Button mButtonStartStop;
    private CaptureManager mCaptureManager;
    private ListView mListViewCaptures;
    private Handler mHandler;
    private TimerUpdateUi mTimerUpdateUi;


    public class TimerUpdateUi implements Runnable {

        private boolean mPosted = false;

        @Override
        public void run() {
            mPosted = false;
            updateUi();
            if(mCaptureManager.isDirRunningExisting()) {
                postDelayed();
            }
        }

        public void postDelayed() {
            if(!mPosted) {
                mPosted = true;
                mHandler.postDelayed(this, 2000);
            }
        }
    }

    private void reloadInterfaces() {
        ArrayAdapter<String> items = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<String>());
        items.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        List<CaptureManager.Interface> interfaces = mCaptureManager.getInterfaces(this);
        items.add("any");
        for (CaptureManager.Interface intf : interfaces) {
            if (intf.mStateUp) {
                items.add(intf.mName + " (" + intf.mIpv4Net + ")");
            }
        }
        mSpinnerInterface.setAdapter(items);
    }

    @SuppressLint("SdCardPath")
    private void chooseTcpdumpFilter() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);

        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, new ArrayList<String>());

        final LinkedHashMap<String, String> filters = mCaptureManager.loadTcpdumpFilters();
        for(LinkedHashMap.Entry<String, String> e : filters.entrySet()) {
            adapter.add(e.getKey());
        }

        b.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String s = filters.get(adapter.getItem(which));
                if(!Str.isEmpty(s)) {
                    mEditTextFilter.append(s);
                }
                dialog.dismiss();
            }
        });

        b.setTitle("/sdcard/.../" + CaptureFilePath.ConfigFileName_TcpdumpFilters);
        b.show();
    }

    private String getSelectedInterface() {
        String s = (String)mSpinnerInterface.getSelectedItem();
        if(s != null) {
            String []a = s.split(" ");
            return a[0];
        }
        return "any";
    }

    private void doStart() {
        if(!RootUtils.isRootAvailable()) {
            Toast.makeText(this, "Please root your phone and install SuperSU", Toast.LENGTH_LONG).show();
            return;
        }

        mCaptureManager.start(this, getSelectedInterface(), mEditTextFilter.getText().toString());
        Intent intent = new Intent(this, CaptureService.class);
        intent.setAction(CaptureService.ServiceAction_CaptureStart);
        startService(intent);

        mTimerUpdateUi.postDelayed();
        updateUi();
    }

    private void doStop() {
        String captureName = mCaptureManager.stop(this);

        Intent intent = new Intent(this, CaptureService.class);
        intent.setAction(CaptureService.ServiceAction_CaptureStop);
        startService(intent);

        updateUi();

        if(captureName != null) {
            ViewActivity.openCapture(this, captureName);
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        updateTitleWithVersion();

        mHandler = new Handler(Looper.getMainLooper());
        mTimerUpdateUi = new TimerUpdateUi();
        mCaptureManager = new CaptureManager();

        mCaptureManager.prepare(this);

        mSpinnerInterface = (Spinner)findViewById(R.id.spinnerInterface);
        mTextViewInterface = (TextView)findViewById(R.id.textViewInterface);
        mTextViewInterface.setClickable(true);
        mTextViewInterface.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reloadInterfaces();
            }
        });
        reloadInterfaces();

        mTextViewFilter = (TextView)findViewById(R.id.textViewFilter);
        mEditTextFilter = (EditText)findViewById(R.id.editTextFilter);
        mTextViewFilter.setClickable(true);
        mTextViewFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseTcpdumpFilter();
            }
        });


        mListViewCaptures = (ListView)findViewById(R.id.listViewCaptures);
        mListViewCaptures.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ArrayAdapter<String> adapter = (ArrayAdapter<String>)parent.getAdapter();
                ViewActivity.openCapture(MainActivity.this, adapter.getItem(position));
            }
        });

        mButtonStartStop = (Button)findViewById(R.id.buttonStartStop);
        mButtonStartStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mCaptureManager.isDirRunningExisting()) {
                    doStop();
                }
                else {
                    doStart();
                }
            }
        });

    }


    private void updateUi() {
        if(mCaptureManager.isDirRunningExisting()) {

            CaptureManager.Stats stats = mCaptureManager.getStats();
            double minutes = (System.currentTimeMillis() - stats.mStartTime) / 1000.0 / 60;
            double size = stats.mPacketsSize / 1024.0 / 1024.0;
            setTitle("Running time: " + String.format("%.1f", minutes) + "min, Size: " + String.format("%.2f", size)+ "M");

            mButtonStartStop.setText("Stop");
        }
        else {
            mButtonStartStop.setText("Start");

            updateTitleWithVersion();

            File[] files = new File(CaptureFilePath.OutputDir("")).listFiles();
            List<String> names = new ArrayList<>();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        names.add(f.getName());
                    }
                }
            }

            Collections.sort(names, new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    return -lhs.compareTo(rhs);
                }
            });

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_selectable_list_item, names);
            mListViewCaptures.setAdapter(adapter);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
        mTimerUpdateUi.postDelayed();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle("Location")
                    .setMessage("Please allow this app to use GPS & Network Location")
                    .show();
        }


        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean isGpsLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if(!isGpsLocationEnabled) {
            new AlertDialog.Builder(this)
                    .setTitle("Location").setMessage("GPS is not enabled. Do you want to go to settings menu?")
                    .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            MainActivity.this.startActivity(intent);
                        }
                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            }).show();
        }
    }

    private void updateTitleWithVersion() {
        String s = getString(R.string.app_name);
        setTitle(s + " (" + BuildConfig.VERSION_NAME + "-" + BuildConfig.VERSION_CODE + ")");
    }
}
