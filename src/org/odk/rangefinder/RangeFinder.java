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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;

/**
 * Activity for the Range Finder.  Displays the range card, handles launching
 * preferences and returning intent values.
 *
 * @author harlanh@google.com
 */
public class RangeFinder extends Activity {
  
  public static final String PREFS_NAME = "RangeFinderPrefsFile";
  private static final int PREFS = 0;
  private static final int EXIT = 1;
  private static final int HELP = 2;
  
  private RangeCard rangeCard;
  private SharedPreferences settings;
  private AlertDialog.Builder prefdialog;
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    settings = getSharedPreferences(PREFS_NAME, 0);
    checkPreferencesOk();
    this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    DisplayMetrics metrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(metrics); 
    rangeCard = new RangeCard(this, metrics.xdpi);  
    rangeCard.paramsUpdated(settings);
    addContentView(rangeCard, new LayoutParams
        (LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(Menu.NONE, PREFS, Menu.NONE, R.string.menu_preferences);
    menu.add(Menu.NONE, HELP, Menu.NONE, R.string.menu_help);
    menu.add(Menu.NONE, EXIT, Menu.NONE, R.string.menu_exit);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case PREFS:
        startActivity(new Intent(this, RangeFinderPreferences.class));
        return true;

      case HELP:
        showHelp();
        return true;

      case EXIT:
        finish();
        return true;
    }

    return super.onOptionsItemSelected(item);
  }

  private synchronized void checkPreferencesOk() {
    if (prefdialog == null  // Seems necessary to avoid showing two dialogs.
        && (RangeFinderPreferences.getArmValue(settings) == 0 
            || RangeFinderPreferences.getEyeValue(settings) == 0)) {
      final RangeFinder thisObj = this;
      DialogInterface.OnClickListener dialogClickListener =
        new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          switch (which){
            case DialogInterface.BUTTON_POSITIVE:  // Yes button clicked
              startActivity(new Intent(thisObj, RangeFinderPreferences.class));
              break;
            case DialogInterface.BUTTON_NEGATIVE:  // No button clicked
              finish();
              break;
          }
          resetPrefDialog();
        }
      };

      prefdialog = new AlertDialog.Builder(this);
      prefdialog.setMessage(R.string.preferences_prompt).setPositiveButton(
          R.string.preferences_prompt_agree, dialogClickListener)
              .setNegativeButton(R.string.preferences_prompt_cancel, 
                  dialogClickListener).show();

    }
  }

  private void resetPrefDialog() {
    prefdialog = null;
  }

  private void showHelp() {
    // TODO: It would be nice to make this dialog fill more of the screen,
    // at least to fill in the space where there would be a status bar.
    final WebView webView = new WebView(this);
    webView.loadUrl("file:///android_asset/help.html");
    new AlertDialog.Builder(this).setView(webView)
        .setTitle(R.string.help_title).show();
  }

  @Override
  public void finish() {
    if (rangeCard.solveUserDist()) {
      Intent data = new Intent();
      data.putExtra("distance", rangeCard.getUserDistance());
      data.putExtra("accuracy", rangeCard.getUserDistanceAccuracy());
      if (RangeFinderPreferences.isImperial(settings)) { 
        data.putExtra("units", "feet");
      } else {
        data.putExtra("units", "meters");
      }
      if (rangeCard.getInclinationAtLastAdjustment() != 
        RangeCard.NO_MEASUREMENT) {
        data.putExtra("inclination", 
            rangeCard.getInclinationAtLastAdjustment());
      }
      setResult(RESULT_OK, data);
    } else {
      setResult(RESULT_CANCELED);
    }
    super.finish();
  }

  @Override
  public void onResume() {
    super.onResume();
    checkPreferencesOk();
    rangeCard.paramsUpdated(settings);
  }
}