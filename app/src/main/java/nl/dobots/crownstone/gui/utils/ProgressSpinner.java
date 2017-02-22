package nl.dobots.crownstone.gui.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.ProgressBar;

import nl.dobots.crownstone.R;

/**
 * A simple activity to show an endless spinner in the middle of the screen over the current
 * activity (the background of the spinner is transparent)
 *
 * Use the following snippet in your Manifest:
 *
 * 		<activity android:name=".gui.utils.ProgressSpinner" android:theme="@style/Theme.Translucent"/>
 *
 * And add this snipet to the styles.xml
 *
 * 		<style name="Theme.Translucent" parent="Theme.AppCompat.NoActionBar">
 * 			<item name="android:windowIsTranslucent">true</item>
 * 			<item name="android:windowBackground">@android:color/transparent</item>
 * 			<item name="android:windowContentOverlay">@null</item>
 * 			<item name="android:windowNoTitle">true</item>
 * 			<item name="android:windowIsFloating">true</item>
 * 			<item name="android:backgroundDimEnabled">true</item>
 * 		</style>
 *
 * Can be started with
 * 		ProgressSpinner.show(context)
 * and dismissed with
 * 		ProgressSpinner.dismiss()
 *
 * If a cancel event should be triggered, use
 * 		ProgressSpinner.show(context, listener)
 *
 * the listener will be informed if the user presses the back button (to cancel and
 * dismiss the spinner)
 *
 * Created on 17-2-17
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public class ProgressSpinner extends AppCompatActivity {

	public interface OnCancelListener {
		void onCancel();
	}

	// use this flag in case the spinner is dismissed even before it is shown
	private static boolean _show = true;

	private static ProgressSpinner _instance;
	private static OnCancelListener _cancelListener = null;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		_instance = this;
		setContentView(R.layout.activity_progress);
		ProgressBar pg = (ProgressBar) findViewById(R.id.progressBar);
		pg.setIndeterminate(true);

		if (!_show) finish();
	}

	/**
	 * Show the spinner over the current activity
	 * @param context the current context
	 */
	public static void show(Context context) {
		show(context, null);
	}

	/**
	 * Show the spinner over the current activity and use the listener to inform
	 * about a cancel (back button is pressed)
	 * @param context the current context
	 * @param listener the listener to be called if it is cancelled
	 */
	public static void show(Context context, OnCancelListener listener) {
		_show = true;
		_cancelListener = listener;
		context.startActivity(new Intent(context, ProgressSpinner.class));
	}

	/**
	 * If the back button is pressed, call the OnCancelListener
	 */
	@Override
	public void onBackPressed() {
		super.onBackPressed();
		if (_cancelListener != null) {
			_cancelListener.onCancel();
		}
	}

	/**
	 * Dismiss the Spinner
	 */
	public static void dismiss() {
		_show = false;
		if (_instance != null) {
			_instance.finish();
			_instance = null;
		}
	}

}
