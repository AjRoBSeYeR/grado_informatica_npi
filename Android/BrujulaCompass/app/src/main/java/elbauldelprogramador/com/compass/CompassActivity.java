/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package elbauldelprogramador.com.compass;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CompassActivity extends Activity implements RecognitionListener {

    protected final Handler mHandler = new Handler();
    private final float MAX_ROATE_DEGREE = 1.0f;
    View mCompassView;
    CompassView mPointer;
    TextView mLocationTextView;
    LinearLayout mDirectionLayout;
    LinearLayout mAngleLayout;
    private Context mCtx;
    private SensorManager mSensorManager;
    private Sensor mOrientationSensor;
    private LocationManager mLocationManager;
    private String mLocationProvider;
    LocationListener mLocationListener = new LocationListener() {

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (status != LocationProvider.OUT_OF_SERVICE) {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                updateLocation(mLocationManager.getLastKnownLocation(mLocationProvider));
            } else {
                mLocationTextView.setText(R.string.cannot_get_location);
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onLocationChanged(Location location) {
            updateLocation(location);
        }

    };
    private float mDirection;
    private float mTargetDirection;
    private AccelerateInterpolator mInterpolator;
    private boolean mStopDrawing;
    private boolean mChinease;

    @Override
    public String[] fileList() {
        return super.fileList();
    }

    public CompassActivity() {
        super();
    }

    @Override
    public void setIntent(Intent newIntent) {
        super.setIntent(newIntent);
    }

    protected Runnable mCompassViewUpdater = new Runnable() {
        @Override
        public void run() {
            if (mPointer != null && !mStopDrawing) {
                if (mDirection != mTargetDirection) {

                    // calculate the short routine
                    float to = mTargetDirection;
                    if (to - mDirection > 180) {
                        to -= 360;
                    } else if (to - mDirection < -180) {
                        to += 360;
                    }

                    // limit the max speed to MAX_ROTATE_DEGREE
                    float distance = to - mDirection;
                    if (Math.abs(distance) > MAX_ROATE_DEGREE) {
                        distance = distance > 0 ? MAX_ROATE_DEGREE : (-1.0f * MAX_ROATE_DEGREE);
                    }

                    // need to slow down if the distance is short
                    mDirection = normalizeDegree(mDirection
                            + ((to - mDirection) * mInterpolator.getInterpolation(Math
                            .abs(distance) > MAX_ROATE_DEGREE ? 0.4f : 0.3f)));
                    mPointer.updateDirection(mDirection);
                }

                updateDirection();

                mHandler.postDelayed(mCompassViewUpdater, 20);
            }
        }
    };
    private SpeechRecognizer myASR;
    private SensorEventListener mOrientationSensorEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            float direction = event.values[0] * -1.0f;
            mTargetDirection = normalizeDegree(direction);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        initResources();
        initServices();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mLocationProvider != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            updateLocation(mLocationManager.getLastKnownLocation(mLocationProvider));
            mLocationManager.requestLocationUpdates(mLocationProvider, 2000, 10, mLocationListener);
        } else {
            mLocationTextView.setText(R.string.cannot_get_location);
        }
        if (mOrientationSensor != null) {
            mSensorManager.registerListener(mOrientationSensorEventListener, mOrientationSensor,
                    SensorManager.SENSOR_DELAY_GAME);
        }
        mStopDrawing = false;
        mHandler.postDelayed(mCompassViewUpdater, 20);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mStopDrawing = true;
        if (mOrientationSensor != null) {
            mSensorManager.unregisterListener(mOrientationSensorEventListener);
        }
        if (mLocationProvider != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            mLocationManager.removeUpdates(mLocationListener);
        }
    }

    private void initResources() {
        mDirection = 0.0f;
        mTargetDirection = 0.0f;
        mInterpolator = new AccelerateInterpolator();
        mStopDrawing = true;
        mChinease = TextUtils.equals(Locale.getDefault().getLanguage(), "zh");

        mCompassView = findViewById(R.id.view_compass);
        mPointer = (CompassView) findViewById(R.id.compass_pointer);
        mLocationTextView = (TextView) findViewById(R.id.textview_location);
        mDirectionLayout = (LinearLayout) findViewById(R.id.layout_direction);
        mAngleLayout = (LinearLayout) findViewById(R.id.layout_angle);

        mPointer.setImageResource(mChinease ? R.drawable.compass_cn : R.drawable.compass);

        mCtx = this;
    }

    private void initServices() {
        // sensor manager
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mOrientationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        // location manager
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setCostAllowed(true);
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        mLocationProvider = mLocationManager.getBestProvider(criteria, true);

        // ASR
        // find out whether speech recognition is supported
        List<ResolveInfo> intActivities = getPackageManager().queryIntentActivities(
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        if (intActivities.size() != 0) {
            myASR = SpeechRecognizer.createSpeechRecognizer(mCtx);
            myASR.setRecognitionListener(this);
        } else
            myASR = null;

        startListening();
    }

    private void updateDirection() {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        mDirectionLayout.removeAllViews();
        mAngleLayout.removeAllViews();

        ImageView east = null;
        ImageView west = null;
        ImageView south = null;
        ImageView north = null;
        float direction = normalizeDegree(mTargetDirection * -1.0f);
        if (direction > 22.5f && direction < 157.5f) {
            // east
            east = new ImageView(this);
            east.setImageResource(mChinease ? R.drawable.e_cn : R.drawable.e);
            east.setLayoutParams(lp);
        } else if (direction > 202.5f && direction < 337.5f) {
            // west
            west = new ImageView(this);
            west.setImageResource(mChinease ? R.drawable.w_cn : R.drawable.w);
            west.setLayoutParams(lp);
        }

        if (direction > 112.5f && direction < 247.5f) {
            // south
            south = new ImageView(this);
            south.setImageResource(mChinease ? R.drawable.s_cn : R.drawable.s);
            south.setLayoutParams(lp);
        } else if (direction < 67.5 || direction > 292.5f) {
            // north
            north = new ImageView(this);
            north.setImageResource(mChinease ? R.drawable.n_cn : R.drawable.n);
            north.setLayoutParams(lp);
        }

        if (mChinease) {
            // east/west should be before north/south
            if (east != null) {
                mDirectionLayout.addView(east);
            }
            if (west != null) {
                mDirectionLayout.addView(west);
            }
            if (south != null) {
                mDirectionLayout.addView(south);
            }
            if (north != null) {
                mDirectionLayout.addView(north);
            }
        } else {
            // north/south should be before east/west
            if (south != null) {
                mDirectionLayout.addView(south);
            }
            if (north != null) {
                mDirectionLayout.addView(north);
            }
            if (east != null) {
                mDirectionLayout.addView(east);
            }
            if (west != null) {
                mDirectionLayout.addView(west);
            }
        }

        int direction2 = (int) direction;
        boolean show = false;
        if (direction2 >= 100) {
            mAngleLayout.addView(getNumberImage(direction2 / 100));
            direction2 %= 100;
            show = true;
        }
        if (direction2 >= 10 || show) {
            mAngleLayout.addView(getNumberImage(direction2 / 10));
            direction2 %= 10;
        }
        mAngleLayout.addView(getNumberImage(direction2));

        ImageView degreeImageView = new ImageView(this);
        degreeImageView.setImageResource(R.drawable.degree);
        degreeImageView.setLayoutParams(lp);
        mAngleLayout.addView(degreeImageView);
    }

    private ImageView getNumberImage(int number) {
        ImageView image = new ImageView(this);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        switch (number) {
            case 0:
                image.setImageResource(R.drawable.number_0);
                break;
            case 1:
                image.setImageResource(R.drawable.number_1);
                break;
            case 2:
                image.setImageResource(R.drawable.number_2);
                break;
            case 3:
                image.setImageResource(R.drawable.number_3);
                break;
            case 4:
                image.setImageResource(R.drawable.number_4);
                break;
            case 5:
                image.setImageResource(R.drawable.number_5);
                break;
            case 6:
                image.setImageResource(R.drawable.number_6);
                break;
            case 7:
                image.setImageResource(R.drawable.number_7);
                break;
            case 8:
                image.setImageResource(R.drawable.number_8);
                break;
            case 9:
                image.setImageResource(R.drawable.number_9);
                break;
        }
        image.setLayoutParams(lp);
        return image;
    }

    private void updateLocation(Location location) {
        if (location == null) {
            mLocationTextView.setText(R.string.getting_location);
        } else {
            StringBuilder sb = new StringBuilder();
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            if (latitude >= 0.0f) {
                sb.append(getString(R.string.location_north, getLocationString(latitude)));
            } else {
                sb.append(getString(R.string.location_south, getLocationString(-1.0 * latitude)));
            }

            sb.append("    ");

            if (longitude >= 0.0f) {
                sb.append(getString(R.string.location_east, getLocationString(longitude)));
            } else {
                sb.append(getString(R.string.location_west, getLocationString(-1.0 * longitude)));
            }

            mLocationTextView.setText(sb.toString());
        }
    }

    private String getLocationString(double input) {
        int du = (int) input;
        int fen = (((int) ((input - du) * 3600))) / 60;
        int miao = (((int) ((input - du) * 3600))) % 60;
        return String.valueOf(du) + "°" + String.valueOf(fen) + "'" + String.valueOf(miao) + "\"";
    }

    private float normalizeDegree(float degree) {
        return (degree + 720) % 360;
    }

    /**
     * Starts speech recognition after checking the ASR parameters
     *
     * @param language      Language used for speech recognition (e.g. Locale.ENGLISH)
     * @param languageModel Type of language model used (free form or web search)
     * @param maxResults    Maximum number of recognition results
     * @throws An exception is raised if the language specified is not available or the other parameters are not valid
     */
    public void listen(final Locale language, final String languageModel, final int maxResults) throws Exception {
        if ((languageModel.equals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) || languageModel.equals(RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)) && (maxResults >= 0)) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

            // Specify the calling package to identify the application
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, mCtx.getPackageName());
            //Caution: be careful not to use: getClass().getPackage().getName());

            // Specify language model
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, languageModel);

            // Specify how many results to receive. Results listed in order of confidence
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxResults);

            // Specify recognition language
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);

            myASR.startListening(intent);

        } else {
            Log.e(this.getLocalClassName(), "Invalid params to listen method");
            throw new Exception("Invalid params to listen method"); //If the input parameters are not valid, it throws an exception
        }

    }

    /**
     * Starts listening for any user input.
     * When it recognizes something, the <code>processAsrResult</code> method is invoked.
     * If there is any error, the <code>processAsrError</code> method is invoked.
     */
    private void startListening() {

//        if(deviceConnectedToInternet()){
        try {

        /*Start listening, with the following default parameters:
            * Language = English
            * Recognition model = Free form,
            * Number of results = 1 (we will use the best result to perform the search)
            */
            listen(Locale.ENGLISH, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, 1); //Start listening
            Toast.makeText(getApplicationContext(), "ASR STARTED and LISTENING", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            this.runOnUiThread(new Runnable() {  //Toasts must be in the main thread
                public void run() {
                    Toast.makeText(getApplicationContext(), "ASR could not be started", Toast.LENGTH_SHORT).show();
//                        changeButtonAppearanceToDefault();
                }
            });

            Log.e(this.getLocalClassName(), "ASR could not be started");
//                e.printStackTrace(); //TODO: remove later
//                try { speak("Speech recognition could not be started", "EN", ID_PROMPT_INFO); } catch (Exception ex) { Log.e(LOGTAG, "TTS not accessible"); }

        }
//        } else {
//
//            this.runOnUiThread(new Runnable() { //Toasts must be in the main thread
//                public void run() {
//                    Toast.makeText(getApplicationContext(),"Please check your Internet connection", Toast.LENGTH_SHORT).show();
////                    changeButtonAppearanceToDefault();
//                }
//            });
////            try { speak("Please check your Internet connection", "EN", ID_PROMPT_INFO); } catch (Exception ex) { Log.e(LOGTAG, "TTS not accessible"); }
//            Log.e(this.getLocalClassName(), "Device not connected to Internet");
//
//        }
    }


    @Override
    public void onReadyForSpeech(Bundle params) {

    }

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onRmsChanged(float rmsdB) {

    }

    @Override
    public void onBufferReceived(byte[] buffer) {

    }

    @Override
    public void onEndOfSpeech() {

    }

    @Override
    public void onError(int error) {
        processAsrError(error);
    }

    @Override
    public void onResults(Bundle results) {
        if (results != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {  //Checks the API level because the confidence scores are supported only from API level 14:
                //http://developer.android.com/reference/android/speech/SpeechRecognizer.html#CONFIDENCE_SCORES
                //Processes the recognition results and their confidences
                processAsrResults(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION), results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES));
                //											Attention: It is not RecognizerIntent.EXTRA_RESULTS, that is for intents (see the ASRWithIntent app)
            } else {
                //Processes the recognition results and their confidences
                processAsrResults(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION), null);
            }
        } else
            //Processes recognition errors
            processAsrError(SpeechRecognizer.ERROR_NO_MATCH);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {

    }

    @Override
    public void onEvent(int eventType, Bundle params) {

    }

    /**
     * Initiates a Google search intent with the results of the recognition
     */
    public void processAsrResults(ArrayList<String> nBestList, float[] nBestConfidences) {

        if (nBestList != null) {

            Log.d(CompassActivity.class.getSimpleName(), "ASR found " + nBestList.size() + " results");

            if (nBestList.size() > 0) {
                String bestResult = nBestList.get(0); //We will use the best result
                Log.e(CompassActivity.class.getSimpleName(), "Said: " + bestResult);
            }
        }
    }

    /**
     * Provides feedback to the user (by means of a Toast and a synthesized message) when the ASR encounters an error
     */
    public void processAsrError(int errorCode) {

        String errorMessage;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                errorMessage = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                errorMessage = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                errorMessage = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                errorMessage = "Network related error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                errorMessage = "Network operation timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                errorMessage = "No recognition result matched";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                errorMessage = "RecognitionServiceBusy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                errorMessage = "Server sends error status";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                errorMessage = "No speech input";
                break;
            default:
                errorMessage = "ASR error";
                break;
        }

        Toast.makeText(getApplicationContext(), "Speech recognition error", Toast.LENGTH_LONG).show();

        Log.e(CompassActivity.class.getSimpleName(), "Error when attempting to listen: " + errorMessage);
    }

}
