/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2012 The CyanogenMod Project (Weather, Calendar)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.policy.impl;

import com.android.internal.R;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCard.State;
import com.android.internal.widget.DigitalClock;
import com.android.internal.widget.DigitalClockAlt;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.TransportControlView;
import com.android.internal.policy.impl.KeyguardUpdateMonitor.InfoCallbackImpl;
import com.android.internal.policy.impl.KeyguardUpdateMonitor.SimStateCallback;

import android.content.ContentResolver;
import android.content.Context;
import android.content.ContentUris;
import android.content.Intent;
import android.content.res.Resources;
//import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Typeface;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;
//import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
//import android.view.ViewGroup;
//import android.view.ViewGroup.MarginLayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.android.internal.R;
import com.android.internal.policy.impl.KeyguardUpdateMonitor.SimStateCallback;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCard.State;
import com.android.internal.util.weather.HttpRetriever;
import com.android.internal.util.weather.WeatherInfo;
import com.android.internal.util.weather.WeatherXmlParser;
import com.android.internal.util.weather.YahooPlaceFinder;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.TransportControlView;

import org.w3c.dom.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import libcore.util.MutableInt;

/***
 * Manages a number of views inside of LockScreen layouts. See below for a list of widgets
 *
 */
class KeyguardStatusViewManager implements OnClickListener {
    private static final boolean DEBUG = false;
    private static final String TAG = "KeyguardStatusView";

    public static final int LOCK_ICON = 0; // R.drawable.ic_lock_idle_lock;
    public static final int ALARM_ICON = R.drawable.ic_lock_idle_alarm;
    public static final int CHARGING_ICON = 0; //R.drawable.ic_lock_idle_charging;
    public static final int DISCHARGING_ICON = 0; // no icon used in ics+ currently
    public static final int BATTERY_LOW_ICON = 0; //R.drawable.ic_lock_idle_low_battery;
    public static final int BATTERY_ICON = 0; //insert a R.drawable icon if you want it to show up
    private static final long INSTRUCTION_RESET_DELAY = 2000; // time until instruction text resets

    private static final int INSTRUCTION_TEXT = 10;
    private static final int CARRIER_TEXT = 11;
    private static final int CARRIER_HELP_TEXT = 12;
    private static final int HELP_MESSAGE_TEXT = 13;
    private static final int OWNER_INFO = 14;
    private static final int BATTERY_INFO = 15;
    private static final int WEATHER_INFO = 16;
    private static final int CALENDAR_INFO = 17;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    public static final String EXTRA_CITY = "city";
    public static final String EXTRA_FORECAST_DATE = "forecast_date";
    public static final String EXTRA_CONDITION = "condition";
    public static final String EXTRA_TEMP = "temp";
    public static final String EXTRA_HUMIDITY = "humidity";
    public static final String EXTRA_WIND = "wind";
    public static final String EXTRA_LOW = "todays_low";
    public static final String EXTRA_HIGH = "todays_high";

    private static final String SYSTEM = "/system/fonts/";
    private static final String SYSTEM_FONT_TIME_LIGHT = SYSTEM + "AndroidClockMono-Light.ttf";

    private boolean mLockAlwaysBattery;

    private StatusMode mStatus;
    private String mDateFormatString;
    private TransientTextManager mTransientTextManager;

    // Views that this class controls.
    // NOTE: These may be null in some LockScreen screens and should protect from NPE
    private TextView mCarrierView;
    private TextView mDateView;
    private TextView mStatus1View;
    private TextView mOwnerInfoView;
    private TextView mAlarmStatusView;
//    private LinearLayout mDateLineView;
    private TransportControlView mTransportView;
    private RelativeLayout mWeatherPanel, mWeatherTempsPanel;
    private TextView mWeatherCity, mWeatherCondition, mWeatherLowHigh, mWeatherTemp, mWeatherUpdateTime;
    private ImageView mWeatherImage;
    private LinearLayout mCalendarPanel;
    private TextView mCalendarEventTitle, mCalendarEventDetails;
    private WeatherPanel mWeatherPanelView;
    private WeatherText mWeatherTextView;
    private ViewFlipper mCalendarView;

    private boolean mCalendarUsingColors = true;
    private ArrayList<EventBundle> mCalendarEvents = null;

    // Top-level container view for above views
    private View mContainer;

    // are we showing battery information?
    private boolean mShowingBatteryInfo = false;

    private Intent mWeatherInfoAOKP = null; // being tricky

    // last known plugged in state
    private boolean mPluggedIn = false;

    // last known battery level
    private int mBatteryLevel = 100;

    // always show battery status?
    private boolean mAlwaysShowBattery = false;

    // last known SIM state
    protected State mSimState;

    private LockPatternUtils mLockPatternUtils;
    private KeyguardUpdateMonitor mUpdateMonitor;
    private Button mEmergencyCallButton;
    private boolean mEmergencyButtonEnabledBecauseSimLocked;

    // Shadowed text values
    private CharSequence mCarrierText;
    private CharSequence mCarrierHelpText;
    private String mHelpMessageText;
    private String mInstructionText;
    private CharSequence mOwnerInfoText;
    private boolean mShowingStatus;
    private KeyguardScreenCallback mCallback;
    private final boolean mEmergencyCallButtonEnabledInScreen;
    private CharSequence mPlmn;
    private CharSequence mSpn;
    protected int mPhoneState;
    private DigitalClock mDigitalClock;
    private DigitalClockAlt mDigitalClockAlt;
    private boolean mCirclesLock;

    private static final Typeface sLightFont;

    static {
        sLightFont = Typeface.createFromFile(SYSTEM_FONT_TIME_LIGHT);
    }

    private class TransientTextManager {
        private TextView mTextView;
        private class Data {
            final int icon;
            final CharSequence text;
            Data(CharSequence t, int i) {
                text = t;
                icon = i;
            }
        };
        private ArrayList<Data> mMessages = new ArrayList<Data>(5);

        TransientTextManager(TextView textView) {
            mTextView = textView;
        }

        /* Show given message with icon for up to duration ms. Newer messages override older ones.
         * The most recent message with the longest duration is shown as messages expire until
         * nothing is left, in which case the text/icon is defined by a call to
         * getAltTextMessage() */
        void post(final CharSequence message, final int icon, long duration) {
            if (mTextView == null) {
                return;
            }
            mTextView.setText(message);
            mTextView.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
            final Data data = new Data(message, icon);
            mContainer.postDelayed(new Runnable() {
                public void run() {
                    mMessages.remove(data);
                    int last = mMessages.size() - 1;
                    final CharSequence lastText;
                    final int lastIcon;
                    if (last > 0) {
                        final Data oldData = mMessages.get(last);
                        lastText = oldData.text;
                        lastIcon = oldData.icon;
                    } else {
                        final MutableInt tmpIcon = new MutableInt(0);
                        lastText = getAltTextMessage(tmpIcon);
                        lastIcon = tmpIcon.value;
                    }
                    mTextView.setText(lastText);
                    mTextView.setCompoundDrawablesWithIntrinsicBounds(lastIcon, 0, 0, 0);
                }
            }, duration);
        }
    };

    /**
     *
     * @param view the containing view of all widgets
     * @param updateMonitor the update monitor to use
     * @param lockPatternUtils lock pattern util object
     * @param callback used to invoke emergency dialer
     * @param emergencyButtonEnabledInScreen whether emergency button is enabled by default
     */
    public KeyguardStatusViewManager(View view, KeyguardUpdateMonitor updateMonitor,
                LockPatternUtils lockPatternUtils, KeyguardScreenCallback callback,
                boolean emergencyButtonEnabledInScreen) {
        if (DEBUG) Log.v(TAG, "KeyguardStatusViewManager()");
        mContainer = view;
        mDateFormatString = getContext().getString(R.string.abbrev_wday_month_day_no_year);
        mLockPatternUtils = lockPatternUtils;
        mUpdateMonitor = updateMonitor;
        mCallback = callback;

        mCarrierView = (TextView) findViewById(R.id.carrier);
        mDateView = (TextView) findViewById(R.id.date);
        mStatus1View = (TextView) findViewById(R.id.status1);
        mAlarmStatusView = (TextView) findViewById(R.id.alarm_status);
        mOwnerInfoView = (TextView) findViewById(R.id.propertyOf);
//        mDateLineView = (LinearLayout) findViewById(R.id.date_line);
        mTransportView = (TransportControlView) findViewById(R.id.transport);
        mEmergencyCallButton = (Button) findViewById(R.id.emergencyCallButton);
        mEmergencyCallButtonEnabledInScreen = emergencyButtonEnabledInScreen;
        mDigitalClock = (DigitalClock) findViewById(R.id.time);
        mDigitalClockAlt = (DigitalClockAlt) findViewById(R.id.time_alt);
        mWeatherPanelView = (WeatherPanel) findViewById(R.id.weatherpanel);
        mWeatherTextView = (WeatherText) findViewById(R.id.weather);
        mCalendarView = (ViewFlipper) findViewById(R.id.calendar);

        mCirclesLock = Settings.System.getBoolean(getContext().getContentResolver(), Settings.System.USE_CIRCLES_LOCKSCREEN, false);

        // Weather panel
        mWeatherPanel = (RelativeLayout) findViewById(R.id.weather_panel);
        mWeatherCity = (TextView) findViewById(R.id.weather_city);
        mWeatherCondition = (TextView) findViewById(R.id.weather_condition);
        mWeatherImage = (ImageView) findViewById(R.id.weather_image);
        mWeatherTemp = (TextView) findViewById(R.id.weather_temp);
        mWeatherLowHigh = (TextView) findViewById(R.id.weather_low_high);
        mWeatherUpdateTime = (TextView) findViewById(R.id.update_time);
        mWeatherTempsPanel = (RelativeLayout) findViewById(R.id.weather_temps_panel);

        // Hide Weather panel view until we know we need to show it.
        if (mWeatherPanel != null) {
            mWeatherPanel.setVisibility(View.GONE);
            mWeatherPanel.setOnClickListener(this);
        }

        // Calendar panel
        mCalendarPanel = (LinearLayout) findViewById(R.id.calendar_panel);
        mCalendarEventTitle = (TextView) findViewById(R.id.calendar_event_title);
        mCalendarEventDetails = (TextView) findViewById(R.id.calendar_event_details);

        // Hide calendar panel view until we know we need to show it.
        if (mCalendarPanel != null) {
            mCalendarPanel.setVisibility(View.GONE);
        }

 if (mWeatherPanelView != null) {	
            mWeatherPanelView.setOnClickListener(mWeatherListener);	
        }

        // Hide transport control view until we know we need to show it.
        if (mTransportView != null) {
            mTransportView.setVisibility(View.GONE);
        }

        if (mEmergencyCallButton != null) {
            mEmergencyCallButton.setText(R.string.lockscreen_emergency_call);
            mEmergencyCallButton.setOnClickListener(this);
            mEmergencyCallButton.setFocusable(false); // touch only!
        }

        if (mDateView != null) {
            if (mCirclesLock) {
                mDateView.setTypeface(sLightFont);
            }
        }

        if (mAlarmStatusView != null) {
            if (mCirclesLock) {
                mAlarmStatusView.setTypeface(sLightFont);
            }
        }

        mTransientTextManager = new TransientTextManager(mCarrierView);

        mUpdateMonitor.registerInfoCallback(mInfoCallback);
        mUpdateMonitor.registerSimStateCallback(mSimStateCallback);

        resetStatusInfo();
        refreshDate();
        updateOwnerInfo();
        updateColors();
        refreshWeather();
        refreshCalendar();
//        if (mDigitalClock != null) {
//            updateClockAlign();
//        }

        // Required to get Marquee to work.
        final View scrollableViews[] = { mCarrierView, mDateView, mStatus1View, mOwnerInfoView,
//                mAlarmStatusView, mCalendarEventDetails, mWeatherCity, mWeatherCondition };
                mAlarmStatusView, mCalendarEventDetails, mWeatherCity, mWeatherCondition, mWeatherTextView, mCalendarView, mWeatherPanelView };
        for (View v : scrollableViews) {
            if (v != null) {
                v.setSelected(true);
            }
        }
    }

    /*
     * CyanogenMod Lock screen Weather related functionality
     */
    private static final String URL_YAHOO_API_WEATHER = "http://weather.yahooapis.com/forecastrss?w=%s&u=";
    private static WeatherInfo mWeatherInfo = new WeatherInfo();
    private static final int QUERY_WEATHER = 0;
    private static final int UPDATE_WEATHER = 1;
    private boolean mWeatherRefreshing;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case QUERY_WEATHER:
                Thread queryWeather = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        LocationManager locationManager = (LocationManager) getContext().
                                getSystemService(Context.LOCATION_SERVICE);
                        final ContentResolver resolver = getContext().getContentResolver();
                        boolean useCustomLoc = Settings.System.getInt(resolver,
                                Settings.System.WEATHER_USE_CUSTOM_LOCATION, 0) == 1;
                        String customLoc = Settings.System.getString(resolver,
                                    Settings.System.WEATHER_CUSTOM_LOCATION);
                        String woeid = null;

                        // custom location
                        if (customLoc != null && useCustomLoc) {
                            try {
                                woeid = YahooPlaceFinder.GeoCode(getContext().getApplicationContext(), customLoc);
                                if (DEBUG)
                                    Log.d(TAG, "Yahoo location code for " + customLoc + " is " + woeid);
                            } catch (Exception e) {
                                Log.e(TAG, "ERROR: Could not get Location code");
                                e.printStackTrace();
                            }
                        // network location
                        } else {
                            Criteria crit = new Criteria();
                            crit.setAccuracy(Criteria.ACCURACY_COARSE);
                            String bestProvider = locationManager.getBestProvider(crit, true);
                            Location loc = null;
                            if (bestProvider != null) {
                                loc = locationManager.getLastKnownLocation(bestProvider);
                            } else {
                                loc = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                            }
                            try {
                                woeid = YahooPlaceFinder.reverseGeoCode(getContext(), loc.getLatitude(),
                                        loc.getLongitude());
                                if (DEBUG)
                                    Log.d(TAG, "Yahoo location code for current geolocation is " + woeid);
                            } catch (Exception e) {
                                Log.e(TAG, "ERROR: Could not get Location code");
                                e.printStackTrace();
                            }
                        }
                        if (DEBUG) {
                            Log.d(TAG, "Location code is " + woeid);
                        }
                        WeatherInfo w = null;
                        if (woeid != null) {
                            try {
                                w = parseXml(getDocument(woeid));
                            } catch (Exception e) {
                            }
                        }
                        Message msg = Message.obtain();
                        msg.what = UPDATE_WEATHER;
                        msg.obj = w;
                        mHandler.sendMessage(msg);
                    }
                });
                mWeatherRefreshing = true;
                queryWeather.setPriority(Thread.MIN_PRIORITY);
                queryWeather.start();
                break;
            case UPDATE_WEATHER:
                WeatherInfo w = (WeatherInfo) msg.obj;
                if (w != null) {
                    mWeatherRefreshing = false;
                    setWeatherData(w);
                    mWeatherInfo = w;
                } else {
                    mWeatherRefreshing = false;
                    if (mWeatherInfo.temp.equals(WeatherInfo.NODATA)) {
                        setNoWeatherData();
                    } else {
                        setWeatherData(mWeatherInfo);
                    }
                }
                break;
            }
        }
    };

    /**
     * Reload the weather forecast
     */
    private void refreshWeather() {
        final ContentResolver resolver = getContext().getContentResolver();
        boolean showWeather = Settings.System.getInt(resolver,Settings.System.LOCKSCREEN_WEATHER_CM, 0) == 1;

        if (showWeather) {
            final long interval = Settings.System.getLong(resolver,
                    Settings.System.WEATHER_UPDATE_INTERVAL, 60); // Default to hourly
            boolean manualSync = (interval == 0);
            if (!manualSync && (((System.currentTimeMillis() - mWeatherInfo.last_sync) / 60000) >= interval)) {
                if (!mWeatherRefreshing) {
                    mHandler.sendEmptyMessage(QUERY_WEATHER);
                }
            } else if (manualSync && mWeatherInfo.last_sync == 0) {
                setNoWeatherData();
            } else {
                setWeatherData(mWeatherInfo);
            }
        } else {
            // Hide the Weather panel view
            if (mWeatherPanel != null) {
                mWeatherPanel.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Display the weather information
     * @param w
     */
    private void setWeatherData(WeatherInfo w) {
        final ContentResolver resolver = getContext().getContentResolver();
        final Resources res = getContext().getResources();
        boolean showLocation = Settings.System.getInt(resolver,
                Settings.System.WEATHER_SHOW_LOCATION_CM, 1) == 1;
        boolean showTimestamp = Settings.System.getInt(resolver,
                Settings.System.WEATHER_SHOW_TIMESTAMP, 1) == 1;
        boolean invertLowhigh = Settings.System.getInt(resolver,
                Settings.System.WEATHER_INVERT_LOWHIGH, 0) == 1;

        if (mWeatherPanel != null) {
            if (mWeatherImage != null) {
                String conditionCode = w.condition_code;
                String condition_filename = "weather_" + conditionCode;
                int resID = res.getIdentifier(condition_filename, "drawable",
                        getContext().getPackageName());

                if (DEBUG)
                    Log.d("Weather", "Condition:" + conditionCode + " ID:" + resID);

                if (resID != 0) {
                    mWeatherImage.setImageDrawable(res.getDrawable(resID));
                } else {
                    mWeatherImage.setImageResource(R.drawable.weather_na);
                }
            }
            if (mWeatherCity != null) {
                mWeatherCity.setText(w.city);
                mWeatherCity.setVisibility(showLocation ? View.VISIBLE : View.GONE);
            }
            if (mWeatherCondition != null && !mWeatherRefreshing) {
                mWeatherCondition.setText(w.condition);
                mWeatherCondition.setVisibility(View.VISIBLE);
            }
            if (mWeatherUpdateTime != null) {
                long now = System.currentTimeMillis();
                if (now - w.last_sync < 60000) {
                    mWeatherUpdateTime.setText(R.string.weather_last_sync_just_now);
                } else {
                    mWeatherUpdateTime.setText(DateUtils.getRelativeTimeSpanString(
                            w.last_sync, now, DateUtils.MINUTE_IN_MILLIS));
                }
                mWeatherUpdateTime.setVisibility(showTimestamp ? View.VISIBLE : View.GONE);
            }
            if (mWeatherTempsPanel != null && mWeatherTemp != null && mWeatherLowHigh != null) {
                mWeatherTemp.setText(w.temp);
                mWeatherLowHigh.setText(invertLowhigh ? w.high + " | " + w.low : w.low + " | " + w.high);
                mWeatherTempsPanel.setVisibility(View.VISIBLE);
            }

            // Show the Weather panel view
            mWeatherPanel.setVisibility(View.VISIBLE);
        }
    }

    /**
     * There is no data to display, display 'empty' fields and the
     * 'Tap to reload' message
     */
    private void setNoWeatherData() {

        if (mWeatherPanel != null) {
            if (mWeatherImage != null) {
                mWeatherImage.setImageResource(R.drawable.weather_na);
            }
            if (mWeatherCity != null) {
                mWeatherCity.setText(R.string.weather_no_data);
                mWeatherCity.setVisibility(View.VISIBLE);
            }
            if (mWeatherCondition != null && !mWeatherRefreshing) {
                mWeatherCondition.setText(R.string.weather_tap_to_refresh);
            }
            if (mWeatherUpdateTime != null) {
                mWeatherUpdateTime.setVisibility(View.GONE);
            }
            if (mWeatherTempsPanel != null ) {
                mWeatherTempsPanel.setVisibility(View.GONE);
            }

            // Show the Weather panel view
            mWeatherPanel.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Get the weather forecast XML document for a specific location
     * @param woeid
     * @return
     */
    private Document getDocument(String woeid) {
        try {
            boolean celcius = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.WEATHER_USE_METRIC, 1) == 1;
            String urlWithDegreeUnit;

            if (celcius) {
                urlWithDegreeUnit = URL_YAHOO_API_WEATHER + "c";
            } else {
                urlWithDegreeUnit = URL_YAHOO_API_WEATHER + "f";
            }

            return new HttpRetriever().getDocumentFromURL(String.format(urlWithDegreeUnit, woeid));
        } catch (IOException e) {
            Log.e(TAG, "Error querying Yahoo weather");
        }

        return null;
    }

    /**
     * Parse the weather XML document
     * @param wDoc
     * @return
     */
    private WeatherInfo parseXml(Document wDoc) {
        try {
            return new WeatherXmlParser(getContext()).parseWeatherResponse(wDoc);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Yahoo weather XML document");
            e.printStackTrace();
        }
        return null;
    }

    /*
     * CyanogenMod Lock screen Calendar related functionality
     */

    private void refreshCalendar() {
        if (mCalendarPanel != null) {
            final ContentResolver resolver = getContext().getContentResolver();
            String[] nextCalendar = null;
            boolean visible = false; // Assume we are not showing the view

            // Load the settings
            boolean lockCalendar = (Settings.System.getInt(resolver,
                    Settings.System.LOCKSCREEN_CALENDAR_CM, 0) == 1);
            String[] calendars = parseStoredValue(Settings.System.getString(
                    resolver, Settings.System.LOCKSCREEN_CALENDARS));
            boolean lockCalendarRemindersOnly = (Settings.System.getInt(resolver,
                    Settings.System.LOCKSCREEN_CALENDAR_REMINDERS_ONLY, 0) == 1);
            long lockCalendarLookahead = Settings.System.getLong(resolver,
                    Settings.System.LOCKSCREEN_CALENDAR_LOOKAHEAD, 10800000);

            if (lockCalendar) {
                nextCalendar = mLockPatternUtils.getNextCalendarAlarm(lockCalendarLookahead,
                        calendars, lockCalendarRemindersOnly);
                if (nextCalendar[0] != null && mCalendarEventTitle != null) {
                    mCalendarEventTitle.setText(nextCalendar[0].toString());
                    if (nextCalendar[1] != null && mCalendarEventDetails != null) {
                        mCalendarEventDetails.setText(nextCalendar[1]);
                    }
                    visible = true;
                }
            }

           mCalendarPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Split the MultiSelectListPreference string based on a separator of ',' and
     * stripping off the start [ and the end ]
     * @param val
     * @return
     */
    private static String[] parseStoredValue(String val) {
        if (val == null || val.isEmpty())
            return null;
        else {
            // Strip off the start [ and the end ] before splitting
            val = val.substring(1, val.length() -1);
            return (val.split(","));
        }
    }

    private boolean inWidgetMode() {
        return mTransportView != null && mTransportView.getVisibility() == View.VISIBLE;
    }

    void setInstructionText(String string) {
        mInstructionText = string;
        update(INSTRUCTION_TEXT, string);
    }

    void setCarrierText(CharSequence string) {
        mCarrierText = string;
        update(CARRIER_TEXT, string);
    }

    void setOwnerInfo(CharSequence string) {
        mOwnerInfoText = string;
        update(OWNER_INFO, string);
    }

    /**
     * Sets the carrier help text message, if view is present. Carrier help text messages are
     * typically for help dealing with SIMS and connectivity.
     *
     * @param resId resource id of the message
     */
    public void setCarrierHelpText(int resId) {
        mCarrierHelpText = getText(resId);
        update(CARRIER_HELP_TEXT, mCarrierHelpText);
    }

    private CharSequence getText(int resId) {
        return resId == 0 ? null : getContext().getText(resId);
    }

    /**
     * Unlock help message.  This is typically for help with unlock widgets, e.g. "wrong password"
     * or "try again."
     *
     * @param textResId
     * @param lockIcon
     */
    public void setHelpMessage(int textResId, int lockIcon) {
        final CharSequence tmp = getText(textResId);
        mHelpMessageText = tmp == null ? null : tmp.toString();
        update(HELP_MESSAGE_TEXT, mHelpMessageText);
    }

    private void update(int what, CharSequence string) {
        if (inWidgetMode()) {
            if (DEBUG) Log.v(TAG, "inWidgetMode() is true");
            // Use Transient text for messages shown while widget is shown.
            switch (what) {
                case INSTRUCTION_TEXT:
                case CARRIER_HELP_TEXT:
                case HELP_MESSAGE_TEXT:
                case BATTERY_INFO:
                    mTransientTextManager.post(string, 0, INSTRUCTION_RESET_DELAY);
                    break;

                case WEATHER_INFO:
                    updateWeatherInfoAOKP();
                    break;
                case CALENDAR_INFO:
                    updateCalendar();
                    updateColors();
                    break;
                case OWNER_INFO:
                case CARRIER_TEXT:
                default:
                    if (DEBUG) Log.w(TAG, "Not showing message id " + what + ", str=" + string);
            }
        } else {
//            updateStatusLines(mShowingStatus);
            // dont update everything 7 times, filter based on "what"
            switch (what) {
                case INSTRUCTION_TEXT:
                case CARRIER_HELP_TEXT:
                case HELP_MESSAGE_TEXT:
                case BATTERY_INFO:
                    updateStatus1();
                    break;
                case OWNER_INFO:
                    updateOwnerInfo();
                    break;
                case CARRIER_TEXT:
                    updateCarrierText();
                    break;
                case WEATHER_INFO:
                    updateWeatherInfoAOKP();
                    break;
                case CALENDAR_INFO:
                    updateCalendar();
                    updateColors();
                    break;
                default:
                    ;
            }
        }
    }

    public void onPause() {
        if (DEBUG) Log.v(TAG, "onPause()");
        mUpdateMonitor.removeCallback(mInfoCallback);
        mUpdateMonitor.removeCallback(mSimStateCallback);
    }

    /** {@inheritDoc} */
    public void onResume() {
        if (DEBUG) Log.v(TAG, "onResume()");

        // First update the clock, if present.
        if (mDigitalClock != null) {
            mDigitalClock.updateTime();
//            updateClockAlign();
        }

        if (mDigitalClockAlt != null) {
            mDigitalClockAlt.updateTime();
        }

        refreshWeather();

        mUpdateMonitor.registerInfoCallback(mInfoCallback);
        mUpdateMonitor.registerSimStateCallback(mSimStateCallback);
        resetStatusInfo();
        // Issue the biometric unlock failure message in a centralized place
        // TODO: we either need to make the Face Unlock multiple failures string a more general
        // 'biometric unlock' or have each biometric unlock handle this on their own.
        if (mUpdateMonitor.getMaxBiometricUnlockAttemptsReached()) {
            setInstructionText(getContext().getString(R.string.faceunlock_multiple_failures));
        }
    }

    void resetStatusInfo() {
        mInstructionText = null;
        mShowingBatteryInfo = mUpdateMonitor.shouldShowBatteryInfo();
        mPluggedIn = mUpdateMonitor.isDevicePluggedIn();
        mBatteryLevel = mUpdateMonitor.getBatteryLevel();
        mAlwaysShowBattery = KeyguardUpdateMonitor.shouldAlwaysShowBatteryInfo(getContext());
	mWeatherInfoAOKP = mUpdateMonitor.getWeather();
        updateStatusLines(true);
    }

    /**
     * Update the status lines based on these rules:
     * AlarmStatus: Alarm state always gets it's own line.
     * Status1 is shared between help, battery status and generic unlock instructions,
     * prioritized in that order.
     * @param showStatusLines status lines are shown if true
     */
    void updateStatusLines(boolean showStatusLines) {
        if (DEBUG) Log.v(TAG, "updateStatusLines(" + showStatusLines + ")");
        mShowingStatus = showStatusLines;
        updateAlarmInfo();
        updateWeatherInfoAOKP();
        updateCalendar();
        updateOwnerInfo();
        updateStatus1();
        updateCarrierText();
        updateColors();
    }

    private void updateAlarmInfo() {
        if (mAlarmStatusView != null) {
            String nextAlarm = mLockPatternUtils.getNextAlarm();
            boolean showAlarm = mShowingStatus && !TextUtils.isEmpty(nextAlarm);
            mAlarmStatusView.setText(nextAlarm);
            mAlarmStatusView.setCompoundDrawablesWithIntrinsicBounds(ALARM_ICON, 0, 0, 0);
            mAlarmStatusView.setVisibility(showAlarm ? View.VISIBLE : View.GONE);
        }
    }

    private void updateOwnerInfo() {
        final ContentResolver res = getContext().getContentResolver();
        final boolean ownerInfoEnabled = Settings.Secure.getInt(res,
                Settings.Secure.LOCK_SCREEN_OWNER_INFO_ENABLED, 1) != 0;
        mOwnerInfoText = ownerInfoEnabled ?
                Settings.Secure.getString(res, Settings.Secure.LOCK_SCREEN_OWNER_INFO) : null;
        if (mOwnerInfoView != null) {
            mOwnerInfoView.setText(mOwnerInfoText);
            mOwnerInfoView.setVisibility(TextUtils.isEmpty(mOwnerInfoText) ? View.GONE:View.VISIBLE);
        }
    }

    private void updateWeatherInfoAOKP() {
        final ContentResolver res = getContext().getContentResolver();
        final boolean weatherInfoEnabled = (Settings.System.getBoolean(res,
                Settings.System.LOCKSCREEN_WEATHER, false)
                && (Settings.System.getBoolean(res, Settings.System.USE_WEATHER, false))
                && mShowingStatus);

        final boolean weatherLocationEnabled = Settings.System.getBoolean(res,
                Settings.System.WEATHER_SHOW_LOCATION, false);

        final int weatherInfoType = Settings.System.getInt(res,
                Settings.System.LOCKSCREEN_WEATHER_TYPE, 0);

        if (weatherInfoEnabled) {
            if (weatherInfoType == 0) {
                if (mWeatherPanelView != null && mWeatherInfoAOKP != null) {
                        mWeatherPanelView.updateWeather(mWeatherInfoAOKP);
                        mWeatherPanelView.setVisibility(weatherInfoEnabled ? View.VISIBLE : View.GONE);
                }
                if (mWeatherTextView != null) {
                    mWeatherTextView.setVisibility(View.GONE);
                }
            } else {
                if (mWeatherTextView != null && mWeatherInfoAOKP != null) {
                    mWeatherTextView.updateWeather(mWeatherInfoAOKP);
                    mWeatherTextView.setVisibility(weatherInfoEnabled ? View.VISIBLE : View.GONE);
                }
                if (mWeatherPanelView != null) {
                    mWeatherPanelView.setVisibility(View.GONE);
                }
            }
        } else {
            if (mWeatherPanelView != null) {
                mWeatherPanelView.setVisibility(View.GONE);
            }
            if (mWeatherTextView != null) {
                mWeatherTextView.setVisibility(View.GONE);
            }
        }
    }

    private View.OnClickListener mWeatherListener = new View.OnClickListener() {
        public void onClick(View v) {
             Intent weatherintent = new Intent("com.aokp.romcontrol.INTENT_WEATHER_REQUEST");
             weatherintent.putExtra("com.aokp.romcontrol.INTENT_EXTRA_TYPE", "updateweather");
             weatherintent.putExtra("com.aokp.romcontrol.INTENT_EXTRA_ISMANUAL", true);
             v.getContext().sendBroadcast(weatherintent);
             Toast.makeText(getContext(), R.string.update_weather, Toast.LENGTH_SHORT).show();
        }
    };

    private void updateCalendar() {
        ContentResolver resolver = getContext().getContentResolver();
        String calendarSources = Settings.System.getString(resolver,
                Settings.System.LOCKSCREEN_CALENDAR_SOURCES);
        if (calendarSources == null)
            return;
        boolean multipleEventsEnabled = (Settings.System.getBoolean(resolver,
                Settings.System.LOCKSCREEN_CALENDAR_FLIP, false));
        int interval = Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_CALENDAR_INTERVAL, 2500);
        long range = Settings.System.getLong(resolver,
                Settings.System.LOCKSCREEN_CALENDAR_RANGE, 86400000);
        boolean hideOnGoing = (Settings.System.getBoolean(resolver,
                Settings.System.LOCKSCREEN_CALENDAR_HIDE_ONGOING, false));
        mCalendarUsingColors = (Settings.System.getBoolean(resolver,
                Settings.System.LOCKSCREEN_CALENDAR_USE_COLORS, false));
        boolean calendarEventsEnabled = (Settings.System.getBoolean(resolver,
                Settings.System.LOCKSCREEN_CALENDAR, false) && mShowingStatus
                && !calendarSources.isEmpty());

        if (calendarEventsEnabled) {
            if (mCalendarEvents == null) {
                try {
                    getCalendarEvents(resolver, calendarSources, multipleEventsEnabled, hideOnGoing, range);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (!calendarEventsEnabled && mCalendarView != null) {
            mCalendarView.setVisibility(View.GONE);
            return;
        } else {
            return;
        }

        if (mCalendarView != null) {
            mCalendarView.removeAllViews();
            Log.d(TAG, "we have " + String.valueOf(mCalendarEvents.size()) + " event(s)");
            int dateWidth = (int) (findViewById(mCirclesLock ? R.id.time_alt : R.id.time).getWidth() * 1.2);

            for (EventBundle e : mCalendarEvents) {
                String title = e.title + (e.dayString.isEmpty() ? " " : ", ");
                String details = e.dayString
                        + ((e.allDay) ? "" : " " + DateFormat.format(
                                DateFormat.is24HourFormat(getContext()) ? "kk:mm"
                                        : "hh:mm a", e.begin).toString())
                        + (!e.location.isEmpty() ? " (" + e.location + ")" : "");
                CalendarEntry cEntry = new CalendarEntry(getContext(), title, details, dateWidth);
                if (mCalendarUsingColors) {
                    cEntry.setColor(e.color);
                }
                mCalendarView.addView(cEntry);
                mCalendarView.requestLayout();
            }
            if (!multipleEventsEnabled || mCalendarEvents.size() <= 1) {
                mCalendarView.stopFlipping();
            } else {
                mCalendarView.setFlipInterval(interval);
                mCalendarView.startFlipping();
            }
            mCalendarView.setVisibility(View.VISIBLE);
        }
    }
/*
    private void updateClockAlign() {
        final Configuration config = getContext().getResources().getConfiguration();
        // No alignment on landscape.
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return;
        }

        final int clockAlign = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.LOCKSCREEN_CLOCK_ALIGN, 2);
        int margin = (int) Math.round(getContext().getResources().getDimension(
                R.dimen.keyguard_lockscreen_status_line_font_right_margin));

        // Adjust for each layout
        if (config.screenWidthDp >= 600) { // sw600dp
            margin = 0;
        }

        int leftMargin = 0, rightMargin = 0;
        int gravity = Gravity.RIGHT;

        switch (clockAlign) {
        case 0:
            gravity = Gravity.LEFT;
            leftMargin = margin;
            break;
        case 1:
            gravity = Gravity.CENTER;
            break;
        case 2:
            rightMargin = margin;
            break;
        }

        mDigitalClock.setGravity(gravity);
        setSpecificMargins(mDigitalClock, leftMargin, -1, rightMargin, -1);

        if (mDateLineView != null) {
            mDateLineView.setGravity(gravity);
            setSpecificMargins(mDateLineView, leftMargin, -1, rightMargin, -1);
        }
        if (mStatus1View != null) {
            mStatus1View.setGravity(gravity);
            setSpecificMargins(mStatus1View, leftMargin, -1, rightMargin, -1);
        }
    }

    private void setSpecificMargins(View view, int left, int top, int right,
            int bottom) {
        MarginLayoutParams params = (MarginLayoutParams) view.getLayoutParams();
        if (left != -1) params.leftMargin = left;
        if (top != -1) params.topMargin = top;
        if (right != -1) params.rightMargin = right;
        if (bottom != -1) params.bottomMargin = bottom;
        view.setLayoutParams(params);
    }
*/
    private void updateStatus1() {
        if (mStatus1View != null) {
            MutableInt icon = new MutableInt(0);
            CharSequence string = getPriorityTextMessage(icon);
            mStatus1View.setText(string);
            mStatus1View.setCompoundDrawablesWithIntrinsicBounds(icon.value, 0, 0, 0);
//            mStatus1View.setVisibility(mShowingStatus ? View.VISIBLE : View.INVISIBLE);
            mStatus1View.setVisibility(mShowingStatus ? View.VISIBLE : View.GONE);
        }
    }

    private void updateCarrierText() {
        if (!inWidgetMode() && mCarrierView != null) {
            mCarrierView.setText(mCarrierText);
        }
    }

    private CharSequence getAltTextMessage(MutableInt icon) {
        // If we have replaced the status area with a single widget, then this code
        // prioritizes what to show in that space when all transient messages are gone.
        CharSequence string = null;
//        if (mShowingBatteryInfo) {
        mLockAlwaysBattery = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.LOCKSCREEN_BATTERY, 0) == 1;
        if (mShowingBatteryInfo || mLockAlwaysBattery) {
            // Battery status
            if (mPluggedIn) {
                // Charging or charged
                if (mUpdateMonitor.isDeviceCharged()) {
                    string = getContext().getString(R.string.lockscreen_charged);
                } else {
                    string = getContext().getString(R.string.lockscreen_plugged_in, mBatteryLevel);
                }
                icon.value = CHARGING_ICON;
            } else if (mBatteryLevel < KeyguardUpdateMonitor.LOW_BATTERY_THRESHOLD) {
                // Battery is low
                string = getContext().getString(R.string.lockscreen_low_battery, mBatteryLevel);
                icon.value = BATTERY_LOW_ICON;
                if (mLockAlwaysBattery) {
                    // Show battery at low percent
                    string = getContext().getString(R.string.lockscreen_always_low_battery, mBatteryLevel);
                            icon.value = BATTERY_LOW_ICON;
                }
            } else if (mLockAlwaysBattery) {
                // Always show battery
                string = getContext().getString(R.string.lockscreen_always_battery, mBatteryLevel);
                icon.value = BATTERY_ICON;
            } else if (mAlwaysShowBattery) {
                // Discharging
                string = getContext().getString(R.string.lockscreen_discharging, mBatteryLevel);
                icon.value = DISCHARGING_ICON;
            }
        } else {
            string = mCarrierText;
        }
        return string;
    }

    private CharSequence getPriorityTextMessage(MutableInt icon) {
        CharSequence string = null;
        mLockAlwaysBattery = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.LOCKSCREEN_BATTERY, 0) == 1;
        if (!TextUtils.isEmpty(mInstructionText)) {
            // Instructions only
            string = mInstructionText;
            icon.value = LOCK_ICON;
//        } else if (mShowingBatteryInfo) {
        } else if (mShowingBatteryInfo || mLockAlwaysBattery) {
            // Battery status
            if (mPluggedIn) {
                // Charging or charged
                if (mUpdateMonitor.isDeviceCharged()) {
                    string = getContext().getString(R.string.lockscreen_charged);
                } else {
                    string = getContext().getString(R.string.lockscreen_plugged_in, mBatteryLevel);
                }
                icon.value = CHARGING_ICON;
            } else if (mBatteryLevel < KeyguardUpdateMonitor.LOW_BATTERY_THRESHOLD) {
                // Battery is low
                string = getContext().getString(R.string.lockscreen_low_battery, mBatteryLevel);
                icon.value = BATTERY_LOW_ICON;
                if (mLockAlwaysBattery) {
                    // Show battery at low percent
                    string = getContext().getString(R.string.lockscreen_always_low_battery, mBatteryLevel);
                            icon.value = BATTERY_LOW_ICON;
                }
            } else if (mLockAlwaysBattery) {
                // Always show battery
                string = getContext().getString(R.string.lockscreen_always_battery, mBatteryLevel);
                icon.value = BATTERY_ICON;
            } else if (mAlwaysShowBattery) {
                // Discharging
                string = getContext().getString(R.string.lockscreen_discharging, mBatteryLevel);
                icon.value = DISCHARGING_ICON;
            }
        } else if (!inWidgetMode() && mOwnerInfoView == null && mOwnerInfoText != null) {
            // OwnerInfo shows in status if we don't have a dedicated widget
            string = mOwnerInfoText;
        }
        return string;
    }

    void refreshDate() {
        if (mDateView != null) {
            mDateView.setText(DateFormat.format(mDateFormatString, new Date()));
        }
    }

    /**
     * Determine the current status of the lock screen given the sim state and other stuff.
     */
    public StatusMode getStatusForIccState(IccCard.State simState) {
        // Since reading the SIM may take a while, we assume it is present until told otherwise.
        if (simState == null) {
            return StatusMode.Normal;
        }

        final boolean missingAndNotProvisioned = (!mUpdateMonitor.isDeviceProvisioned()
                && (simState == IccCard.State.ABSENT || simState == IccCard.State.PERM_DISABLED));

        // Assume we're NETWORK_LOCKED if not provisioned
        simState = missingAndNotProvisioned ? State.NETWORK_LOCKED : simState;
        switch (simState) {
            case ABSENT:
                return StatusMode.SimMissing;
            case NETWORK_LOCKED:
                return StatusMode.SimMissingLocked;
            case NOT_READY:
                return StatusMode.SimMissing;
            case PIN_REQUIRED:
                return StatusMode.SimLocked;
            case PUK_REQUIRED:
                return StatusMode.SimPukLocked;
            case READY:
                return StatusMode.Normal;
            case PERM_DISABLED:
                return StatusMode.SimPermDisabled;
            case UNKNOWN:
                return StatusMode.SimMissing;
        }
        return StatusMode.SimMissing;
    }

    private Context getContext() {
        return mContainer.getContext();
    }

    /**
     * Update carrier text, carrier help and emergency button to match the current status based
     * on SIM state.
     *
     * @param simState
     */
    private void updateCarrierStateWithSimStatus(State simState) {
        if (DEBUG) Log.d(TAG, "updateCarrierTextWithSimStatus(), simState = " + simState);

        CharSequence carrierText = null;
        int carrierHelpTextId = 0;
        mEmergencyButtonEnabledBecauseSimLocked = false;
        mStatus = getStatusForIccState(simState);
        mSimState = simState;
        switch (mStatus) {
            case Normal:
                carrierText = makeCarierString(mPlmn, mSpn);
                break;

            case NetworkLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.lockscreen_network_locked_message),
                        mPlmn);
                carrierHelpTextId = R.string.lockscreen_instructions_when_pattern_disabled;
                break;

            case SimMissing:
                // Shows "No SIM card | Emergency calls only" on devices that are voice-capable.
                // This depends on mPlmn containing the text "Emergency calls only" when the radio
                // has some connectivity. Otherwise, it should be null or empty and just show
                // "No SIM card"
                carrierText =  makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.lockscreen_missing_sim_message_short),
                        mPlmn);
                carrierHelpTextId = R.string.lockscreen_missing_sim_instructions_long;
                break;

            case SimPermDisabled:
                carrierText = getContext().getText(
                        R.string.lockscreen_permanent_disabled_sim_message_short);
                carrierHelpTextId = R.string.lockscreen_permanent_disabled_sim_instructions;
                mEmergencyButtonEnabledBecauseSimLocked = true;
                break;

            case SimMissingLocked:
                carrierText =  makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.lockscreen_missing_sim_message_short),
                        mPlmn);
                carrierHelpTextId = R.string.lockscreen_missing_sim_instructions;
                mEmergencyButtonEnabledBecauseSimLocked = true;
                break;

            case SimLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.lockscreen_sim_locked_message),
                        mPlmn);
                mEmergencyButtonEnabledBecauseSimLocked = true;
                break;

            case SimPukLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.lockscreen_sim_puk_locked_message),
                        mPlmn);
                if (!mLockPatternUtils.isPukUnlockScreenEnable()) {
                    // This means we're showing the PUK unlock screen
                    mEmergencyButtonEnabledBecauseSimLocked = true;
                }
                break;
        }

//        setCarrierText(carrierText);
        String customLabel;
        customLabel = Settings.System.getString(getContext().getContentResolver(),
            Settings.System.CUSTOM_CARRIER_LABEL);
        if(customLabel == null || customLabel.length() == 0)
            setCarrierText(carrierText);
        else
            setCarrierText(customLabel);

        setCarrierHelpText(carrierHelpTextId);
        updateEmergencyCallButtonState(mPhoneState);
    }


    /*
     * Add emergencyCallMessage to carrier string only if phone supports emergency calls.
     */
    private CharSequence makeCarrierStringOnEmergencyCapable(
            CharSequence simMessage, CharSequence emergencyCallMessage) {
        if (mLockPatternUtils.isEmergencyCallCapable()) {
            return makeCarierString(simMessage, emergencyCallMessage);
        }
        return simMessage;
    }

    private View findViewById(int id) {
        return mContainer.findViewById(id);
    }

    /**
     * The status of this lock screen. Primarily used for widgets on LockScreen.
     */
    enum StatusMode {
        /**
         * Normal case (sim card present, it's not locked)
         */
        Normal(true),

        /**
         * The sim card is 'network locked'.
         */
        NetworkLocked(true),

        /**
         * The sim card is missing.
         */
        SimMissing(false),

        /**
         * The sim card is missing, and this is the device isn't provisioned, so we don't let
         * them get past the screen.
         */
        SimMissingLocked(false),

        /**
         * The sim card is PUK locked, meaning they've entered the wrong sim unlock code too many
         * times.
         */
        SimPukLocked(false),

        /**
         * The sim card is locked.
         */
        SimLocked(true),

        /**
         * The sim card is permanently disabled due to puk unlock failure
         */
        SimPermDisabled(false);

        private final boolean mShowStatusLines;

        StatusMode(boolean mShowStatusLines) {
            this.mShowStatusLines = mShowStatusLines;
        }

        /**
         * @return Whether the status lines (battery level and / or next alarm) are shown while
         *         in this state.  Mostly dictated by whether this is room for them.
         */
        public boolean shouldShowStatusLines() {
            return mShowStatusLines;
        }
    }

    private void updateEmergencyCallButtonState(int phoneState) {
        if (mEmergencyCallButton != null) {
            boolean enabledBecauseSimLocked =
                    mLockPatternUtils.isEmergencyCallEnabledWhileSimLocked()
                    && mEmergencyButtonEnabledBecauseSimLocked;
            boolean shown = mEmergencyCallButtonEnabledInScreen || enabledBecauseSimLocked;
            mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton,
                    phoneState, shown);
        }
    }

    private InfoCallbackImpl mInfoCallback = new InfoCallbackImpl() {

        @Override
        public void onRefreshBatteryInfo(boolean showBatteryInfo, boolean pluggedIn,
                int batteryLevel) {
            mShowingBatteryInfo = showBatteryInfo;
            mPluggedIn = pluggedIn;
            mBatteryLevel = batteryLevel;
            final MutableInt tmpIcon = new MutableInt(0);
            update(BATTERY_INFO, getAltTextMessage(tmpIcon));
        }

        public void onRefreshWeatherInfoAOKP(Intent weatherIntent) {
            mWeatherInfoAOKP = weatherIntent;
            update(WEATHER_INFO, null);
        }

        public void onRefreshCalendarInfo() {
            update(CALENDAR_INFO, null);
        }

        @Override
        public void onTimeChanged() {
            refreshDate();
            refreshWeather();
        }

        @Override
        public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn) {
            mPlmn = plmn;
            mSpn = spn;
            updateCarrierStateWithSimStatus(mSimState);
        }

        @Override
        public void onPhoneStateChanged(int phoneState) {
            mPhoneState = phoneState;
            updateEmergencyCallButtonState(phoneState);
        }

    };

    private SimStateCallback mSimStateCallback = new SimStateCallback() {

        public void onSimStateChanged(State simState) {
            updateCarrierStateWithSimStatus(simState);
        }
    };

    public void onClick(View v) {
        if (v == mEmergencyCallButton) {
            mCallback.takeEmergencyCallAction();
        } else if (v == mWeatherPanel) {
            // Indicate we are refreshing
            if (mWeatherCondition != null) {
                mWeatherCondition.setText(R.string.weather_refreshing);
            }

            mCallback.pokeWakelock();
            if (!mWeatherRefreshing) {
                mHandler.sendEmptyMessage(QUERY_WEATHER);
            }
        }
    }

    /**
     * Performs concentenation of PLMN/SPN
     * @param plmn
     * @param spn
     * @return
     */
    private static CharSequence makeCarierString(CharSequence plmn, CharSequence spn) {
        final boolean plmnValid = !TextUtils.isEmpty(plmn);
        final boolean spnValid = !TextUtils.isEmpty(spn);
        if (plmnValid && spnValid) {
            return plmn + "|" + spn;
        } else if (plmnValid) {
            return plmn;
        } else if (spnValid) {
            return spn;
        } else {
            return "";
        }
    }

    public void updateColors() {
        if (DEBUG) Log.d(TAG, "Lets update the colors");
        ContentResolver resolver = getContext().getContentResolver();
        int color = Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_CUSTOM_TEXT_COLOR, COLOR_WHITE);

        // carrier view
        try {
            mCarrierView.setTextColor(color);
            if (DEBUG) Log.d(TAG, String.format("Setting mCarrierView text color to %d", color));
        } catch (NullPointerException ne) {
            if (DEBUG) ne.printStackTrace();
        }

        // date view
        try {
            mDateView.setTextColor(color);
            if (DEBUG) Log.d(TAG, String.format("Setting mDateView DATE text color to %d", color));
        } catch (NullPointerException ne) {
            if (DEBUG) ne.printStackTrace();
        }

        // status view
        try {
            mStatus1View.setTextColor(color);
            if (DEBUG) Log.d(TAG, String.format("Setting mStatus1View DATE text color to %d", color));
        } catch (NullPointerException ne) {
            if (DEBUG) ne.printStackTrace();
        }

        // weather view
        try {
            mWeatherTextView.setTextColor(color);
            if (DEBUG)
                Log.d(TAG, String.format("Setting mWeatherTextView DATE text color to %d", color));
        } catch (NullPointerException ne) {
            if (DEBUG)
                ne.printStackTrace();
        }

        // weatherpanel view
        try {
            mWeatherPanelView.setTextColor(color);
            if (DEBUG) Log.d(TAG, String.format("Setting mWeatherPanelView DATE text color to %d", color));
        } catch (NullPointerException ne) {
            if (DEBUG) ne.printStackTrace();
        }

        // calendar view
        try {
            if (!mCalendarUsingColors) {
                for (int i = 0; i < mCalendarView.getChildCount(); i++) {
                    ((CalendarEntry) mCalendarView.getChildAt(i)).setColor(color);
                }
                if (DEBUG)
                    Log.d(TAG, String.format("Setting mCalendarView DATE text color to %d", color));
            }
        } catch (NullPointerException ne) {
            if (DEBUG)
                ne.printStackTrace();
        }

        // owner info view
        try {
            mOwnerInfoView.setTextColor(color);
            if (DEBUG) Log.d(TAG, String.format("Setting mOwnerInfoView DATE text color to %d", color));
        } catch (NullPointerException ne) {
            if (DEBUG) ne.printStackTrace();
        }

        // alarm status view
        try {
            mAlarmStatusView.setTextColor(color);
            if (DEBUG) Log.d(TAG, String.format("Setting mAlarmStatusView DATE text color to %d", color));
        } catch (NullPointerException ne) {
            if (DEBUG) ne.printStackTrace();
        }
    }

    private void getCalendarEvents(ContentResolver resolver, String sources,
            boolean multipleEvents, boolean hideOnGoing, long range) {

        mCalendarEvents = new ArrayList<EventBundle>();

        Calendar now = Calendar.getInstance();

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, now.getTimeInMillis());
        ContentUris.appendId(builder, now.getTimeInMillis() + range);
        String selection = "(( " + CalendarContract.Instances.CALENDAR_ID
                + " IN ( " + sources + " ))"
                + (hideOnGoing ? " AND ( " + CalendarContract.Instances.BEGIN
                        + " > " + now.getTimeInMillis() + " ))" : ")");

        Cursor eventCur = resolver.query(builder.build(), new String[] {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_COLOR
        }, selection, null,
                CalendarContract.Instances.START_DAY + " ASC, "
                        + CalendarContract.Instances.START_MINUTE + " ASC");
        
        int events = eventCur.getCount();
        
        if (events > 0) {
            eventCur.moveToFirst();
            do {
            mCalendarEvents.add(new EventBundle(eventCur.getString(0),
                    eventCur.getLong(1), eventCur.getString(2),
                    now, (eventCur.getInt(3) != 0), eventCur.getInt(4)));
            if (!multipleEvents)
                break;
            } while (eventCur.moveToNext());
        }
        eventCur.close();
    }

    private class EventBundle {
        public String title;
        public Calendar begin;
        public String location;
        public String dayString;
        public boolean allDay = false;
        public int color;

        EventBundle(String s, long b, String l, Calendar now, boolean a, int c) {
            title = s;
            begin = Calendar.getInstance();
            if (a) {
                begin.setTimeInMillis(b - begin.get(Calendar.ZONE_OFFSET) - begin.get(Calendar.DST_OFFSET));
                allDay = true;
            } else {
                begin.setTimeInMillis(b);
            }
            location = (l == null) ? "" : l;
            int beginDay = begin.get(Calendar.DAY_OF_YEAR);
            int today = now.get(Calendar.DAY_OF_YEAR);
            if (beginDay == today) { // today
                dayString = "";
            } else if (today + 1 == beginDay || (today >= 365 && beginDay == 1)) { // tomorrow
                dayString = getContext().getString(R.string.lockscreen_calendar_tomorrow);
            } else { // another day of week
                dayString = begin.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT,
                        Locale.getDefault());
            }
            allDay = a;
            color = c;
        }
    }
}
