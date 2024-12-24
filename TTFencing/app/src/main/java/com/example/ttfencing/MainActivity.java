package com.example.ttfencing;

import static android.view.View.VISIBLE;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
//import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothSocket;
//import android.content.pm.PackageManager;
import android.os.Bundle;
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

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

//public class MainActivity extends AppCompatActivity implements View.OnClickListener {
public class MainActivity extends AppCompatActivity {

    Button timerButton, leftUpButton, leftDownButton, rightUpButton, rightDownButton, laptopButton;
    TextView leftScoreText, rightScoreText, screenTimerText;
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

    public void setRightOfWayHolder(int x) {
        rightOfWayHolder = x;
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        timerButton = (Button) findViewById(R.id.timer);
        timerButton.setText("Allez!");
        leftUpButton = (Button) findViewById(R.id.leftUp);
        leftDownButton = (Button) findViewById(R.id.leftDown);
        rightUpButton = (Button) findViewById(R.id.rightUp);
        rightDownButton = (Button) findViewById(R.id.rightDown);
        leftScoreText = (TextView) findViewById(R.id.leftScore);
        rightScoreText = (TextView) findViewById(R.id.rightScore);
        screenTimerText = (TextView) findViewById(R.id.screenTimer);
        laptopButton = (Button) findViewById(R.id.laptopButton);
        foundDevices = new ArrayList<BluetoothDevice>();
        bluetoothAdapter = null;
        timerTask = null;
        timer = new Timer();
        //timeRound = 3 * 60;
        timeRound = 20;
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

    public void laptopClick() {
        laptopButton.setBackgroundColor(Color.BLUE);
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
                    laptopButton.setText("Laptop " + foundDevices.size());

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

    public void timerClick(View v) {
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
                if (laptopThread != null) {
                    try {
                        laptopThread.sendTime(secondsLeft);
                        laptopThread.sendStartMessage();
                        Log.i("Bluetooth", "Sent start message.");
                    } catch (IOException e) {
                        Log.e("Bluetooth", "laptopThread startMessage exception: " + e.getMessage());
                    }
                }
            }
        } else {
            if (timerTask != null) {
                timerTask.cancel();
            }
            timerButton.setText("Allez!");
            timerRunning = !timerRunning;
            if (laptopThread != null) {
                try {
                    laptopThread.sendStopMessage();
                    Log.i("Bluetooth", "Sent stop message.");
                } catch (IOException e) {
                    Log.e("Bluetooth", "laptopThread stopMessage exception: " + e.getMessage());
                }
            }
        }
    }

    public void sendInfo() {
        if (laptopThread == null) {
            return;
        }
        try {
            laptopThread.sendInfo(leftScore, rightScore, secondsLeft, 0, 0); // TODO
        } catch (IOException e) {
            Log.e("Bluetooth", "laptopThread sendInfo exception: " + e.getMessage());
        }
    }

    public void leftUpClick(View v) {
        leftScore += 1;
        leftScoreText.setText(String.valueOf(leftScore));
        sendInfo();
    }
    public void leftDownClick(View v) {
        if (leftScore > 0) {
            leftScore -= 1;
            leftScoreText.setText(String.valueOf(leftScore));
            sendInfo();
        }
    }
    public void rightUpClick(View v) {
        rightScore += 1;
        rightScoreText.setText(String.valueOf(rightScore));
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
            timerClick(v);
        }
        reset();
        sendInfo();
    }

    public void onLaptopClick(View v) { laptopClick(); }

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

    public void setError(int code) {
        errCode = errCode + (1 << code);
        TextView errText = (TextView) findViewById(R.id.errcode);
        errText.setVisibility(VISIBLE);
        errText.setText(String.valueOf(errCode));
    }

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