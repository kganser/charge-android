package com.kganser.charge.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Checkable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
//import com.google.android.gms.maps.StreetViewPanorama;
import com.google.android.gms.maps.SupportMapFragment;
//import com.google.android.gms.maps.SupportStreetViewPanoramaFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.SphericalUtil;
import com.kganser.charge.Charge;
import com.kganser.charge.HTTP;
import com.kganser.charge.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Home extends ActionBarActivity implements LocationListener {

    private static final TranslateAnimation showDetail = new TranslateAnimation(Animation.ABSOLUTE, 0, Animation.ABSOLUTE, 0, Animation.RELATIVE_TO_PARENT, 1, Animation.ABSOLUTE, 0);
    private static final TranslateAnimation hideDetail = new TranslateAnimation(Animation.ABSOLUTE, 0, Animation.ABSOLUTE, 0, Animation.ABSOLUTE, 0, Animation.RELATIVE_TO_PARENT, 1);
    static {
        showDetail.setDuration(500);
        hideDetail.setDuration(500);
        showDetail.setFillAfter(true);
        hideDetail.setFillAfter(true);
    }

    private Bundle settings = new Bundle();
    private CameraPosition position;
    private LatLngBounds bounds;
    private Set<String> favorites = new HashSet<String>();
    private HashMap<String, Marker> markers = new HashMap<String, Marker>();
    private HashMap<Marker, StationGroup> stations = new HashMap<Marker, StationGroup>();
    private DrawerLayout drawer;
    private ActionBarDrawerToggle drawerToggle;
    private LocationManager locationManager;
    private View detail;
    private TextView address, network, level1, level2, level3;
    private ImageView favorite;
    private AdView ad;
    private MapFragment mapFragment;
    private GoogleMap map;
    //TODO: private StreetViewPanorama streetView;
    private Marker marker;
    private int pendingMoves;
    private Handler requestHandler = new Handler();
    private Runnable requestRunnable = new Runnable() {
        @Override
        public void run() {
            if (--pendingMoves > 0) return;
            // Erase stations outside current bounds
            Iterator<Map.Entry<String, Marker>> entries = markers.entrySet().iterator();
            while (entries.hasNext()) {
                Marker m = entries.next().getValue();
                if (!bounds.contains(m.getPosition())) {
                    m.remove();
                    entries.remove();
                    stations.remove(m);
                }
            }
            if (position.zoom > 7 && marker == null) {
                HTTP.get("https://na.chargepoint.com/dashboard/getChargeSpots",
                    new HTTP.Query()
                        .add("ne_lat", bounds.northeast.latitude)
                        .add("ne_lng", bounds.northeast.longitude)
                        .add("sw_lat", bounds.southwest.latitude)
                        .add("sw_lng", bounds.southwest.longitude),
                    new HTTP.Callback() {
                        @Override
                        public void onResponse(HTTP.Response response) {
                            if (response == null) return;
                            try {
                                JSONArray s = new JSONArray(response.toString()).getJSONObject(0).getJSONObject("station_list").getJSONArray("summaries");
                                JSONObject level;
                                for (int i = 0; i < s.length(); i++) {
                                    JSONObject data = s.getJSONObject(i);
                                    if (data.getString("station_status").equals("out_of_network"))
                                        continue;
                                    int level1Avail = 0, level1Total = 0, level2Avail = 0, level2Total = 0, level3Avail = 0, level3Total = 0;
                                    try {
                                        level = data.getJSONObject("map_data").getJSONObject("level1").getJSONObject("paid");
                                        level1Avail = level.getInt("available");
                                        level1Total = level.getInt("total");
                                    } catch (JSONException e) {}
                                    try {
                                        level = data.getJSONObject("map_data").getJSONObject("level2").getJSONObject("paid");
                                        level2Avail = level.getInt("available");
                                        level2Total = level.getInt("total");
                                    } catch (JSONException e) {}
                                    try {
                                        level = data.getJSONObject("map_data").getJSONObject("level3").getJSONObject("paid");
                                        level3Avail = level.getInt("available");
                                        level3Total = level.getInt("total");
                                    } catch (JSONException e) {}
                                    addStation(new Station("chargepoint-" + data.getString("device_id"),
                                        new LatLng(data.getDouble("lat"), data.getDouble("lon")),
                                        R.string.chargepoint, data.getJSONObject("address").optString("address1"),
                                        level1Avail, level1Total, level2Avail, level2Total, level3Avail, level3Total));
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                );
                HTTP.get("http://www.blinknetwork.com/locator/locations",
                    new HTTP.Query()
                        .add("lat", position.target.latitude)
                        .add("lng", position.target.longitude)
                        .add("latd", Math.abs(bounds.northeast.latitude - position.target.latitude))
                        .add("lngd", Math.abs(bounds.northeast.longitude - position.target.longitude))
                        .add("mode", "avail"),
                    new HTTP.Callback() {
                        @Override
                        public void onResponse(HTTP.Response response) {
                            if (response == null) return;
                            try {
                                JSONArray s = new JSONArray(response.toString());
                                for (int i = 0; i < s.length(); i++) {
                                    JSONObject data = s.getJSONObject(i);
                                    JSONObject chargers;
                                    Iterator keys;
                                    int level1Avail = 0, level1Total = 0, level2Avail = 0, level2Total = 0, level3Avail = 0, level3Total = 0;
                                    try {
                                        chargers = data.getJSONObject("units").getJSONObject("1");
                                        keys = chargers.keys();
                                        while (keys.hasNext()) {
                                            if (chargers.getJSONObject((String) keys.next()).getString("state").equals("AVAIL"))
                                                level1Avail++;
                                            level1Total++;
                                        }
                                    } catch (JSONException e) {}
                                    try {
                                        chargers = data.getJSONObject("units").getJSONObject("2");
                                        keys = chargers.keys();
                                        while (keys.hasNext()) {
                                            if (chargers.getJSONObject((String) keys.next()).getString("state").equals("AVAIL"))
                                                level2Avail++;
                                            level2Total++;
                                        }
                                    } catch (JSONException e) {}
                                    try {
                                        chargers = data.getJSONObject("units").getJSONObject("DCFAST");
                                        keys = chargers.keys();
                                        while (keys.hasNext()) {
                                            if (chargers.getJSONObject((String) keys.next()).getString("state").equals("AVAIL"))
                                                level3Avail++;
                                            level3Total++;
                                        }
                                    } catch (JSONException e) {}
                                    addStation(new Station("blink-" + data.getString("id"),
                                        new LatLng(data.getDouble("latitude"), data.getDouble("longitude")),
                                        R.string.blink, data.optString("address1"),
                                        level1Avail, level1Total, level2Avail, level2Total, level3Avail, level3Total));
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                );
            }
        }

        private void addStation(Station station) {
            for (StationGroup s : stations.values()) {
                if (s.add(station)) {
                    Station.Status status = getStationStatus(s);
                    Marker marker = markers.get(s.id);
                    boolean open = marker.isInfoWindowShown();
                    marker.setIcon(Station.getIcon(status));
                    marker.setVisible(Home.this.marker == null ? status != Station.Status.HIDDEN : Home.this.marker.equals(marker));
                    if (open) marker.showInfoWindow();
                    return;
                }
            }
            if (bounds.contains(station.position)) {
                StationGroup s = new StationGroup(station);
                Station.Status status = getStationStatus(s);
                Marker marker = map.addMarker(new MarkerOptions()
                    .position(station.position)
                    .title(station.address)
                    .snippet(getResources().getString(station.network))
                    .icon(Station.getIcon(status))
                    .visible(Home.this.marker == null && status != Station.Status.HIDDEN));
                markers.put(s.id, marker);
                stations.put(marker, s);
            }
        }
    };
    private Animation.AnimationListener hideAfter = new Animation.AnimationListener() {
        @Override
        public void onAnimationEnd(Animation animation) {
            mapFragment.bringToFront();
        }
        @Override
        public void onAnimationStart(Animation animation) {}
        @Override
        public void onAnimationRepeat(Animation animation) {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        ((Charge) getApplication()).getTracker();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.setDrawerListener(drawerToggle = new ActionBarDrawerToggle(this, drawer, R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close));

        hideDetail.setAnimationListener(hideAfter);

        detail = findViewById(R.id.detail);
        address = (TextView) detail.findViewById(R.id.address);
        network = (TextView) detail.findViewById(R.id.network);
        level1 = (TextView) detail.findViewById(R.id.level_1);
        level2 = (TextView) detail.findViewById(R.id.level_2);
        level3 = (TextView) detail.findViewById(R.id.level_3);
        favorite = (ImageView) detail.findViewById(R.id.favorite);
        ad = (AdView) detail.findViewById(R.id.ad);

        // default values
        settings.putBoolean("option_chargepoint", true);
        settings.putBoolean("option_blink", true);
        settings.putBoolean("option_level_1", true);
        settings.putBoolean("option_level_2", true);
        settings.putBoolean("option_level_1", true);
        settings.putBoolean("option_unavailable", true);
        settings.putFloat("latitude", 38);
        settings.putFloat("longitude", -96);
        settings.putFloat("zoom", 3);

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                settings.putBoolean(key, (Boolean) value);
            } else if (value instanceof Float) {
                settings.putFloat(key, (Float) value);
            }
        }

        Collections.addAll(favorites, prefs.getString("favorites", "").split(" "));

        ((Checkable) findViewById(R.id.option_chargepoint)).setChecked(settings.getBoolean("option_chargepoint"));
        ((Checkable) findViewById(R.id.option_blink)).setChecked(settings.getBoolean("option_blink"));
        ((Checkable) findViewById(R.id.option_level_1)).setChecked(settings.getBoolean("option_level_1"));
        ((Checkable) findViewById(R.id.option_level_2)).setChecked(settings.getBoolean("option_level_2"));
        ((Checkable) findViewById(R.id.option_level_3)).setChecked(settings.getBoolean("option_level_3"));
        ((Checkable) findViewById(R.id.option_unavailable)).setChecked(settings.getBoolean("option_unavailable"));
        ((Checkable) findViewById(R.id.option_favorites)).setChecked(settings.getBoolean("option_favorites"));

        setUpMap();
    }

    @Override
    protected void onStart() {
        GoogleAnalytics.getInstance(this).reportActivityStart(this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        GoogleAnalytics.getInstance(this).reportActivityStop(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMap();
    }

    private void setUpMap() {
        NetworkInfo net = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (net == null || !net.isConnectedOrConnecting()) {
            new AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage("Please check your internet connection and try again.")
                .setPositiveButton("Try Again", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) { setUpMap(); }
                }).create().show();
            return;
        }

        if (map != null) return;
        mapFragment = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        map = mapFragment.getMap();
        //streetView = ((SupportStreetViewPanoramaFragment) getSupportFragmentManager().findFragmentById(R.id.street_view)).getStreetViewPanorama();
        if (map == null) return;

        map.setMyLocationEnabled(true);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(settings.getFloat("latitude"), settings.getFloat("longitude")), settings.getFloat("zoom")));
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        map.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                Home.this.marker = marker;

                map.getUiSettings().setMyLocationButtonEnabled(false);
                map.getUiSettings().setZoomControlsEnabled(false);
                marker.hideInfoWindow();
                for (Marker m : markers.values())
                    if (!m.equals(marker)) m.setVisible(false);

                try {
                    map.setPadding(0, 0, 0, mapFragment.getView().getHeight() / 2);
                } catch (NullPointerException e) {}
                map.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
                detail.bringToFront();
                detail.startAnimation(showDetail);
                //streetView.setPosition(marker.getPosition(), 200);

                StationGroup station = stations.get(marker);
                String provider = locationManager.getBestProvider(new Criteria(), true);
                address.setText(station.address);
                network.setText(station.network);
                level1.setText(status(station.level1Avail, station.level1Total));
                level2.setText(status(station.level2Avail, station.level2Total));
                level3.setText(status(station.level3Avail, station.level3Total));
                favorite.setImageResource(station.containsAny(favorites) ? R.drawable.ic_action_important : R.drawable.ic_action_not_important);
                ad.loadAd(new AdRequest.Builder()
                    .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                    .setLocation(provider == null ? null : locationManager.getLastKnownLocation(provider))
                    .build());
            }

            private Spanned status(int available, int total) {
                if (total == 0) return new SpannableString("none");
                int color = available == 0
                    ? R.color.red : available == total
                    ? R.color.green : R.color.yellow;
                return Html.fromHtml("<font color='#" + String.format("%06X", getResources().getColor(color) & 0xFFFFFF) + "'>" + available + "</font>/" + total + " available");
            }
        });
        map.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                position = cameraPosition;
                bounds = map.getProjection().getVisibleRegion().latLngBounds;
                pendingMoves++;
                requestHandler.postDelayed(requestRunnable, 500);
            }
        });
    }

    @Override
    public void onPause() {
        if (ad != null) ad.pause();
        savePreferences();
        super.onPause();
    }

    private void savePreferences() {
        SharedPreferences.Editor prefs = getPreferences(MODE_PRIVATE).edit();
        for (String key : settings.keySet()) {
            Object value = settings.get(key);
            if (value instanceof Boolean)
                prefs.putBoolean(key, (Boolean) value);
        }
        if (position != null) {
            prefs.putFloat("latitude", (float) position.target.latitude);
            prefs.putFloat("longitude", (float) position.target.longitude);
            prefs.putFloat("zoom", position.zoom);
            prefs.putString("favorites", TextUtils.join(" ", favorites.toArray()));
        }
        prefs.apply();

        pendingMoves = 0;
        requestHandler.removeCallbacks(requestRunnable);
    }

    @Override
    public void onDestroy() {
        if (ad != null) ad.destroy();
        super.onDestroy();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home, menu);
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
        savePreferences();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.settings) {
            if (drawer.isDrawerOpen(Gravity.LEFT)) {
                drawer.closeDrawers();
            } else {
                drawer.openDrawer(Gravity.LEFT);
            }
            return true;
        }
        return drawerToggle.onOptionsItemSelected(item)
            || super.onOptionsItemSelected(item);
    }

    public void hideDetail(View v) {
        if (marker != null) {
            map.getUiSettings().setMyLocationButtonEnabled(true);
            map.getUiSettings().setZoomControlsEnabled(true);

            map.setPadding(0, 0, 0, 0);
            map.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
            detail.startAnimation(hideDetail);

            marker.showInfoWindow();
            marker = null;
            updateMarkers();
        }
    }

    public void getDirections(View v) {
        LatLng c = marker.getPosition();
        startActivity(new Intent(android.content.Intent.ACTION_VIEW,
            Uri.parse("http://maps.google.com/maps?daddr=" + c.latitude + "," + c.longitude)));
    }

    public void toggleFavorite(View v) {
        if (marker != null) {
            StationGroup group = stations.get(marker);
            String text = "Added to favorites";
            int image = R.drawable.ic_action_important;
            if (group.containsAny(favorites)) {
                for (String id : group.stations.keySet())
                    favorites.remove(id);
                image = R.drawable.ic_action_not_important;
                text = "Removed from favorites";
            } else {
                for (String id : group.stations.keySet())
                    favorites.add(id);
            }
            favorite.setImageResource(image);
            Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    public void clickOption(View v) {
        Checkable option = (Checkable) v;
        option.toggle();
        settings.putBoolean(getResources().getResourceEntryName(v.getId()), option.isChecked());
        updateMarkers();
    }

    private void updateMarkers() {
        for (String key : markers.keySet()) {
            Marker m = markers.get(key);
            boolean open = m.isInfoWindowShown();
            Station.Status status = getStationStatus(stations.get(m));
            m.setIcon(Station.getIcon(status));
            m.setVisible(Home.this.marker == null ? status != Station.Status.HIDDEN : Home.this.marker.equals(m));
            if (open) m.showInfoWindow();
        }
    }

    private Station.Status getStationStatus(StationGroup station) {
        boolean level1 = settings.getBoolean("option_level_1"),
            level2 = settings.getBoolean("option_level_2"),
            level3 = settings.getBoolean("option_level_3"),
            unavailable = settings.getBoolean("option_unavailable");

        // in a selected network and with any of the selected levels
        if ((level1 ? station.level1Total : 0) + (level2 ? station.level2Total : 0) + (level3 ? station.level3Total : 0) > 0
            && (station.network == R.string.chargepoint && settings.getBoolean("option_chargepoint")
            || station.network == R.string.blink && settings.getBoolean("option_blink"))
            && (!settings.getBoolean("option_favorites") || favorites.contains(station.id))) {

            boolean level1Avail = level1 && station.level1Avail > 0,
                level2Avail = level2 && station.level2Avail > 0,
                level3Avail = level3 && station.level3Avail > 0;

            Station.Status status = level1Avail || level2Avail || level3Avail
                ? (!level1 || level1Avail) && (!level2 || level2Avail) && (!level3 || level3Avail)
                    ? Station.Status.ALL_LEVELS : Station.Status.SOME_LEVELS
                : Station.Status.NO_LEVELS;
            return !unavailable && status == Station.Status.NO_LEVELS ? Station.Status.HIDDEN : status;
        }
        return Station.Status.HIDDEN;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (map != null)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 12));
    }

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    private static class StationGroup extends Station {
        public final HashMap<String, Station> stations = new HashMap<String, Station>();
        public StationGroup(Station s) {
            super(s.id, s.position, s.network, s.address, s.level1Avail, s.level1Total, s.level2Avail, s.level2Total, s.level3Avail, s.level3Total);
            stations.put(s.id, s);
        }
        public boolean add(Station station) {
            if (station.network == network) {
                if (stations.containsKey(station.id))
                    return true;
                for (Station s : stations.values()) {
                    double distance = SphericalUtil.computeDistanceBetween(s.position, station.position);
                    //System.err.println(distance+"m between\n  "+s.address+"\n  "+station.address);
                    if (distance < 100 || distance < 200 && s.address.equals(station.address)) {
                        stations.put(station.id, station);
                        level1Avail += station.level1Avail;
                        level1Total += station.level1Total;
                        level2Avail += station.level2Avail;
                        level2Total += station.level2Total;
                        level3Avail += station.level3Avail;
                        level3Total += station.level3Total;
                        return true;
                    }
                }
            }
            return false;
        }
        public boolean containsAny(Set<String> ids) {
            for (String id : ids)
                if (stations.containsKey(id))
                    return true;
            return false;
        }
    }

    private static class Station {
        public static enum Status { ALL_LEVELS, SOME_LEVELS, NO_LEVELS, HIDDEN }
        public static BitmapDescriptor getIcon(Status status) {
            float hue = BitmapDescriptorFactory.HUE_RED;
            switch (status) {
                case ALL_LEVELS:
                    hue = BitmapDescriptorFactory.HUE_GREEN;
                    break;
                case SOME_LEVELS:
                    hue = BitmapDescriptorFactory.HUE_YELLOW;
                    break;
            }
            return BitmapDescriptorFactory.defaultMarker(hue);
        }
        public final LatLng position;
        public final String id, address;
        public int network, level1Avail, level1Total, level2Avail, level2Total, level3Avail, level3Total;
        public Station(String id, LatLng position, int network, String address, int level1Avail, int level1Total, int level2Avail, int level2Total, int level3Avail, int level3Total) {
            this.id = id;
            this.position = position;
            this.network = network;
            this.address = address;
            this.level1Avail = level1Avail;
            this.level1Total = level1Total;
            this.level2Avail = level2Avail;
            this.level2Total = level2Total;
            this.level3Avail = level3Avail;
            this.level3Total = level3Total;
        }
    }

    public static class MapFragment extends SupportMapFragment {
        private View view;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
            ViewGroup container = new FrameLayout(getActivity()) {
                private boolean touched;
                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    if (!touched && ev.getAction() == MotionEvent.ACTION_DOWN) {
                        touched = true;
                        Home home = (Home) getContext();
                        home.locationManager.removeUpdates(home);
                    }
                    return super.dispatchTouchEvent(ev);
                }
            };
            container.addView(view = super.onCreateView(inflater, container, savedInstanceState));
            View facade = new View(getActivity());
            facade.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            container.addView(facade);
            return container;
        }

        public void bringToFront() {
            ((View) view.getParent().getParent()).bringToFront();
        }
    }
}
