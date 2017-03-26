package hackemory.dementiaapp;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;


public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks, LocationListener {
    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private Button mCallApiButton;
    ProgressDialog mProgress;


    LocationManager locationManager;
    String mprovider;
    long startTime, difference;
    boolean shopped;
    TextView longitude, latitude, inLocation, nearLocation, didTask;


    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String BUTTON_TEXT = "Call Gmail API";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { GmailScopes.GMAIL_LABELS, GmailScopes.GMAIL_READONLY};
    private Context con;
    private boolean storeisneargoshopping;
    private boolean finishedShoppingHeadHome;
    private boolean timeToGoShopping;
    private boolean goHome;

    //Creating a broadcast receiver for gcm registration
    private BroadcastReceiver mRegistrationBroadcastReceiver;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        LinearLayout activityLayout = new LinearLayout(this);
//        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.MATCH_PARENT,
//                LinearLayout.LayoutParams.MATCH_PARENT);
//        activityLayout.setLayoutParams(lp);
//        activityLayout.setOrientation(LinearLayout.VERTICAL);
//        activityLayout.setPadding(16, 16, 16, 16);

        longitude = (TextView) findViewById(R.id.textView1);
        latitude = (TextView) findViewById(R.id.textView);
        inLocation = (TextView) findViewById(R.id.textView2);
        nearLocation = (TextView) findViewById(R.id.textView3);
        didTask = (TextView) findViewById(R.id.textView4);
        nearLocation.setText("near location : false");
        didTask.setText("shopping complete : false");
        inLocation.setText("in small box: false");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        mprovider = locationManager.getBestProvider(criteria, false);

        if (mprovider != null && !mprovider.equals("")) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            Location location = locationManager.getLastKnownLocation(mprovider);
            locationManager.requestLocationUpdates(mprovider, 1000, 1, this);

            if (location != null)
                onLocationChanged(location);
            else
                Toast.makeText(getBaseContext(), "No Location Provider Found Check Your Code",
                        Toast.LENGTH_SHORT).show();
        }



        con = this.getApplicationContext();

//        ViewGroup.LayoutParams tlp = new ViewGroup.LayoutParams(
//                ViewGroup.LayoutParams.WRAP_CONTENT,
//                ViewGroup.LayoutParams.WRAP_CONTENT);

        mCallApiButton = (Button) findViewById(R.id.callAPIButton);
        mCallApiButton.setText(BUTTON_TEXT);
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallApiButton.setEnabled(false);
                mOutputText.setText("");
                getResultsFromApi();
                mCallApiButton.setEnabled(true);
            }
        });
//        activityLayout.addView(mCallApiButton);

        mOutputText = (TextView) findViewById(R.id.Output);
        mOutputText.setVerticalScrollBarEnabled(true);
        mOutputText.setMovementMethod(new ScrollingMovementMethod());
        mOutputText.setText(
                "Click the \'" + BUTTON_TEXT +"\' button to test the API.");

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Gmail API ...");

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

        //Initializing our broadcast receiver
        mRegistrationBroadcastReceiver = new BroadcastReceiver() {

            //When the broadcast received
            //We are sending the broadcast from GCMRegistrationIntentService

            @Override
            public void onReceive(Context context, Intent intent) {
                //If the broadcast has received with success
                //that means device is registered successfully
                if(intent.getAction().equals(GCMRegistrationIntentService.REGISTRATION_SUCCESS)){
                    //Getting the registration token from the intent
                    String token = intent.getStringExtra("token");
                    //Displaying the token as toast
                    Toast.makeText(getApplicationContext(), "Registration token:" + token, Toast.LENGTH_LONG).show();

                    //if the intent is not with success then displaying error messages
                } else if(intent.getAction().equals(GCMRegistrationIntentService.REGISTRATION_ERROR)){
                    Toast.makeText(getApplicationContext(), "GCM registration error!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error occurred", Toast.LENGTH_LONG).show();
                }
            }
        };

        //Checking play service is available or not
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());

        //if play service is not available
        if(ConnectionResult.SUCCESS != resultCode) {
            //If play service is supported but not installed
            if(GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                //Displaying message that play service is not installed
                Toast.makeText(getApplicationContext(), "Google Play Service is not install/enabled in this device!", Toast.LENGTH_LONG).show();
                GooglePlayServicesUtil.showErrorNotification(resultCode, getApplicationContext());

                //If play service is not supported
                //Displaying an error message
            } else {
                Toast.makeText(getApplicationContext(), "This device does not support for Google Play Service!", Toast.LENGTH_LONG).show();
            }

            //If play service is available
        } else {
            //Starting intent to register device
            Intent itent = new Intent(this, GCMRegistrationIntentService.class);
            startService(itent);
        }
    }

    //Registering receiver on activity resume
    @Override
    protected void onResume() {
        super.onResume();
        Log.w("MainActivity", "onResume");
        LocalBroadcastManager.getInstance(this).registerReceiver(mRegistrationBroadcastReceiver,
                new IntentFilter(GCMRegistrationIntentService.REGISTRATION_SUCCESS));
        LocalBroadcastManager.getInstance(this).registerReceiver(mRegistrationBroadcastReceiver,
                new IntentFilter(GCMRegistrationIntentService.REGISTRATION_ERROR));
    }


    //Unregistering receiver on activity paused
    @Override
    protected void onPause() {
        super.onPause();
        Log.w("MainActivity", "onPause");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mRegistrationBroadcastReceiver);
    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    public void getResultsFromApi() {
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (! isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            new MakeRequestTask(mCredential).execute();
        }
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
//            EasyPermissions.requestPermissions(
//                    this,
//                    "This app needs to access your Google account (via Contacts).",
//                    REQUEST_PERMISSION_GET_ACCOUNTS,
//                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    /**
     * An asynchronous task that handles the Gmail API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
        private com.google.api.services.gmail.Gmail mService = null;
        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Gmail API Android Quickstart")
                    .build();
        }

        /**
         * Background task to call Gmail API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of Gmail labels attached to the specified account.
         * @return List of Strings labels.
         * @throws IOException
         */
        private List<String> getDataFromApi() throws IOException {
            // Get the labels in the user's account.
            String user = "me";

            ListMessagesResponse messagesResponse = mService.users().messages().list(user).execute();
            List<Message> messages = messagesResponse.getMessages();
            List<String> labels = lookThroughEmails(messages);

            return labels;
        }

        private List<String> lookThroughEmails (List<Message> messages) throws IOException {
            String user = "me";
            List<String> labels = new ArrayList<String>();
            for (Message message : messages) {
                message = mService.users().messages().get(user, message.getId()).execute();

                try{
                    List<MessagePart> parts  =message.getPayload().getParts();
                    List<MessagePartHeader> headers = message.getPayload().getHeaders();
                    MessageReader mreader = readParts(parts);
                    //mreader.setDate(message.getInternalDate());
                    for(MessagePartHeader header:headers){
                        String name = header.getName();
                        if(name.equals("From")||name.equals("from")){
                            mreader.setSender(header.getValue());
                        }

                        if(name.equals("Subject") || name.equals("subject")) {
                            mreader.setSubject(header.getValue());
                            break;
                        }
                    }

                    String sender = mreader.getSender();
                    boolean librayNotfication = false;
                    if (sender.contains("library") || sender.contains("Library")) {
                        librayNotfication = true;
                    }

                    String subject = mreader.getSubject();
                    if(librayNotfication) {
                        labels.add(subject);
                    }

                    if(subject.contains("bill")) {
                        String dueDate = getDueDate(mreader.getText());
                        labels.add(dueDate);
                        //labels.add(mreader.getSubject());
                    }

                }catch(Exception e){

                }
            }
            return labels;
        }
        private String getDueDate(String text) {
            String result = "";
            List<String> linesWithDue = new ArrayList<>();
            Matcher m = Pattern.compile("(.+Due.+|.+due.+)").matcher(text);
            while (m.find()) {
                linesWithDue.add(m.group());
            }

            String lineWithMonth = "";
            boolean monthNotFound = true;
            for (String line: linesWithDue) {
                Matcher month = Pattern.compile("(January|February|March|April|May|June|July|August|September|October|November|December)").matcher(line);
                if(month.find()) {
                    lineWithMonth = line;
                    String dueMonth  = month.group();
                    Matcher dueDayFinder = Pattern.compile("(\\s\\d\\d\\s|\\s\\d\\s)").matcher(lineWithMonth);
                    String dueDay = "";
                    if (dueDayFinder.find()) {
                        dueDay = dueDayFinder.group();
                    }
                    result = dueMonth + " " + dueDay;
                    monthNotFound = false;
                    break;
                }
            }

            if (monthNotFound) {
                Matcher dueDayFinder = Pattern.compile("\\$[0-9.]+\\s+(\\d\\d/\\d\\d/\\d\\d)").matcher(text);
                if(dueDayFinder.find()) {
                    result = dueDayFinder.group(1);
                }
            }
            return result;
        }

        @Override
        protected void onPreExecute() {
            mOutputText.setText("");
            mProgress.show();
        }

        @Override
        protected void onPostExecute(List<String> output) {
            mProgress.hide();
            if (output == null || output.size() == 0) {
                mOutputText.setText("No results returned.");
            } else {
                //output.add(0, "Data retrieved using the Gmail API:");
                mOutputText.setText(TextUtils.join("\n", output));
                int id = 0;
                for (String message: output) {
                    NotificationCompat.Builder mBuilder =
                            (NotificationCompat.Builder) new NotificationCompat.Builder(con)
                                    .setSmallIcon(R.drawable.common_google_signin_btn_icon_dark)
                                    .setContentTitle("Notification " + (id + 1))
                                    .setContentText(message);

                    NotificationManager mNotificationManager =
                            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    mNotificationManager.notify(id,mBuilder.build());
                    id++;
                }
            }
        }

        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }
    }

    private MessageReader readParts(List<MessagePart> parts){
        MessageReader mreader = new MessageReader();
        int cnt =0;
        for(MessagePart part:parts){

            try{

                String mime = part.getMimeType();
                if(mime.contentEquals("text/plain")){
                    String s = new String(Base64.decodeBase64(part.getBody().getData().getBytes()));
                    mreader.setText(s);
                }else if(mime.contentEquals("text/html")){
                    String s = new String(Base64.decodeBase64(part.getBody().getData().getBytes()));
                    mreader.setHtml(s);

                }else if(mime.contentEquals("multipart/alternative")){
                    List<MessagePart> subparts  =part.getParts();
                    MessageReader subreader = readParts(subparts);
                    mreader.setText(subreader.getText());
                    mreader.setHtml(subreader.getHtml());
                }else if(mime.contentEquals("application/octet-stream")){
                    cnt++;
                    mreader.setNo_of_files(cnt);
                }

            }catch(Exception e){
                // get file here

            }

        }
        return mreader;
    }





    public class MessageReader {

        private String text;
        private String html;
        int no_of_files;
        private String sender, subject;
        private long date;
        // file data to be made

        public String getSubject() {
            return subject;
        }
        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getSender() {
            return sender;
        }
        public void setSender(String sender) {
            this.sender = sender;
        }
        public long getDate() {
            return date;
        }

        public void setDate(long date) {
            this.date = date;
        }
        public String getText() {
            return text;
        }
        public void setText(String text) {
            this.text = text;
        }
        public String getHtml() {
            return html;
        }
        public void setHtml(String html) {
            this.html = html;
        }
        public int getNo_of_files() {
            return no_of_files;
        }
        public void setNo_of_files(int no_of_files) {
            this.no_of_files = no_of_files;
        }



    }

    @Override
    public void onLocationChanged(Location location) {

        int id = 10;
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        longitude.setText("Longitude:" + location.getLongitude());
        latitude.setText("Latitude:" + location.getLatitude());

        String message = "";
        if(!shopped) {
            if (((lat > 33.789260) && (lat < 33.789894)) && ((lon > -84.327046) && (lon < -84.326246))) {
                nearLocation.setText("near location : true");
                inLocation.setText("in small box: false");
                message = "store is near, go shopping";
                if (((lat > 33.789515) && (lat < 33.789679)) && ((lon > -84.326657) && (lon < -84.326392))) {
                    nearLocation.setText("near location : true");
                    inLocation.setText("in small box: true");
                    if (startTime == 0) {
                        startTime = System.currentTimeMillis();
                    } else {
                        difference = (System.currentTimeMillis() - startTime) / 1000;
                        if (difference > 10) {
                            didTask.setText("shopping complete : true");
                            message = "finished shopping, head home";
                            if(!finishedShoppingHeadHome) {
                                NotificationCompat.Builder mBuilder =
                                        (NotificationCompat.Builder) new NotificationCompat.Builder(con)
                                                .setSmallIcon(R.drawable.common_google_signin_btn_icon_dark)
                                                .setContentTitle("Notification " + (id + 1))
                                                .setContentText(message);

                                NotificationManager mNotificationManager =
                                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                                mNotificationManager.notify(id, mBuilder.build());
                                finishedShoppingHeadHome = true;
                            }
                            shopped = true;
                            startTime = System.currentTimeMillis();
                        }
                    }
                } else {
                    inLocation.setText("in small box: false");
                    startTime = 0;
                }
            } else {
                nearLocation.setText("near location : false");
            }
        } else {
            difference = (System.currentTimeMillis() - startTime) / 1000;
            if (difference > 60) {
                shopped = false;
                didTask.setText("shopping complete : false");
                message = "time to go shopping";
                if(!timeToGoShopping) {
                    NotificationCompat.Builder mBuilder =
                            (NotificationCompat.Builder) new NotificationCompat.Builder(con)
                                    .setSmallIcon(R.drawable.common_google_signin_btn_icon_dark)
                                    .setContentTitle("Notification " + (id + 1))
                                    .setContentText(message);

                    NotificationManager mNotificationManager =
                            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    mNotificationManager.notify(id, mBuilder.build());
                    timeToGoShopping = true;
                }
                finishedShoppingHeadHome = false;
                storeisneargoshopping = false;
                timeToGoShopping = false;
                goHome = false;
                startTime = 0;
            } else {
                if(!goHome) {
                    message = "done shopping, go home";
                    NotificationCompat.Builder mBuilder =
                            (NotificationCompat.Builder) new NotificationCompat.Builder(con)
                                    .setSmallIcon(R.drawable.common_google_signin_btn_icon_dark)
                                    .setContentTitle("Notification " + (id + 1))
                                    .setContentText(message);

                    NotificationManager mNotificationManager =
                            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    mNotificationManager.notify(id, mBuilder.build());
                    goHome = true;
                }
            }
        }
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }
}
