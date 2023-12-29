package com.dfrobot.angelo.blunobasicdemo;

import static android.support.v4.app.ActivityCompat.requestPermissions;

import android.Manifest;
import android.app.Activity;
import android.os.Build;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class BlunoLibrary {

	private final String [] mStrPermission = {
			Manifest.permission.ACCESS_FINE_LOCATION
	};

	private final List<String>  mPerList   = new ArrayList<>();
	private final List<String>  mPerNoList = new ArrayList<>();

	private  OnPermissionsResult permissionsResult;
	private  int requestCode;

	private final static String TAG = BlunoLibrary.class.getSimpleName();

	private static final String SerialPortUUID = "0000dfb1-0000-1000-8000-00805f9b34fb";
	private static final String CommandUUID = "0000dfb2-0000-1000-8000-00805f9b34fb";
	private static final String ModelNumberStringUUID = "00002a24-0000-1000-8000-00805f9b34fb";

	private static final int DEFAULT_BAUD_RATE = 115200;
	private static final String DEFAULT_PASSWORD = "DFRobot";
	private FencingBluno back;
	private int slot;

	private final Activity mainActivity;
	private String mPassword;
	private String mBaudrateBuffer;
	private boolean mInitialized, mReceiverRegistered = false;

	private /* static */ BluetoothGattCharacteristic mSCharacteristic, mModelNumberCharacteristic, mSerialPortCharacteristic, mCommandCharacteristic;
	private BluetoothLeService mBluetoothLeService = null;
	private BluetoothLeService2 mBluetoothLeService2 = null;
	private BluetoothAdapter mBluetoothAdapter;

	private BlunoListener blunoListener;



//    private boolean mScanning = false;
//    private String mDeviceName;
//    private String mDeviceAddress;

	public enum ConnectionStateEnum {isNull, isScanning, isToScan, isConnecting, isConnected, isDisconnecting}

	private ConnectionStateEnum mConnectionState = ConnectionStateEnum.isNull;

	ConnectionStateEnum getConnectionState() {
		return mConnectionState;
	}

	private Handler mHandler = new Handler();

	private void leClose() {
		if (slot == 0) {
			if (mBluetoothLeService != null)
				mBluetoothLeService.close();
		} else {
			if (mBluetoothLeService2 != null)
				mBluetoothLeService2.close();
		}
	}

	private void leDisconnect() {
		if (slot == 0) {
			if (mBluetoothLeService != null) {
				mBluetoothLeService.disconnect();
				mHandler.postDelayed(mDisonnectingOverTimeRunnable, 10000);
			}
		} else {
			if (mBluetoothLeService2 != null) {
				mBluetoothLeService2.disconnect();
				mHandler.postDelayed(mDisonnectingOverTimeRunnable, 10000);
			}
		}
	}
	// Connecting Timeout Handler
	private Runnable mConnectingOverTimeRunnable = new Runnable() {

		@Override
		public void run() {
			if (mConnectionState == ConnectionStateEnum.isConnecting)
				changeState(ConnectionStateEnum.isToScan);
			leClose();
		}
	};

	// Disconnecting Timeout Handler
	private Runnable mDisonnectingOverTimeRunnable = new Runnable() {

		@Override
		public void run() {
			if (mConnectionState == ConnectionStateEnum.isDisconnecting)
				changeState(ConnectionStateEnum.isToScan);
			leClose();
		}
	};

	// Code to manage Service lifecycle.
	private ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			System.out.println("mServiceConnection onServiceConnected");
			if (slot == 0) {
				mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
				if (!mBluetoothLeService.initialize()) {
					Log.e(TAG, "Unable to initialize Bluetooth");
					//((Activity) mainActivity).finish();
				} else {
					changeState(ConnectionStateEnum.isToScan);
				}
			} else {
				mBluetoothLeService2 = ((BluetoothLeService2.LocalBinder) service).getService();
				if (!mBluetoothLeService2.initialize()) {
					Log.e(TAG, "Unable to initialize Bluetooth");
					//((Activity) mainActivity).finish();
				} else {
					changeState(ConnectionStateEnum.isToScan);
				}
			}

		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			System.out.println("mServiceConnection onServiceDisconnected");
			mBluetoothLeService = null;
			mBluetoothLeService2 = null;
			mInitialized = false;
		}
	};

	// Device scan callback.
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
			blunoListener.onDeviceDetected(device, rssi, scanRecord);
		}
	};

	// Handles various events fired by the Service.
	// ACTION_GATT_CONNECTED: connected to a GATT server.
	// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	// ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
	//                        or notification operations.
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			System.out.println("mGattUpdateReceiver->onReceive->action=" + action);

			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				mHandler.removeCallbacks(mConnectingOverTimeRunnable);
			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
				changeState(ConnectionStateEnum.isToScan);
				mHandler.removeCallbacks(mDisonnectingOverTimeRunnable);
				leClose();
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				// Show all the supported services and characteristics on the user interface.
				List<BluetoothGattService> services = mBluetoothLeService.getSupportedGattServices();
				for (BluetoothGattService service : services) {
					System.out.println("ACTION_GATT_SERVICES_DISCOVERED  " + service.getUuid().toString());
				}
				getGattServices(services);
			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				if (mSCharacteristic == mModelNumberCharacteristic) {
					if (intent.getStringExtra(BluetoothLeService.EXTRA_DATA).toUpperCase().startsWith("DF BLUNO")) {
						mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, false);

						mSCharacteristic = mCommandCharacteristic;
						if (mPassword == null || mPassword.isEmpty()) {
							System.out.println("empty password");
						}
						mSCharacteristic.setValue(mPassword);
						mBluetoothLeService.writeCharacteristic(mSCharacteristic);
						mSCharacteristic.setValue(mBaudrateBuffer);
						mBluetoothLeService.writeCharacteristic(mSCharacteristic);

						mSCharacteristic = mSerialPortCharacteristic;
						mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, true);

						changeState(ConnectionStateEnum.isConnected);
					} else {
						Log.e(TAG, "Please select DFRobot devices");
						changeState(ConnectionStateEnum.isToScan);
					}
				} else if (mSCharacteristic == mSerialPortCharacteristic) {
					if (blunoListener != null)
						blunoListener.onSerialReceived(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
				}

				System.out.println("displayData " + intent.getStringExtra(BluetoothLeService.EXTRA_DATA));

//            	mPlainProtocol.mReceivedframe.append(intent.getStringExtra(BluetoothLeService.EXTRA_DATA)) ;
//            	System.out.print("mPlainProtocol.mReceivedframe:");
//            	System.out.println(mPlainProtocol.mReceivedframe.toString());
			}
		}
	};

	private final BroadcastReceiver mGattUpdateReceiver2 = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			System.out.println("mGattUpdateReceiver2->onReceive->action=" + action);

			if (BluetoothLeService2.ACTION_GATT_CONNECTED2.equals(action)) {
				mHandler.removeCallbacks(mConnectingOverTimeRunnable);
			} else if (BluetoothLeService2.ACTION_GATT_DISCONNECTED2.equals(action)) {
				changeState(ConnectionStateEnum.isToScan);
				mHandler.removeCallbacks(mDisonnectingOverTimeRunnable);
				leClose();
			} else if (BluetoothLeService2.ACTION_GATT_SERVICES_DISCOVERED2.equals(action)) {
				// Show all the supported services and characteristics on the user interface.
				List<BluetoothGattService> services = mBluetoothLeService2.getSupportedGattServices();
				for (BluetoothGattService service : services) {
					System.out.println("ACTION_GATT_SERVICES_DISCOVERED  " + service.getUuid().toString());
				}
				getGattServices(services);
			} else if (BluetoothLeService2.ACTION_DATA_AVAILABLE2.equals(action)) {
				if (mSCharacteristic == mModelNumberCharacteristic) {
					if (intent.getStringExtra(BluetoothLeService2.EXTRA_DATA2).toUpperCase().startsWith("DF BLUNO")) {
						mBluetoothLeService2.setCharacteristicNotification(mSCharacteristic, false);

						mSCharacteristic = mCommandCharacteristic;
						if (mPassword == null || mPassword.isEmpty()) {
							System.out.println("empty password");
						}
						mSCharacteristic.setValue(mPassword);
						mBluetoothLeService2.writeCharacteristic(mSCharacteristic);
						mSCharacteristic.setValue(mBaudrateBuffer);
						mBluetoothLeService2.writeCharacteristic(mSCharacteristic);

						mSCharacteristic = mSerialPortCharacteristic;
						mBluetoothLeService2.setCharacteristicNotification(mSCharacteristic, true);

						changeState(ConnectionStateEnum.isConnected);
					} else {
						Log.e(TAG, "Please select DFRobot devices");
						changeState(ConnectionStateEnum.isToScan);
					}
				} else if (mSCharacteristic == mSerialPortCharacteristic) {
					if (blunoListener != null)
						blunoListener.onSerialReceived(intent.getStringExtra(BluetoothLeService2.EXTRA_DATA2));
				}

				System.out.println("displayData " + intent.getStringExtra(BluetoothLeService2.EXTRA_DATA2));

//            	mPlainProtocol.mReceivedframe.append(intent.getStringExtra(BluetoothLeService2.EXTRA_DATA2)) ;
//            	System.out.print("mPlainProtocol.mReceivedframe:");
//            	System.out.println(mPlainProtocol.mReceivedframe.toString());
			}
		}
	};

	public interface BlunoListener {
		void onDeviceDetected(final BluetoothDevice device, int rssi, byte[] scanRecord);

		void onConnectionStateChange(ConnectionStateEnum state);
		// void onConnectionStateChange(ConnectionStateEnum state, String deviceName, String deviceAddress);
		void onSerialReceived(String data);
	}

	public BlunoLibrary(Activity theActivity, FencingBluno back, int slot) {
		mainActivity = theActivity;
		this.back = back;
		this.slot = slot;

		if (!prepareBluetoothFeature()) {
			throw new ExceptionInInitializerError("No BLE Feature");
		}
	}

	public void setBlunoListener(BlunoListener listener) {
		blunoListener = listener;
	}

	public void serialSend(String data) {
		if (mConnectionState == ConnectionStateEnum.isConnected) {
			mSCharacteristic.setValue(data);
			if (slot == 0) {
				mBluetoothLeService.writeCharacteristic(mSCharacteristic);
			} else {
				mBluetoothLeService2.writeCharacteristic(mSCharacteristic);
			}
		}
	}

	public void serialSend(byte[] data) {
		if (mConnectionState == ConnectionStateEnum.isConnected) {
			mSCharacteristic.setValue(data);
			if (slot == 0) {
				mBluetoothLeService.writeCharacteristic(mSCharacteristic);
			} else {
				mBluetoothLeService2.writeCharacteristic(mSCharacteristic);
			}
		}
	}


	public boolean initialize() {
		if (!mBluetoothAdapter.isEnabled()) {
			return false;
		}

		if (!mInitialized) {
			Intent gattServiceIntent = null;
			if (slot == 0) {
				gattServiceIntent = new Intent(mainActivity, BluetoothLeService.class);
			} else {
				gattServiceIntent = new Intent(mainActivity, BluetoothLeService2.class);
			}
			mainActivity.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

			registerReceiver();

			mInitialized = true;
		}

		return true;
	}

	@SuppressWarnings("deprecation")
	public void startScan() {
		if (mConnectionState == ConnectionStateEnum.isToScan) {
			mBluetoothAdapter.startLeScan(mLeScanCallback);
			changeState(ConnectionStateEnum.isScanning);
		}
	}

	@SuppressWarnings("deprecation")
	public void stopScan() {
		if (mConnectionState == ConnectionStateEnum.isScanning) {
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
			changeState(ConnectionStateEnum.isToScan);
		}
	}

	public void connect(String address) {
		connect(address, DEFAULT_BAUD_RATE, DEFAULT_PASSWORD);
	}

	public void connect(String address, int baudrate) {
		connect(address, baudrate, DEFAULT_PASSWORD);
	}

	public void connect(String address, int baudrate, String password) {
		stopScan();

		setBaudRate(baudrate);
		setPassword(password);

		Log.e(TAG, "A");
		if (slot == 0) {
			if (mBluetoothLeService != null) {
				Log.e(TAG, "B");
				if (mBluetoothLeService.connect(address)) {
					Log.e(TAG, "C");
					changeState(ConnectionStateEnum.isConnecting);
					mHandler.postDelayed(mConnectingOverTimeRunnable, 10000);
				} else {
					Log.e(TAG, "Connect request fail");
					changeState(ConnectionStateEnum.isToScan);

				}
			}
		} else {
			if (mBluetoothLeService2 != null) {
				Log.e(TAG, "B");
				if (mBluetoothLeService2.connect(address)) {
					Log.e(TAG, "C");
					changeState(ConnectionStateEnum.isConnecting);
					mHandler.postDelayed(mConnectingOverTimeRunnable, 10000);
				} else {
					Log.e(TAG, "Connect request fail");
					changeState(ConnectionStateEnum.isToScan);

				}
			}
		}
	}

	public void disconnect() {
		changeState(ConnectionStateEnum.isDisconnecting);
        leDisconnect();
		mSCharacteristic = null;
	}

	public void resume() {
		if (mInitialized) {
			registerReceiver();
		}
	}

	public void pause() {
		stopScan();
		if (mReceiverRegistered) {
			if (slot == 0) {
				mainActivity.unregisterReceiver(mGattUpdateReceiver);
			} else {
				mainActivity.unregisterReceiver(mGattUpdateReceiver2);
			}
			mReceiverRegistered = false;
		}
	}

	public void destroy() {
		mainActivity.unbindService(mServiceConnection);

		if (slot == 0) {
			if (mBluetoothLeService != null) {
				mHandler.removeCallbacks(mDisonnectingOverTimeRunnable);
				mBluetoothLeService.close();
			}
		} else {
			if (mBluetoothLeService2 != null) {
				mHandler.removeCallbacks(mDisonnectingOverTimeRunnable);
				mBluetoothLeService2.close();
			}
		}

		mBluetoothLeService = null;
		mBluetoothLeService2 = null;
		mSCharacteristic = null;

		mInitialized = false;

		changeState(ConnectionStateEnum.isNull);
	}

	public boolean isBluetoothEnabled() {
		return mBluetoothAdapter.isEnabled();
	}

	/*
	private static final int REQUEST_ENABLE_BT = 1;

	public void onResumeProcess() {
		//System.out.println("BlUNOActivity onResume");
		// Ensures Bluetooth is enabled on the device. If Bluetooth is not
		// currently enabled,
		// fire an intent to display a dialog asking the user to grant
		// permission to enable it.
		if (!mBluetoothAdapter.isEnabled()) {
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBtIntent = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				((Activity) mainActivity).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			}
		}

        if (slot == 0) {
		mainActivity.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		} else {
		mainActivity.registerReceiver(mGattUpdateReceiver2, makeGattUpdateIntentFilter());
		}

	}

    public void onPauseProcess() {
//        scanLeDevice(false);
        if (slot == 0) {
        mainActivity.unregisterReceiver(mGattUpdateReceiver);
        } else {
        mainActivity.unregisterReceiver(mGattUpdateReceiver2);
        }
        mConnectionState = ConnectionStateEnum.isToScan;
        blunoListener.onConnectionStateChange(mConnectionState);
        leDisconnect();
        mSCharacteristic = null;

    }


    public void onStopProcess() {
        if (slot == 0) {
        if (mBluetoothLeService != null) {
            mHandler.removeCallbacks(mDisconnectingOverTimeRunnable);
            mBluetoothLeService.close();
        }
        } else {
        if (mBluetoothLeService2 != null) {
            mHandler.removeCallbacks(mDisconnectingOverTimeRunnable);
            mBluetoothLeService2.close();
        }
        }
        mSCharacteristic = null;
    }

    public void onDestroyProcess() {
        mainActivity.unbindService(mServiceConnection);
        mBluetoothLeService = null;
        mBluetoothLeService2 = null;
    }

    public void onActivityResultProcess(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT
                && resultCode == Activity.RESULT_CANCELED) {
//            ((Activity) mainActivity).finish();
        }
    }
	 */

	private boolean prepareBluetoothFeature() {
		// Use this check to determine whether BLE is supported on the device.
		// Then you can
		// selectively disable BLE-related features.
		if (!mainActivity.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE)) {
			return false;
		}

		// Initializes a Bluetooth adapter. For API level 18 and above, get a
		// reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager bluetoothManager = (BluetoothManager) mainActivity.getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		// Checks if Bluetooth is supported on the device.
		return mBluetoothAdapter != null;
	}

	private void registerReceiver() {
		final IntentFilter intentFilter = new IntentFilter();

		if (slot == 0) {
			intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
			intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
			intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
			intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
			mainActivity.registerReceiver(mGattUpdateReceiver, intentFilter);
		} else {
			intentFilter.addAction(BluetoothLeService2.ACTION_GATT_CONNECTED2);
			intentFilter.addAction(BluetoothLeService2.ACTION_GATT_DISCONNECTED2);
			intentFilter.addAction(BluetoothLeService2.ACTION_GATT_SERVICES_DISCOVERED2);
			intentFilter.addAction(BluetoothLeService2.ACTION_DATA_AVAILABLE2);
			mainActivity.registerReceiver(mGattUpdateReceiver2, intentFilter);
		}
		mReceiverRegistered = true;
	}

	private void setBaudRate(int baud) {
		mBaudrateBuffer = "AT+CURRUART=" + baud + "\r\n";
	}

	private void setPassword(String pass) {
		mPassword = "AT+PASSWOR=" + pass + "\r\n";
	}

	private void getGattServices(List<BluetoothGattService> gattServices) {
		if (gattServices == null) return;

		String uuid;
		mModelNumberCharacteristic = null;
		mSerialPortCharacteristic = null;
		mCommandCharacteristic = null;

		// Loops through available GATT Services.
		for (BluetoothGattService gattService : gattServices) {
			uuid = gattService.getUuid().toString();
			System.out.println("displayGattServices + uuid=" + uuid);

			List<BluetoothGattCharacteristic> gattCharacteristics =
					gattService.getCharacteristics();

			// Loops through available Characteristics.
			for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
				uuid = gattCharacteristic.getUuid().toString();
				switch (uuid) {
					case ModelNumberStringUUID:
						mModelNumberCharacteristic = gattCharacteristic;
						System.out.println("mModelNumberCharacteristic  " + mModelNumberCharacteristic.getUuid().toString());
						break;
					case SerialPortUUID:
						mSerialPortCharacteristic = gattCharacteristic;
						System.out.println("mSerialPortCharacteristic  " + mSerialPortCharacteristic.getUuid().toString());
						break;
					case CommandUUID:
						mCommandCharacteristic = gattCharacteristic;
						System.out.println("mCommandCharacteristic  " + mCommandCharacteristic.getUuid().toString());
						break;
				}
			}
		}

		if (mModelNumberCharacteristic == null || mSerialPortCharacteristic == null || mCommandCharacteristic == null) {
			Toast.makeText(mainActivity, "Please select DFRobot devices", Toast.LENGTH_SHORT).show();
			changeState(ConnectionStateEnum.isToScan);
		} else {
			mSCharacteristic = mModelNumberCharacteristic;
			if (slot == 0) {
				mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, true);
				mBluetoothLeService.readCharacteristic(mSCharacteristic);
			} else {
				mBluetoothLeService2.setCharacteristicNotification(mSCharacteristic, true);
				mBluetoothLeService2.readCharacteristic(mSCharacteristic);
			}
		}
	}

	private void changeState(ConnectionStateEnum state) {
		mConnectionState = state;
		if (blunoListener != null)
			blunoListener.onConnectionStateChange(mConnectionState);
	}

	/*
	private void changeState(ConnectionStateEnum state, String name, String addr) {
		mConnectionState = state;
		if (blunoListener != null)
			blunoListener.onConnectionStateChange(mConnectionState, name, addr);
	}
	*/

	/**
	 *
	 * @param requestCode
	 * @param permissionsResult
	 */
	public void request(int requestCode, OnPermissionsResult permissionsResult){
		if(!checkPermissionsAll()){
			requestPermissionAll(requestCode, permissionsResult);
		}
	}

	/**
	 * @param permissions
	 * @return
	 */
	protected boolean checkPermissions(String permissions){
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
			int check = mainActivity.checkSelfPermission(permissions);
			return check == PackageManager.PERMISSION_GRANTED;
		}
		return false;
	}

	protected boolean checkPermissionsAll(){
		mPerList.clear();
		for(int i = 0; i < mStrPermission.length; i++ ){
			boolean check = checkPermissions(mStrPermission[i]);
			if(!check){
				mPerList.add(mStrPermission[i]);
			}
		}
		return mPerList.size() > 0 ? false : true;
	}

	/**
	 * @param mPermissions
	 * @param requestCode
	 */
	protected void requestPermission(String[] mPermissions, int requestCode){
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
			requestPermissions(mainActivity, mPermissions, requestCode);
		}
	}

	/**
	 * @param requestCode
	 */
	protected void requestPermissionAll(int requestCode, OnPermissionsResult permissionsResult){
		this.permissionsResult = permissionsResult;
		this.requestCode = requestCode;
		requestPermission((String[]) mPerList.toArray(new String[mPerList.size()]),requestCode);
	}

	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if(requestCode == this.requestCode){
			if(grantResults.length>0){
				for(int i = 0; i < grantResults.length; i++){
					if(grantResults[i] == PackageManager.PERMISSION_DENIED){
						System.out.println(permissions[i]);
						mPerNoList.add(permissions[i]);
					}
				}
				if(permissionsResult != null){
					if(mPerNoList.size() == 0){
						permissionsResult.OnSuccess();
					}else {
						permissionsResult.OnFail(mPerNoList);
					}
				}
			}
		}
	}

	public interface OnPermissionsResult{
		void OnSuccess();
		void OnFail(List<String> noPermissions);
	}
}