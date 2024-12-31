package com.example.ttfencing;

import static android.view.View.VISIBLE;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
//import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothSocket;
//import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

//public class MainActivity extends AppCompatActivity implements View.OnClickListener {
public class MainActivity extends AppCompatActivity {

    Button timerButton, leftUpButton, leftDownButton, rightUpButton, rightDownButton, connectButton;
    TextView leftScoreText, rightScoreText, screenTimerText;
    View leftFlash, rightFlash;
    int leftScore, rightScore;
    float secondsLeft;
    boolean timerRunning;
    Timer timer;
    TimerTask timerTask;
    float timeRound;
    int errCode;
    int rightOfWayHolder; // 0 for left, 1 for right
    private UUID appUUID;
    private BluetoothAdapter bluetoothAdapter;
    private ArrayList<BluetoothDevice> foundDevices;

    public static final int REQUEST_CONNECT_PERMISSION = 1;
    public static final int DISCOVERY_REQUEST = 2;

    LaptopThread laptopThread;
    BluetoothTTFencing bluetoothThread;
    BluetoothSocket laptopSocket;

    FencingBluno fencingBlunoLeft;
    FencingBluno fencingBlunoRight;

    private boolean lbHadTouchedOther = false;
    private boolean rbHadTouchedOther = false;
    private boolean waitingForReview = false;

    private boolean laptopConnected = false;
    private boolean leftBlunoConnected = false;
    private boolean rightBlunoConnected = false;

    private long touchTimeMillis = 0;

    private final List<String> connectedAddressList = new ArrayList<>();

    public void setRightOfWayHolder(int x) {
        if (!waitingForReview) {
            Log.e("Bluetooth", "Received right-of-way response while not waiting for review");
            return;
        }

        rightOfWayHolder = x;
        if (rightOfWayHolder == 0) {
            if (lbHadTouchedOther) {
                incrementLeftScore();
            }
        } else if (rightOfWayHolder == 1) {
            if (rbHadTouchedOther) {
                incrementRightScore();
            }
        } else {
            Log.e("Bluetooth", "Received invalid right-of-way holder");
            return;
        }

        waitingForReview = false;
        timerButton.setText("Allez!");

        leftFlash.setBackgroundColor(Color.BLACK);
        rightFlash.setBackgroundColor(Color.BLACK);

        sendInfo();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Debug.waitForDebugger();

        setContentView(R.layout.activity_main);
        timerButton = (Button) findViewById(R.id.timer);
        timerButton.setText("Allez!");
        leftUpButton = (Button) findViewById(R.id.leftUp);
        leftDownButton = (Button) findViewById(R.id.leftDown);
        rightUpButton = (Button) findViewById(R.id.rightUp);
        rightDownButton = (Button) findViewById(R.id.rightDown);
        leftScoreText = (TextView) findViewById(R.id.leftScore);
        rightScoreText = (TextView) findViewById(R.id.rightScore);
        leftFlash = (View) findViewById(R.id.leftFlash);
        rightFlash = (View) findViewById(R.id.rightFlash);
        screenTimerText = (TextView) findViewById(R.id.screenTimer);
        connectButton = (Button) findViewById(R.id.connectButton);
        foundDevices = new ArrayList<BluetoothDevice>();
        bluetoothAdapter = null;
        timerTask = null;
        timer = new Timer();
        timeRound = 3 * 60;
        //timeRound = 20;
        laptopThread = null;
        errCode = 0;
        appUUID = UUID.fromString("cacd2b25-d9e4-4169-b9e7-90de83e2e127");

        /*
        timerButton.setOnClickListener(MainActivity.this);
        leftUpButton.setOnClickListener(MainActivity.this);
        leftDownButton.setOnClickListener(MainActivity.this);
        rightUpButton.setOnClickListener(MainActivity.this);
        rightDownButton.setOnClickListener(MainActivity.this);
        */

        reset();

        /*
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        BluetoothServerSocket serverSocket = null;
        try {
            serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("ttfencing", UUID.randomUUID());
            BluetoothSocket socket = serverSocket.accept();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        */

        //bluetoothThread = new BluetoothTTFencing(this);
        //bluetoothThread.start();

        fencingBlunoLeft = new FencingBluno(this,
                " left",
                0,
                "F4:B8:5E:42:4C:EE");

        fencingBlunoRight = new FencingBluno(this,
                " right",
                1,
                "F4:B8:5E:42:6D:43");

        fencingBlunoLeft.setOther(fencingBlunoRight);
        fencingBlunoRight.setOther(fencingBlunoLeft);

        fencingBlunoLeft.getBlunoLibrary().request(1000, new BlunoLibrary.OnPermissionsResult() {
            @Override
            public void OnSuccess() {
                // intentionally do nothing
            }

            @Override
            public void OnFail(List<String> noPermissions) {
                Toast.makeText(MainActivity.this, "Failed to get permissions for BlunoLibrary", Toast.LENGTH_SHORT).show();
            }
        });

        fencingBlunoRight.getBlunoLibrary().request(1001, new BlunoLibrary.OnPermissionsResult() {
            @Override
            public void OnSuccess() {
                // intentionally do nothing
            }

            @Override
            public void OnFail(List<String> noPermissions) {
                Toast.makeText(MainActivity.this, "Failed to get permissions for BlunoLibrary", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void flashColorOnTouch(View flashView, @ColorInt int color, boolean needReferee, boolean wasTimerRunning) {
        flashView.setBackgroundColor(color);
        if (!needReferee || !wasTimerRunning) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> flashView.setBackgroundColor(Color.BLACK), 3000);
        }
    }

    public boolean markTouch() {
        if (waitingForReview) return false;

        if (touchTimeMillis == 0) {
            touchTimeMillis = System.currentTimeMillis();

            boolean timerRunningWhenFirstTouch = timerRunning;
            lbHadTouchedOther = false;
            rbHadTouchedOther = false;

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                int leftTouch;
                if (fencingBlunoLeft.touchedOther()) {
                    lbHadTouchedOther = true;
                    if (fencingBlunoRight.wasTouched()) {
                        leftTouch = 1;
                    } else {
                        leftTouch = 2;
                    }
                } else {
                    leftTouch = 0;
                }
                int rightTouch;
                if (fencingBlunoRight.touchedOther()) {
                    rbHadTouchedOther = true;
                    if (fencingBlunoLeft.wasTouched()) {
                        rightTouch = 1;
                    } else {
                        rightTouch = 2;
                    }
                } else {
                    rightTouch = 0;
                }

                if (timerRunningWhenFirstTouch) {
                    timerClick(); // Stop timer
                    catchLaptopSocketException(() -> laptopThread.sendStopMessage());
                }
                waitingForReview = timerRunningWhenFirstTouch && ((leftTouch == 1 && rightTouch != 0) || (rightTouch == 1 && leftTouch != 0));
                if (waitingForReview) {
                    timerButton.setText("Waiting...");
                }
                sendTouches(leftTouch, rightTouch, waitingForReview);

                if (leftTouch == 1) {
                    flashColorOnTouch(leftFlash, Color.parseColor("#D51B1B"), waitingForReview, timerRunningWhenFirstTouch);
                    if (rightTouch == 0) {
                        if (timerRunningWhenFirstTouch) {
                            incrementLeftScore();
                            sendInfo();
                        }
                    }
                } else if (leftTouch == 2) {
                    flashColorOnTouch(leftFlash, Color.WHITE, waitingForReview, timerRunningWhenFirstTouch);
                }

                if (rightTouch == 1) {
                    flashColorOnTouch(rightFlash, Color.parseColor("#0AC649"), waitingForReview, timerRunningWhenFirstTouch);
                    if (leftTouch == 0) {
                        if (timerRunningWhenFirstTouch) {
                            incrementRightScore();
                            sendInfo();
                        }
                    }
                } else if (rightTouch == 2) {
                    flashColorOnTouch(rightFlash, Color.WHITE, waitingForReview, timerRunningWhenFirstTouch);
                }
                if (!timerRunningWhenFirstTouch) {
                    touchTimeMillis = 0;
                    fencingBlunoRight.resetTouchFlags();
                    fencingBlunoLeft.resetTouchFlags();
                }},
                300);
            if (timerRunningWhenFirstTouch) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!timerRunning) {
                        touchTimeMillis = 0;
                        fencingBlunoRight.resetTouchFlags();
                        fencingBlunoLeft.resetTouchFlags();
                    }},
                    3000);
            }

            return true;
        } else {
            return System.currentTimeMillis() - touchTimeMillis <= 300;
        }
    }


    protected void onResume() {
        super.onResume();
        System.out.println("BlUNOActivity onResume");
        fencingBlunoLeft.getBlunoLibrary().resume();
        fencingBlunoRight.getBlunoLibrary().resume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        assert false;
        //fencingBlunoLeft.getBlunoLibrary().onActivityResultProcess(requestCode, resultCode, data);                    //onActivityResult Process by BlunoLibrary
        //fencingBlunoRight.getBlunoLibrary().onActivityResultProcess(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onStart() {
        super.onStart();
        fencingBlunoLeft.initialize();
        fencingBlunoRight.initialize();
    }

    @Override
    protected void onPause() {
        super.onPause();
        fencingBlunoLeft.getBlunoLibrary().pause();
        fencingBlunoRight.getBlunoLibrary().pause();
    }

    protected void onStop() {
        super.onStop();
        assert false;
        //fencingBlunoLeft.getBlunoLibrary().stop();                                                        //onStop Process by BlunoLibrary
        //fencingBlunoRight.getBlunoLibrary().onStopProcess();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fencingBlunoLeft.getBlunoLibrary().destroy();
        fencingBlunoRight.getBlunoLibrary().destroy();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        fencingBlunoLeft.getBlunoLibrary().onRequestPermissionsResult(requestCode, permissions, grantResults);
        fencingBlunoRight.getBlunoLibrary().onRequestPermissionsResult(requestCode, permissions, grantResults);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void addConnectedAddress(String address) {
        connectedAddressList.add(address);
        if (address.equals(fencingBlunoLeft.mPreferredMacAddr)) {
            leftBlunoConnected = true;
        } else if (address.equals(fencingBlunoRight.mPreferredMacAddr)) {
            rightBlunoConnected = true;
        }
        updateConnectButtonText();
    }

    public void removeConnectedAddress(String address) {
        connectedAddressList.remove(address);
        if (address.equals(fencingBlunoLeft.mPreferredMacAddr)) {
            leftBlunoConnected = false;
        } else if (address.equals(fencingBlunoRight.mPreferredMacAddr)) {
            rightBlunoConnected = false;
        }
        updateConnectButtonText();
    }

    public boolean isAddressConnected(String address) {
        return connectedAddressList.contains(address);
    }


    public void startBluetoothConnection(BluetoothSocket socket) {
     //Start the thread to manage the connection and perform transmissions
        laptopThread = new LaptopThread(this, socket);
        laptopThread.start();
    }

    public void reset() {
        leftScore = 0;
        rightScore = 0;
        secondsLeft = timeRound;
        timerRunning = false;
        leftScoreText.setText(String.valueOf(leftScore));
        rightScoreText.setText(String.valueOf(rightScore));
        timerButton.setText("Allez!");
        screenTimerText.setText(getTimerText());
    }

    private void updateConnectButtonText() {
        String connectButtonText = "Connected: ";
        if (laptopConnected) {
            connectButtonText += "1";
        }
        if (leftBlunoConnected) {
            connectButtonText += "2";
        }
        if (rightBlunoConnected) {
            connectButtonText += "3";
        }
        connectButton.setText(connectButtonText);
    }

    public void connectClick() {
        connectButton.setBackgroundColor(Color.BLUE);
        updateConnectButtonText();

        if (!leftBlunoConnected) {
            fencingBlunoLeft.connect();
        }
        if (!rightBlunoConnected) {
            fencingBlunoRight.connect();
        }

        if (!laptopConnected) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                Log.e("Bluetooth", "Bluetooth is not available");
                return;
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Log.e("Bluetooth", "Missing ACCESS_FINE_LOCATION permission");
                return;
            }

            if (true) {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice("E4:02:9B:93:0F:CF");
                if (device != null) {
                    Log.i("Bluetooth", "Connected to laptop at known MAC address.");
                    try {
                        //laptopSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                        laptopSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("c3c870f0-699a-4264-866c-6bf8be151bcc"));
                        laptopSocket.connect();
                        Log.i("Bluetooth", "Successfully connected to laptop");
                        laptopConnected = true;
                        updateConnectButtonText();

                        startBluetoothConnection(laptopSocket);
                        sendInfo();
                    } catch (IOException e) {
                        Log.e("Bluetooth", "Connection failed: " + e.getMessage());
                    }
                } else {
                    Log.e("Bluetooth", "Could not connect to laptop at known MAC address.");
                }
            } else {
                // Register for broadcasts when a device is discovered
                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                registerReceiver(laptopreceiver, filter);
                // Start discovery
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

                if (bluetoothAdapter.startDiscovery()) {
                    Log.d("Bluetooth", "Bluetooth discovery successful");
                } else {
                    Log.e("Bluetooth", "Bluetooth discovery returned false");
                }
            }
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver laptopreceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    foundDevices.add(device);
                    if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        Log.e("Bluetooth", "Missing Bluetooth permission");
                        return;
                    }

                    String deviceName = device.getName();
                    Log.d("Bluetooth", "Device found: " + deviceName + " - " + device.getAddress());
                    //connectButton.setText("Laptop " + foundDevices.size());

                    if (deviceName != null && deviceName.equals("DESKTOP-VCLA0R6")) {
                        try {
                            //laptopSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                            laptopSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("c3c870f0-699a-4264-866c-6bf8be151bcc"));
                            laptopSocket.connect();
                            Log.i("Bluetooth", "Successfully connected to laptop");

                            startBluetoothConnection(laptopSocket);
                        } catch (IOException e) {
                            Log.e("Bluetooth", "Connection failed: " + e.getMessage());
                        }
                    }
                }
            }
        }
    };

    private String getTimerText() {
        int timeInt = Math.round(secondsLeft);
        int mins = timeInt / 60;
        int secs = timeInt % 60;
        return String.valueOf(mins) + ":" + String.format("%02d", secs);
    }

    public void timerClick() {
        if (waitingForReview) return;

        if (!timerRunning) {
            if (secondsLeft <= 0) {
                secondsLeft = timeRound;
                timerButton.setText("Allez!");
                screenTimerText.setText(getTimerText());
                sendInfo();
            } else {
                timerButton.setText("Halt!");
                startTime();
                timerRunning = !timerRunning;
                touchTimeMillis = 0;
                fencingBlunoLeft.resetTouchFlags();
                fencingBlunoRight.resetTouchFlags();
                if (laptopThread != null) {
                    catchLaptopSocketException(() -> laptopThread.sendTime(secondsLeft));
                    catchLaptopSocketException(() -> laptopThread.sendStartMessage());
                    Log.i("Bluetooth", "Sent start message.");
                }
            }
        } else {
            if (timerTask != null) {
                timerTask.cancel();
            }
            timerButton.setText("Allez!");
            timerRunning = !timerRunning;
            if (laptopThread != null) {
                catchLaptopSocketException(() -> {
                    laptopThread.sendStopMessage();
                    Log.i("Bluetooth", "Sent stop message.");
                });
            }
        }
    }

    public void sendTouches(int leftTouch, int rightTouch, boolean doReview) {
        if (laptopThread == null) {
            return;
        }
        catchLaptopSocketException(() -> laptopThread.sendTouches(leftTouch, rightTouch, doReview));
    }

    public void sendInfo() {
        if (laptopThread == null) {
            return;
        }
        catchLaptopSocketException(() -> laptopThread.sendInfo(leftScore, rightScore, secondsLeft));
    }

    private interface MessageForLaptop {
        void sendMessage() throws IOException;
    }

    private void catchLaptopSocketException(MessageForLaptop sendMessageCall) {
        try {
            sendMessageCall.sendMessage();
        } catch (IOException e) {
            Log.e("Bluetooth", "laptopThread sendMessage exception: " + e.getMessage());
            setLaptopDisconnected();
        }
    }

    public synchronized void setLaptopDisconnected() {
        laptopConnected = false;
    }

    public void incrementLeftScore() {
        leftScore++;
        leftScoreText.setText(String.valueOf(leftScore));
    }

    public void leftUpClick(View v) {
        incrementLeftScore();
        sendInfo();
    }

    public void leftDownClick(View v) {
        if (leftScore > 0) {
            leftScore -= 1;
            leftScoreText.setText(String.valueOf(leftScore));
            sendInfo();
        }
    }

    public void incrementRightScore() {
        rightScore++;
        rightScoreText.setText(String.valueOf(rightScore));
    }

    public void rightUpClick(View v) {
        incrementRightScore();
        sendInfo();
    }
    public void rightDownClick(View v) {
        if (rightScore > 0) {
            rightScore -= 1;
            rightScoreText.setText(String.valueOf(rightScore));
            sendInfo();
        }
    }

    public void resetClick(View v) {
        if (timerRunning) {
            timerClick();
        }
        reset();
        sendInfo();
    }

    public void onConnectClick(View v) { connectClick(); }

    public void onTimerClick(View v) { timerClick(); }

    private void startTime() {
        timerTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run()
                    {
                        secondsLeft -= 0.1;
                        screenTimerText.setText(getTimerText());
                        if (secondsLeft <= 0) {
                            secondsLeft = 0;
                            timerTask.cancel();
                            timerButton.setText("Reset");
                            timerRunning = false;
                        }
                    }
                });
            }
        };
        timer.scheduleAtFixedRate(timerTask, 0, 100);
    }

    /*
    public void setError(int code) {
        errCode = errCode + (1 << code);
        TextView errText = (TextView) findViewById(R.id.errcode);
        errText.setVisibility(VISIBLE);
        errText.setText(String.valueOf(errCode));
    }
     */

    /*
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode) {
            case REQUEST_CONNECT_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    bluetoothThread.connectPermissionGranted();
                }
                break;
            default:
                return;
        }
    }
     */
}