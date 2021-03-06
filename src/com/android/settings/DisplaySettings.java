/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.settings;

import android.os.UserHandle;
import com.android.internal.view.RotationPolicy;
import com.android.settings.notification.DropDownPreference;
import com.android.settings.notification.DropDownPreference.Callback;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import static android.provider.Settings.Secure.DOZE_ENABLED;
import static android.provider.Settings.Secure.WAKE_GESTURE_ENABLED;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.preference.TwoStatePreference;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import com.android.settings.cyanogenmod.DisplayRotation;
import com.android.settings.hardware.DisplayColor;
import com.android.settings.hardware.DisplayGamma;

import org.cyanogenmod.hardware.AdaptiveBacklight;
import org.cyanogenmod.hardware.ColorEnhancement;
import org.cyanogenmod.hardware.SunlightEnhancement;
import org.cyanogenmod.hardware.TapToWake;

public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, OnPreferenceClickListener, Indexable {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;
	
    private static final String PROP_DISPLAY_DENSITY = "persist.sf.lcd_density";

    private static final String KEY_CATEGORY_LIGHTS = "lights";
    private static final String KEY_CATEGORY_DISPLAY = "display";
    private static final String KEY_CATEGORY_CALIBRATION = "calibration";
    private static final String KEY_CATEGORY_INTERFACE = "interface";

    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_SCREEN_SAVER = "screensaver";
    private static final String KEY_LIFT_TO_WAKE = "lift_to_wake";
    private static final String KEY_AUTO_BRIGHTNESS = "auto_brightness";
    private static final String DISABLE_IMMERSIVE_MESSAGE = "disable_immersive_message";

    private static final String KEY_ADAPTIVE_BACKLIGHT = "adaptive_backlight";
    private static final String KEY_SUNLIGHT_ENHANCEMENT = "sunlight_enhancement";
    private static final String KEY_COLOR_ENHANCEMENT = "color_enhancement";
    private static final String KEY_TAP_TO_WAKE = "double_tap_wake_gesture";
    private static final String KEY_DISPLAY_DENSITY = "display_density";
    private static final String PROP_DISPLAY_DENSITY_MAX = "ro.sf.lcd_density.max";
    private static final String PROP_DISPLAY_DENSITY_MIN = "ro.sf.lcd_density.min";
    private static final String PROP_DISPLAY_DENSITY_OVERRIDE = "persist.sf.lcd_density.override";    
    private static final String KEY_PROXIMITY_WAKE = "proximity_on_wake";
    private static final String KEY_DISPLAY_ROTATION = "display_rotation";
    private static final String KEY_WAKEUP_WHEN_PLUGGED_UNPLUGGED = "wakeup_when_plugged_unplugged";
    private static final String KEY_NOTIFICATION_LIGHT = "notification_light";
    private static final String KEY_BATTERY_LIGHT = "battery_light";
	private static final String KEY_WAKEUP_CATEGORY = "category_wakeup_options";

    private static final String KEY_DISPLAY_COLOR = "color_calibration";
    private static final String KEY_DISPLAY_GAMMA = "gamma_tuning";
    private static final String KEY_SCREEN_OFF_GESTURE_SETTINGS = "screen_off_gesture_settings";
    private static final String KEY_SCREEN_COLOR_SETTINGS = "screencolor_settings";
    private static final String KEY_DISPLAY_DENSITY_OVERRIDE = "display_density_override";

    private static final String KEY_DOZE_FRAGMENT = "doze_fragment";

    private static final int DLG_GLOBAL_CHANGE_WARNING = 1;

    private PreferenceCategory mWakeUpOptions;
    private FontDialogPreference mFontSizePref;
    private PreferenceScreen mDisplayRotationPreference;
    private PreferenceScreen mScreenColorSettings;

    private final Configuration mCurConfig = new Configuration();

    private ListPreference mScreenTimeoutPreference;
    private Preference mScreenSaverPreference;
    private SwitchPreference mAccelerometer;
    private SwitchPreference mLiftToWakePreference;
    private PreferenceScreen mDozeFragment;
    private SwitchPreference mAutoBrightnessPreference;
    private SwitchPreference mTapToWake;
    private SwitchPreference mDisableIM;
    private EditTextPreference mDisplayDensity;
    private EditTextPreference mDisplayDensityOverride;
    private SwitchPreference mWakeUpWhenPluggedOrUnplugged;
    private SwitchPreference mAdaptiveBacklight;
    private SwitchPreference mSunlightEnhancement;
    private SwitchPreference mColorEnhancement;

    private TwoStatePreference mNotificationPulse;

    private ContentObserver mAccelerometerRotationObserver =
            new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateDisplayRotationPreferenceDescription();
            updateAccelerometerRotationSwitch();
        }
    };

    private final RotationPolicy.RotationPolicyListener mRotationPolicyListener =
            new RotationPolicy.RotationPolicyListener() {
        @Override
        public void onChange() {
            updateDisplayRotationPreferenceDescription();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Activity activity = getActivity();
        final ContentResolver resolver = activity.getContentResolver();

        addPreferencesFromResource(R.xml.display);
		
		PreferenceScreen prefSet = getPreferenceScreen();

        PreferenceCategory displayPrefs = (PreferenceCategory)
                findPreference(KEY_CATEGORY_DISPLAY);
        PreferenceCategory interfacePrefs = (PreferenceCategory)
                findPreference(KEY_CATEGORY_INTERFACE);
        PreferenceCategory calibrationPrefs = (PreferenceCategory)
                findPreference(KEY_CATEGORY_CALIBRATION);

        mDisplayRotationPreference = (PreferenceScreen) findPreference(KEY_DISPLAY_ROTATION);
        mAccelerometer = (SwitchPreference) findPreference(DisplayRotation.KEY_ACCELEROMETER);
        if (mAccelerometer != null) {
            mAccelerometer.setPersistent(false);
        }

        mScreenSaverPreference = findPreference(KEY_SCREEN_SAVER);
        if (mScreenSaverPreference != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_dreamsSupported) == false) {
            interfacePrefs.removePreference(mScreenSaverPreference);
        }

        mScreenTimeoutPreference = (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        final long currentTimeout = Settings.System.getLong(resolver, SCREEN_OFF_TIMEOUT,
                FALLBACK_SCREEN_TIMEOUT_VALUE);
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        mScreenTimeoutPreference.setOnPreferenceChangeListener(this);
        disableUnusableTimeouts(mScreenTimeoutPreference);
        updateTimeoutPreferenceDescription(currentTimeout);
        updateDisplayRotationPreferenceDescription();

        mFontSizePref = (FontDialogPreference) findPreference(KEY_FONT_SIZE);
        mFontSizePref.setOnPreferenceChangeListener(this);
        mFontSizePref.setOnPreferenceClickListener(this);


        mDisableIM = (SwitchPreference) findPreference(DISABLE_IMMERSIVE_MESSAGE);
        mDisableIM.setChecked((Settings.System.getInt(resolver,
                Settings.System.DISABLE_IMMERSIVE_MESSAGE, 0) == 1));

        mAutoBrightnessPreference = (SwitchPreference) findPreference(KEY_AUTO_BRIGHTNESS);
        if (mAutoBrightnessPreference != null && isAutomaticBrightnessAvailable(getResources())) {
            mAutoBrightnessPreference.setOnPreferenceChangeListener(this);
        } else {
            if (displayPrefs != null && mAutoBrightnessPreference != null) {
                displayPrefs.removePreference(mAutoBrightnessPreference);
                mAutoBrightnessPreference = null;
            }
        }

        mLiftToWakePreference = (SwitchPreference) findPreference(KEY_LIFT_TO_WAKE);
        if (mLiftToWakePreference != null && isLiftToWakeAvailable(activity)) {
            mLiftToWakePreference.setOnPreferenceChangeListener(this);
        } else {
            if (displayPrefs != null && mLiftToWakePreference != null) {
                displayPrefs.removePreference(mLiftToWakePreference);
                mLiftToWakePreference = null;
            }
        }

        mAdaptiveBacklight = (SwitchPreference) findPreference(KEY_ADAPTIVE_BACKLIGHT);
        if (calibrationPrefs != null && !isAdaptiveBacklightSupported()) {
            calibrationPrefs.removePreference(mAdaptiveBacklight);
            mAdaptiveBacklight = null;
        }

        mSunlightEnhancement = (SwitchPreference) findPreference(KEY_SUNLIGHT_ENHANCEMENT);
        if (calibrationPrefs != null && mSunlightEnhancement != null
                && !isSunlightEnhancementSupported()) {
            calibrationPrefs.removePreference(mSunlightEnhancement);
            mSunlightEnhancement = null;
        }

        mColorEnhancement = (SwitchPreference) findPreference(KEY_COLOR_ENHANCEMENT);
        if (calibrationPrefs != null && mColorEnhancement != null
                && !isColorEnhancementSupported()) {
            calibrationPrefs.removePreference(mColorEnhancement);
            mColorEnhancement = null;
        }

        if (calibrationPrefs != null && !DisplayColor.isSupported()) {
            Preference colorPref = findPreference(KEY_DISPLAY_COLOR);
            if (colorPref != null) {
                calibrationPrefs.removePreference(colorPref);
            }
        }
        if (calibrationPrefs != null && !DisplayGamma.isSupported()) {
            Preference gammaPref = findPreference(KEY_DISPLAY_GAMMA);
            if (gammaPref != null) {
                calibrationPrefs.removePreference(gammaPref);
            }
        }

        mDozeFragment = (PreferenceScreen) findPreference(KEY_DOZE_FRAGMENT);
        if (!isDozeAvailable(activity)) {
            displayPrefs.removePreference(mDozeFragment);
            mDozeFragment = null;
        }

       if (!isDeviceHandlerInstalled()) {
            displayPrefs.removePreference(findPreference(KEY_SCREEN_OFF_GESTURE_SETTINGS));
       }
        mTapToWake = (SwitchPreference) findPreference(KEY_TAP_TO_WAKE);
        if (!isTapToWakeSupported() || isDeviceHandlerInstalled()) {
            displayPrefs.removePreference(mTapToWake);
            mTapToWake = null;
        }

        mDisplayDensity = (EditTextPreference) findPreference(KEY_DISPLAY_DENSITY);
        mDisplayDensity.setText(SystemProperties.get(PROP_DISPLAY_DENSITY, "0"));
        mDisplayDensity.setOnPreferenceChangeListener(this);
        
        mDisplayDensityOverride = (EditTextPreference) findPreference(KEY_DISPLAY_DENSITY_OVERRIDE);
        mDisplayDensityOverride.setText(SystemProperties.get(PROP_DISPLAY_DENSITY_OVERRIDE, "0"));
        mDisplayDensityOverride.setOnPreferenceChangeListener(this);        
        boolean proximityCheckOnWake = getResources().getBoolean(
                com.android.internal.R.bool.config_proximityCheckOnWake);
        if (calibrationPrefs != null && !proximityCheckOnWake) {
            calibrationPrefs.removePreference(findPreference(KEY_PROXIMITY_WAKE));
            Settings.System.putInt(getContentResolver(), Settings.System.PROXIMITY_ON_WAKE, 1);
        }

        mWakeUpOptions = (PreferenceCategory) prefSet.findPreference(KEY_WAKEUP_CATEGORY);
        mWakeUpWhenPluggedOrUnplugged =
            (SwitchPreference) findPreference(KEY_WAKEUP_WHEN_PLUGGED_UNPLUGGED);

        // hide option if device is already set to never wake up
        if(!getResources().getBoolean(
                com.android.internal.R.bool.config_unplugTurnsOnScreen)) {
                mWakeUpOptions.removePreference(mWakeUpWhenPluggedOrUnplugged);
                prefSet.removePreference(mWakeUpOptions);
        } else {
            mWakeUpWhenPluggedOrUnplugged.setChecked(Settings.System.getInt(resolver,
                        Settings.System.WAKEUP_WHEN_PLUGGED_UNPLUGGED, 1) == 1);
            mWakeUpWhenPluggedOrUnplugged.setOnPreferenceChangeListener(this);
        }

        Utils.updatePreferenceToSpecificActivityFromMetaDataOrRemove(getActivity(),
                getPreferenceScreen(), KEY_SCREEN_OFF_GESTURE_SETTINGS);

        mScreenColorSettings = (PreferenceScreen) findPreference(KEY_SCREEN_COLOR_SETTINGS);
        if (calibrationPrefs != null && !isPostProcessingSupported(getActivity())
                && mScreenColorSettings != null) {
            calibrationPrefs.removePreference(mScreenColorSettings);
        }

        initPulse((PreferenceCategory) findPreference(KEY_CATEGORY_LIGHTS));
    }

    private static boolean allowAllRotations(Context context) {
        return Resources.getSystem().getBoolean(
                com.android.internal.R.bool.config_allowAllRotations);
    }

    private static boolean isLiftToWakeAvailable(Context context) {
        SensorManager sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return sensors != null && sensors.getDefaultSensor(Sensor.TYPE_WAKE_GESTURE) != null;
    }

    private static boolean isDozeAvailable(Context context) {
        String name = Build.IS_DEBUGGABLE ? SystemProperties.get("debug.doze.component") : null;
        if (TextUtils.isEmpty(name)) {
            name = context.getResources().getString(
                    com.android.internal.R.string.config_dozeComponent);
        }
        return !TextUtils.isEmpty(name);
    }

    private static boolean isAutomaticBrightnessAvailable(Resources res) {
        return res.getBoolean(com.android.internal.R.bool.config_automatic_brightness_available);
    }

    private void updateAccelerometerRotationSwitch() {
        if (mAccelerometer != null) {
            mAccelerometer.setChecked(!RotationPolicy.isRotationLocked(getActivity()));
        }
    }

    private void updateDisplayRotationPreferenceDescription() {
        if (mDisplayRotationPreference == null) {
            // The preference was removed, do nothing
            return;
        }

        // We have a preference, lets update the summary
        boolean rotationEnabled = Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) != 0;

        if (!rotationEnabled) {
            mDisplayRotationPreference.setSummary(R.string.display_rotation_disabled);
            return;
        }

        StringBuilder summary = new StringBuilder();
        int mode = Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION_ANGLES,
                DisplayRotation.ROTATION_0_MODE
                | DisplayRotation.ROTATION_90_MODE
                | DisplayRotation.ROTATION_270_MODE);
        ArrayList<String> rotationList = new ArrayList<String>();
        String delim = "";

        if ((mode & DisplayRotation.ROTATION_0_MODE) != 0) {
            rotationList.add("0");
        }
        if ((mode & DisplayRotation.ROTATION_90_MODE) != 0) {
            rotationList.add("90");
        }
        if ((mode & DisplayRotation.ROTATION_180_MODE) != 0) {
            rotationList.add("180");
        }
        if ((mode & DisplayRotation.ROTATION_270_MODE) != 0) {
            rotationList.add("270");
        }
        for (int i = 0; i < rotationList.size(); i++) {
            summary.append(delim).append(rotationList.get(i));
            if ((rotationList.size() - i) > 2) {
                delim = ", ";
            } else {
                delim = " & ";
            }
        }
        summary.append(" " + getString(R.string.display_rotation_unit));
        mDisplayRotationPreference.setSummary(summary);
    }

    private void updateTimeoutPreferenceDescription(long currentTimeout) {
        ListPreference preference = mScreenTimeoutPreference;
        String summary;
        if (currentTimeout < 0) {
            // Unsupported value
            summary = "";
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            if (entries == null || entries.length == 0) {
                summary = "";
            } else {
                int best = 0;
                for (int i = 0; i < values.length; i++) {
                    long timeout = Long.parseLong(values[i].toString());
                    try {
                        if (currentTimeout >= timeout) {
                           best = i;
                        }
                    } catch (Exception e) {
                      // Do nothing
                    }
                }
                summary = preference.getContext().getString(R.string.screen_timeout_summary,
                        entries[best]);
            }
        }
        preference.setSummary(summary);
    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        if (maxTimeout == 0) {
            return; // policy not enforced
        }
        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.parseLong(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            final int userPreference = Integer.parseInt(screenTimeoutPreference.getValue());
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else if (revisedValues.size() > 0
                    && Long.parseLong(revisedValues.get(revisedValues.size() - 1).toString())
                    == maxTimeout) {
                // If the last one happens to be the same as the max timeout, select that
                screenTimeoutPreference.setValue(String.valueOf(maxTimeout));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateDisplayRotationPreferenceDescription();
        if (mAdaptiveBacklight != null) {
            mAdaptiveBacklight.setChecked(AdaptiveBacklight.isEnabled());
        }

        if (mSunlightEnhancement != null) {
            if (SunlightEnhancement.isAdaptiveBacklightRequired() &&
                    !AdaptiveBacklight.isEnabled()) {
                mSunlightEnhancement.setEnabled(false);
            } else {
                mSunlightEnhancement.setChecked(SunlightEnhancement.isEnabled());
            }
        }

        if (mColorEnhancement != null) {
            mColorEnhancement.setChecked(ColorEnhancement.isEnabled());
        }

        if (mTapToWake != null) {
            mTapToWake.setChecked(TapToWake.isEnabled());
        }

        RotationPolicy.registerRotationPolicyListener(getActivity(),
                mRotationPolicyListener);

        final ContentResolver resolver = getContentResolver();

        // Display rotation observer
        resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), true,
                mAccelerometerRotationObserver);

        if (mAdaptiveBacklight != null) {
            mAdaptiveBacklight.setChecked(AdaptiveBacklight.isEnabled());
        }

        updateState();
        updateAccelerometerRotationSwitch();
    }

    @Override
    public void onPause() {
        super.onPause();

        RotationPolicy.unregisterRotationPolicyListener(getActivity(),
                mRotationPolicyListener);

        // Display rotation observer
        getContentResolver().unregisterContentObserver(mAccelerometerRotationObserver);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        if (dialogId == DLG_GLOBAL_CHANGE_WARNING) {
            return Utils.buildGlobalChangeWarningDialog(getActivity(),
                    R.string.global_font_change_title,
                    new Runnable() {
                        public void run() {
                            mFontSizePref.click();
                        }
                    });
        }
        return null;
    }

    private void updateState() {
        readFontSizePreference(mFontSizePref);
        updateScreenSaverSummary();

        // Update auto brightness if it is available.
        if (mAutoBrightnessPreference != null) {
            int brightnessMode = Settings.System.getInt(getContentResolver(),
                    SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL);
            mAutoBrightnessPreference.setChecked(brightnessMode != SCREEN_BRIGHTNESS_MODE_MANUAL);
        }

        // Update lift-to-wake if it is available.
        if (mLiftToWakePreference != null) {
            int value = Settings.Secure.getInt(getContentResolver(), WAKE_GESTURE_ENABLED, 0);
            mLiftToWakePreference.setChecked(value != 0);
        }
    }

    private void updateScreenSaverSummary() {
        if (mScreenSaverPreference != null) {
            mScreenSaverPreference.setSummary(
                    DreamSettings.getSummaryTextWithDreamName(getActivity()));
        }
    }

    private static boolean isTapToWakeSupported() {
        try {
            return TapToWake.isSupported();
        } catch (NoClassDefFoundError e) {
            // Hardware abstraction framework not installed
            return false;
        }
    }


    // === Pulse notification light ===

    private void initPulse(PreferenceCategory parent) {
        if (!getResources().getBoolean(
                com.android.internal.R.bool.config_intrusiveNotificationLed)) {
            parent.removePreference(parent.findPreference(KEY_NOTIFICATION_LIGHT));
        }
        if (!getResources().getBoolean(
                com.android.internal.R.bool.config_intrusiveBatteryLed)
                || UserHandle.myUserId() != UserHandle.USER_OWNER) {
            parent.removePreference(parent.findPreference(KEY_BATTERY_LIGHT));
        }
        if (parent.getPreferenceCount() == 0) {
            getPreferenceScreen().removePreference(parent);
        }
    }

    /**
     * Reads the current font size and sets the value in the summary text
     */
    public void readFontSizePreference(Preference pref) {
        try {
            mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to retrieve font size");
        }

        // report the current size in the summary text
        final Resources res = getResources();
        String fontDesc = FontDialogPreference.getFontSizeDescription(res, mCurConfig.fontScale);
        pref.setSummary(getString(R.string.summary_font_size, fontDesc));
    }

    public void writeFontSizePreference(Object objValue) {
        try {
            mCurConfig.fontScale = Float.parseFloat(objValue.toString());
            ActivityManagerNative.getDefault().updatePersistentConfiguration(mCurConfig);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to save font size");
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mTapToWake) {
            return TapToWake.setEnabled(mTapToWake.isChecked());
        } else if (preference == mDisableIM) {
            boolean checked = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.DISABLE_IMMERSIVE_MESSAGE, checked ? 1:0);
            return true;                    
        } else if (preference == mAdaptiveBacklight) {
            if (mSunlightEnhancement != null &&
                    SunlightEnhancement.isAdaptiveBacklightRequired()) {
                mSunlightEnhancement.setEnabled(mAdaptiveBacklight.isChecked());
            }
            return AdaptiveBacklight.setEnabled(mAdaptiveBacklight.isChecked());
        } else if (preference == mSunlightEnhancement) {
            return SunlightEnhancement.setEnabled(mSunlightEnhancement.isChecked());
        } else if (preference == mColorEnhancement) {
            return ColorEnhancement.setEnabled(mColorEnhancement.isChecked());
        } else if (preference == mAccelerometer) {
            RotationPolicy.setRotationLockForAccessibility(getActivity(),
                    !mAccelerometer.isChecked());
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            try {
                int value = Integer.parseInt((String) objValue);
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                updateTimeoutPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        }
        if (KEY_FONT_SIZE.equals(key)) {
            writeFontSizePreference(objValue);
        }
        if (preference == mAutoBrightnessPreference) {
            boolean auto = (Boolean) objValue;
            Settings.System.putInt(getContentResolver(), SCREEN_BRIGHTNESS_MODE,
                    auto ? SCREEN_BRIGHTNESS_MODE_AUTOMATIC : SCREEN_BRIGHTNESS_MODE_MANUAL);
        }
        if (preference == mLiftToWakePreference) {
            boolean value = (Boolean) objValue;
            Settings.Secure.putInt(getContentResolver(), WAKE_GESTURE_ENABLED, value ? 1 : 0);
        }
        if (KEY_WAKEUP_WHEN_PLUGGED_UNPLUGGED.equals(key)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.WAKEUP_WHEN_PLUGGED_UNPLUGGED,
                    (Boolean) objValue ? 1 : 0);
        }
        if (KEY_DISPLAY_DENSITY.equals(key)) {
            final int max = SystemProperties.getInt(PROP_DISPLAY_DENSITY_MAX, 480);
            final int min = SystemProperties.getInt(PROP_DISPLAY_DENSITY_MIN, 240);

            int value = SystemProperties.getInt(PROP_DISPLAY_DENSITY, 0);
            try {
                value = Integer.parseInt(String.valueOf(objValue));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid input", e);
            }

            // 0 disables the custom density, so do not check for the value, else…
            if (value != 0) {
                // …cap the value
                if (value < min) {
                    value = min;
                } else if (value > max) {
                    value = max;
                }
            }

            SystemProperties.set(PROP_DISPLAY_DENSITY, String.valueOf(value));
            mDisplayDensity.setText(String.valueOf(value));

            // we handle it, return false
            return false;
        }
        if (KEY_DISPLAY_DENSITY_OVERRIDE.equals(key)) {
            int value = SystemProperties.getInt(PROP_DISPLAY_DENSITY_OVERRIDE, 0);
            try {
                value = Integer.parseInt(String.valueOf(objValue));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid input", e);
            }

            SystemProperties.set(PROP_DISPLAY_DENSITY_OVERRIDE, String.valueOf(value));
            mDisplayDensityOverride.setText(String.valueOf(value));

            // we handle it, return false
            return false;
        }        
        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mFontSizePref) {
            if (Utils.hasMultipleUsers(getActivity())) {
                showDialog(DLG_GLOBAL_CHANGE_WARNING);
                return true;
            } else {
                mFontSizePref.click();
            }
        }
        return false;
    }


    /**
     * Restore the properties associated with this preference on boot
       @param ctx A valid context
     */
    public static void restore(Context ctx) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (isTapToWakeSupported()) {
            final boolean enabled = prefs.getBoolean(KEY_TAP_TO_WAKE,
                TapToWake.isEnabled());

            if (!TapToWake.setEnabled(enabled)) {
                Log.e(TAG, "Failed to restore tap-to-wake settings.");
            } else {
                Log.d(TAG, "Tap-to-wake settings restored.");
            }
        }

        if (isAdaptiveBacklightSupported()) {
            final boolean enabled = prefs.getBoolean(KEY_ADAPTIVE_BACKLIGHT,
                    AdaptiveBacklight.isEnabled());
            if (!AdaptiveBacklight.setEnabled(enabled)) {
                Log.e(TAG, "Failed to restore adaptive backlight settings.");
            } else {
                Log.d(TAG, "Adaptive backlight settings restored.");
            }
        }

        if (isSunlightEnhancementSupported()) {
            final boolean enabled = prefs.getBoolean(KEY_SUNLIGHT_ENHANCEMENT,
                    SunlightEnhancement.isEnabled());
            if (SunlightEnhancement.isAdaptiveBacklightRequired() &&
                    !AdaptiveBacklight.isEnabled()) {
                SunlightEnhancement.setEnabled(false);
                Log.d(TAG, "SRE requires CABC, disabled");
            } else {
                if (!SunlightEnhancement.setEnabled(enabled)) {
                    Log.e(TAG, "Failed to restore SRE settings.");
                } else {
                    Log.d(TAG, "SRE settings restored.");
                }
            }
        }

        if (isColorEnhancementSupported()) {
            final boolean enabled = prefs.getBoolean(KEY_COLOR_ENHANCEMENT,
                    ColorEnhancement.isEnabled());
            if (!ColorEnhancement.setEnabled(enabled)) {
                Log.e(TAG, "Failed to restore color enhancement settings.");
            } else {
                Log.d(TAG, "Color enhancement settings restored.");
            }
        }
    }

    private static boolean isPostProcessingSupported(Context context) {
        return Utils.isPackageInstalled(context, "com.qualcomm.display");
    }

    private static boolean isAdaptiveBacklightSupported() {
        try {
            return AdaptiveBacklight.isSupported();
        } catch (NoClassDefFoundError e) {
            // Hardware abstraction framework not installed
            return false;
        }
    }

    private static boolean isSunlightEnhancementSupported() {
        try {
            return SunlightEnhancement.isSupported();
        } catch (NoClassDefFoundError e) {
            // Hardware abstraction framework not installed
            return false;
        }
    }

    private static boolean isColorEnhancementSupported() {
        try {
            return ColorEnhancement.isSupported();
        } catch (NoClassDefFoundError e) {
            // Hardware abstraction framework not installed
            return false;
        }
    }

    private boolean isDeviceHandlerInstalled() {
        boolean ret = true;
        final PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo("com.slim.device", PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            ret = false;
        }
        return ret;
    }

    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                private boolean mHasTapToWake;
                private boolean mHasSunlightEnhancement, mHasColorEnhancement;
                private boolean mHasDisplayGamma, mHasDisplayColor;

                @Override
                public void prepare() {
                    mHasTapToWake = isTapToWakeSupported();
                    mHasSunlightEnhancement = isSunlightEnhancementSupported();
                    mHasColorEnhancement = isColorEnhancementSupported();
                    mHasDisplayGamma = DisplayGamma.isSupported();
                    mHasDisplayColor = DisplayColor.isSupported();
                }

                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();

                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.display;
                    result.add(sir);

                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    ArrayList<String> result = new ArrayList<String>();
                    if (!context.getResources().getBoolean(
                            com.android.internal.R.bool.config_dreamsSupported)) {
                        result.add(KEY_SCREEN_SAVER);
                    }
                    if (!context.getResources().getBoolean(
                            com.android.internal.R.bool.config_intrusiveNotificationLed)) {
                        result.add(KEY_NOTIFICATION_LIGHT);
                    }
                    if (!context.getResources().getBoolean(
                            com.android.internal.R.bool.config_intrusiveBatteryLed)) {
                        result.add(KEY_BATTERY_LIGHT);
                    }
                    if (!context.getResources().getBoolean(
                            com.android.internal.R.bool.config_proximityCheckOnWake)) {
                        result.add(KEY_PROXIMITY_WAKE);
                    }
                    if (!mHasTapToWake) {
                        result.add(KEY_TAP_TO_WAKE);
                    }
                    if (!mHasSunlightEnhancement) {
                        result.add(KEY_SUNLIGHT_ENHANCEMENT);
                    }
                    if (!mHasColorEnhancement) {
                        result.add(KEY_COLOR_ENHANCEMENT);
                    }
                    if (!isPostProcessingSupported(context)) {
                        result.add(KEY_SCREEN_COLOR_SETTINGS);
                    }
                    if (!mHasDisplayColor) {
                        result.add(KEY_DISPLAY_COLOR);
                    }
                    if (!mHasDisplayGamma) {
                        result.add(KEY_DISPLAY_GAMMA);
                    }
                    if (!isAutomaticBrightnessAvailable(context.getResources())) {
                        result.add(KEY_AUTO_BRIGHTNESS);
                    }
                    if (!isLiftToWakeAvailable(context)) {
                        result.add(KEY_LIFT_TO_WAKE);
                    }
                    if (!isDozeAvailable(context)) {
                        result.add(KEY_DOZE_FRAGMENT);
                    }
                    return result;
                }
            };
}
