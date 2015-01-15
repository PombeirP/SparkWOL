package com.pedropombeiro.sparkwol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity {

    private InvokeSparkPostMethodTask invokeSparkPostMethodTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent myIntent = new Intent(this, SettingsActivity.class);
            //myIntent.putExtra("key", value); //Optional parameters
            this.startActivity(myIntent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onWakeComputerButtonClick(View view) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String deviceId = sharedPreferences.getString("device_id", "");
        String authenticationToken = sharedPreferences.getString("authentication_token", "");
        String ipAddress = sharedPreferences.getString("ip_address", "");
        String macAddress = NetworkHelpers.GetMacFromArpCache(ipAddress);

        if (deviceId.length() == 0)
        {
            Toast.makeText(this.getBaseContext(), "Target Spark device not defined", Toast.LENGTH_LONG).show();
            return;
        }
        if (authenticationToken.length() == 0)
        {
            Toast.makeText(this.getBaseContext(), "Authentication token not defined", Toast.LENGTH_LONG).show();
            return;
        }
        if (ipAddress.length() == 0)
        {
            Toast.makeText(this.getBaseContext(), "Target IP address not defined", Toast.LENGTH_LONG).show();
            return;
        }
        if (macAddress.length() == 0)
        {
            Toast.makeText(this.getBaseContext(), "Could not retrieve target MAC address", Toast.LENGTH_LONG).show();
            return;
        }

        this.invokeSparkPostMethodTask = new InvokeSparkPostMethodTask("wakeHome", deviceId, authenticationToken, ipAddress, macAddress);
        this.invokeSparkPostMethodTask.execute();
    }

    private class InvokeSparkPostMethodTask extends InvokeHttpMethodTaskBase {
        private final String method;
        private final String deviceId;
        private final String ipAddress;
        private final String macAddress;

        public InvokeSparkPostMethodTask(String method, String deviceId, String authenticationToken, String ipAddress, String macAddress) {
            super(authenticationToken);

            this.method = method;
            this.deviceId = deviceId;
            this.ipAddress = ipAddress;
            this.macAddress = macAddress;
        }

        @Override
        protected String GetUrl() {
            return String.format("https://api.spark.io/v1/devices/%s/%s", this.deviceId, this.method);
        }

        @Override
        protected String doInBackground(String... params) {
            InputStream inputStream = null;
            String result = "";

            // HTTP Post
            try {
                HttpClient httpclient = new DefaultHttpClient();
                HttpPost request = new HttpPost(this.GetUrl());
                request.setHeader("Accept", "application/json");
                request.addHeader("Authorization", String.format("Bearer %s", this.authenticationToken));

                List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);

                nameValuePairs.add(new BasicNameValuePair("args",String.format("%s;%s", this.ipAddress, this.macAddress)));
                request.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                HttpResponse httpResponse = httpclient.execute(request);

                // receive response as inputStream
                inputStream = httpResponse.getEntity().getContent();

                if(inputStream != null)
                    result = convertInputStreamToString(inputStream);
                else
                    result = "Did not work!";
            } catch (Exception e) {
                System.out.println(e.getMessage());
                return e.getMessage();
            }
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                JSONObject response = new JSONObject(result);
                if (response.has("code")) {
                    int code = response.getInt("code");
                    if (code >= 400) {
                        Toast.makeText(getBaseContext(), String.format("Couldn't send wake request:\n%s", response.getString("error_description")), Toast.LENGTH_LONG).show();
                        Log.i("FromOnPostExecute", result);
                        return;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }

            Toast.makeText(getBaseContext(), "Sent wake request", Toast.LENGTH_LONG).show();
            Log.i("FromOnPostExecute", result);

            Runnable getStateVariableRunnable = new Runnable() {
                @Override
                public void run() {
                    new InvokeSparkGetStateVariableMethodTask(deviceId, authenticationToken).execute();
                }
            };

            Handler handler = new Handler();
            handler.postDelayed(getStateVariableRunnable, 2000);
        }
    }

    private class InvokeSparkGetStateVariableMethodTask extends InvokeSparkGetMethodTaskBase {
        private String deviceId;

        public InvokeSparkGetStateVariableMethodTask(String deviceId, String authToken) {
            super(authToken);
            this.deviceId = deviceId;
        }

        @Override
        protected String GetUrl() {
            return String.format("https://api.spark.io/v1/devices/%s/state", this.deviceId);
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            try {
                JSONObject response = new JSONObject(result);
                if (response.has("code")) {
                    int code = response.getInt("code");
                    if (code >= 400) {
                        Toast.makeText(getBaseContext(), String.format("Could not retrieve status from Spark:\n%s", response.getString("error_description")), Toast.LENGTH_LONG).show();
                        Log.i("FromOnPostExecute", result);
                        return;
                    }
                }

                String state = response.getString("result");
                switch (state)
                {
                    case "Sent WOL":
                    case "Pinging":
                    {
                        Runnable runnable = new Runnable() {
                            @Override
                            public void run() {
                                new InvokeSparkGetStateVariableMethodTask(deviceId, authenticationToken).execute();
                            }
                        };

                        Handler handler = new Handler();
                        handler.postDelayed(runnable, 2000);
                        break;
                    }
                    case "Unreachable":
                        Toast.makeText(getBaseContext(), "Could not contact the target machine :-(", Toast.LENGTH_LONG).show();
                        break;
                    case "Reachable":
                        Toast.makeText(getBaseContext(), "Machine is awake!", Toast.LENGTH_LONG).show();
                        break;
                }
            } catch (JSONException e) {
                Toast.makeText(getBaseContext(), "Could not retrieve status from Spark", Toast.LENGTH_LONG).show();
                Log.w("FromOnPostExecute", e.getMessage());
            }
        }
    }
}