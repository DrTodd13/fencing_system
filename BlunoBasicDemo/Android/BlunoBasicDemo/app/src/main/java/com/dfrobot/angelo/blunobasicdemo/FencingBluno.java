package com.dfrobot.angelo.blunobasicdemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class FencingBluno implements BlunoLibrary.BlunoListener {
    private final MainActivity mainActivity;

    private final BlunoLibrary blunoLibrary;
    Button scan;
    Button clear;
    //EditText editField;
    TextView displayText;
    String postfix;
    int slot;

    public String mDeviceName;
    public String mDeviceAddress;

    private LeDeviceListAdapter mLeDeviceListAdapter=null;
    AlertDialog mScanDeviceDialog;
    FencingBluno mOther;

    String mReceived;

    private final Timer timer = new Timer();

    public FencingBluno(MainActivity main,
                        Button scan,
                        Button clear,
                        //EditText editField,
                        TextView displayText,
                        String postfix,
                        int slot) {
        blunoLibrary = new BlunoLibrary(main, this, slot);
        mainActivity=main;
        this.scan = scan;
        this.clear = clear;
        //this.editField = editField;
        this.displayText = displayText;
        this.postfix = postfix;
        this.slot = slot;
        mReceived = "";
        blunoLibrary.setBlunoListener(this);

        createScanDeviceDialog();
    }

    public void setOther(FencingBluno other) {
        mOther = other;
    }
    private void createScanDeviceDialog() {
        // Initializes list view adapter.
        //DeviceListAdapter
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        // Initializes and show the scan Device Dialog
        mScanDeviceDialog = new AlertDialog.Builder(mainActivity)
                .setTitle("BLE Device Scan...").setAdapter(mLeDeviceListAdapter, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(which);
                        if (device == null)
                            return;
                        blunoLibrary.stopScan(); //scanLeDevice(false);

                        if (device.getName() != null && device.getAddress() != null) {
                            System.out.println("onListItemClick " + device.getName());
                            System.out.println("Device Name:" + device.getName() + "   " + "Device Name:" + device.getAddress());
                            mDeviceName = device.getName();
                            mDeviceAddress = device.getAddress();
                            blunoLibrary.connect(mDeviceAddress);
                        }
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface arg0) {
                        System.out.println("mBluetoothAdapter.stopLeScan");

                        blunoLibrary.stopScan();
                        mScanDeviceDialog.dismiss();
                    }
                }).create();
    }
    /*
    void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.

            System.out.println("mBluetoothAdapter.startLeScan");

            if(mLeDeviceListAdapter != null)
            {
                mLeDeviceListAdapter.clear();
                mLeDeviceListAdapter.notifyDataSetChanged();
            }

            if(!mScanning)
            {
                mScanning = true;
                //mBluetoothAdapter.startLeScan(mLeScanCallback);
                mBluetoothAdapter.getBluetoothLeScanner().startScan(mBlunoScanCallback);
            }
        } else {
            if(mScanning)
            {
                mScanning = false;
                //mBluetoothAdapter.stopLeScan(mLeScanCallback);
                mBluetoothAdapter.getBluetoothLeScanner().stopScan(mBlunoScanCallback);
            }
        }
    }

    private final ScanCallback mBlunoScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbacktype, ScanResult result) {
            ((Activity) mainActivity).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    System.out.println("mLeScanCallback onLeScan run ");
                    mLeDeviceListAdapter.addDevice(result.getDevice());
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };
    */

    public BlunoLibrary getBlunoLibrary() {
        return blunoLibrary;
    }

    public void onDeviceDetected(final BluetoothDevice device, int rssi, byte[] scanRecord) {
        ((Activity) mainActivity).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("onDeviceDetected ");
                String dName = device.getName();
                if (dName == null || dName.equals("")) {
                    return;
                }
                if (!mainActivity.isAddressConnected(device.getAddress())) {
                    mLeDeviceListAdapter.addDevice(device);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    @Override
    public void onConnectionStateChange(BlunoLibrary.ConnectionStateEnum state) {//Once connection state changes, this function will be called
        switch (state) {											//Four connection state
            case isConnected:
                scan.setText("Connected" + postfix);
                displayText.setText("");
                mainActivity.addConnectedAddress(mDeviceAddress);
                break;
            case isConnecting:
                scan.setText("Connecting");
                //assert false;
                break;
            case isToScan:
                scan.setText("Scan" + postfix);
                break;
            case isScanning:
                scan.setText("Scanning");
                break;
            case isDisconnecting:
                scan.setText("isDisconnecting");
                mainActivity.removeConnectedAddress(mDeviceAddress);
                mLeDeviceListAdapter.clear();
                break;
            default:
                break;
        }
    }

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

    private class LeDeviceListAdapter extends BaseAdapter {
        private final ArrayList<BluetoothDevice> mLeDevices;
        private final LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator =  ((Activity) mainActivity).getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view
                        .findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view
                        .findViewById(R.id.device_name);
                System.out.println("mInflator.inflate  getView");
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    /*
    @Override
    public void onConnectionStateChange(BlunoLibrary.ConnectionStateEnum state, String deviceName, String deviceAddress) {//Once connection state changes, this function will be called
        switch (state) {											//Four connection state
            case isConnected:
                assert false;
                break;
            case isConnecting:
                scan.setText("Connecting");
                mDeviceName = deviceName;
                mDeviceAddress = deviceAddress;
                break;
            case isToScan:
                assert false;
                break;
            case isScanning:
                assert false;
                break;
            case isDisconnecting:
                assert false;
                break;
            default:
                break;
        }
    }
     */

    public void makeClear() {
        displayText.setText("");
        mReceived = "";
        //blunoLibrary.serialSend("2");
    }

    private void processReceived() {
        int textLength = displayText.getText().length();
        int keepChars = 1500;
        if (textLength > keepChars) {
            displayText.setText(displayText.getText().subSequence(textLength - keepChars, textLength));
        }

        while (!mReceived.isEmpty()) {
            System.out.println("Before: " + mReceived);
            if (mReceived.charAt(0) == '1') {
                displayText.append("Is touched! ");
                mReceived = mReceived.substring(1);
                System.out.println("After1: " + mReceived);
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        synchronized (FencingBluno.this) {
                            blunoLibrary.serialSend("1");
                            //mainActivity.runOnUiThread(() -> displayText.append("Task run "));
                        }
                    }
                }, 5000);
                //displayText.append("Task scheduled ");
            } else if (mReceived.charAt(0) == '2') {
                displayText.append("Touch! ");
                mReceived = mReceived.substring(1);
                System.out.println("After2: " + mReceived);
            } else if (mReceived.charAt(0) == '3') {
                if (mReceived.length() >= 6) {
                    //displayText.append(mReceived);
                    String sslen = mReceived.substring(1,5).trim();
                    //assert (sslen.length() == 4);
                    int slen = Integer.parseInt(sslen);
                    System.out.println("Length: " + slen);
                    if (mReceived.length() >= 1 + 4 + slen) {
                        displayText.append(mReceived.substring(5, 5 + slen));
                        displayText.append(" ");
                        mReceived = mReceived.substring(5 + slen);
                        System.out.println("After3: " + mReceived);
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            } else {
                displayText.append("Protocol error: ");
                displayText.append(mReceived);
                displayText.append(" " + String.valueOf(mReceived.length()));
                break;
            }
        }

        //The Serial data from the BLUNO may be sub-packaged, so using a buffer to hold the String is a good choice.
        ((ScrollView)displayText.getParent()).fullScroll(View.FOCUS_DOWN);
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
