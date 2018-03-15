package nl.dobots.crownstone.gui.control;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import nl.dobots.bluenet.ble.base.structs.ControlMsg;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.mesh.structs.MeshControlMsg;
import nl.dobots.bluenet.ble.mesh.structs.keepalive.MeshKeepAlivePacket;
import nl.dobots.bluenet.ble.mesh.structs.keepalive.MeshKeepAliveSameTimeoutPacket;
import nl.dobots.bluenet.ble.mesh.structs.multiswitch.MeshMultiSwitchListPacket;
import nl.dobots.bluenet.ble.mesh.structs.multiswitch.MeshMultiSwitchPacket;
import nl.dobots.bluenet.ble.mesh.structs.cmd.MeshControlPacket;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;
import nl.dobots.crownstone.CrownstoneDevApp;
import nl.dobots.crownstone.R;

/**
 * Copyright (c) 2015 Bart van Vliet <bart@dobots.nl>. All rights reserved.
 * <p/>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p/>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p/>
 * Created on 1-5-17
 *
 * @author Bart van Vliet
 */
public class ControlMeshFragment extends Fragment {
	private static final String TAG = ControlMeshFragment.class.getCanonicalName();

	// Raw mesh message
//	private EditText _txtMeshMessage;
//	private Button   _btnMeshMessage;

	// Keep alive
	private EditText                       _txtMeshKeepAliveArray;
	private Button                         _btnMeshKeepAliveSend;
	private Button                         _btnMeshKeepAliveClear;
	private EditText                       _txtMeshKeepAliveTimeout;
	private Button                         _btnMeshKeepAliveTimeoutSet;
	private EditText                       _txtMeshKeepAliveId;
	private EditText                       _txtMeshKeepAliveAction;
	private Button                         _btnMeshKeepAliveAddItem;
	private MeshKeepAliveSameTimeoutPacket _meshKeepAliveSameTimeoutPacket = new MeshKeepAliveSameTimeoutPacket();
	private MeshKeepAlivePacket            _meshKeepAlivePacket = new MeshKeepAlivePacket();

	// Multi switch
	private EditText                  _txtMeshMultiSwitchArray;
	private Button                    _btnMeshMultiSwitchSend;
	private Button                    _btnMeshMultiSwitchClear;
	private EditText                  _txtMeshMultiSwitchId;
	private EditText                  _txtMeshMultiSwitchSwitchState;
	private EditText                  _txtMeshMultiSwitchTimeout;
	private EditText                  _txtMeshMultiSwitchIntent;
	private Button                    _btnMeshMultiSwitchAddItem;
	private MeshMultiSwitchListPacket _meshMultiSwitchListPacket = new MeshMultiSwitchListPacket();
	private MeshMultiSwitchPacket     _meshMultiSwitchPacket = new MeshMultiSwitchPacket();

	// Command control
	private EditText          _txtMeshCommandControlId;
	private Button            _btnMeshCommandControlAddId;
	private EditText          _txtMeshCommandControl;
	private Button            _btnMeshCommandControlSet;
	private EditText          _txtMeshCommandControlArray;
	private Button            _btnMeshCommandControlSend;
	private Button            _btnMeshCommandControlClear;
	private MeshControlPacket _meshCommandControlPacket = new MeshControlPacket();



	// Other stuff
	private CrownstoneDevApp _app;
//	private BleExt  _ble;
	private String  _address;
	private boolean _closing;
	private Handler _handler;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		HandlerThread ht = new HandlerThread("BleHandler");
		ht.start();
		_handler = new Handler(ht.getLooper());

		_app = CrownstoneDevApp.getInstance();
//		_ble = ControlActivity.getInstance().getBle();
		_address = ControlActivity.getInstance().getAddress();

		_meshKeepAlivePacket.setPayload(_meshKeepAliveSameTimeoutPacket);
		_meshMultiSwitchPacket.setPayload(_meshMultiSwitchListPacket);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		_closing = true;
		_handler.removeCallbacksAndMessages(null);
		_app.getScanner().stopScanning();
		// finish has to be called on the library to release the objects if the library
		// is not used anymore
		if (_app.getBle().isConnected(null)) {
			_app.getBle().disconnectAndClose(false, new IStatusCallback() {
				@Override
				public void onSuccess() {

				}

				@Override
				public void onError(int error) {

				}
			});
		}
	}


	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.frag_control_mesh, container, false);

		// Mesh message //
//		_txtMeshMessage = (EditText) v.findViewById(R.id.txtMeshMessage);
//		_btnMeshMessage = (Button) v.findViewById(R.id.btnMeshMessage);
//		_btnMeshMessage.setOnClickListener(new View.OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				writeMeshMessage();
//			}
//		});

		////////////////
		// Keep alive //
		////////////////
		_txtMeshKeepAliveTimeout    = (EditText) v.findViewById(R.id.txtMeshKeepAliveTimeout);
		_btnMeshKeepAliveTimeoutSet = (Button)   v.findViewById(R.id.btnMeshKeepAliveTimeoutSet);
		_txtMeshKeepAliveId         = (EditText) v.findViewById(R.id.txtMeshKeepAliveId);
		_txtMeshKeepAliveAction     = (EditText) v.findViewById(R.id.txtMeshKeepAliveAction);
		_btnMeshKeepAliveAddItem    = (Button)   v.findViewById(R.id.btnMeshKeepAliveAddItem);
		_txtMeshKeepAliveArray      = (EditText) v.findViewById(R.id.txtMeshKeepAliveArray);
		_btnMeshKeepAliveSend       = (Button)   v.findViewById(R.id.btnMeshKeepAliveSend);
		_btnMeshKeepAliveClear      = (Button)   v.findViewById(R.id.btnMeshKeepAliveClear);
		_btnMeshKeepAliveAddItem.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				addKeepAliveItem();
			}
		});
		_btnMeshKeepAliveSend.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				writeKeepAlive();
			}
		});
		_btnMeshKeepAliveClear.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				clearKeepAlive();
			}
		});
		_btnMeshKeepAliveTimeoutSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setKeepAliveTimeout();
			}
		});

		//////////////////
		// Multi switch //
		//////////////////
		_txtMeshMultiSwitchId          = (EditText) v.findViewById(R.id.txtMeshMultiSwitchId);
		_txtMeshMultiSwitchSwitchState = (EditText) v.findViewById(R.id.txtMeshMultiSwitchSwitchState);
		_txtMeshMultiSwitchTimeout     = (EditText) v.findViewById(R.id.txtMeshMultiSwitchTimeout);
		_txtMeshMultiSwitchIntent      = (EditText) v.findViewById(R.id.txtMeshMultiSwitchIntent);
		_btnMeshMultiSwitchAddItem     = (Button)   v.findViewById(R.id.btnMeshMultiSwitchAddItem);
		_txtMeshMultiSwitchArray       = (EditText) v.findViewById(R.id.txtMeshMultiSwitchArray);
		_btnMeshMultiSwitchSend        = (Button)   v.findViewById(R.id.btnMeshMultiSwitchSend);
		_btnMeshMultiSwitchClear       = (Button)   v.findViewById(R.id.btnMeshMultiSwitchClear);
		_btnMeshMultiSwitchSend.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				writeMultiSwitch();
			}
		});
		_btnMeshMultiSwitchClear.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				clearMultiSwitch();
			}
		});
		_btnMeshMultiSwitchAddItem.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				addMultiSwitchItem();
			}
		});

		//////////////////////////
		// Mesh command control //
		//////////////////////////
		_txtMeshCommandControlId    = (EditText) v.findViewById(R.id.txtMeshCommandControlId);
		_btnMeshCommandControlAddId = (Button)   v.findViewById(R.id.btnMeshCommandControlAddId);
		_txtMeshCommandControl      = (EditText) v.findViewById(R.id.txtMeshCommandControl);
		_btnMeshCommandControlSet   = (Button)   v.findViewById(R.id.btnMeshCommandControlSet);
		_txtMeshCommandControlArray = (EditText) v.findViewById(R.id.txtMeshCommandControlArray);
		_btnMeshCommandControlSend  = (Button)   v.findViewById(R.id.btnMeshCommandControlSend);
		_btnMeshCommandControlClear = (Button)   v.findViewById(R.id.btnMeshCommandControlClear);
		_btnMeshCommandControlAddId.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				addCommandControlId();
			}
		});
		_btnMeshCommandControlSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setCommandControl();
			}
		});
		_btnMeshCommandControlSend.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				writeCommandControl();
			}
		});
		_btnMeshCommandControlClear.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				clearCommandControl();
			}
		});

//		return super.onCreateView(inflater, container, savedInstanceState);
		return v;
	}


	void writeMeshMessage(String text) {
		text = text.replace("[", ""); // Remove all [
		text = text.replace("]", ""); // Remove all ]
		String[] textArr = text.split("[ ,]+"); // Split by space and/or comma
		byte[] msgBytes = new byte[textArr.length];
		for (int i=0; i<textArr.length; i++) {
			msgBytes[i] = (byte)Integer.parseInt(textArr[i]);
		}
		BleLog.getInstance().LOGd(TAG, "txt=" + text);
		BleLog.getInstance().LOGd(TAG, "msg=" + BleUtils.bytesToString(msgBytes));

		MeshControlMsg msg = new MeshControlMsg();
		if (!msg.fromArray(msgBytes)) {
			BleLog.getInstance().LOGw(TAG, "Invalid message: " + BleUtils.bytesToString(msgBytes));
			return;
		}
		_app.getBle().writeMeshMessage(_address, msg, new IStatusCallback() {
			@Override
			public void onSuccess() {
				BleLog.getInstance().LOGd(TAG, "mesh message success");
			}

			@Override
			public void onError(int error) {
				BleLog.getInstance().LOGe(TAG, "mesh message error: " + error);
			}
		});
	}

	void writeControlMessage(String text) {
		text = text.replace("[", ""); // Remove all [
		text = text.replace("]", ""); // Remove all ]
		String[] textArr = text.split("[ ,]+"); // Split by space and/or comma
		byte[] msgBytes = new byte[textArr.length];
		for (int i=0; i<textArr.length; i++) {
			msgBytes[i] = (byte)Integer.parseInt(textArr[i]);
		}
		BleLog.getInstance().LOGd(TAG, "txt=" + text);
		BleLog.getInstance().LOGd(TAG, "msg=" + BleUtils.bytesToString(msgBytes));

		ControlMsg msg = new ControlMsg();
		if (!msg.fromArray(msgBytes)) {
			BleLog.getInstance().LOGw(TAG, "Invalid message: " + BleUtils.bytesToString(msgBytes));
			return;
		}
		_app.getBle().writeControl(_address, msg, new IStatusCallback() {
			@Override
			public void onSuccess() {
				BleLog.getInstance().LOGd(TAG, "mesh message success");
			}

			@Override
			public void onError(int error) {
				BleLog.getInstance().LOGe(TAG, "mesh message error: " + error);
			}
		});
	}


	//////////////////
	// Mesh message //
	//////////////////

//	void writeMeshMessage() {
//		BleLog.getInstance().LOGd(TAG, "writeMeshMessage");
//		String text = _txtMeshMessage.getText().toString();
//		writeMeshMessage(text);
//	}


	////////////////
	// Keep alive //
	////////////////

	void writeKeepAlive() {
		BleLog.getInstance().LOGd(TAG, "writeKeepAlive");
		String text = _txtMeshKeepAliveArray.getText().toString();
		writeControlMessage(text);
	}

	void addKeepAliveItem() {
		BleLog.getInstance().LOGd(TAG, "addKeepAliveItem");
		int id = Integer.parseInt(_txtMeshKeepAliveId.getText().toString());
		int action = Integer.parseInt(_txtMeshKeepAliveAction.getText().toString());
		_meshKeepAliveSameTimeoutPacket.addItem(id, action);
		updateKeepAliveText();
	}

	void setKeepAliveTimeout() {
		BleLog.getInstance().LOGd(TAG, "setKeepAliveTimeout");
		int timeout = Integer.parseInt(_txtMeshKeepAliveTimeout.getText().toString());
		_meshKeepAliveSameTimeoutPacket.setTimeout(timeout);
		updateKeepAliveText();
	}

	void clearKeepAlive() {
		BleLog.getInstance().LOGd(TAG, "clearKeepAlive");
		int timeout = Integer.parseInt(_txtMeshKeepAliveTimeout.getText().toString());
		_meshKeepAliveSameTimeoutPacket = new MeshKeepAliveSameTimeoutPacket();
		_meshKeepAlivePacket.setPayload(_meshKeepAliveSameTimeoutPacket);
		updateKeepAliveText();
	}

	void updateKeepAliveText() {
		byte[] payload = _meshKeepAlivePacket.toArray();
		ControlMsg msg = new ControlMsg(BluenetConfig.CMD_KEEP_ALIVE_MESH, payload.length, payload);
		String text = BleUtils.bytesToString(msg.toArray());
		_txtMeshKeepAliveArray.setText(text);
	}


	//////////////////
	// Multi switch //
	//////////////////

	void writeMultiSwitch() {
		BleLog.getInstance().LOGd(TAG, "writeMultiSwitch");
		String text = _txtMeshMultiSwitchArray.getText().toString();
		writeControlMessage(text);
	}

	void clearMultiSwitch() {
		BleLog.getInstance().LOGd(TAG, "clearMultiSwitch");
		_meshMultiSwitchListPacket = new MeshMultiSwitchListPacket();
		_meshMultiSwitchPacket.setPayload(_meshMultiSwitchListPacket);
		updateMultiSwitchText();
	}

	void addMultiSwitchItem() {
		BleLog.getInstance().LOGd(TAG, "addMultiSwitchItem");
		int crownstoneId = Integer.parseInt(_txtMeshMultiSwitchId.getText().toString());
		int switchState = Integer.parseInt(_txtMeshMultiSwitchSwitchState.getText().toString());
		int timeout = Integer.parseInt(_txtMeshMultiSwitchTimeout.getText().toString());
		int intent = Integer.parseInt(_txtMeshMultiSwitchIntent.getText().toString());
		_meshMultiSwitchListPacket.addItem(crownstoneId, switchState, timeout, intent);
		updateMultiSwitchText();
	}

	void updateMultiSwitchText() {
		BleLog.getInstance().LOGd(TAG, "updateMultiSwitchText");
		byte[] payload = _meshMultiSwitchPacket.toArray();
		ControlMsg msg = new ControlMsg(BluenetConfig.CMD_MULTI_SWITCH, payload.length, payload);
		String text = BleUtils.bytesToString(msg.toArray());
		_txtMeshMultiSwitchArray.setText(text);
	}


	/////////////////////
	// Command control //
	/////////////////////
	void addCommandControlId() {
		BleLog.getInstance().LOGd(TAG, "addCommandControlId");
		int crownstoneId = Integer.parseInt(_txtMeshCommandControlId.getText().toString());
		_meshCommandControlPacket.addId(crownstoneId);
		updateCommandControlText();
	}
	void setCommandControl() {
		BleLog.getInstance().LOGd(TAG, "setCommandControl");
		String text = _txtMeshCommandControl.getText().toString();
		text = text.replace("[", ""); // Remove all [
		text = text.replace("]", ""); // Remove all ]
		String[] textArr = text.split("[ ,]+"); // Split by space and/or comma
		byte[] msgBytes = new byte[textArr.length];
		for (int i=0; i<textArr.length; i++) {
			msgBytes[i] = (byte)Integer.parseInt(textArr[i]);
		}
		BleLog.getInstance().LOGd(TAG, "txt=" + text);
		BleLog.getInstance().LOGd(TAG, "msg=" + BleUtils.bytesToString(msgBytes));
		_meshCommandControlPacket.setPayload(msgBytes);
		updateCommandControlText();
	}
	void writeCommandControl() {
		BleLog.getInstance().LOGd(TAG, "writeCommandControl");
		String text = _txtMeshCommandControlArray.getText().toString();
		writeControlMessage(text);
	}
	void clearCommandControl() {
		BleLog.getInstance().LOGd(TAG, "clearCommandControl");
		_meshCommandControlPacket = new MeshControlPacket();
		updateCommandControlText();
	}
	void updateCommandControlText() {
		BleLog.getInstance().LOGd(TAG, "updateCommandControlText");
		byte[] payload = _meshCommandControlPacket.toArray();
		if (payload == null) {
			return;
		}
		ControlMsg msg = new ControlMsg(BluenetConfig.CMD_MESH_COMMAND, payload.length, payload);
		String text = BleUtils.bytesToString(msg.toArray());
		_txtMeshCommandControlArray.setText(text);
	}
}
