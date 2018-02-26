package com.wmstein.tourcount;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.CursorIndexOutOfBoundsException;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.wmstein.egm.EarthGravitationalModel;
import com.wmstein.tourcount.database.Count;
import com.wmstein.tourcount.database.CountDataSource;
import com.wmstein.tourcount.database.IndividualsDataSource;
import com.wmstein.tourcount.database.Section;
import com.wmstein.tourcount.database.SectionDataSource;
import com.wmstein.tourcount.database.Temp;
import com.wmstein.tourcount.database.TempDataSource;
import com.wmstein.tourcount.widgets.CountingWidget;
import com.wmstein.tourcount.widgets.CountingWidgetLH;
import com.wmstein.tourcount.widgets.NotesWidget;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * CountingActivity is the central activity of TourCount. It provides the counter, starts GPS-location polling,
 * starts EditIndividualActivity, starts editSectionActivity, switches screen off when pocketed and allows sending notes.
 * Basic counting functions created by milo for beecount on 05/05/2014.
 * Adopted, modified and enhanced for TourCount by wmstein since 18.04.2016
 */

public class CountingActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener
{
    //private static final String TAG = CountingActivity.class.getSimpleName();
    private static String TAG = "TourCountCountAct";

    private SharedPreferences prefs;

    private LinearLayout count_area;
    private LinearLayout notes_area;
    private LinearLayout count_areaLH;
    private LinearLayout notes_areaLH;

    // the actual data
    private Section section;
    private List<Count> counts;
    private Temp temp;
    private List<CountingWidget> countingWidgets;
    private List<CountingWidgetLH> countingWidgetsLH;

    // Location info handling
    private LocationManager locationManager;
    private LocationListener locationListener;
    private String provider;
    private double latitude, longitude, height, uncertainty;
    
    private PowerManager.WakeLock mProximityWakeLock;

    // preferences
    private boolean awakePref;
    private boolean brightPref;
    private String sortPref;
    private boolean fontPref;
    private boolean lhandPref; // true for lefthand mode of counting screen
    private boolean buttonSoundPref;
    private String buttonAlertSound;
    private boolean screenOrientL; // option for screen orientation
    private boolean metaPref;      // option for reverse geocoding
    private String emailString = ""; // mail address for OSM query

    // data sources
    private SectionDataSource sectionDataSource;
    private CountDataSource countDataSource;
    private IndividualsDataSource individualsDataSource;
    private TempDataSource tempDataSource;

    private int i_Id = 0;
    private String spec_name; // could be used in prepared toast in deleteIndividual()
    private int spec_count;
    private String sLocality = "", sPlz = "", sCity = "", sPlace = "", sCountry = "";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Context context = this.getApplicationContext();

        sectionDataSource = new SectionDataSource(this);
        countDataSource = new CountDataSource(this);
        individualsDataSource = new IndividualsDataSource(this);
        tempDataSource = new TempDataSource(this);

        TourCountApplication tourCount = (TourCountApplication) getApplication();
        prefs = TourCountApplication.getPrefs();
        prefs.registerOnSharedPreferenceChangeListener(this);
        getPrefs();

        if (screenOrientL)
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        else
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        if (lhandPref) // if left-handed counting page
        {
            setContentView(R.layout.activity_counting_lh);
        }
        else
        {
            setContentView(R.layout.activity_counting);
        }

        // Set full brightness of screen
        if (brightPref)
        {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.screenBrightness = 1.0f;
            getWindow().setAttributes(params);
        }

        if (lhandPref) // if left-handed counting page
        {
            ScrollView counting_screen = (ScrollView) findViewById(R.id.countingScreenLH);
            if (counting_screen != null)
            {
                counting_screen.setBackground(tourCount.getBackground());
            }
            count_areaLH = (LinearLayout) findViewById(R.id.countCountLayoutLH);
            notes_areaLH = (LinearLayout) findViewById(R.id.countNotesLayoutLH);
        }
        else
        {
            ScrollView counting_screen = (ScrollView) findViewById(R.id.countingScreen);
            if (counting_screen != null)
            {
                counting_screen.setBackground(tourCount.getBackground());
            }
            count_area = (LinearLayout) findViewById(R.id.countCountLayout);
            notes_area = (LinearLayout) findViewById(R.id.countNotesLayout);
        }

        if (awakePref)
        {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // check for API-Level >= 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            PowerManager mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (mPowerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK))
            {
                mProximityWakeLock = mPowerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "WAKE LOCK");
            }
            enableProximitySensor();
        }

        // get parameters from CountingActivity
        Bundle extras = getIntent().getExtras();
        if (extras != null)
        {
            latitude = extras.getDouble("Latitude");
            longitude = extras.getDouble("Longitude");
            height = extras.getDouble("Height");
        }
        
    } // End of onCreate

    /*
     * So preferences can be loaded at the start, and also when a change is detected.
     */
    private void getPrefs()
    {
        awakePref = prefs.getBoolean("pref_awake", true);      // stay awake while counting
        brightPref = prefs.getBoolean("pref_bright", true);    // bright counting page
        sortPref = prefs.getString("pref_sort_sp", "none");    // sorted species list on counting page
        fontPref = prefs.getBoolean("pref_note_font", false);  // larger font for remarks
        lhandPref = prefs.getBoolean("pref_left_hand", false); // left-handed counting page
        buttonSoundPref = prefs.getBoolean("pref_button_sound", false);
        buttonAlertSound = prefs.getString("alert_button_sound", null);
        screenOrientL = prefs.getBoolean("screen_Orientation", false);
        metaPref = prefs.getBoolean("pref_metadata", false);   // use Reverse Geocoding
        emailString = prefs.getString("email_String", "");     // for reliable query of Nominatim service
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        prefs = TourCountApplication.getPrefs();
        prefs.registerOnSharedPreferenceChangeListener(this);
        getPrefs();

        // Set full brightness of screen
        if (brightPref)
        {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.screenBrightness = 1.0f;
            getWindow().setAttributes(params);
        }

        // check for API-Level >= 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            enableProximitySensor();
        }

        // Get LocationManager instance
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Request list with names of all providers
        List<String> providers = locationManager.getAllProviders();
        for (String name : providers)
        {
            LocationProvider lp = locationManager.getProvider(name);
            if (MyDebug.LOG)
            {
                Log.d(TAG, lp.getName() + " --- isProviderEnabled(): " + locationManager.isProviderEnabled(name));
                Log.d(TAG, "requiresCell(): " + lp.requiresCell());
                Log.d(TAG, "requiresNetwork(): " + lp.requiresNetwork());
                Log.d(TAG, "requiresSatellite(): " + lp.requiresSatellite());
            }
        }

        // Best possible provider
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        // criteria.setPowerRequirement(Criteria.POWER_HIGH);
        provider = locationManager.getBestProvider(criteria, true);
        if (MyDebug.LOG)
            Log.d(TAG, "Provider: " + provider);

        // Create LocationListener object
        locationListener = new LocationListener()
        {
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras)
            {
                if (MyDebug.LOG)
                    Log.d(TAG, "onStatusChanged()");
            }

            @Override
            public void onProviderEnabled(String provider)
            {
                if (MyDebug.LOG)
                    Log.d(TAG, "onProviderEnabled()");
            }

            @Override
            public void onProviderDisabled(String provider)
            {
                if (MyDebug.LOG)
                    Log.d(TAG, "onProviderDisabled()");
            }

            @Override
            public void onLocationChanged(Location location)
            {
                if (MyDebug.LOG)
                    Log.d(TAG, "onLocationChanged()");
                if (location != null)
                {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    height = location.getAltitude();
                    if (height != 0)
                        height = correctHeight(latitude, longitude, height);
                    uncertainty = location.getAccuracy();
                }
            }
        };

        // get location service
        try
        {
            locationManager.requestLocationUpdates(provider, 3000, 0, locationListener);
        } catch (Exception e)
        {
            // nothing
        }

        // get reverse geocoding (todo: 1st count missing geo info)
        if (metaPref &&  (latitude != 0 || longitude != 0))
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);

            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    RetrieveAddr getXML = new RetrieveAddr();
                    getXML.execute(new LatLong(latitude, longitude));
                }
            });
        }

        // clear any existing views
        if (lhandPref) // if left-handed counting page
        {
            count_areaLH.removeAllViews();
            notes_areaLH.removeAllViews();
        }
        else
        {
            count_area.removeAllViews();
            notes_area.removeAllViews();
        }

        // setup the data sources
        sectionDataSource.open();
        countDataSource.open();
        individualsDataSource.open();
        tempDataSource.open();

        // load the data
        // sections
        try
        {
            section = sectionDataSource.getSection();
        } catch (CursorIndexOutOfBoundsException e)
        {
            if (MyDebug.LOG)
                Log.e(TAG, "Problem loading section: " + e.toString());
            Toast.makeText(CountingActivity.this, getString(R.string.getHelp), Toast.LENGTH_LONG).show();
            finish();
        }

        //noinspection ConstantConditions
        getSupportActionBar().setTitle(section.name);

        // display list notes
        if (section.notes != null)
        {
            if (!section.notes.isEmpty())
            {
                if (lhandPref) // if left-handed counting page
                {
                    NotesWidget section_notes = new NotesWidget(this, null);
                    section_notes.setNotes(section.notes);
                    section_notes.setFont(fontPref);
                    notes_areaLH.addView(section_notes);
                }
                else
                {
                    NotesWidget section_notes = new NotesWidget(this, null);
                    section_notes.setNotes(section.notes);
                    section_notes.setFont(fontPref);
                    notes_area.addView(section_notes);
                }
            }
        }

        // display counts with notes and alerts
        if (lhandPref) // if left-handed counting page
        {
            countingWidgetsLH = new ArrayList<>();
        }
        else
        {
            countingWidgets = new ArrayList<>();
        }

        switch (sortPref)
        {
        case "names_alpha":
            counts = countDataSource.getAllSpeciesSrtName();
            break;
        case "codes":
            counts = countDataSource.getAllSpeciesSrtCode();
            break;
        default:
            counts = countDataSource.getAllSpecies();
            break;
        }

        // display all the counts by adding them to countCountLayout
        if (lhandPref) // if left-handed counting page
        {
            for (Count count : counts)
            {
                CountingWidgetLH widget = new CountingWidgetLH(this, null);
                widget.setCount(count);
                countingWidgetsLH.add(widget);
                count_areaLH.addView(widget);

                // add a species note widget if there are any notes
                if (isNotBlank(count.notes))
                {
                    NotesWidget count_notes = new NotesWidget(this, null);
                    count_notes.setNotes(count.notes);
                    count_notes.setFont(fontPref);
                    count_areaLH.addView(count_notes);
                }
            }
        }
        else
        {
            for (Count count : counts)
            {
                CountingWidget widget = new CountingWidget(this, null);
                widget.setCount(count);
                countingWidgets.add(widget);
                count_area.addView(widget);

                // add a species note widget if there are any notes
                if (isNotBlank(count.notes))
                {
                    NotesWidget count_notes = new NotesWidget(this, null);
                    count_notes.setNotes(count.notes);
                    count_notes.setFont(fontPref);
                    count_area.addView(count_notes);
                }
            }
        }

        temp = tempDataSource.getTemp();

        if (awakePref)
        {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Save sCountry, sPlz, sCity, sPlace to DB Section
        // (sLocality is saved in EditIndividualActivity)
        if(sCountry.length() > 0)
        {
            section.country = sCountry;
            sectionDataSource.updateEmptyCountry(section.id, section.country);
        }
        if(sPlz.length() > 0)
        {
            section.plz = sPlz;
            sectionDataSource.updateEmptyPlz(section.id, section.plz);
        }
        if(sCity.length() > 0)
        {
            section.city = sCity;
            sectionDataSource.updateEmptyCity(section.id, section.city);
        }
        if(sPlace.length() > 0)
        {
            section.place = sPlace;
            sectionDataSource.updateEmptyPlace(section.id, section.place);
        }
        
        // Save sLocality to DB Temp
        if(sLocality.length() > 0)
        {
            temp.temp_loc = sLocality;
            tempDataSource.saveTempLoc(temp);
        }
        
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        // check for API-Level >= 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            disableProximitySensor(true);
        }

        try
        {
            locationManager.removeUpdates(locationListener);
        } catch (Exception e)
        {
            // do nothing
        }

        // save the data
        saveData();

        // close the data sources
        sectionDataSource.close();
        countDataSource.close();
        individualsDataSource.close();
        tempDataSource.close();

        // N.B. a wakelock might not be held, e.g. if someone is using Cyanogenmod and
        // has denied wakelock permission to TourCount
        if (awakePref)
        {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

    }

    // Correct height with geoid offset from EarthGravitationalModel
    private double correctHeight(double latitude, double longitude, double gpsHeight)
    {
        double corrHeight;
        double nnHeight;

        EarthGravitationalModel gh = new EarthGravitationalModel();
        try
        {
            gh.load(this); // load the WGS84 correction coefficient table egm180.txt
        } catch (IOException e)
        {
            return 0;
        }

        // Calculate the offset between the ellipsoid and geoid
        try
        {
            corrHeight = gh.heightOffset(latitude, longitude, gpsHeight);
        } catch (Exception e)
        {
            return 0;
        }

        nnHeight = gpsHeight + corrHeight;
        return nnHeight;
    }

    /**************************************************
     * Zählerstände sichern
     */
    private void saveData()
    {

        Toast.makeText(CountingActivity.this, getString(R.string.sectSaving) + " " + section.name + "!", Toast.LENGTH_SHORT).show();
        for (Count count : counts)
        {
            countDataSource.saveCount(count);
        }
    }

    // Triggered by count up button
    // starts EditIndividualActivity
    public void countUp(View view)
    {
        buttonSound();
        int count_id = Integer.valueOf(view.getTag().toString());
        CountingWidget widget = getCountFromId(count_id);
        if (widget != null)
        {
            widget.countUp();
        }

        // check for API-Level >= 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            disableProximitySensor(true);
        }

        // Show coords latitude, longitude for current count
        // Toast.makeText(CountingActivity.this, "Latitude: " + latitude + "\nLongitude: " + longitude, Toast.LENGTH_SHORT).show();

        // append individual with its Id, coords, date and time
        String uncert; // uncertainty about position (m)

        // if (provider.equals("gps") && latitude != 0) 
        if (latitude != 0)
        {
            uncert = String.valueOf(uncertainty);
        }
        else
        {
            uncert = "0";
        }

        String name, datestamp, timestamp;
        //noinspection ConstantConditions
        name = widget.count.name;
        datestamp = getcurDate();
        timestamp = getcurTime();

        i_Id = individualsDataSource.saveIndividual(individualsDataSource.createIndividuals
            (count_id, name, latitude, longitude, height, uncert, datestamp, timestamp));

        // get edited info for individual and start EditIndividualActivity
        Intent intent = new Intent(CountingActivity.this, EditIndividualActivity.class);
        intent.putExtra("count_id", count_id);
        intent.putExtra("indiv_id", i_Id);
        intent.putExtra("SName", widget.count.name);
        intent.putExtra("Latitude", latitude);
        intent.putExtra("Longitude", longitude);
        intent.putExtra("Height", height);
        intent.putExtra("Locality", sLocality);
        startActivity(intent);
    }

    public void countUpLH(View view)
    {
        buttonSound();
        int count_id = Integer.valueOf(view.getTag().toString());
        CountingWidgetLH widget = getCountFromIdLH(count_id);
        if (widget != null)
        {
            widget.countUpLH();
        }

        // check for API-Level >= 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            disableProximitySensor(true);
        }

        // Show coords latitude, longitude for current count
        // Toast.makeText(CountingActivity.this, "Latitude: " + latitude + "\nLongitude: " + longitude, Toast.LENGTH_SHORT).show();

        // append individual with its Id, coords, date and time
        String uncert; // uncertainty about position (m)

        if (latitude != 0)
        {
            uncert = String.valueOf(uncertainty);
        }
        else
        {
            uncert = "0";
        }

        String name, datestamp, timestamp;
        //noinspection ConstantConditions
        name = widget.count.name;
        datestamp = getcurDate();
        timestamp = getcurTime();

        i_Id = individualsDataSource.saveIndividual(individualsDataSource.createIndividuals
            (count_id, name, latitude, longitude, height, uncert, datestamp, timestamp));

        // get edited info for individual and start EditIndividualActivity
        Intent intent = new Intent(CountingActivity.this, EditIndividualActivity.class);
        intent.putExtra("count_id", count_id);
        intent.putExtra("indiv_id", i_Id);
        intent.putExtra("SName", widget.count.name);
        intent.putExtra("Latitude", latitude);
        intent.putExtra("Longitude", longitude);
        intent.putExtra("Height", height);
        intent.putExtra("Locality", sLocality);
        startActivity(intent);
    }

    // Triggered by count down button
    // deletes last count
    public void countDown(View view)
    {
        buttonSound();
        int count_id = Integer.valueOf(view.getTag().toString());
        CountingWidget widget = getCountFromId(count_id);
        //noinspection ConstantConditions
        spec_name = widget.count.name; // set spec_name for toast in deleteIndividual
        spec_count = widget.count.count;
        if (spec_count > 0)
        {
            widget.countDown();
            i_Id = individualsDataSource.readLastIndividual(count_id);
            int icount = individualsDataSource.getIndividualCount(i_Id);
            if (i_Id > 0 && icount < 2)
            {
                deleteIndividual(i_Id);
                i_Id--;
                return;
            }
            if (i_Id > 0 && icount > 1)
            {
                int icount1 = icount -1;
                individualsDataSource.decreaseIndividual(i_Id, icount1);
            }
        }
    }

    // Triggered by count down button from left-hand view
    // deletes last count
    public void countDownLH(View view)
    {
        buttonSound();
        int count_id = Integer.valueOf(view.getTag().toString());
        CountingWidgetLH widget = getCountFromIdLH(count_id);
        //noinspection ConstantConditions
        spec_name = widget.count.name; // set spec_name for toast in deleteIndividual
        spec_count = widget.count.count;
        if (spec_count > 0)
        {
            widget.countDownLH();
            i_Id = individualsDataSource.readLastIndividual(count_id);
            int icount = individualsDataSource.getIndividualCount(i_Id);
            if (i_Id > 0 && icount < 2)
            {
                deleteIndividual(i_Id);
                i_Id--;
                return;
            }
            if (i_Id > 0 && icount > 1)
            {
                int icount1 = icount -1;
                individualsDataSource.decreaseIndividual(i_Id, icount1);
            }
        }
    }

    /*
     * Get a counting widget (with reference to the associated count) from the list of widgets.
     */
    private CountingWidget getCountFromId(int id)
    {
        for (CountingWidget widget : countingWidgets)
        {
            if (widget.count.id == id)
            {
                return widget;
            }
        }
        return null;
    }

    /*
     * Get a left-handed counting widget (with references to the
     * associated count) from the list of widgets.
     */
    private CountingWidgetLH getCountFromIdLH(int id)
    {
        for (CountingWidgetLH widget : countingWidgetsLH)
        {
            if (widget.count.id == id)
            {
                return widget;
            }
        }
        return null;
    }

    // delete individual for count_id
    private void deleteIndividual(int id)
    {
        // Toast.makeText(CountingActivity.this, getString(R.string.indivdel1) + spec_name, Toast.LENGTH_SHORT).show();
        System.out.println(getString(R.string.indivdel) + " " + id);
        individualsDataSource.deleteIndividualById(id);
    }

    // Date for date_stamp
    @SuppressLint("SimpleDateFormat")
    private String getcurDate()
    {
        Date date = new Date();
        DateFormat dform;
        String lng = Locale.getDefault().toString().substring(0, 2);

        if (lng.equals("de"))
        {
            dform = new SimpleDateFormat("dd.MM.yyyy");
        }
        else
        {
            dform = new SimpleDateFormat("yyyy-MM-dd");
        }
        return dform.format(date);
    }

    // Date for time_stamp
    private String getcurTime()
    {
        Date date = new Date();
        @SuppressLint("SimpleDateFormat")
        DateFormat dform = new SimpleDateFormat("HH:mm");
        return dform.format(date);
    }

    // Edit count options
    public void edit(View view)
    {
        // check for API-Level >= 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            disableProximitySensor(true);
        }

        int count_id = Integer.valueOf(view.getTag().toString());
        Intent intent = new Intent(CountingActivity.this, CountOptionsActivity.class);
        intent.putExtra("count_id", count_id);
        startActivity(intent);
    }

    private void buttonSound()
    {
        if (buttonSoundPref)
        {
            try
            {
                Uri notification;
                if (isNotBlank(buttonAlertSound) && buttonAlertSound != null)
                {
                    notification = Uri.parse(buttonAlertSound);
                }
                else
                {
                    notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }
                Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                r.play();
            } catch (Exception e)
            {
                if (MyDebug.LOG)
                    Log.e(TAG, "could not play botton sound.", e);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.counting, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.menuEditSection)
        {
            // check for API-Level >= 21
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                disableProximitySensor(true);
            }

            Intent intent = new Intent(CountingActivity.this, EditSectionActivity.class);
            startActivity(intent);
            return true;
        }
        else if (id == R.id.action_share)
        {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, section.notes);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, section.name);
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.send_to)));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void enableProximitySensor()
    {
        if (mProximityWakeLock == null)
        {
            return;
        }

        if (!mProximityWakeLock.isHeld())
        {
            mProximityWakeLock.acquire();
        }
    }

    @SuppressLint("NewApi")
    private void disableProximitySensor(boolean waitForFarState)
    {
        if (mProximityWakeLock == null)
        {
            return;
        }
        if (mProximityWakeLock.isHeld())
        {
            int flags = waitForFarState ? PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY : 0;
            mProximityWakeLock.release(flags);
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
    {
        getPrefs();
    }

    /*********************************************************
     * Get address info from Reverse Geocoder of OpenStreetMap
     */
    private class RetrieveAddr extends AsyncTask<LatLong, Void, String>
    {
        URL url;
        String xmlString;
        String urlString = "https://nominatim.openstreetmap.org/reverse?email=" + emailString + "&format=xml&lat="
            + Double.toString(latitude) + "&lon=" + Double.toString(longitude) + "&zoom=18&addressdetails=1";

        @Override
        protected String doInBackground(LatLong... params)
        {
                try
                {
                    url = new URL(urlString);
                    if (MyDebug.LOG)
                        Log.d(TAG, "urlString: " + urlString); // Log url

                    //HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection(); // https-version?
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setReadTimeout(10000);
                    urlConnection.setConnectTimeout(15000);
                    urlConnection.setRequestMethod("GET");
                    urlConnection.setDoInput(true);
                    urlConnection.connect();

                    int status = urlConnection.getResponseCode();
                    if (status >= 400) // Error
                    {
                        return "";
                    }

                    // get the XML from input stream
                    InputStream iStream = urlConnection.getInputStream();

                    xmlString = convertStreamToString(iStream);
                    if (MyDebug.LOG)
                        Log.d(TAG, "xmlString: " + xmlString); // Log content of url


                } catch (IOException e)
                {
                    if (MyDebug.LOG)
                        Log.e(TAG, "Problem with address handling: " + e.toString());
                    xmlString = "";
                    return xmlString;
                }
            return xmlString;
        }

        // Write DB fields or textview fields
        protected void onPostExecute(String xmlString)
        {
            super.onPostExecute(xmlString);

            // parse the XML content 
            if (xmlString.contains("<addressparts>"))
            {
                int sstart = xmlString.indexOf("<addressparts>") + 14;
                int send = xmlString.indexOf("</addressparts>");
                xmlString = xmlString.substring(sstart, send);
                if (MyDebug.LOG)
                    Log.d(TAG, "<addressparts>: " + xmlString);
                
                StringBuilder msg = new StringBuilder();
                StringBuilder locality = new StringBuilder();
                StringBuilder plz = new StringBuilder();
                StringBuilder city = new StringBuilder();
                StringBuilder place = new StringBuilder();
                StringBuilder country = new StringBuilder();
                
                // 1. locality with road, street and suburb
                if (xmlString.contains("<road>"))
                {
                    sstart = xmlString.indexOf("<road>") + 6;
                    send = xmlString.indexOf("</road>");
                    String road = xmlString.substring(sstart, send);
                    locality.append(road);
                }

                if (xmlString.contains("<street>"))
                {
                    sstart = xmlString.indexOf("<street>") + 8;
                    send = xmlString.indexOf("</street>");
                    String street = xmlString.substring(sstart, send);
                    locality.append(street);
                }
                if (!locality.toString().equals("") && xmlString.contains("<suburb>"))
                    locality.append(", ");

                if (xmlString.contains("<suburb>"))
                {
                    sstart = xmlString.indexOf("<suburb>") + 8;
                    send = xmlString.indexOf("</suburb>");
                    String suburb = xmlString.substring(sstart, send);
                    locality.append(suburb);
                }
                sLocality = locality.toString();

                // 2. place with city_district and village
                if (xmlString.contains("<city_district>"))
                {
                    sstart = xmlString.indexOf("<city_district>") + 15;
                    send = xmlString.indexOf("</city_district>");
                    String city_district = xmlString.substring(sstart, send);
                    place.append(city_district);
                }

                if (!place.toString().equals("") && xmlString.contains("<village>"))
                    locality.append(", ");

                if (xmlString.contains("<village>"))
                {
                    sstart = xmlString.indexOf("<village>") + 9;
                    send = xmlString.indexOf("</village>");
                    String village = xmlString.substring(sstart, send);
                    place.append(village);
                }
                sPlace = place.toString();

                // 3. plz
                if (xmlString.contains("<postcode>"))
                {
                    sstart = xmlString.indexOf("<postcode>") + 10;
                    send = xmlString.indexOf("</postcode>");
                    String postcode = xmlString.substring(sstart, send);
                    plz.append(postcode);
                }
                sPlz = plz.toString();
                
                // 4. city with city and town
                if (xmlString.contains("<city>"))
                {
                    sstart = xmlString.indexOf("<city>") + 6;
                    send = xmlString.indexOf("</city>");
                    String tcity = xmlString.substring(sstart, send);
                    city.append(tcity);
                }
                
                if (!city.toString().equals("") && xmlString.contains("<town>"))
                    locality.append(", ");

                if (xmlString.contains("<town>"))
                {
                    sstart = xmlString.indexOf("<town>") + 6;
                    send = xmlString.indexOf("</town>");
                    String town = xmlString.substring(sstart, send);
                    city.append(town);
                }
                sCity = city.toString();
                
                // 5. country 
                if (xmlString.contains("<country>"))
                {
                    sstart = xmlString.indexOf("<country>") + 9;
                    send = xmlString.indexOf("</country>");
                    String tcountry = xmlString.substring(sstart, send);
                    country.append(tcountry);
                }
                sCountry = country.toString();
            }
        }
        
        private String convertStreamToString(InputStream is)
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();

            String line;
            try
            {
                while ((line = reader.readLine()) != null)
                {
                    sb.append(line).append('\n');
                }
            } catch (IOException e)
            {
                if (MyDebug.LOG)
                    Log.e(TAG, "Problem converting Stream to String: " + e.toString());
            } finally
            {
                try
                {
                    is.close();
                } catch (IOException e)
                {
                    if (MyDebug.LOG)
                        Log.e(TAG, "Problem closing InputStream: " + e.toString());
                }
            }
            return sb.toString();
        }
    } // end of class RetrieveAddr

    // Interface for latitude, longitude
    class LatLong
    {
        double latitude, longitude;
  
        LatLong(double latitude, double longitude)
        {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /**
     * Checks if a CharSequence is whitespace, empty ("") or null
     * <p>
     * isBlank(null)      = true
     * isBlank("")        = true
     * isBlank(" ")       = true
     * isBlank("bob")     = false
     * isBlank("  bob  ") = false
     *
     * @param cs the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is null, empty or whitespace
     */
    private static boolean isBlank(final CharSequence cs)
    {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0)
        {
            return true;
        }
        for (int i = 0; i < strLen; i++)
        {
            if (!Character.isWhitespace(cs.charAt(i)))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a CharSequence is not empty (""), not null and not whitespace only.
     * <p>
     * isNotBlank(null)      = false
     * isNotBlank("")        = false
     * isNotBlank(" ")       = false
     * isNotBlank("bob")     = true
     * isNotBlank("  bob  ") = true
     *
     * @param cs the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is
     * not empty and not null and not whitespace
     */
    private static boolean isNotBlank(final CharSequence cs)
    {
        return !isBlank(cs);
    }

}
