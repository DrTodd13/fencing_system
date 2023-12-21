package com.dfrobot.angelo.blunobasicdemo;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
	private Button buttonScanLeft, buttonScanRight;
	private Button buttonSendLeft, buttonSendRight;
	private EditText sendTextLeft, sendTextRight;
	private TextView receivedTextLeft, receivedTextRight;
	private FencingBluno fencingBlunoLeft, fencingBlunoRight;

	public MainActivity() {
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		buttonScanLeft = (Button) findViewById(R.id.buttonScanLeft);
		receivedTextLeft = (TextView) findViewById(R.id.receivedTextLeft);
		sendTextLeft = (EditText) findViewById(R.id.sendTextLeft);
		buttonSendLeft = (Button) findViewById(R.id.buttonSendLeft);

		buttonScanRight = (Button) findViewById(R.id.buttonScanRight);
		receivedTextRight = (TextView) findViewById(R.id.receivedTextRight);
		sendTextRight = (EditText) findViewById(R.id.sendTextRight);
		buttonSendRight = (Button) findViewById(R.id.buttonSendRight);

		fencingBlunoLeft = new FencingBluno(this,
				buttonScanLeft,
				buttonSendLeft,
				sendTextLeft,
				receivedTextLeft,
				" left");
		fencingBlunoRight = new FencingBluno(this,
				buttonScanRight,
				buttonSendRight,
				sendTextRight,
				receivedTextRight,
				" right");
		fencingBlunoLeft.getBlunoLibrary().request(1000, new BlunoLibrary.OnPermissionsResult() {
			@Override
			public void OnSuccess() {
				//Toast.makeText(MainActivity.this,"权限请求成功",Toast.LENGTH_SHORT).show();
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
				//Toast.makeText(MainActivity.this,"权限请求成功",Toast.LENGTH_SHORT).show();
				// intentionally do nothing
			}

			@Override
			public void OnFail(List<String> noPermissions) {
				Toast.makeText(MainActivity.this, "Failed to get permissions for BlunoLibrary", Toast.LENGTH_SHORT).show();
			}
		});

		fencingBlunoLeft.getBlunoLibrary().initialize();                                                    //onCreate Process by BlunoLibrary
		fencingBlunoRight.getBlunoLibrary().initialize();

		// Using default baudrate of 115200

		buttonSendLeft.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				fencingBlunoLeft.getBlunoLibrary().serialSend(sendTextLeft.getText().toString());                //send the data to the BLUNO
			}
		});

		buttonSendRight.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				fencingBlunoRight.getBlunoLibrary().serialSend(sendTextRight.getText().toString());                //send the data to the BLUNO
			}
		});
		buttonScanLeft.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				fencingBlunoLeft.getBlunoLibrary().buttonScanOnClickProcess();                                        //Alert Dialog for selecting the BLE device
			}
		});

		buttonScanRight.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				fencingBlunoRight.getBlunoLibrary().buttonScanOnClickProcess();                                        //Alert Dialog for selecting the BLE device
			}
		});
	}

	protected void onResume() {
		super.onResume();
		System.out.println("BlUNOActivity onResume");
		fencingBlunoLeft.getBlunoLibrary().onResumeProcess();                                                        //onResume Process by BlunoLibrary
		fencingBlunoRight.getBlunoLibrary().onResumeProcess();
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		fencingBlunoLeft.getBlunoLibrary().onActivityResultProcess(requestCode, resultCode, data);                    //onActivityResult Process by BlunoLibrary
		fencingBlunoRight.getBlunoLibrary().onActivityResultProcess(requestCode, resultCode, data);
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onPause() {
		super.onPause();
		fencingBlunoLeft.getBlunoLibrary().onPauseProcess();                                                        //onPause Process by BlunoLibrary
		fencingBlunoRight.getBlunoLibrary().onPauseProcess();
	}

	protected void onStop() {
		super.onStop();
		fencingBlunoLeft.getBlunoLibrary().onStopProcess();                                                        //onStop Process by BlunoLibrary
		fencingBlunoRight.getBlunoLibrary().onStopProcess();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		fencingBlunoLeft.getBlunoLibrary().onDestroyProcess();                                                        //onDestroy Process by BlunoLibrary
		fencingBlunoRight.getBlunoLibrary().onDestroyProcess();
	}

	/*
	@Override
	public void onConnectionStateChange(connectionStateEnum theConnectionState) {//Once connection state changes, this function will be called
		switch (theConnectionState) {											//Four connection state
		case isConnected:
			buttonScanLeft.setText("Connected Left");
			break;
		case isConnecting:
			buttonScanLeft.setText("Connecting");
			break;
		case isToScan:
			buttonScanLeft.setText("Scan Left");
			break;
		case isScanning:
			buttonScanLeft.setText("Scanning");
			break;
		case isDisconnecting:
			buttonScanLeft.setText("isDisconnecting");
			break;
		default:
			break;
		}
	}

	@Override
	public void onSerialReceived(String theString) {							//Once connection data received, this function will be called
		// TODO Auto-generated method stub
		serialReceivedTextLeft.append(theString);							//append the text into the EditText
		//The Serial data from the BLUNO may be sub-packaged, so using a buffer to hold the String is a good choice.
		((ScrollView)serialReceivedTextLeft.getParent()).fullScroll(View.FOCUS_DOWN);
	}
	 */

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		fencingBlunoLeft.getBlunoLibrary().onRequestPermissionsResult(requestCode, permissions, grantResults);
		fencingBlunoRight.getBlunoLibrary().onRequestPermissionsResult(requestCode, permissions, grantResults);
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}
}