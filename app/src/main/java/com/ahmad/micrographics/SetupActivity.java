package com.ahmad.micrographics;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONException;

import java.io.IOException;

/**
 * Launcher entry point and the widget's (optional) configuration screen: permissions,
 * weather location, temperature unit, widget colour and which rows to show.
 */
public class SetupActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 1;

    private TextView statusLocation;
    private TextView statusCalendar;
    private TextView statusMessage;
    private FrameLayout previewHost;
    /** Bumped on each preview request, so a slow, older render doesn't replace a newer one. */
    private int previewGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        // Opened by the launcher while placing the widget: configuration is optional, so accept
        // straight away and let the user back out without losing the widget.
        int widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        }

        statusLocation = findViewById(R.id.status_location);
        statusCalendar = findViewById(R.id.status_calendar);
        statusMessage = findViewById(R.id.status_message);
        previewHost = findViewById(R.id.preview_host);
        previewHost.post(this::updatePreview);

        AppWidgetManager awm = getSystemService(AppWidgetManager.class);
        Button pin = findViewById(R.id.btn_pin);
        pin.setEnabled(awm.isRequestPinAppWidgetSupported());
        pin.setOnClickListener(v ->
                awm.requestPinAppWidget(new ComponentName(this, MicrographicsWidget.class), null, null));
        Button pinGooey = findViewById(R.id.btn_pin_gooey);
        pinGooey.setEnabled(awm.isRequestPinAppWidgetSupported());
        pinGooey.setOnClickListener(v ->
                awm.requestPinAppWidget(new ComponentName(this, GooeyClockWidget.class), null, null));

        findViewById(R.id.btn_permissions).setOnClickListener(v -> requestPermissions(new String[] {
                Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_COARSE_LOCATION,
        }, REQUEST_PERMISSIONS));
        findViewById(R.id.btn_locate).setOnClickListener(v -> locate());

        EditText city = findViewById(R.id.city);
        Button setCity = findViewById(R.id.btn_city);
        setCity.setOnClickListener(v -> lookUpCity(city.getText().toString().trim()));
        city.setOnEditorActionListener((v, actionId, event) -> {
            setCity.performClick();
            return true;
        });

        Switch fahrenheit = findViewById(R.id.switch_fahrenheit);
        fahrenheit.setChecked(Prefs.fahrenheit(this));
        fahrenheit.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setFahrenheit(this, checked);
            refreshWidget();
        });

        RadioGroup ink = findViewById(R.id.ink);
        // Indexed by the Prefs.INK_* values.
        int[] inkButtons = {R.id.ink_light, R.id.ink_dark, R.id.ink_system, R.id.ink_auto};
        int[] inkHints = {R.string.setup_ink_light, R.string.setup_ink_dark, R.string.setup_ink_system,
                R.string.setup_ink_auto};
        TextView inkHint = findViewById(R.id.ink_hint);
        ink.check(inkButtons[Prefs.ink(this)]);
        inkHint.setText(inkHints[Prefs.ink(this)]);
        ink.setOnCheckedChangeListener((group, checkedId) -> {
            for (int i = 0; i < inkButtons.length; i++) {
                if (inkButtons[i] != checkedId) continue;
                Prefs.setInk(this, i);
                inkHint.setText(inkHints[i]);
            }
            refreshWidget();
        });

        Switch accents = findViewById(R.id.switch_accents);
        accents.setChecked(Prefs.accents(this));
        accents.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setAccents(this, checked);
            refreshWidget();
        });
        Switch amPm = findViewById(R.id.switch_am_pm);
        amPm.setChecked(Prefs.amPm(this));
        amPm.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setAmPm(this, checked);
            refreshWidget();
        });

        bindRowSwitch(R.id.show_timer, Prefs.ROW_TIMER);
        bindRowSwitch(R.id.show_flashlight, Prefs.ROW_FLASHLIGHT);
        bindRowSwitch(R.id.show_weather, Prefs.ROW_WEATHER);
        bindRowSwitch(R.id.show_meeting, Prefs.ROW_MEETING);
        bindRowSwitch(R.id.show_birthday, Prefs.ROW_BIRTHDAY);
        bindRowSwitch(R.id.show_alarm, Prefs.ROW_ALARM);
        bindRowSwitch(R.id.show_charging, Prefs.ROW_CHARGING);

        Switch rowsOpenApps = findViewById(R.id.switch_rows_open_apps);
        rowsOpenApps.setChecked(Prefs.rowsOpenApps(this));
        rowsOpenApps.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setRowsOpenApps(this, checked);
            refreshWidget();
        });
        findViewById(R.id.btn_listener).setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                new ComponentName(this, StatusListener.class).flattenToString())));

        findViewById(R.id.btn_refresh).setOnClickListener(v -> {
            refreshWidget();
            showMessage(R.string.status_refreshed);
        });
        findViewById(R.id.btn_done).setOnClickListener(v -> finish());
    }

    private void bindRowSwitch(int id, String row) {
        Switch s = findViewById(id);
        s.setChecked(Prefs.show(this, row));
        s.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setShow(this, row, checked);
            refreshWidget();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        showStatus();
    }

    private void showStatus() {
        String place = Prefs.place(this);
        statusLocation.setText(place == null
                ? getString(R.string.status_location_none)
                : getString(R.string.status_location, place));
        statusCalendar.setText(Meeting.hasPermission(this)
                ? R.string.status_calendar_on : R.string.status_calendar_off);
        boolean allGranted = Meeting.hasPermission(this) && hasLocationPermission();
        findViewById(R.id.btn_permissions).setVisibility(allGranted ? View.GONE : View.VISIBLE);
        findViewById(R.id.btn_locate).setEnabled(hasLocationPermission());
        boolean listener = getSystemService(NotificationManager.class)
                .isNotificationListenerAccessGranted(new ComponentName(this, StatusListener.class));
        ((TextView) findViewById(R.id.status_listener)).setText(
                listener ? R.string.status_listener_on : R.string.status_listener_off);
        findViewById(R.id.btn_listener).setVisibility(listener ? View.GONE : View.VISIBLE);
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        showStatus();
        if (hasLocationPermission() && !Prefs.hasLocation(this)) {
            locate();
        } else {
            refreshWidget();
        }
    }

    @SuppressLint("MissingPermission") // checked by hasLocationPermission()
    private void locate() {
        if (!hasLocationPermission()) return;
        showMessage(R.string.status_locating);
        LocationManager lm = getSystemService(LocationManager.class);
        String provider = lm.hasProvider(LocationManager.FUSED_PROVIDER)
                ? LocationManager.FUSED_PROVIDER : LocationManager.NETWORK_PROVIDER;
        try {
            lm.getCurrentLocation(provider, null, getMainExecutor(), loc -> {
                if (loc == null) loc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                onLocation(loc);
            });
        } catch (SecurityException | IllegalArgumentException e) {
            onLocation(null);
        }
    }

    private void onLocation(Location loc) {
        if (loc == null) {
            showMessage(R.string.status_location_failed);
            return;
        }
        Prefs.setLocation(this, loc.getLatitude(), loc.getLongitude(), getString(R.string.my_location));
        showMessage("");
        showStatus();
        refreshWidget();
    }

    private void lookUpCity(String name) {
        if (name.isEmpty()) return;
        showMessage(R.string.status_locating);
        new Thread(() -> {
            String[] display = new String[1];
            double[] latLon = null;
            try {
                latLon = Weather.geocode(name, display);
            } catch (IOException | JSONException ignored) {
                // Reported below as "not found".
            }
            double[] found = latLon;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (found == null) {
                    showMessage(R.string.status_city_failed);
                    return;
                }
                Prefs.setLocation(this, found[0], found[1], display[0]);
                showMessage("");
                showStatus();
                refreshWidget();
            });
        }, "micrographics-geocode").start();
    }

    /** Shows a one-line status under the weather settings, or hides the line when empty. */
    private void showMessage(CharSequence text) {
        statusMessage.setText(text);
        statusMessage.setVisibility(text.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void showMessage(int text) {
        showMessage(getText(text));
    }

    private void refreshWidget() {
        MicrographicsWidget.refreshAsync(this, null);
        updatePreview();
    }

    /** Redraws the live preview with the current settings, building it off the main thread. */
    private void updatePreview() {
        int generation = ++previewGeneration;
        float density = getResources().getDisplayMetrics().density;
        int widthDp = Math.max(250, Math.round(previewHost.getWidth() / density));
        new Thread(() -> {
            RemoteViews views = WidgetUpdater.preview(this, widthDp, 280);
            int backdrop = Palette.previewBackdrop(this);
            runOnUiThread(() -> {
                if (isFinishing() || generation != previewGeneration) return;
                ((GradientDrawable) findViewById(R.id.preview_frame).getBackground().mutate()).setColor(backdrop);
                previewHost.removeAllViews();
                previewHost.addView(views.apply(this, previewHost));
            });
        }, "micrographics-preview").start();
    }
}
