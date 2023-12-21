package com.dfrobot.angelo.blunobasicdemo;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

public class FencingBluno implements BlunoLibrary.BlunoListener {
    //private final MainActivity mainContext;

    private final BlunoLibrary blunoLibrary;
    Button scan;
    Button send;
    EditText editField;
    TextView displayText;
    String postfix;

    public FencingBluno(MainActivity main,
                        Button scan,
                        Button send,
                        EditText editField,
                        TextView displayText,
                        String postfix) {
        blunoLibrary = new BlunoLibrary(main);
        //mainContext=main;
        this.scan = scan;
        this.send = send;
        this.editField = editField;
        this.displayText = displayText;
        this.postfix = postfix;
        blunoLibrary.setBlunoListener(this);
    }

    public BlunoLibrary getBlunoLibrary() {
        return blunoLibrary;
    }

    public void onDeviceDetected(final BluetoothDevice device, int rssi, byte[] scanRecord) {

    }

    @Override
    public void onConnectionStateChange(BlunoLibrary.ConnectionStateEnum state) {//Once connection state changes, this function will be called
        switch (state) {											//Four connection state
            case isConnected:
                scan.setText("Connected" + postfix);
                break;
            case isConnecting:
                scan.setText("Connecting");
                break;
            case isToScan:
                scan.setText("Scan" + postfix);
                break;
            case isScanning:
                scan.setText("Scanning");
                break;
            case isDisconnecting:
                scan.setText("isDisconnecting");
                break;
            default:
                break;
        }
    }

    @Override
    public void onSerialReceived(String data) {							//Once connection data received, this function will be called
        // TODO Auto-generated method stub
        displayText.append(data);							//append the text into the EditText
        //The Serial data from the BLUNO may be sub-packaged, so using a buffer to hold the String is a good choice.
        ((ScrollView)displayText.getParent()).fullScroll(View.FOCUS_DOWN);
    }

}
