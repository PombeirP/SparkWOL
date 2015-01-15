package com.pedropombeiro.sparkwol;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p/>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends PreferenceActivity {
    /**
     * Determines whether to always show the simplified settings UI, where
     * settings are presented in a single list. When false, settings are shown
     * as a master/detail two-pane view on tablets. When true, a single pane is
     * shown on tablets.
     */
    private static final boolean ALWAYS_SIMPLE_PREFS = false;
    private static final String AUTHENTICATION_TOKEN = "authentication_token";
    private static final String DEVICE_ID = "device_id";
    private static final String IP_ADDRESS = "ip_address";


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        setupSimplePreferencesScreen();
    }

    private class InvokeSparkGetDevicesMethodTask extends InvokeSparkGetMethodTaskBase {
        public InvokeSparkGetDevicesMethodTask(String authToken)
        {
            super(authToken);
        }

        @Override
        protected String GetUrl() {
            return "https://api.spark.io/v1/devices";
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            try {
                JSONArray deviceArray = new JSONArray(result);
                List<String> entries = new ArrayList<String>(deviceArray.length());
                List<String> entryValues = new ArrayList<String>(deviceArray.length());

                for (int i = 0; i < deviceArray.length(); ++i) {
                    JSONObject device = deviceArray.getJSONObject(i);
                    entries.add(device.getString("name"));
                    entryValues.add(device.getString("id"));
                }

                ListPreference deviceIdPreference = (ListPreference) findPreference(DEVICE_ID);
                deviceIdPreference.setEntries(entries.toArray(new String[entries.size()]));
                deviceIdPreference.setEntryValues(entryValues.toArray(new String[entryValues.size()]));

                bindPreferenceSummaryToValue(deviceIdPreference);
            }
            catch (JSONException e)
            {
                Toast.makeText(getBaseContext(), "Error requesting Spark device list", Toast.LENGTH_LONG).show();
                Log.w("FromOnPostExecute", e.getMessage());
            }
        }
    }

    /**
     * Shows the simplified settings UI if the device configuration if the
     * device configuration dictates that a simplified, single-pane UI should be
     * shown.
     */
    private void setupSimplePreferencesScreen() {
        if (!isSimplePreferences(this)) {
            return;
        }

        // In the simplified UI, fragments are not used at all and we instead
        // use the older PreferenceActivity APIs.

        // Add 'general' preferences.
        addPreferencesFromResource(R.xml.pref_general);
        IPAddressPreference ipAddressPreference = new IPAddressPreference(this);
        ipAddressPreference.setKey(IP_ADDRESS);
        ipAddressPreference.setTitle(R.string.pref_ip_address);
        getPreferenceScreen().addPreference(ipAddressPreference);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        bindPreferenceSummaryToValue(findPreference(AUTHENTICATION_TOKEN));
        bindPreferenceSummaryToValue(findPreference(IP_ADDRESS));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this) && !isSimplePreferences(this);
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Determines whether the simplified settings UI should be shown. This is
     * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
     * doesn't have newer APIs like {@link PreferenceFragment}, or the device
     * doesn't have an extra-large screen. In these cases, a single-pane
     * "simplified" settings UI should be shown.
     */
    private static boolean isSimplePreferences(Context context) {
        return ALWAYS_SIMPLE_PREFS
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
                || !isXLargeTablet(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        if (!isSimplePreferences(this)) {
            loadHeadersFromResource(R.xml.pref_headers, target);
        }
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else if (preference instanceof IPAddressPreference) {
                if (stringValue.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                    String macAddress = NetworkHelpers.GetMacFromArpCache(stringValue);

                    if (macAddress == null)
                        preference.setSummary(String.format("%s (MAC address unknown)", stringValue));
                    else
                        preference.setSummary(String.format("%s (%s)", stringValue, macAddress));
                }
                else {
                    preference.setSummary(String.format("%s (Unknown/invalid)", stringValue));
                }
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }

            ListPreference deviceIdPreference = null;
            CharSequence[] deviceIdEntries = null;
            String authToken = "";
            String preferenceKey = preference.getKey();
            if (preferenceKey.equals(AUTHENTICATION_TOKEN)) {
                authToken = stringValue;
                deviceIdPreference = (ListPreference) preference.getPreferenceManager().findPreference(DEVICE_ID);
                deviceIdEntries = deviceIdPreference.getEntries();
            }
            else if (preferenceKey.equals(DEVICE_ID)) {
                EditTextPreference authTokenPreference = (EditTextPreference) preference.getPreferenceManager().findPreference(AUTHENTICATION_TOKEN);

                deviceIdPreference = (ListPreference) preference;
                deviceIdEntries = deviceIdPreference.getEntries();
                authToken = authTokenPreference.getText();
            }

            if (deviceIdPreference != null) {
                // update device list preference enabled state
                deviceIdPreference.setEnabled(authToken.length() > 0 && deviceIdEntries != null && deviceIdEntries.length > 0);

                // update device list preference summary
                if (authToken.length() > 0) {
                    if (deviceIdPreference != null && deviceIdEntries != null) {
                        if (deviceIdEntries.length > 0) {
                            if (deviceIdPreference.getEntry() == null) {
                                deviceIdPreference.setSummary(R.string.pref_device_select_device);
                            }
                        } else {
                            deviceIdPreference.setSummary(R.string.pref_device_no_devices_found);
                        }
                    }
                } else {
                    deviceIdPreference.setSummary("");
                }

                // If authentication token changes, request list of devices again
                if (preferenceKey.equals(AUTHENTICATION_TOKEN) && authToken.length() > 0) {
                    // HTTP Get
                    new InvokeSparkGetDevicesMethodTask(authToken).execute();
                }
            }

            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference(DEVICE_ID));
            bindPreferenceSummaryToValue(findPreference(AUTHENTICATION_TOKEN));
            bindPreferenceSummaryToValue(findPreference(IP_ADDRESS));
        }
    }
}