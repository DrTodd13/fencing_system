package com.example.ttfencing;

import android.bluetooth.BluetoothSocket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
//import java.util.UUID;

public class LaptopThread extends Thread {
    private final BluetoothSocket socket;
    private final MainActivity main;
    private boolean running;
    private DataInputStream dis;
    private DataOutputStream dos;

    public LaptopThread(MainActivity main, BluetoothSocket socket) {
        this.main = main;
        this.socket = socket;
        running = true;

        InputStream sinput = null;
        try {
            sinput = socket.getInputStream();
        } catch (IOException e) {
            running = false;
            main.setError(4);
            return;
        }
        dis = new DataInputStream(sinput);

        OutputStream soutput = null;
        try {
            soutput = socket.getOutputStream();
        } catch (IOException e) {
            running = false;
            main.setError(4);
            return;
        }
        dos = new DataOutputStream(soutput);
    }

    public void sendLeftScore(int x) throws IOException {
        dos.writeInt(0);
        dos.writeInt(x);
    }
    public void sendRightScore(int x) throws IOException {
        dos.writeInt(1);
        dos.writeInt(x);
    }

    public void sendTime(float x) throws IOException {
        dos.writeInt(2);
        dos.writeFloat(x);
    }

    public void sendLeftTouch(int x) throws IOException {
        dos.writeInt(3);
        dos.writeInt(x);
    }
    public void sendRightTouch(int x) throws IOException {
        dos.writeInt(4);
        dos.writeInt(x);
    }

    public void run() {
        while (running) {
            int msgType;
            try {
                msgType = dis.readInt();
            } catch (IOException e) {
                running = false;
                main.setError(5);
                return;
            }

            /*
             * This is for when the laptop runs the right-of-way model and sends the phone
             * changes of right-of-way.
             */
            switch(msgType) {
                case 0:
                    if (!handleRightOfWayMessage(dis)) {
                        running = false;
                        main.setError(8);
                    }
                    break;
                default:
                    running = false;
                    main.setError(7);
            }
        }
    }

    private boolean handleRightOfWayMessage(DataInputStream dis) {
        int rightOfWayHolder;
        try {
            rightOfWayHolder = dis.readInt();
            main.setRightOfWayHolder(rightOfWayHolder);
        } catch (IOException e) {
            running = false;
            main.setError(5);
            return false;
        }

        return true;
    }
    public void cancel() {
        running = false;
        try {
            socket.close();
        } catch (IOException e) {
            main.setError(6);
        }
    }
}