package com.ahmad.micrographics;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONException;

import java.io.IOException;

/**
 * Launcher entry point and the widget's (optional) configuration screen: permissions,
 * weather location and temperature unit.
 */
public class SetupActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 1;

    private TextView statusLocation;
    private TextView statusCalendar;
    private TextView statusMessage;

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

        findViewById(R.id.btn_refresh).setOnClickListener(v -> {
            refreshWidget();
            statusMessage.setText(R.string.status_refreshed);
        });
        findViewById(R.id.btn_done).setOnClickListener(v -> finish());
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
        findViewById(R.id.btn_permissions).setEnabled(!allGranted);
        findViewById(R.id.btn_locate).setEnabled(hasLocationPermission());
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
        statusMessage.setText(R.string.status_locating);
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
            statusMessage.setText(R.string.status_location_failed);
            return;
        }
        Prefs.setLocation(this, loc.getLatitude(), loc.getLongitude(), getString(R.string.my_location));
        statusMessage.setText("");
        showStatus();
        refreshWidget();
    }

    private void lookUpCity(String name) {
        if (name.isEmpty()) return;
        statusMessage.setText(R.string.status_locating);
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
                    statusMessage.setText(R.string.status_city_failed);
                    return;
                }
                Prefs.setLocation(this, found[0], found[1], display[0]);
                statusMessage.setText("");
                showStatus();
                refreshWidget();
            });
        }, "micrographics-geocode").start();
    }

    private void refreshWidget() {
        MicrographicsWidget.refreshAsync(this, null);
    }
}
