package nl.dobots.crownstone.gui;

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;

import nl.dobots.crownstone.R;

/**
 * Activity (container) for SelectControlFragment and SelectMonitorFragment. Provides
 * swiping and tabs to change between one and the other
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class MainActivity  extends FragmentActivity {

	private static final String TAG = MainActivity.class.getCanonicalName();

	private FragmentPagerAdapter _pagerAdapter;

	private ViewPager _pager;

	private SelectControlFragment _fragMain;
	private SelectMonitorFragment _fragSelect;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_viewpager);

		initUI();
	}

	private void initUI() {
		_fragMain = new SelectControlFragment();
		_fragSelect = new SelectMonitorFragment();

		_pagerAdapter = new FragmentPagerAdapter(getSupportFragmentManager()) {
			@Override
			public Fragment getItem(int position) {
				if (position == 0) {
					return _fragMain;
				} else {
					return _fragSelect;
				}
			}

			@Override
			public int getCount() {
				return 2;
			}
		};

		_pager = (ViewPager) findViewById(R.id.pager);
		_pager.setAdapter(_pagerAdapter);
		_pager.setOnPageChangeListener(
				new ViewPager.SimpleOnPageChangeListener() {
					@Override
					public void onPageSelected(int position) {
						// When swiping between pages, select the
						// corresponding tab.
						getActionBar().setSelectedNavigationItem(position);
					}
				});

		final ActionBar actionBar = getActionBar();

		// Specify that tabs should be displayed in the action bar.
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		// Create a tab listener that is called when the user changes tabs.
		ActionBar.TabListener tabListener = new ActionBar.TabListener() {
			public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
				// show the given tab
				_pager.setCurrentItem(tab.getPosition());
			}

			public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
				// hide the given tab
			}

			public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
				// probably ignore this event
			}
		};

		actionBar.addTab(
				actionBar.newTab()
						.setText("Control")
						.setTabListener(tabListener));

		actionBar.addTab(
				actionBar.newTab()
						.setText("Monitor")
						.setTabListener(tabListener));

	}

}
