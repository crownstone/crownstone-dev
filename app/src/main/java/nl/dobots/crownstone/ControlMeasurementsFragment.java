package nl.dobots.crownstone;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.achartengine.tools.ZoomEvent;
import org.achartengine.tools.ZoomListener;

import java.util.Date;

import nl.dobots.bluenet.ble.base.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.ble.base.callbacks.IDiscoveryCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.IPowerSamplesCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.CrownstoneServiceData;
import nl.dobots.bluenet.ble.base.structs.PowerSamples;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.utils.BleLog;

/**
 * This example activity shows the use of the bluenet library. The library is first initialized,
 * which enables the bluetooth adapter. It shows the following steps:
 *
 * 1. Connect to a device and discover the available services / characteristics
 * 2. Read a characteristic (PWM characteristic)
 * 3. Write a characteristic (PWM characteristic)
 * 4. Disconnect and close the device
 * 5. And how to do the 3 steps (connectDiscover, execute and disconnectClose) with one
 *    function call
 *
 * For an example of how to scan for devices see MainActivity.java or MainActivityService.java
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class ControlMeasurementsFragment extends Fragment {

	private static final String TAG = ControlMeasurementsFragment.class.getCanonicalName();

	private static final int TEMP_UPDATE_TIME = 5000;
	public static final int STATISTICS_X_TIME = 5;

	private GraphicalView _graphView;

	private String _address;
	private BleExt _ble;

	private RelativeLayout _layGraph;
	private ImageButton _btnZoomIn;
	private ImageButton _btnZoomOut;
	private ImageButton _btnZoomReset;

//	private int _zoomLevel;

//	private Handler _handler;

//	private long _liveMinTime;
//	private long _maxTime;
//	private long _minTemp;
//	private long _maxTemp;
	private RelativeLayout _layStatistics;
	private RelativeLayout _layControl;
	private int _currentSeries = 0;
//	private int _temperatureSeries;
	private int _currentPointStyle = 0;
	private int _currentSeriesColor = 0;
//	private int _switchStateSeries;
	private Button _btnSamplePower;

	private int _currentSampleSeries;
	private int _voltageSampleSeries;
	private int _minCurrentSample;
	private int _maxCurrentSample;
	private int _minVoltageSample;
	private int _maxVoltageSample;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		_ble = ControlActivity.getInstance().getBle();
		_address = ControlActivity.getInstance().getAddress();

	}

	@Override
	public void onStart() {
		super.onStart();

	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.frag_control_measurements, container, false);

		_layControl = (RelativeLayout) v.findViewById(R.id.layControl);
		_layStatistics = (RelativeLayout) v.findViewById(R.id.layStatistics);
		_layGraph = (RelativeLayout) v.findViewById(R.id.graph);

		_btnZoomIn = (ImageButton) v.findViewById(R.id.zoomIn);
		_btnZoomIn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_layStatistics.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
				_layControl.setVisibility(View.INVISIBLE);
//				_graphView.zoomIn();
//				_zoomLevel++;
			}
		});
		_btnZoomOut = (ImageButton) v.findViewById(R.id.zoomOut);
		_btnZoomOut.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int pixels = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300, getResources().getDisplayMetrics());
				_layStatistics.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, pixels));
				_layControl.setVisibility(View.VISIBLE);
//				_graphView.zoomOut();
//				_zoomLevel--;
			}
		});
		_btnZoomReset = (ImageButton) v.findViewById(R.id.zoomReset);
		_btnZoomReset.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_graphView.zoomReset();

				updateVoltageSamplesRange();
				updateCurrentSamplesRange();
			}
		});

		_btnSamplePower = (Button) v.findViewById(R.id.btnSamplePower);
		_btnSamplePower.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				samplePower();
			}
		});

		createGraph();
//		_layStatistics.setVisibility(View.GONE);

		return v;
	}

	void samplePower() {

		final ProgressDialog dlg = ProgressDialog.show(getActivity(), "Retrieving samples", "Please wait...", true, true);
		dlg.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				getActivity().finish();
			}
		});

		_ble.readPowerSamples(_address, new IPowerSamplesCallback() {
			@Override
			public void onData(PowerSamples powerSamples) {
				dlg.dismiss();
				onPowerSamples(powerSamples);
			}

			@Override
			public void onError(int error) {
				Toast.makeText(getActivity(), "Failed to get samples", Toast.LENGTH_LONG).show();
				dlg.dismiss();
			}
		});
	}

	void onPowerSamples(PowerSamples powerSamples) {

		onCurrentSamples(powerSamples.getCurrentSamples(), powerSamples.getCurrentTimestamps());
		onVoltageSamples(powerSamples.getVoltageSamples(), powerSamples.getVoltageTimestamps());

	}

	private void updateCurrentSamplesRange() {
		_multipleSeriesRenderer.setYAxisMin(_minCurrentSample - Math.max((double)(_maxCurrentSample - _minCurrentSample) / 10, 10), _currentSampleSeries);
		_multipleSeriesRenderer.setYAxisMax(_maxCurrentSample + Math.max((double)(_maxCurrentSample - _minCurrentSample) / 10, 10), _currentSampleSeries);
	}

	private void updateVoltageSamplesRange() {
		_multipleSeriesRenderer.setYAxisMin(_minVoltageSample - Math.max((double)(_maxVoltageSample - _minVoltageSample) / 10, 10), _voltageSampleSeries);
		_multipleSeriesRenderer.setYAxisMax(_maxVoltageSample + Math.max((double)(_maxVoltageSample - _minVoltageSample) / 10, 10), _voltageSampleSeries);
	}

	private void onCurrentSamples(PowerSamples.Samples currentSamples, PowerSamples.Timestamps currentTimestamps) {

		XYSeries series = _dataSet.getSeriesAt(_currentSampleSeries);
		series.clear();

		_minCurrentSample = Integer.MAX_VALUE;
		_maxCurrentSample = Integer.MIN_VALUE;

		for (int i = 0; i < currentSamples.getCount(); ++i) {
			int sample = currentSamples.getSample(i);
			series.add(currentTimestamps.getTimestamp(i) - currentTimestamps.getFirst(), sample);

			if (sample < _minCurrentSample) {
				_minCurrentSample = sample;
			}
			if (sample > _maxCurrentSample) {
				_maxCurrentSample = sample;
			}
		}

		updateCurrentSamplesRange();

		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				_graphView.repaint();
			}
		});

	}

	private void onVoltageSamples(PowerSamples.Samples voltageSamples, PowerSamples.Timestamps voltageTimestamps) {

		XYSeries series = _dataSet.getSeriesAt(_voltageSampleSeries);
		series.clear();

		_minVoltageSample = Integer.MAX_VALUE;
		_maxVoltageSample = Integer.MIN_VALUE;

		for (int i = 0; i < voltageSamples.getCount(); ++i) {
			int sample = voltageSamples.getSample(i);

			series.add(voltageTimestamps.getTimestamp(i) - voltageTimestamps.getFirst(), sample);

			if (sample < _minVoltageSample) {
				_minVoltageSample = sample;
			}
			if (sample > _maxVoltageSample) {
				_maxVoltageSample = sample;
			}
		}

		updateVoltageSamplesRange();

		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				_graphView.repaint();
			}
		});

	}

	private XYMultipleSeriesRenderer _multipleSeriesRenderer;
	private XYMultipleSeriesDataset _dataSet;

	private PointStyle[] listOfPointStyles = new PointStyle[] { PointStyle.CIRCLE, PointStyle.DIAMOND,
			PointStyle.POINT, PointStyle.SQUARE, PointStyle.TRIANGLE, PointStyle.X };

	private int[] listOfSeriesColors = new int[] { 0xFF00BFFF, Color.GREEN, Color.RED, Color.YELLOW,
			Color.MAGENTA, Color.CYAN, Color.WHITE };


	private void createPowerSamplesSeries() {

		createCurrentSamplesSeries();
		createVoltageSamplesSeries();

	}

	private void createCurrentSamplesSeries() {

		_currentSampleSeries = _currentSeries++;

		// create time series (series with x = timestamp, y = temperature)
//		TimeSeries series = new TimeSeries("SwitchState");
		XYSeries series = new XYSeries("Current", _currentSampleSeries);

		_dataSet.addSeries(series);

		// create new renderer for the new series
		XYSeriesRenderer renderer = new XYSeriesRenderer();
		_multipleSeriesRenderer.addSeriesRenderer(renderer);

		renderer.setPointStyle(listOfPointStyles[_currentSampleSeries]);
		renderer.setColor(listOfSeriesColors[_currentSampleSeries]);
		renderer.setFillPoints(false);
		renderer.setDisplayChartValues(true);
//		renderer.setDisplayChartValuesDistance(50);
		renderer.setChartValuesTextSize(30f);
		renderer.setShowLegendItem(true);

		_multipleSeriesRenderer.setYAxisAlign(Paint.Align.LEFT, _currentSampleSeries);
		_multipleSeriesRenderer.setYLabelsAlign(Paint.Align.RIGHT, _currentSampleSeries);
		_multipleSeriesRenderer.setAxisTitleTextSize(30f);
		_multipleSeriesRenderer.setYLabelsColor(_currentSampleSeries, listOfSeriesColors[_currentSampleSeries]);
		_multipleSeriesRenderer.setYTitle("Current", _currentSampleSeries);
		_multipleSeriesRenderer.setYAxisMin(0, _currentSampleSeries);
		_multipleSeriesRenderer.setYAxisMax(300, _currentSampleSeries);
	}

	private void createVoltageSamplesSeries() {

		_voltageSampleSeries = _currentSeries++;

		// create time series (series with x = timestamp, y = temperature)
//		TimeSeries series = new TimeSeries("SwitchState");
		XYSeries series = new XYSeries("Voltage", _voltageSampleSeries);

		_dataSet.addSeries(series);

		// create new renderer for the new series
		XYSeriesRenderer renderer = new XYSeriesRenderer();
		_multipleSeriesRenderer.addSeriesRenderer(renderer);

		renderer.setPointStyle(listOfPointStyles[_voltageSampleSeries]);
		renderer.setColor(listOfSeriesColors[_voltageSampleSeries]);
		renderer.setFillPoints(false);
		renderer.setDisplayChartValues(true);
//		renderer.setDisplayChartValuesDistance(50);
		renderer.setChartValuesTextSize(30f);
		renderer.setShowLegendItem(true);

		_multipleSeriesRenderer.setYAxisAlign(Paint.Align.RIGHT, _voltageSampleSeries);
		_multipleSeriesRenderer.setYLabelsAlign(Paint.Align.LEFT, _voltageSampleSeries);
		_multipleSeriesRenderer.setAxisTitleTextSize(30f);
		_multipleSeriesRenderer.setYLabelsColor(_voltageSampleSeries, listOfSeriesColors[_voltageSampleSeries]);
		_multipleSeriesRenderer.setYTitle("Voltage", _voltageSampleSeries);
		_multipleSeriesRenderer.setYAxisMin(0, _voltageSampleSeries);
		_multipleSeriesRenderer.setYAxisMax(300, _voltageSampleSeries);

	}

	void createGraph() {

		// get graph renderer
		_multipleSeriesRenderer = getRenderer(2);
		_dataSet = new XYMultipleSeriesDataset();

		createPowerSamplesSeries();

//		_maxTime = new Date().getTime();
//		_liveMinTime = new Date().getTime() - STATISTICS_X_TIME * 60 * 1000;

//		_multipleSeriesRenderer.setInitialRange(new double[] {_liveMinTime, _maxTime, _minTemp, _maxTemp}, _temperatureSeries);

		// create graph
		_graphView = ChartFactory.getLineChartView(getActivity(), _dataSet, _multipleSeriesRenderer);
//		_graphView.addZoomListener(this, false, true);

		// add to screen
		_layGraph.addView(_graphView);
	}

	/**
	 * Create graph renderer
	 *
	 * @return renderer object
	 */
	public XYMultipleSeriesRenderer getRenderer(int series) {
		XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer(series);

		// scrolling enabled
//		renderer.setPanEnabled(true, false);
		renderer.setPanEnabled(true, true);
		// limits for scrolling (minx, maxx, miny, maxy)
		// zoom buttons (in, out, 1:1)
		renderer.setZoomButtonsVisible(true);
		// enable zoom
//		renderer.setZoomEnabled(true, false);
		renderer.setZoomEnabled(true, true);

		// set labels text size
		renderer.setLabelsTextSize(30f);

		// hide legend
//		renderer.setShowLegend(false);
		renderer.setShowLegend(true);
		renderer.setLegendTextSize(30f);
		renderer.setLegendHeight(130);

		// set margins
//		renderer.setMargins(new int[] {20, 30, 15, 0});
		renderer.setMargins(new int[] {30, 80, 50, 70});

//		renderer.setApplyBackgroundColor(true);
//		renderer.setBackgroundColor(Color.WHITE);
//		renderer.setMarginsColor(Color.WHITE);

		renderer.setMarginsColor(Color.argb(0x00, 0x01, 0x01, 0x01));
		// todo: need to get background colour of activity, transparent is not good enough
//		renderer.setMarginsColor(((ColorDrawable) _layGraph.getBackground()).getColor());

//		renderer.setXAxisMin(new Date().getTime() - STATISTICS_X_TIME * 60 * 1000);

		renderer.setZoomButtonsVisible(false);
		renderer.setExternalZoomEnabled(true);

//		XYSeriesRenderer r = new XYSeriesRenderer();

		// set color
//		r.setColor(Color.GREEN);

		// set fill below line
//		r.setFillBelowLine(true);

//		renderer.addSeriesRenderer(r);
		return renderer;
	}

//	@Override
//	public void zoomApplied(ZoomEvent zoomEvent) {
////		_zoomLevel = 100;
//	}
//
//	@Override
//	public void zoomReset() {
////		_zoomLevel = 0;
//	}
}
