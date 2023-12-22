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

	private static final boolean rightActive = true;

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
				" left",
				0);

		if (rightActive) {
			fencingBlunoRight = new FencingBluno(this,
					buttonScanRight,
					buttonSendRight,
					sendTextRight,
					receivedTextRight,
					" right",
					1);
		}

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

		if (rightActive) {
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
		}

		fencingBlunoLeft.getBlunoLibrary().initialize();
		if (rightActive) {
			fencingBlunoRight.getBlunoLibrary().initialize();
		}

		buttonSendLeft.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				fencingBlunoLeft.getBlunoLibrary().serialSend(sendTextLeft.getText().toString());                //send the data to the BLUNO
			}
		});

		if (rightActive) {
			buttonSendRight.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
					fencingBlunoRight.getBlunoLibrary().serialSend(sendTextRight.getText().toString());                //send the data to the BLUNO
				}
			});
		}

		buttonScanLeft.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				fencingBlunoLeft.buttonScanOnClickProcess();                                        //Alert Dialog for selecting the BLE device
			}
		});

		if (rightActive) {
			buttonScanRight.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
					fencingBlunoRight.buttonScanOnClickProcess();                                        //Alert Dialog for selecting the BLE device
				}
			});
		}
	}

	protected void onResume() {
		super.onResume();
		System.out.println("BlUNOActivity onResume");
		fencingBlunoLeft.getBlunoLibrary().resume();
		if (rightActive) {
			fencingBlunoRight.getBlunoLibrary().resume();
		}
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		assert false;
		//fencingBlunoLeft.getBlunoLibrary().onActivityResultProcess(requestCode, resultCode, data);                    //onActivityResult Process by BlunoLibrary
		//fencingBlunoRight.getBlunoLibrary().onActivityResultProcess(requestCode, resultCode, data);
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onPause() {
		super.onPause();
		fencingBlunoLeft.getBlunoLibrary().pause();
		if (rightActive) {
			fencingBlunoRight.getBlunoLibrary().pause();
		}
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
		if (rightActive) {
			fencingBlunoRight.getBlunoLibrary().destroy();
		}
	}


	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		fencingBlunoLeft.getBlunoLibrary().onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (rightActive) {
			fencingBlunoRight.getBlunoLibrary().onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}
}