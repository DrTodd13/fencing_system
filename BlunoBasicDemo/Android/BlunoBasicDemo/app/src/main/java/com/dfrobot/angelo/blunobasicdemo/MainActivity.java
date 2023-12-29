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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
	private Button buttonScanLeft, buttonScanRight;
	private Button buttonClearLeft, buttonClearRight;
	private TextView receivedTextLeft, receivedTextRight;
	private FencingBluno fencingBlunoLeft, fencingBlunoRight;

	private final List<String> connectedAddressList = new ArrayList<>();

	public MainActivity() {
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		buttonScanLeft = (Button) findViewById(R.id.buttonScanLeft);
		receivedTextLeft = (TextView) findViewById(R.id.receivedTextLeft);
		buttonClearLeft = (Button) findViewById(R.id.buttonClearLeft);

		buttonScanRight = (Button) findViewById(R.id.buttonScanRight);
		receivedTextRight = (TextView) findViewById(R.id.receivedTextRight);
		buttonClearRight = (Button) findViewById(R.id.buttonClearRight);

		fencingBlunoLeft = new FencingBluno(this,
				buttonScanLeft,
				buttonClearLeft,
				receivedTextLeft,
				" left",
				0);

		fencingBlunoRight = new FencingBluno(this,
				buttonScanRight,
				buttonClearRight,
				receivedTextRight,
				" right",
				1);

		fencingBlunoLeft.setOther(fencingBlunoRight);
		fencingBlunoRight.setOther(fencingBlunoLeft);

		fencingBlunoLeft.getBlunoLibrary().request(1000, new BlunoLibrary.OnPermissionsResult() {
			@Override
			public void OnSuccess() {
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
				// intentionally do nothing
			}

			@Override
			public void OnFail(List<String> noPermissions) {
				Toast.makeText(MainActivity.this, "Failed to get permissions for BlunoLibrary", Toast.LENGTH_SHORT).show();
			}
		});

		fencingBlunoLeft.getBlunoLibrary().initialize();
		fencingBlunoRight.getBlunoLibrary().initialize();

		buttonClearLeft.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				fencingBlunoLeft.makeClear();
			}
		});

		buttonClearRight.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				fencingBlunoRight.makeClear();
			}
		});

		buttonScanLeft.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				fencingBlunoLeft.buttonScanOnClickProcess();   //Alert Dialog for selecting the BLE device
			}
		});

		buttonScanRight.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				fencingBlunoRight.buttonScanOnClickProcess();  //Alert Dialog for selecting the BLE device
			}
		});
	}

	protected void onResume() {
		super.onResume();
		System.out.println("BlUNOActivity onResume");
		fencingBlunoLeft.getBlunoLibrary().resume();
		fencingBlunoRight.getBlunoLibrary().resume();
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
		fencingBlunoRight.getBlunoLibrary().pause();
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
		fencingBlunoRight.getBlunoLibrary().destroy();
	}


	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		fencingBlunoLeft.getBlunoLibrary().onRequestPermissionsResult(requestCode, permissions, grantResults);
		fencingBlunoRight.getBlunoLibrary().onRequestPermissionsResult(requestCode, permissions, grantResults);
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	public void addConnectedAddress(String address) {
		connectedAddressList.add(address);
	}

	public void removeConnectedAddress(String address) {
		connectedAddressList.remove(address);
	}

	public boolean isAddressConnected(String address) {
		return connectedAddressList.contains(address);
	}
}