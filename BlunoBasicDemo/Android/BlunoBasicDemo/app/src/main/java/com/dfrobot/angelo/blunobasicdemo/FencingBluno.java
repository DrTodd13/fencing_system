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

public class FencingBluno implements BlunoLibrary.BlunoListener {
    private final MainActivity mainActivity;

    private final BlunoLibrary blunoLibrary;
    Button scan;
    Button send;
    EditText editField;
    TextView displayText;
    String postfix;
    int slot;

    public String mDeviceName;
    public String mDeviceAddress;

    private LeDeviceListAdapter mLeDeviceListAdapter=null;
    AlertDialog mScanDeviceDialog;
    public FencingBluno(MainActivity main,
                        Button scan,
                        Button send,
                        EditText editField,
                        TextView displayText,
                        String postfix,
                        int slot) {
        blunoLibrary = new BlunoLibrary(main, this, slot);
        mainActivity=main;
        this.scan = scan;
        this.send = send;
        this.editField = editField;
        this.displayText = displayText;
        this.postfix = postfix;
        this.slot = slot;
        blunoLibrary.setBlunoListener(this);

        createScanDeviceDialog();
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
                mLeDeviceListAdapter.addDevice(device);
                mLeDeviceListAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onConnectionStateChange(BlunoLibrary.ConnectionStateEnum state) {//Once connection state changes, this function will be called
        switch (state) {											//Four connection state
            case isConnected:
                scan.setText("Connected" + postfix);
                displayText.setText("");
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

    @Override
    public void onSerialReceived(String data) {							//Once connection data received, this function will be called
        // TODO Auto-generated method stub
        int tlen = displayText.getText().length();
        int keepChars = 500;
        if (tlen > keepChars) {
            displayText.setText(displayText.getText().subSequence(tlen - keepChars, tlen));
        }
        displayText.append(data);							//append the text into the EditText
        //The Serial data from the BLUNO may be sub-packaged, so using a buffer to hold the String is a good choice.
        ((ScrollView)displayText.getParent()).fullScroll(View.FOCUS_DOWN);
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
