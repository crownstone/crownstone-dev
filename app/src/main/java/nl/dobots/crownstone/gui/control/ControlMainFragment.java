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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Toast;

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

	private ImageView _lightBulb;
	private CheckBox _cbPwmEnable;

	private Button _btnRelayOff;
	private Button _btnRelayOn;
	private Button _btnPwmOff;
	private Button _btnPwmOn;
	private SeekBar _sbPwm;
	private RelativeLayout _layGraph;
	private ImageButton _btnZoomIn;
	private ImageButton _btnZoomOut;
	private ImageButton _btnZoomReset;

	private RelativeLayout _layStatistics;
	private RelativeLayout _layControl;

	private Handler _handler;
	private boolean _closing;

	private boolean _lightOn;

	private BleExt _ble;
	private String _address;

	private AdvertisementGraph _graph;
	private boolean _pwmEnabled = true;
	private LinearLayout _layPwm;
	private LinearLayout _layPower;
	private boolean _led1On;
	private boolean _led2On;
	private boolean _connected = false;

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

		checkPwm();
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

			if (Build.VERSION.SDK_INT >= 24) {
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
										if (serviceData != null && !serviceData.isExternalData()) {
											_lastUpdate = System.nanoTime();
											_graph.onServiceData(device.getName(), serviceData);
											updateLightBulb(serviceData.getPwm() > 0 || serviceData.getRelayState());
										} else {
											_graph.updateRange();
										}
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
			// On older versions we can stop scanning each time we got an advertisement,
			// and start again after GRAPH_UPDATE_TIME
			else {
				if (_ble.isDisconnected(null)) {
					BleLog.getInstance().LOGi(TAG, "starting scan");
					if (!_ble.isScanning()) {
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
										if (serviceData != null && !serviceData.isExternalData()) {
											_lastUpdate = System.nanoTime();
											_graph.onServiceData(device.getName(), serviceData);
											updateLightBulb(serviceData.getPwm() > 0 || serviceData.getRelayState());
											// Once an advertisement is received for the device, stop the scan again
											_ble.stopScan(null);
											done();
										} else {
											_graph.updateRange();
										}
									}
								}
								if (_closing) {
									BleLog.getInstance().LOGd(TAG, "closing: stop scanning");
									_ble.stopScan(null);
									done();
								}
							}

							@Override
							public void onError(int error) {
								BleLog.getInstance().LOGe(TAG, "scan error: %d", error);
								done();
							}
						});
					}
					_handler.postDelayed(this, GRAPH_UPDATE_TIME);
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

		_lightBulb = (ImageView) v.findViewById(R.id.imgLightBulb);
		_lightBulb.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				toggleRelay();
			}
		});

		_btnPwmOn = (Button) v.findViewById(R.id.btnPwmOn);
		_btnPwmOn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				pwmOn();
//				toggleLed1();
			}
		});

		_btnPwmOff = (Button) v.findViewById(R.id.btnPwmOff);
		_btnPwmOff.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				pwmOff();
//				toggleLed2();
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

		_cbPwmEnable = (CheckBox) v.findViewById(R.id.cbPwmEnable);
		_cbPwmEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				_sbPwm.setEnabled(isChecked);
			}
		});

		_sbPwm = (SeekBar) v.findViewById(R.id.sbPwm);
		_sbPwm.setEnabled(_cbPwmEnable.isChecked());
		_sbPwm.setMax(100);
		_sbPwm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				setPwm(seekBar.getProgress());
			}
		});

		_layPwm = (LinearLayout) v.findViewById(R.id.layPwm);
		_layPower = (LinearLayout) v.findViewById(R.id.layPower);
		_layControl = (RelativeLayout) v.findViewById(R.id.layControl);
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

		enablePwm(_pwmEnabled);
		_handler.postDelayed(_advStateChecker, 2000);

		return v;
	}

	private void checkPwm() {
		ProgressSpinner.show(getActivity(), new ProgressSpinner.OnCancelListener() {
			@Override
			public void onCancel() {
				getActivity().finish();
			}
		});

		// first we have to connect to the device and discover the available characteristics.
		_ble.connectAndDiscover(_address, new IDiscoveryCallback() {
			@Override
			public void onDiscovery(String serviceUuid, String characteristicUuid) {
				// this function is called for every detected characteristic with the
				// characteristic's UUID and the UUID of the service it belongs.
				// you can keep track of what functions are available on the device,
				// but you don't have to, the library does that for you.
			}

			@Override
			public void onSuccess() {
				// once discovery is completed, this function will be called. we can now execute
				// the functions on the device. in this case, we want to know what the current
				// PWM state is
				_connected = true;

				// first we try and read the PWM value from the device. this call will make sure
				// that the PWM or State characteristic is available, otherwise an error is created
				_ble.readRelay(new IBooleanCallback() {
					@Override
					public void onSuccess(boolean result) {
						// if reading was successful, we get the value in the onSuccess as
						// the parameter

						// now we can update the image of the light bulb to on (if PWM value is
						// greater than 0) or off if it is 0
						updateLightBulb(result);

						// at the end we disconnect and close the device again. you could also
						// stay connected if you want. but it's preferable to only connect,
						// execute and disconnect, so that the device can continue advertising
						// again.
						_ble.disconnectAndClose(false, new IStatusCallback() {
							@Override
							public void onSuccess() {
								// at this point we successfully disconnected and closed
								// the device again
								dismissProgressSpinner();
							}

							@Override
							public void onError(int error) {
								// an error occurred while disconnecting
								dismissProgressSpinner();
							}
						});
					}

					@Override
					public void onError(int error) {
						// an error occurred while trying to read the PWM state
						Log.e(TAG, "Failed to get Pwm: " + error);

						if (error == BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND) {

							dismissProgressSpinner();
							// return an error and exit if the PWM characteristic is not available
							getActivity().runOnUiThread(new Runnable() {
								@Override
								public void run() {
									Toast.makeText(getActivity(), "No PWM Characteristic found for this device!", Toast.LENGTH_LONG).show();
								}
							});
							getActivity().finish();
						} else {

							// disconnect and close the device again
							_ble.disconnectAndClose(false, new IStatusCallback() {
								@Override
								public void onSuccess() {
									// at this point we successfully disconnected and closed
									// the device again.
									dismissProgressSpinner();
								}

								@Override
								public void onError(int error) {
									// an error occurred while disconnecting
									dismissProgressSpinner();
								}
							});
						}
					}
				});
			}

			@Override
			public void onError(int error) {
				// an error occurred during connect/discover
				Log.e(TAG, "failed to connect/discover: " + error);
				displayError(error);
				dismissProgressSpinner();
				// device will disconnect after idle and error 19 is thrown,
				// so only close the activity if connect fails. if already connected
				// only log the error without closing activity
				if (error == 19) {
					if (getActivity() != null) {
						getActivity().finish();
					}
				}
			}
		});
	}

	private void toggleLed2() {
		showProgressSpinner();
		_handler.post(new SequentialRunner("toggleLed2") {
			@Override
			public boolean execute() {
					_ble.writeLed(_address, 2, !_led2On, new IStatusCallback() {
						@Override
						public void onSuccess() {
							Log.i(TAG, "write led success");
							_led2On = !_led2On;
							done();
							dismissProgressSpinner();
						}

						@Override
						public void onError(int error) {
							Log.i(TAG, "write led failed: " + error);
							displayError(error);
							done();
							dismissProgressSpinner();
						}
					});
				return true;
			}
		});
	}


	private void toggleLed1() {
		showProgressSpinner();
		_handler.post(new SequentialRunner("toggleLed1") {
			@Override
			public boolean execute() {
					_ble.writeLed(_address, 1, !_led1On, new IStatusCallback() {
						@Override
						public void onSuccess() {
							Log.i(TAG, "write led success");
							_led1On = !_led1On;
							done();
							dismissProgressSpinner();
						}

						@Override
						public void onError(int error) {
							Log.i(TAG, "write led failed: " + error);
							displayError(error);
							done();
							dismissProgressSpinner();
						}
					});
				return true;
			}
		});
	}

	private void pwmOff() {
		showProgressSpinner();
		_handler.post(new SequentialRunner("pwmOff") {
			@Override
			public boolean execute() {
				// switch the device off. this function will check first if the device is connected
				// (and connect if it is not), then it switches the device off, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.pwmOff(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "power off success");
						// power was switch off successfully, update the light bulb
//						updateLightBulb(false);
						_sbPwm.setProgress(0);
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

	private void pwmOn() {
		showProgressSpinner();
		_handler.post(new SequentialRunner("pwmOn") {
			@Override
			public boolean execute() {
				// switch the device on. this function will check first if the device is connected
				// (and connect if it is not), then it switches the device on, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.pwmOn(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "power on success");
						// power was switch on successfully, update the light bulb
//						updateLightBulb(true);
						_sbPwm.setProgress(100);
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

	private void toggleRelay() {
		showProgressSpinner();
		_handler.post(new SequentialRunner("toggleRelay") {
			@Override
			public boolean execute() {
				// toggle the device switch, without needing to know the current state. this function will
				// check first if the device is connected (and connect if it is not), then it reads the
				// current PWM state, and depending on the state, decides if it needs to switch it on or
				// off. in the end it disconnects again (once the disconnect timeout expires)
				_ble.toggleRelay(_address, new IBooleanCallback() {
					@Override
					public void onSuccess(boolean result) {
						Log.i(TAG, "toggle success");
						// power was toggled successfully, update the light bulb
						updateLightBulb(result);
						done();
//						dismissProgressSpinner();
						ProgressSpinner.dismiss();
					}

					@Override
					public void onError(int error) {
						Log.e(TAG, "toggle failed: " + error);
						displayError(error);
						done();
//						dismissProgressSpinner();
						ProgressSpinner.dismiss();
					}
				});
				return true;
			}
		});
	}

	private void updateLightBulb(final boolean on) {
		if (getActivity() != null) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					_lightOn = on;
					if (on) {
						_lightBulb.setImageResource(getResources().getIdentifier("light_bulb_on", "drawable", getActivity().getPackageName()));
					} else {
						_lightBulb.setImageResource(getResources().getIdentifier("light_bulb_off", "drawable", getActivity().getPackageName()));
					}
				}
			});
		}
	}


	private void relayOff() {
		showProgressSpinner();
		_handler.post(new SequentialRunner("relayOff") {
			@Override
			public boolean execute() {
				// switch the device off. this function will check first if the device is connected
				// (and connect if it is not), then it switches the device off, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.relayOff(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "relay off success");
						// power was switch off successfully, update the light bulb
//						updateLightBulb(false);
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
				// switch the device on. this function will check first if the device is connected
				// (and connect if it is not), then it switches the device on, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.relayOn(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "relay on success");
						// power was switch on successfully, update the light bulb
//						updateLightBulb(true);
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

	private void setPwm(final int pwm) {
		showProgressSpinner();
		_handler.post(new SequentialRunner("setPwm") {
			@Override
			public boolean execute() {
				// switch the device on. this function will check first if the device is connected
				// (and connect if it is not), then it switches the device on, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.writePwm(_address, pwm, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "set pwm success");
						// power was switch on successfully, update the light bulb
//						if (pwm > 0) {
//							updateLightBulb(true);
//						} else {
//							updateLightBulb(false);
//						}
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

	@Override
	public void onPrepareOptionsMenu(Menu menu) {

		if (_pwmEnabled) {
			menu.findItem(R.id.action_pwm).setTitle("Disable PWM");
		} else {
			menu.findItem(R.id.action_pwm).setTitle("Enable PWM");
		}

		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		switch(id) {
			case R.id.action_pwm: {
				enablePwm(!_pwmEnabled);
				break;
			}
		}

		return super.onOptionsItemSelected(item);
	}

	private void enablePwm(boolean enable) {
		_pwmEnabled = enable;
		_layPwm.setEnabled(_pwmEnabled);
		_layPower.setEnabled(_pwmEnabled);
		_cbPwmEnable.setEnabled(_pwmEnabled);
		_btnPwmOff.setEnabled(_pwmEnabled);
		_btnPwmOn.setEnabled(_pwmEnabled);
		_sbPwm.setEnabled(_pwmEnabled);
	}

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
