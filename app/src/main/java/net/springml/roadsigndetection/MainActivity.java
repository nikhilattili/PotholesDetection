package net.springml.roadsigndetection;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import net.springml.potholesdetection.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    WifiManager wifiManager;
    public static String currentLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageView clickButton = (ImageView) findViewById(R.id.button);
        clickButton.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MainActivity.this, DetectorActivity.class);
                DetectorActivity.TF_OD_API_MODEL_FILE = "roadsign.tflite";
                DetectorActivity.TF_OD_API_LABELS_FILE = "file:///android_asset/roadsign.txt";
                DetectorActivity.TF_OD_API_INPUT_SIZE = 320;
                TFLiteObjectDetectionAPIModel.NUM_DETECTIONS = 40;
                DetectorActivity.MINIMUM_CONFIDENCE_TF_OD_API = 0.8f;
                myIntent.putExtra("key", "road");
                MainActivity.this.startActivity(myIntent);
            }
        });
        ImageView clickButton2 = (ImageView) findViewById(R.id.button2);
        clickButton2.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MainActivity.this, DetectorActivity.class);
                DetectorActivity.TF_OD_API_MODEL_FILE = "potholes.tflite";
                DetectorActivity.TF_OD_API_LABELS_FILE = "file:///android_asset/potholes.txt";
                DetectorActivity.TF_OD_API_INPUT_SIZE = 300;
                TFLiteObjectDetectionAPIModel.NUM_DETECTIONS = 10;
                DetectorActivity.MINIMUM_CONFIDENCE_TF_OD_API = 0.8f;
                myIntent.putExtra("key", "pothole");
                MainActivity.this.startActivity(myIntent);
            }
        });
        ImageView clickButton3 = (ImageView) findViewById(R.id.button3);
        clickButton3.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MainActivity.this, DetectorActivity.class);
                DetectorActivity.TF_OD_API_MODEL_FILE = "manhole5.tflite";
                DetectorActivity.TF_OD_API_LABELS_FILE = "file:///android_asset/manhole.txt";
                DetectorActivity.TF_OD_API_INPUT_SIZE = 320;
                TFLiteObjectDetectionAPIModel.NUM_DETECTIONS = 40;
                DetectorActivity.MINIMUM_CONFIDENCE_TF_OD_API = 0.9f;
                myIntent.putExtra("key", "manhole");
                MainActivity.this.startActivity(myIntent);
            }
        });


//        WIFI LOCATION


        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        List<ScanResult> apList = wifiManager.getScanResults();
        System.out.println(apList);
        System.out.println(apList.size());
        String jsonString = null;
        List l = new ArrayList();
//        BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context c, Intent intent) {
//                boolean success = intent.getBooleanExtra(
//                        WifiManager.EXTRA_RESULTS_UPDATED, false);
//                if (success) {
//                    scanSuccess();
//                } else {
//                    // scan failure handling
//                    scanFailure();
//                }
//            }
//        };
//        IntentFilter intentFilter = new IntentFilter();
//        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
//        getApplicationContext().registerReceiver(wifiScanReceiver, intentFilter);

//        boolean success = wifiManager.startScan();
//        if (!success) {
//            // scan failure handling
//            scanFailure();
//        }
        try {
            for(int i=0; i< apList.size(); i++) {
                l.add(new JSONObject().put("macAddress", apList.get(i).BSSID).put("signalStrength", apList.get(i).level).put("signalToNoiseRatio", 0));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        System.out.println("qwerty"+l);
        try {
            jsonString = new JSONObject()
                    .put("considerIp", "false")
                    .put("wifiAccessPoints", l)
                    .toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        System.out.println("qwerty"+jsonString);
        System.out.println("qwertyuiop"+currentLocation);
        if(currentLocation==null)
        new sendRequest("https://www.googleapis.com/geolocation/v1/geolocate?key=AIzaSyCCyqFalDEwiwEgzHJzW2Sq1cLXU-la-8E",jsonString).execute();
//
    }
    public class sendRequest extends AsyncTask<Void, Void, Bitmap> {

        private String apiurl;
        private String jsonString;

        public sendRequest(String apiurl, String pid) {
            this.apiurl = apiurl;
            this.jsonString = pid;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            try{
                URL url = new URL(apiurl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(false);
                String payload = jsonString;
                System.out.println("qwerty"+payload);
                byte[] out = payload.getBytes(StandardCharsets.UTF_8);
                OutputStream stream = connection.getOutputStream();
                DataOutputStream output = new DataOutputStream(stream);

                output.writeBytes(new String(out));
                output.close();
                DataInputStream input = new DataInputStream(connection.getInputStream());
                byte[] buffer = new byte[1024];
                StringBuffer sb = new StringBuffer();
                InputStreamReader isReader = new InputStreamReader(input);

                BufferedReader reader = new BufferedReader(isReader);
                String str;
                while ((str = reader.readLine()) != null) {
                    sb.append(str);
                }
                System.out.print("no"+sb);

                JSONObject obj = new JSONObject(sb.toString());
                JSONObject location = obj.getJSONObject("location");
                System.out.println("hello"+obj);
                String urlMap = String.format("https://maps.googleapis.com/maps/api/geocode/json?latlng=%s,%s&key=AIzaSyCCyqFalDEwiwEgzHJzW2Sq1cLXU-la-8E",location.getDouble("lat"), location.getDouble("lng"));
                System.out.print("qwertyu"+urlMap);
                URL url2 = new URL(urlMap);

                HttpURLConnection connection2 = (HttpURLConnection) url2.openConnection();

                InputStream input2 = connection2.getInputStream();
                StringBuffer sb2 = new StringBuffer();
                InputStreamReader isReader2 = new InputStreamReader(input2);
                int data = isReader2.read();
                while (data != -1) {
                    char current = (char) data;
                    data = isReader2.read();
                    sb2.append(current);
                }
                System.out.print("qwert"+sb2);
                JSONObject obj2 = new JSONObject(sb2.toString());
                JSONArray array = obj2.getJSONArray("results");
                System.out.println("hello"+array);
                String form_address= new String();
                for(int i=0; i < array.length(); i++)
                {
                    System.out.println(i);
                    JSONObject object = array.getJSONObject(i);
                    System.out.println(object.get("formatted_address"));
                    form_address = object.get("formatted_address").toString();
                    break;
                }
                currentLocation = form_address;

                connection.disconnect();

                connection2.disconnect();
            }catch (Exception e){
                System.out.println("errorrrr"+e);
                System.out.println("Failed");
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap result) {

            super.onPostExecute(result);
        }

    }
//    private void scanSuccess() {
//        List<ScanResult> results = wifiManager.getScanResults();
//        System.out.println("asdf"+results);
//    }
//
//    private void scanFailure() {
//        // handle failure: new scan did NOT succeed
//        // consider using old scan results: these are the OLD results!
//        List<ScanResult> results = wifiManager.getScanResults();
//        System.out.println("asdf"+results);
//
//    }
}