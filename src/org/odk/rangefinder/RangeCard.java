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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Paint.Align;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Generates and displays a range card to allow you to estimate distance to an
 * object using the disparity between your eyes.
 * Based on idea from this webpage:
 * http://photo.net/minox-camera-forum/003HEV
 * 
 * @author harlanh@google.com 
 */
public class RangeCard extends View {
   
  public static final int NO_MEASUREMENT = Integer.MIN_VALUE;
  
  private RangeFinder activity;
  private Resources resource;
  private InclinationReader inclinationReader;
  private float armlength = 0.60f;  // meters
  private float eyesep = 0.07f;  // meters
  private float xdpi = 0;
  private float xdpm = 0;  // dots per meter, in x dimension...
  private boolean imperial = false;  // Display feet or meters.
  
  // Used to display estimate distance to user
  private int userPixel = 40;
  private float userDist = 0;
  private String userDistStr = "";
  private float userDistAccuracy = 0;
  private int inclinationAtLastAdjustment = NO_MEASUREMENT;

  private boolean inited = false;

  private RectF buttonDone;
  private RectF buttonLeft;
  private RectF buttonRight;
  private Path arrowLeft;
  private Path arrowRight;
  private long debounceTime = 0;
  private static final long DEBOUNCE_THRESH = 300;  // milliseconds
  
  // Some default measurements to show on the card.
  private static final float [] GOOD_IMPERIAL_DISTS = 
    {3, 4, 6, 8, 12, 24, 60};  // feet
  private static final float [] GOOD_METRIC_DISTS = 
    {1, 1.5f, 2, 3, 5, 10, 20};  // meters
  
  private static final float METERS_TO_FEET =  0.3048f;
  private static final NumberFormat numberFormat3 = new DecimalFormat("0.000");
  private static final NumberFormat numberFormat2 = new DecimalFormat("0.00");
  private static final NumberFormat numberFormat1 = new DecimalFormat("0.0");
  private static final NumberFormat numberFormat0 = 
    NumberFormat.getIntegerInstance();
  
  public RangeCard(RangeFinder activity, float xdpi) {
    super(activity);
    setFocusable(true);
    this.xdpi = xdpi;
    this.xdpm = xdpi * 39.3700787f;  // inches to meters
    this.activity = activity;
    resource = activity.getResources();
    inclinationReader = new InclinationReader(activity);
  }
    
  /**
   * Updates user settings from the given preferences object.
   */
  public void paramsUpdated(SharedPreferences settings) {
    imperial = RangeFinderPreferences.isImperial(settings);
    armlength = RangeFinderPreferences.getArmValueMeters(settings);
    eyesep = RangeFinderPreferences.getEyeValueMeters(settings);
  }

  /**
   * Initializes the view.  Sets up a few things based on screen dimensions, so
   * should be called after layout.
   */
  private void init() {
    if (inited) {
      return;
    }
    int w = getWidth();
    int h = getHeight();
    userPixel = w / 2;
    // Want to scale buttons based on dpi, each 1/2" wide, 5/16 high
    int buttonw = (int) (xdpi / 2);
    int buttonh = (int) (xdpi * 5 / 16);  // assuming xdpi~=ydpi
    // 30 pixels of padding between buttons..
    buttonDone = new RectF(w - 3 * (buttonw + 30), h - buttonh - 50,
        w - 2 * buttonw - 90, h - 50);
    buttonLeft = new RectF(w - 2 * (buttonw + 30), h - buttonh - 50, 
        w - buttonw - 60, h - 50);
    buttonRight = new RectF(w - buttonw - 30, h - buttonh - 50,
        w - 30, h - 50);
    
    // This is for a right arrow centered at 0,0... invert x for left arrow.
    // Note this isn't scaled with the button, but it looks OK that way.
    float [] arrowPoints = {-30, -10, 5, -10,
                            5, -20, 30, 0,
                            5, 20, 5, 10, -30, 10};
    arrowRight = new Path();
    arrowRight.moveTo(arrowPoints[0] + buttonRight.centerX(),
        arrowPoints[1] + buttonRight.centerY());
    arrowLeft = new Path();
    arrowLeft.moveTo(-arrowPoints[0] + buttonLeft.centerX(),
        arrowPoints[1] + buttonLeft.centerY());
    for (int i = 2; i < arrowPoints.length; i += 2) {
      arrowRight.lineTo(arrowPoints[i] + buttonRight.centerX(),
          arrowPoints[i + 1] + buttonRight.centerY());
      arrowLeft.lineTo(-arrowPoints[i] + buttonLeft.centerX(),
          arrowPoints[i + 1] + buttonLeft.centerY());
    }

    inited = true;
  }
  
  @Override
  protected void onDraw(Canvas canvas) {
    init();
    Paint paint = new Paint();
    paint.setTextSize(20);
    paint.setStyle(Paint.Style.STROKE);
    paint.setColor(Color.WHITE);
    canvas.drawLine(0, 0, 0, 100, paint);
    canvas.drawText(resource.getString(R.string.instructions_line1), 
        0, 120, paint);
    canvas.drawText(resource.getString(R.string.instructions_line2), 
        0, 140, paint);
    canvas.drawText(resource.getString(R.string.instructions_line3), 
        0, 160, paint);
    canvas.drawText(resource.getString(R.string.instructions_line4), 
        0, 180, paint);
    // Draw some tick marks based on armlength A and eyesep E.
    // displacement X = E*(D-A)/D, distance to object is D
    // Or, for D given X, XD = ED-EA, D(X-E) = -EA, D = EA/(E-X)
    // For now, just render some defaults.
    // Displacement can't be more than E, and distance can't be less than A.
    float [] dists = GOOD_METRIC_DISTS;
    if (imperial) {
      dists = GOOD_IMPERIAL_DISTS;
    }
    int stagger = 0;
    canvas.drawLine(0, 0, 5, 10, paint);  // arrow end on first line
    for (float i : dists) {
      float d = i;
      String str = String.valueOf((int) i) 
          + resource.getString(R.string.meter_abbr);
      if (i % 1 > 0.1) { // ok to show a decimal
        str = String.valueOf(i) + resource.getString(R.string.meter_abbr);
      }
      if (imperial) {
        d = i * METERS_TO_FEET;
        str = String.valueOf((int) i) + resource.getString(R.string.feet_abbr);
      }
      float displ = eyesep * (d - armlength) / (d);
      // What pixel to draw at?  use dpi
      float x = displ * xdpm;
      canvas.drawLine(x, 0, x, 55 + stagger, paint);
      canvas.drawText(str, x - 8, 70 + stagger, paint);
      stagger = stagger >= 40 ? 0 : stagger + 20;
    }
    solveUserDist();
    paint.setColor(Color.RED);
    canvas.drawLine(userPixel, 0, userPixel, 175, paint);
    canvas.drawLine(userPixel, 0, userPixel - 5, 10, paint);  // arrow end
    canvas.drawLine(userPixel + 1, 0, userPixel + 6, 10, paint);  // arrow end
    canvas.drawText(userDistStr, userPixel - 10, 200, paint);

    if (inclinationReader.isSupported()) {
      paint.setColor(Color.YELLOW);
      canvas.drawText(resource.getString(R.string.inclination_label) + 
          inclinationReader.getInclination() + 
          resource.getString(R.string.units_degrees),
          10, buttonDone.centerY(), paint);
    }
    
    drawButtons(canvas);
    // Just for fun, draw a ruler on the other edge
    drawRuler(canvas);
    super.onDraw(canvas);
  }
  
  /**
   * Draws the left, right, and done buttons on the display.
   */
  private void drawButtons(Canvas canvas) {
    Paint paint = new Paint();
    paint.setTextSize(25);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(Color.GRAY);
    canvas.drawRoundRect(buttonDone, 4, 4, paint);
    canvas.drawRoundRect(buttonLeft, 4, 4, paint);
    canvas.drawRoundRect(buttonRight, 4, 4, paint);
    paint.setTextAlign(Align.CENTER);
    paint.setColor(Color.BLACK);
    canvas.drawText(resource.getString(R.string.done_button),
        buttonDone.centerX(), buttonDone.centerY() + 10, paint);
    canvas.drawPath(arrowLeft, paint);
    canvas.drawPath(arrowRight, paint);
  }
  
  /**
   * Solves the distance based on user settings.
   * @return false if parameters are not set
   */
  public boolean solveUserDist() {
    if (xdpm == 0 || eyesep == 0 || armlength == 0) {
      return false;
    }
    float userDisp = userPixel / xdpm;
    userDist = eyesep * armlength / (eyesep - userDisp);
    // Method to determine accuracy... 
    // How much change would 1/4 mm in displacement change distance?
    // This is about around 2 pixels on normal dpi range devices.
    // Go this amount on either side of user value and then average difference.
    // This could still be something smarter...
    float delta = 0.00025f;
    float acc1 = (eyesep * armlength 
        / (eyesep - (userDisp + delta))) - userDist;
    float acc2 = userDist - (eyesep * armlength
        / (eyesep - (userDisp - delta)));
    userDistAccuracy = (acc1 + acc2)/2;
    // Accuracy could be as bad as infinity, which isn't very useful, so cap
    // it at 100% error.  Negative means some kind of error, so cap that too.
    if (userDistAccuracy > userDist || userDistAccuracy < 0) {
      System.out.println("acc is "+userDistAccuracy);
      userDistAccuracy = userDist;
    }
    // Number format accuracy as appropriate...  Could be smarter about 
    // formatting based on imperial/metric, but doesn't really matter.
    NumberFormat numberFormat = numberFormat3;
    if (userDistAccuracy > 1) {
      numberFormat = numberFormat0;
    } else if (userDistAccuracy > 0.1) {
      numberFormat = numberFormat1;
    } else if (userDistAccuracy > 0.01) {
      numberFormat = numberFormat2;
    }
    userDistStr = numberFormat.format(userDist) 
        + resource.getString(R.string.meter_abbr);
    if (imperial) {
      userDistStr = numberFormat.format(userDist / METERS_TO_FEET)
          + resource.getString(R.string.feet_abbr);
    }
    if (userDisp >= eyesep) {
      userDisp = eyesep;
      userDistAccuracy = -1;
      userDistStr = resource.getString(R.string.infinity);
    }
    return true;
  }
  
  public float getUserDistance() {
    return userDist;
  }
  
  public float getUserDistanceAccuracy() {
    return userDistAccuracy;
  }
  
  private void drawRuler(Canvas canvas) {
    Paint paint = new Paint();
    paint.setTextSize(18);
    paint.setStyle(Paint.Style.STROKE);
    paint.setColor(Color.WHITE);
    int h = getHeight();
    if (imperial) {
      float inches = getWidth() / xdpi;
      // divide inches into 1/16ths
      int endinches = (int) (inches * 16);
      for (int i = 0; i <= endinches; i++) {  // in 16ths of inch 
        int p = (int) (i * xdpi / 16);
        if (i % 16 == 0) {
          canvas.drawLine(p, h - 28, p, h, paint);
          canvas.drawText(String.valueOf(i / 16), p, h - 30, paint);
        } else if (i % 8 == 0) {
          canvas.drawLine(p, h - 22, p, h, paint);
        } else if (i % 4 == 0) {
          canvas.drawLine(p, h - 15, p, h, paint);
        } else {
          canvas.drawLine(p, h - 8, p, h, paint);
        }
      }
    } else {
      float cm = getWidth() / xdpm * 100;
      // divide cm's into 1/10ths
      int endcm = (int) (cm * 10);
      for (int i = 0; i <= endcm; i++) {  // in 10ths of cms 
        int p = (int) (i * xdpm / 1000);
        if (i % 10 == 0) {
          canvas.drawLine(p, h - 25, p, h, paint);
          canvas.drawText(String.valueOf(i / 10), p, h - 30, paint);
        } else if (i % 5 == 0) {
          canvas.drawLine(p, h - 17, p, h, paint);
        } else {
          canvas.drawLine(p, h - 10, p, h, paint);
        }
      }
    }
  }
  
  @Override
  public boolean onTrackballEvent(MotionEvent event) {
    // Handle trackball to move userPixel (red line).
    int incr = (int) (event.getX() * 10);
    if (incr == 0) {
      if (event.getX() > 0) {
        incr = 1;
      } else {
        incr = -1;
      }
    }
    userPixel += incr;
    handleUserPixelChanged();
    return true;
  }
  
  @Override
  public boolean onTouchEvent(MotionEvent evt) {
    // Implement basic debouncing to avoid unnecessary multiple triggering.
    // Will only trigger once in first 300ms, and then will act like key
    // repeat.  So you can touch and hold on left/right buttons to move more.
    boolean isDebounced = false;
    if (evt.getAction() == MotionEvent.ACTION_DOWN) {
      debounceTime = System.currentTimeMillis();
      isDebounced = true;
    } else if (System.currentTimeMillis() - debounceTime > DEBOUNCE_THRESH) {
      isDebounced = true;
    }
    // If above the buttons, then use it to position userPixel (red line).
    // Otherwise count it as a button press.
    if (evt.getY() < buttonDone.top - 10) {
      userPixel = (int) evt.getX();
      handleUserPixelChanged();
    } else if (isDebounced && buttonLeft.contains(evt.getX(), evt.getY())) {
      userPixel -= 1;
      handleUserPixelChanged();
    } else if (isDebounced && buttonRight.contains(evt.getX(), evt.getY())) {
      userPixel += 1;
      handleUserPixelChanged();
    } else if (isDebounced && buttonDone.contains(evt.getX(), evt.getY())) {
      activity.finish();
    }
    return true;
  }
  
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    // Handle keypad left/right to move userPixel (red line).
    boolean adjusted = false;
    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
      userPixel -= 1;
      adjusted = true;
    } 
    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
      userPixel += 1;
      adjusted = true;
    }
    if (adjusted) {
      handleUserPixelChanged();
      return true;
    }
    return false;
  }
  
  /**
   * Cap the values of user pixel to screen size and set inclination reading,
   * and redraw.
   */
  private void handleUserPixelChanged() {
    if (userPixel < 0) {
      userPixel = 0;
    }
    if (userPixel > getWidth()) {
      userPixel = getWidth();
    }
    if (inclinationReader.isSupported()) {
      inclinationAtLastAdjustment = inclinationReader.getInclination();
    }
    this.invalidate();
  }
  
  public int getInclinationAtLastAdjustment() {
    return inclinationAtLastAdjustment;
  }
  
  public class InclinationReader implements SensorEventListener {

    private boolean supported = false;
    private SensorManager sensorMgr;
    private double inclination;
    // TODO: possibly provide some calibration.
    
    public InclinationReader(Context c) {
      sensorMgr = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
      Sensor sensor = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
      if (sensor != null) {
        supported = sensorMgr.registerListener(this, sensor,
            SensorManager.SENSOR_DELAY_NORMAL);  // Relatively low update rate.
      }
    }

    public boolean isSupported() {
      return supported;
    }
    
    public int getInclination() {
      return (int)Math.toDegrees(inclination);
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
      // Measure angle between z (up) and the x-y plane.
      float x = event.values[0];
      float y = event.values[1];
      float z = event.values[2];
      double magnitude = Math.sqrt(x * x + y * y + z * z);
      // Invert this because want straight up to be 90, down -90.
      inclination = - Math.asin(z / magnitude);
      invalidate();  // Redraw.
    }
  }
}
