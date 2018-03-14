package nl.dobots.crownstone.gui.control;

import android.app.Activity;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;

import nl.dobots.bluenet.ble.base.BleConfiguration;
import nl.dobots.bluenet.ble.base.callbacks.IBooleanCallback;
import nl.dobots.bluenet.ble.base.callbacks.IDiscoveryCallback;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.CrownstoneServiceData;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.crownstone.R;
import nl.dobots.crownstone.gui.utils.AdvertisementGraph;
import nl.dobots.crownstone.gui.utils.ProgressSpinner;

/**
 * This fragment is part of the ControlActivity and provides the page to control the crownstone
 *  - switch PWM
 *  - switch Relay
 *  - Factory Reset (menu)
 *  - goTo DFU (menu)
 *  - graph with service data
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class ControlMainFragment extends Fragment {

	private static final String TAG = ControlMainFragment.class.getCanonicalName();

	private static final int GRAPH_UPDATE_TIME = 2000;

//	private CheckBox _cbPwmEnable;

	private SeekBar _sbSwitch;
	private EditText _editSwitch;
	private Button _btnSwitchOn;
	private Button _btnSwitchOff;
	private Button _btnRelayOff;
	private Button _btnRelayOn;
	private Button _btnPwmOff;
	private Button _btnPwmOn;
	private EditText _editPwm;
	private RelativeLayout _layGraph;
	private ImageButton _btnZoomIn;
	private ImageButton _btnZoomOut;
	private ImageButton _btnZoomReset;

	private TextView _txtLastScanResponse;
	private TextView _txtDimmerState;
	private TextView _txtPowerFactor;
	private TextView _txtPowerUsage;
	private TextView _txtEnergyUsage;
	private TextView _txtChipTemp;
	private TextView _txtName;
	private TextView _txtDimmingAvailable;
	private TextView _txtDimmingAllowed;
	private TextView _txtSwitchLocked;
	private TextView _txtTimeSet;
	private TextView _textErrorBitmask;

	private RelativeLayout _layStatistics;
//	private RelativeLayout _layControl;
	private LinearLayout _layControl;

	private Handler _handler;
	private boolean _closing;

	private BleExt _ble;
	private String _address;

	private AdvertisementGraph _graph;

	private Context _context;

	private BleConfiguration _bleConfiguration;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);

		_context = getContext();

		HandlerThread ht = new HandlerThread("BleHandler");
		ht.start();
		_handler = new Handler(ht.getLooper());

		_ble = ControlActivity.getInstance().getBle();
		_address = ControlActivity.getInstance().getAddress();
		_bleConfiguration = new BleConfiguration(_ble.getBleBase());
	}

	private abstract class SequentialRunner implements Runnable {

		private final String _name;
		private boolean noWait = false;

		public SequentialRunner(String name) {
			_name = name;
		}

		public abstract boolean execute();


		protected synchronized void done() {
			this.notify();
			noWait = true;
			BleLog.getInstance().LOGv(TAG, "notify");
		}

		@Override
		public void run() {
				synchronized (this) {
			if (execute()) {
					try {
						BleLog.getInstance().LOGv(TAG, "wait");
						if (!noWait) {
							wait();
						}
						BleLog.getInstance().LOGv(TAG, "wait done");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	private long _lastUpdate = System.nanoTime();

	private SequentialRunner _advStateChecker = new SequentialRunner("_advStateChecker") {
		@Override
		public boolean execute() {
			// update graph, to move x axis along even if device is not scanned, or currently connected

			if (Build.VERSION.SDK_INT >= 19) {
				if (_ble.isDisconnected(null)) {
					BleLog.getInstance().LOGi(TAG, "starting scan");
					if (!_ble.isScanning()) {
						_ble.getBleBase().setScanMode(ScanSettings.SCAN_MODE_BALANCED);
						_ble.startScan(new IBleDeviceCallback() {
							@Override
							public void onSuccess() {

							}

							@Override
							public void onDeviceScanned(BleDevice device) {
								if (device.getAddress().equals(_address)) {
									BleLog.getInstance().LOGv(TAG, "scanned:" + device.toString());

									// Draw a point at most every GRAPH_UPDATE_TIME ms.
									if (System.nanoTime() - _lastUpdate > GRAPH_UPDATE_TIME * 1000000) {
										CrownstoneServiceData serviceData = device.getServiceData();

										// Only use the data when it has serviceData which is not external data
										if (serviceData != null && !serviceData.getFlagExternalData()) {
											_lastUpdate = System.nanoTime();
											_graph.onServiceData(device.getName(), serviceData);
										} else {
											_graph.updateRange();
										}
									}

									// Update text views
									CrownstoneServiceData serviceData = device.getServiceData();
									if (serviceData != null && !serviceData.getFlagExternalData()) {
										_txtLastScanResponse.setText("Last scanned: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
										_txtDimmerState.setText("Dimmer state: " + serviceData.getPwm());
										_txtPowerFactor.setText("Power factor: " + serviceData.getPowerFactor());
										_txtPowerUsage.setText("Power usage: " + serviceData.getPowerUsageReal() + " W");
										_txtEnergyUsage.setText("Energy used: " + serviceData.getAccumulatedEnergy() + " J");
										_txtChipTemp.setText("Chip temp: " + serviceData.getTemperature() + " C");
										_txtName.setText("Name: " + device.getName());
										_txtDimmingAvailable.setText("Dimming available: " + serviceData.getFlagDimmingAvailable());
										_txtDimmingAllowed.setText("Dimming allowed: " + serviceData.getFlagDimmingAllowed());
										_txtSwitchLocked.setText("Switch locked: " + serviceData.getFlagSwitchLocked());
										_txtTimeSet.setText("Time set: " + serviceData.getFlagTimeSet());
										_textErrorBitmask.setText("Errors: " + serviceData.getErrorBitMaskString());

										// It looks a bit weird to see state change to old state, when you set a new one
//										_sbSwitch.setProgress(serviceData.getPwm());
									}
								}
							}

							@Override
							public void onError(int error) {
								BleLog.getInstance().LOGe(TAG, "scan error: %d", error);
							}
						});

					}
					done();
					return true;
				}
				// When connected, keep updating range
				else {
					BleLog.getInstance().LOGv(TAG, "wait with starting scan..");
					_graph.updateRange();
					_handler.postDelayed(this, 100);
					return false;
				}
			}
//			// On older versions we can stop scanning each time we got an advertisement,
//			// and start again after GRAPH_UPDATE_TIME
//			else {
//				if (_ble.isDisconnected(null)) {
//					BleLog.getInstance().LOGi(TAG, "starting scan");
//					if (!_ble.isScanning()) {
//						_ble.startScan(new IBleDeviceCallback() {
//							@Override
//							public void onSuccess() {
//
//							}
//
//							@Override
//							public void onDeviceScanned(BleDevice device) {
//								if (device.getAddress().equals(_address)) {
//									BleLog.getInstance().LOGv(TAG, "scanned:" + device.toString());
//
//									// Draw a point at most every GRAPH_UPDATE_TIME ms.
//									if (System.nanoTime() - _lastUpdate > GRAPH_UPDATE_TIME * 1000000) {
//										CrownstoneServiceData serviceData = device.getServiceData();
//
//										// Only use the data when it has serviceData which is not external data
//										if (serviceData != null && !serviceData.isExternalData()) {
//											_lastUpdate = System.nanoTime();
//											_graph.onServiceData(device.getName(), serviceData);
//											// Once an advertisement is received for the device, stop the scan again
//											_ble.stopScan(null);
//											done();
//										} else {
//											_graph.updateRange();
//										}
//									}
//									// Update text views
//									CrownstoneServiceData serviceData = device.getServiceData();
//									if (serviceData != null && !serviceData.isExternalData()) {
//										_txtLastScanResponse.setText("Last scanned: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
//										_txtDimmerState.setText("Dimmer state: " + serviceData.getPwm());
//										_txtPowerUsage.setText("Power usage: " + serviceData.getPowerUsage() + " mW");
//										_txtEnergyUsage.setText("Energy used: " + serviceData.getAccumulatedEnergy() + " J");
//										_txtChipTemp.setText("Chip temp: " + serviceData.getTemperature() + " C");
//										_txtName.setText("Name: " + device.getName());
////										_sbSwitch.setProgress(serviceData.getPwm());
//									}
//								}
//								if (_closing) {
//									BleLog.getInstance().LOGd(TAG, "closing: stop scanning");
//									_ble.stopScan(null);
//									done();
//								}
//							}
//
//							@Override
//							public void onError(int error) {
//								BleLog.getInstance().LOGe(TAG, "scan error: %d", error);
//								done();
//							}
//						});
//					}
//					_handler.postDelayed(this, GRAPH_UPDATE_TIME);
//					return true;
//				}
//				// When connected, keep updating range
//				else {
//					BleLog.getInstance().LOGv(TAG, "wait with starting scan..");
//					_graph.updateRange();
//					_handler.postDelayed(this, 100);
//					return false;
//				}
//			}
			return false;
		}
	};

	@Override
	public void onDestroy() {
		super.onDestroy();
		_closing = true;
		_handler.removeCallbacksAndMessages(null);
		if (_ble.isScanning()) {
			_ble.stopScan(null);
		}
		// finish has to be called on the library to release the objects if the library
		// is not used anymore
		if (_ble.isConnected(null)) {
			_ble.disconnectAndClose(false, new IStatusCallback() {
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
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		Log.i(TAG, "onCreateView");
		View v = inflater.inflate(R.layout.frag_control_main, container, false);

		_editSwitch = (EditText) v.findViewById(R.id.editSwitch);
		_editSwitch.setText("0");
		_editSwitch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				// The IME type should match the type set as imeOptions for the editText
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					setSwitch(Integer.parseInt(_editSwitch.getText().toString()));
					return true;
				}
				return false;
			}
		});

		_sbSwitch = (SeekBar) v.findViewById(R.id.sbSwitch);
//		_sbSwitch.setEnabled(_cbPwmEnable.isChecked());
		_sbSwitch.setMax(99);
		_sbSwitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				_editSwitch.setText("" + progress);
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				setSwitch(seekBar.getProgress());
			}
		});

		_btnSwitchOn = (Button) v.findViewById(R.id.btnSwitchOn);
		_btnSwitchOn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				switchOn();
			}
		});

		_btnSwitchOff = (Button) v.findViewById(R.id.btnSwitchOff);
		_btnSwitchOff.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				switchOff();
			}
		});

		_btnPwmOn = (Button) v.findViewById(R.id.btnPwmOn);
		_btnPwmOn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				pwmOn();
			}
		});

		_btnPwmOff = (Button) v.findViewById(R.id.btnPwmOff);
		_btnPwmOff.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				pwmOff();
			}
		});

		_btnRelayOn = (Button) v.findViewById(R.id.btnRelayOn);
		_btnRelayOn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				relayOn();
			}
		});

		_btnRelayOff = (Button) v.findViewById(R.id.btnRelayOff);
		_btnRelayOff.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				relayOff();
			}
		});

//		_cbPwmEnable = (CheckBox) v.findViewById(R.id.cbPwmEnable);
//		_cbPwmEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
//			@Override
//			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//				_sbSwitch.setEnabled(isChecked);
//			}
//		});

		_editPwm = (EditText) v.findViewById(R.id.editPwm);
		_editPwm.setText("0");
		_editPwm.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				// The IME type should match the type set as imeOptions for the editText
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					setPwm(Integer.parseInt(_editPwm.getText().toString()));
					return true;
				}
//				// User pressed the enter button
//				if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN) {
//					setPwm(Integer.parseInt(_editPwm.getText().toString()));
//					return true;
//				}
				return false;
			}
		});

//		_layControl = (RelativeLayout) v.findViewById(R.id.layControl);
		_layControl = (LinearLayout) v.findViewById(R.id.layControl);
		_layStatistics = (RelativeLayout) v.findViewById(R.id.layContainer);

		_layGraph = (RelativeLayout) v.findViewById(R.id.graph);
		_graph = new AdvertisementGraph(getActivity());
		_graph.setView(_layGraph);

		_btnZoomIn = (ImageButton) v.findViewById(R.id.zoomIn);
		_btnZoomIn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_layStatistics.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
				_layControl.setVisibility(View.INVISIBLE);
			}
		});
		_btnZoomOut = (ImageButton) v.findViewById(R.id.zoomOut);
		_btnZoomOut.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int pixels = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, getResources().getDisplayMetrics());
				_layStatistics.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, pixels));
				_layControl.setVisibility(View.VISIBLE);
			}
		});
		_btnZoomReset = (ImageButton) v.findViewById(R.id.zoomReset);
		_btnZoomReset.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_graph.resetZoom();
			}
		});

//		enablePwm(_pwmEnabled);
		_handler.postDelayed(_advStateChecker, 2000);

		_txtLastScanResponse = (TextView) v.findViewById(R.id.textLastScanResponse);
		_txtDimmerState      = (TextView) v.findViewById(R.id.textDimmerState);
		_txtPowerFactor      = (TextView) v.findViewById(R.id.textPowerFactor);
		_txtPowerUsage       = (TextView) v.findViewById(R.id.textPowerUsage);
		_txtEnergyUsage      = (TextView) v.findViewById(R.id.textEnergyUsage);
		_txtChipTemp         = (TextView) v.findViewById(R.id.textChipTemp);
		_txtName             = (TextView) v.findViewById(R.id.textName);
		_txtDimmingAvailable = (TextView) v.findViewById(R.id.textDimmingAvailable);
		_txtDimmingAllowed   = (TextView) v.findViewById(R.id.textDimmingAllowed);
		_txtSwitchLocked     = (TextView) v.findViewById(R.id.textSwitchLocked);
		_txtTimeSet          = (TextView) v.findViewById(R.id.textTimeSet);
		_textErrorBitmask    = (TextView) v.findViewById(R.id.textErrorBitmask);

//		Log.i(TAG, "isFocusable: " + _layControl.isFocusable() + " " + _btnPwmOn.isFocusable() + " " + _sbSwitch.isFocusable() + " " + _txtLastScanResponse.isFocusable());
//		Log.i(TAG, "isFocusableTouch: " + _layControl.isFocusableInTouchMode() + " " + _btnPwmOn.isFocusableInTouchMode() + " " + _sbSwitch.isFocusableInTouchMode() + " " + _txtLastScanResponse.isFocusableInTouchMode());
		_layControl.requestFocus();

		return v;
	}

	private void pwmOff() {
		setPwm(0);
	}

	private void pwmOn() {
		setPwm(100);
	}

	private void relayOff() {
		showProgressSpinner();
		_handler.post(new SequentialRunner("relayOff") {
			@Override
			public boolean execute() {
				// switch the relay off. this function will check first if the device is connected
				// (and connect if it is not), then it switches the relay off, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.relayOff(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "relay off success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "power off failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				});
				return true;
			}
		});
	}

	private void relayOn() {
		showProgressSpinner();
		_handler.post(new SequentialRunner("relayOn") {
			@Override
			public boolean execute() {
				// switch the relay on. this function will check first if the device is connected
				// (and connect if it is not), then it switches the relay on, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.relayOn(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "relay on success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "power on failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				});
				return true;
			}
		});
	}


	private void setPwm(final int value) {
		Log.i(TAG, "setPwm " + value);
		final int pwmVal = value > 100 ? 100 : value;

		_editPwm.setText("" + pwmVal);

		showProgressSpinner();
		_handler.post(new SequentialRunner("setPwm") {
			@Override
			public boolean execute() {
				// set the pwm. this function will check first if the device is connected
				// (and connect if it is not), then it sets pwm, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.writePwm(_address, pwmVal, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "set pwm success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "set pwm failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				});
				return true;
			}
		});
	}

	private void switchOn() {
		setSwitch(100);
	}

	private void switchOff() {
		setSwitch(0);
	}

	private void setSwitch(final int value) {
		Log.i(TAG, "setSwitch " + value);
		final int switchVal = value > 100 ? 100 : value;

		// First set seekbar, as changing that also updates the edit text.
		_sbSwitch.setProgress(switchVal);
		_editSwitch.setText("" + switchVal);


		showProgressSpinner();
		_handler.post(new SequentialRunner("setPwm") {
			@Override
			public boolean execute() {
				// switch the device on. this function will check first if the device is connected
				// (and connect if it is not), then it switches the device on, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.writeSwitch(_address, switchVal, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "set switch success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "set switch failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				});
				return true;
			}
		});
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {

//		if (_pwmEnabled) {
//			menu.findItem(R.id.action_pwm).setTitle("Disable PWM");
//		} else {
//			menu.findItem(R.id.action_pwm).setTitle("Enable PWM");
//		}

		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		switch(id) {
//			case R.id.action_pwm: {
//				enablePwm(!_pwmEnabled);
//				break;
//			}
		}

		return super.onOptionsItemSelected(item);
	}

//	private void enablePwm(boolean enable) {
//		_pwmEnabled = enable;
//		_layPwm.setEnabled(_pwmEnabled);
//		_layPower.setEnabled(_pwmEnabled);
//		_cbPwmEnable.setEnabled(_pwmEnabled);
//		_btnPwmOff.setEnabled(_pwmEnabled);
//		_btnPwmOn.setEnabled(_pwmEnabled);
//		_sbSwitch.setEnabled(_pwmEnabled);
//	}

	private void showProgressSpinner() {
		ProgressSpinner.show(getActivity());
	}

	private void dismissProgressSpinner() {
		ProgressSpinner.dismiss();
	}

	private void displayError(final int error) {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				String errorMsg;
				switch (error) {
					case 19: {
						errorMsg = "Failed: Disconnected by device!";
						break;
					}
					default:
						errorMsg = "Failed with error " + error;
				}
				Toast.makeText(getActivity(), errorMsg, Toast.LENGTH_LONG).show();
			}
		});
	}

	private void showToast(final String str) {
		Activity activity = getActivity();
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(getActivity(), str, Toast.LENGTH_LONG).show();
			}
		});
	}

}
