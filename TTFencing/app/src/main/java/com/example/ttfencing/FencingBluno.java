package com.example.ttfencing;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Timer;

public class FencingBluno implements BlunoLibrary.BlunoListener {
    private final MainActivity mainActivity;

    private final BlunoLibrary blunoLibrary;

    String postfix;
    int slot;

    public String mDeviceName;
    //public String mDeviceAddress;

    public String mPreferredMacAddr;
    FencingBluno mOther;

    String mReceived;

    private boolean touched = false;
    private boolean wasTouched = false;

    private final Timer timer = new Timer();

    public FencingBluno(MainActivity main,
                        String postfix,
                        int slot,
                        String preferred_mac_addr) {
        blunoLibrary = new BlunoLibrary(main, this, slot);
        mainActivity=main;
        this.postfix = postfix;
        this.slot = slot;
        mReceived = "";
        mPreferredMacAddr = preferred_mac_addr;
        blunoLibrary.setBlunoListener(this);
    }

    public void resetTouchFlags() {
        touched = false;
        wasTouched = false;
    }

    public boolean touchedOther() {
        return touched;
    }

    public boolean wasTouched() {
        return wasTouched;
    }

    public void initialize() {
        blunoLibrary.initialize();
    }

    public void connect() {
        if (!mPreferredMacAddr.equals("")) {
            blunoLibrary.connect(mPreferredMacAddr);
        }
    }

    public void setOther(FencingBluno other) {
        mOther = other;
    }

    public BlunoLibrary getBlunoLibrary() {
        return blunoLibrary;
    }

    public void onDeviceDetected(final BluetoothDevice device, int rssi, byte[] scanRecord) {
        assert(false);
    }

    @Override
    public void onConnectionStateChange(BlunoLibrary.ConnectionStateEnum state) {//Once connection state changes, this function will be called
        switch (state) {											//Four connection state
            case isConnected:
                mainActivity.addConnectedAddress(mPreferredMacAddr);
                break;
            case isConnecting:
                break;
            case isToScan:
                break;
            case isScanning:
                break;
            case isDisconnecting:
                mainActivity.removeConnectedAddress(mPreferredMacAddr);
                break;
            default:
                break;
        }
    }

    /*
    void buttonScanOnClickProcess()
    {
        switch (blunoLibrary.getConnectionState()) {
            case isNull:
                //mConnectionState= BlunoLibrary.ConnectionStateEnum.isScanning;
                //blunoListener.onConnectionStateChange(mConnectionState);
                //scanLeDevice(true);
                blunoLibrary.startScan();
                mScanDeviceDialog.show();
                break;
            case isToScan:
                //mConnectionState= BlunoLibrary.ConnectionStateEnum.isScanning;
                //blunoListener.onConnectionStateChange(mConnectionState);
                //scanLeDevice(true);
                blunoLibrary.startScan();
                mScanDeviceDialog.show();
                break;
            case isScanning:
                break;
            case isConnecting:
                break;
            case isConnected:
                //mBluetoothLeService.disconnect();
                //mHandler.postDelayed(mDisonnectingOverTimeRunnable, 10000);
//			mBluetoothLeService.close();
                //mConnectionState= BlunoLibrary.ConnectionStateEnum.isDisconnecting;
                //blunoListener.onConnectionStateChange(mConnectionState);
                blunoLibrary.disconnect();
                break;
            case isDisconnecting:
                break;

            default:
                break;
        }
    }
     */

    private void processReceived() {
        /*
        int textLength = displayText.getText().length();
        int keepChars = 1500;
        if (textLength > keepChars) {
            displayText.setText(displayText.getText().subSequence(textLength - keepChars, textLength));
        }
         */

        while (!mReceived.isEmpty()) {
            System.out.println("Before: " + mReceived);
            if (mReceived.charAt(0) == '1') {
                // Is touched
                mReceived = mReceived.substring(1);
                Log.i("BluetoothLE", "Got wasTouched from " + slot);
                if (mainActivity.markTouch()) {
                    wasTouched = true;
                    Log.i("BluetoothLE", "Logged wasTouched from " + slot);
                }
            } else if (mReceived.charAt(0) == '2') {
                // Touch
                mReceived = mReceived.substring(1);
                Log.i("BluetoothLE", "Got touched from " + slot);
                if (mainActivity.markTouch()) {
                    touched = true;
                    Log.i("BluetoothLE", "Logged touched from " + slot);
                }
            } else {
                // Protocol error
                /*
                displayText.append("Protocol error: ");
                displayText.append(mReceived);
                displayText.append(" " + String.valueOf(mReceived.length()));
                 */
                break;
            }
        }

        //The Serial data from the BLUNO may be sub-packaged, so using a buffer to hold the String is a good choice.
        //((ScrollView)displayText.getParent()).fullScroll(View.FOCUS_DOWN);
    }
    @Override
    public void onSerialReceived(String data) {							//Once connection data received, this function will be called
        mReceived += data;
        processReceived();
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
