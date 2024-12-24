package com.example.ttfencing;

import static androidx.core.app.ActivityCompat.startActivityForResult;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.util.UUID;

public class BluetoothTTFencing extends Thread {
    private BluetoothServerSocket mmServerSocket;
    private final MainActivity main;
    public boolean running;
    private final BluetoothAdapter bluetoothAdapter;

    /*
    @SuppressLint("MissingPermission")
    public void connectPermissionGranted() {
        try {
            mmServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("ttfencing", UUID.randomUUID());
        } catch (IOException e) {
            main.setError(0);
        }

        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivityForResult(main, discoverableIntent, MainActivity.DISCOVERY_REQUEST, null);
    }
     */

    public BluetoothTTFencing(MainActivity main) {
        this.main = main;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mmServerSocket = null;
        running = true;

        //connectPermissionGranted();
        /*
        if (ActivityCompat.checkSelfPermission(main, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(main, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, MainActivity.REQUEST_CONNECT_PERMISSION);
        } else {
            connectPermissionGranted();
        }
         */
    }

    public void run() {
        /*
        BluetoothSocket socket = null;
        while (running) {
            if (mmServerSocket == null) {
                continue;
            }

            try {
                socket = mmServerSocket.accept();
            } catch (IOException e) {
                main.setError(1);
            }

            if (socket != null) {
                main.startBluetoothConnection(socket);
            }
        }
         */
    }

    public void cancel() {
        /*
        try {
            mmServerSocket.close();
        } catch (IOException e) {
            main.setError(2);
        }
         */
    }
}