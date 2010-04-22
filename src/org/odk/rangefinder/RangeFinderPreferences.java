/*
 * Copyright (C) 2010 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */


package org.odk.rangefinder;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.InputType;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Preference settings for Range Finder, handle arm length and eye separation
 * in metric and imperial units.
 *
 * @author harlanh@google.com
 */
public class RangeFinderPreferences extends PreferenceActivity 
    implements OnSharedPreferenceChangeListener {

  private SharedPreferences settings;
  private PreferenceScreen preferenceScreen;
  private ListPreference units;
  private EditTextPreference arm_metric;
  private EditTextPreference eye_metric;
  private EditTextPreference arm_imperial;
  private EditTextPreference eye_imperial;
  
  public static final String UNITS = "units";
  public static final String EYE_SEPARATION_METRIC = "eye_separation_metric";
  public static final String ARM_LENGTH_METRIC = "arm_length_metric";
  public static final String EYE_SEPARATION_IMP = "eye_separation_imperial";
  public static final String ARM_LENGTH_IMP = "arm_length_imperial";
  
  public static final float CM_PER_INCH = 2.54f;
  
  NumberFormat numberFormat1 = new DecimalFormat("0.#");
  NumberFormat numberFormat2 = new DecimalFormat("0.##");

  @Override 
  public void onCreate(Bundle savedInstanceState) { 
    super.onCreate(savedInstanceState); 
    getPreferenceManager().setSharedPreferencesName(RangeFinder.PREFS_NAME);
    settings = getSharedPreferences(RangeFinder.PREFS_NAME, 0);
    settings.registerOnSharedPreferenceChangeListener(this);
    setupOptions();
  }
  
  private void setupOptions() {
    preferenceScreen = getPreferenceManager().createPreferenceScreen(this);
    setPreferenceScreen(preferenceScreen);
    units = new ListPreference(this);
    units.setTitle(R.string.preferences_units);
    units.setKey(UNITS);
    CharSequence[] entries = {getResources().getText(R.string.units_metric),
        getResources().getText(R.string.units_imperial)};
    units.setEntries(entries);
    CharSequence[] values = {"metric", "imperial"};
    units.setEntryValues(values);
    units.setSummary(settings.getString(UNITS, "metric"));
    preferenceScreen.addPreference(units);
    
    arm_metric = new EditTextPreference(this);
    // Would be nice if this showed a keypad with just numbers, but oh well.
    arm_metric.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER 
        | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    arm_metric.setKey(ARM_LENGTH_METRIC);
    arm_metric.setTitle(R.string.arm_length);
    arm_metric.getEditText().setHint(R.string.centimeter_abbr);
    
    arm_imperial = new EditTextPreference(this);
    // Would be nice if this showed a keypad with just numbers, but oh well.
    arm_imperial.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER 
        | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    arm_imperial.setKey(ARM_LENGTH_IMP);
    arm_imperial.setTitle(R.string.arm_length);
    arm_imperial.getEditText().setHint(R.string.inch_abbr);
    
    eye_metric = new EditTextPreference(this);
    // Would be nice if this showed a keypad with just numbers, but oh well.
    eye_metric.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER 
        | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    eye_metric.setKey(EYE_SEPARATION_METRIC);
    eye_metric.setTitle(R.string.eye_separation);
    eye_metric.getEditText().setHint(R.string.centimeter_abbr);
    
    eye_imperial = new EditTextPreference(this);
    // Would be nice if this showed a keypad with just numbers, but oh well.
    eye_imperial.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER 
        | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    eye_imperial.setKey(EYE_SEPARATION_IMP);
    eye_imperial.setTitle(R.string.eye_separation);
    eye_imperial.getEditText().setHint(R.string.inch_abbr);
    
    handleUnits();
  }

  // The following static functions are some utils to interpret the values of
  // what's saved in the preferences.
  public static boolean isImperial(SharedPreferences p) {
    return p.getString(UNITS, "metric").equals("imperial");
  }
  
  public static float getEyeValue(SharedPreferences p) {
    try {
      if (isImperial(p)) {
        return Float.parseFloat(p.getString(EYE_SEPARATION_IMP, "0"));
      } else {
        return Float.parseFloat(p.getString(EYE_SEPARATION_METRIC, "0"));
      }
    } catch (NumberFormatException e) {
      return 0;
    } 
  }
  
  public static float getArmValue(SharedPreferences p) {
    try {
      if (isImperial(p)) {
        return Float.parseFloat(p.getString(ARM_LENGTH_IMP, "0"));
      } else {
        return Float.parseFloat(p.getString(ARM_LENGTH_METRIC, "0"));
      }
    } catch (NumberFormatException e) {
      return 0;
    } 
  }
  
  public static float getEyeValueMeters(SharedPreferences p) {
    float d = getEyeValue(p);
    if (isImperial(p)) {
      d = d * CM_PER_INCH / 100; // inch to m
    } else {
      d = d / 100; // cm to m
    }
    return d;
  }
  
  public static float getArmValueMeters(SharedPreferences p) {
    float d = getArmValue(p);
    if (isImperial(p)) {
      d = d * CM_PER_INCH / 100;  // inch to m
    } else {
      d = d / 100;  // cm to m
    }
    return d; 
  }
  
  private void handleUnits() {
    float armVal = getArmValue(settings);
    float eyeVal = getEyeValue(settings);
    if (isImperial(settings)) {
      units.setSummary(R.string.units_imperial);
      preferenceScreen.removePreference(arm_metric);
      preferenceScreen.addPreference(arm_imperial);
      preferenceScreen.removePreference(eye_metric);
      preferenceScreen.addPreference(eye_imperial);
      if (armVal != 0) {
        arm_imperial.setSummary(numberFormat2.format(armVal) 
            + getResources().getString(R.string.inch_abbr));
      } else {
        arm_imperial.setSummary("");
      }
      if (eyeVal != 0) {
        eye_imperial.setSummary(numberFormat2.format(eyeVal) 
            + getResources().getString(R.string.inch_abbr));
      } else {
        eye_imperial.setSummary("");
      }
    } else {
      units.setSummary(R.string.units_metric);
      preferenceScreen.removePreference(arm_imperial);
      preferenceScreen.addPreference(arm_metric);
      preferenceScreen.removePreference(eye_imperial);
      preferenceScreen.addPreference(eye_metric);
      if (armVal != 0) {
        arm_metric.setSummary(numberFormat1.format(armVal) 
            + getResources().getString(R.string.centimeter_abbr));
      } else {
        arm_metric.setSummary("");
      }
      if (eyeVal != 0) {
        eye_metric.setSummary(numberFormat1.format(eyeVal) 
            + getResources().getString(R.string.centimeter_abbr));
      } else {
        eye_metric.setSummary("");
      }
    }
  }
  
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
      String key) {
    // Always keep metric/imperial in sync, but keep reasonable precision
    // numbers on conversion so UI doesn't look so ugly.
    // Also only update corresponding setting only for the current mode,
    // otherwise there's an infinite loop of calling onSharedPreferenceChanged.
    if (key.equals(EYE_SEPARATION_IMP) && isImperial(settings)) {
      SharedPreferences.Editor edit = settings.edit();
      float v = getEyeValue(settings) * CM_PER_INCH;
      if (v != 0) {
        edit.putString(EYE_SEPARATION_METRIC, numberFormat1.format(v));
      } else {
        edit.putString(EYE_SEPARATION_METRIC, "");
      }
      edit.commit();
    } else if (key.equals(EYE_SEPARATION_METRIC) && !isImperial(settings)) {
      SharedPreferences.Editor edit = settings.edit();
      float v = getEyeValue(settings) / CM_PER_INCH;
      if (v != 0) {
        edit.putString(EYE_SEPARATION_IMP, numberFormat2.format(v));
      } else {
        edit.putString(EYE_SEPARATION_IMP, "");
      }
      edit.commit();
    } else if (key.equals(ARM_LENGTH_IMP) && isImperial(settings)) {
      SharedPreferences.Editor edit = settings.edit();
      float v = getArmValue(settings) * CM_PER_INCH;
      if (v != 0) {
        edit.putString(ARM_LENGTH_METRIC, numberFormat1.format(v));
      } else {
        edit.putString(ARM_LENGTH_METRIC, "");
      }
      edit.commit();
    } else if (key.equals(ARM_LENGTH_METRIC) && !isImperial(settings)) {
      SharedPreferences.Editor edit = settings.edit();
      float v = getArmValue(settings) / CM_PER_INCH;
      if (v != 0) {
        edit.putString(ARM_LENGTH_IMP, numberFormat2.format(v));
      } else {
        edit.putString(ARM_LENGTH_IMP, "");
      }
      edit.commit();
    }
    handleUnits();
  }
}
