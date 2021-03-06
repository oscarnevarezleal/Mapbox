package com.telerik.plugins.mapbox;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.MapboxMapOptions;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 *
 */
// TODO for screen rotation, see https://www.mapbox.com/mapbox-android-sdk/#screen-rotation
// TODO fox Xwalk compat, see nativepagetransitions plugin
// TODO look at demo app: https://github.com/mapbox/mapbox-gl-native/blob/master/android/java/MapboxGLAndroidSDKTestApp/src/main/java/com/mapbox/mapboxgl/testapp/MainActivity.java
public class Mapbox extends CordovaPlugin {

    public static final String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    public static final String COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    public static final int LOCATION_REQ_CODE = 0;

    public static final int PERMISSION_DENIED_ERROR = 20;

    private static final String MAPBOX_ACCESSTOKEN_RESOURCE_KEY = "mapbox_accesstoken";

    private static final String ACTION_SHOW = "show";
    private static final String ACTION_HIDE = "hide";
    private static final String ACTION_ADD_MARKERS = "addMarkers";
    private static final String ACTION_MOVE_MARKER = "moveMarker";
    private static final String ACTION_REMOVE_ALL_MARKERS = "removeAllMarkers";
    private static final String ACTION_ADD_MARKER_CALLBACK = "addMarkerCallback";
    // TODO:
    // private static final String ACTION_REMOVE_MARKER_CALLBACK = "removeMarkerCallback";
    private static final String ACTION_ADD_POLYGON = "addPolygon";
    private static final String ACTION_ADD_GEOJSON = "addGeoJSON";
    private static final String ACTION_GET_ZOOMLEVEL = "getZoomLevel";
    private static final String ACTION_SET_ZOOMLEVEL = "setZoomLevel";
    private static final String ACTION_GET_CENTER = "getCenter";
    private static final String ACTION_SET_CENTER = "setCenter";
    private static final String ACTION_GET_TILT = "getTilt";
    private static final String ACTION_SET_TILT = "setTilt";
    private static final String ACTION_ANIMATE_CAMERA = "animateCamera";
    private static final String ACTION_ON_REGION_WILL_CHANGE = "onRegionWillChange";
    private static final String ACTION_ON_REGION_IS_CHANGING = "onRegionIsChanging";
    private static final String ACTION_ON_REGION_DID_CHANGE = "onRegionDidChange";
    // TODO:
    // private static final String ACTION_OFF_REGION_WILL_CHANGE = "offRegionWillChange";
    // private static final String ACTION_OFF_REGION_IS_CHANGING = "offRegionIsChanging";
    // private static final String ACTION_OFF_REGION_DID_CHANGE = "offRegionDidChange";

    public static MapView mapView;
    public static MapboxMap mapboxMap;
    private static float retinaFactor;
    private String accessToken;
    private CallbackContext callback;
    private CallbackContext markerCallbackContext;

    private boolean showUserLocation;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        DisplayMetrics metrics = new DisplayMetrics();
        cordova.getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        retinaFactor = metrics.density;

        try {
            int mapboxAccesstokenResourceId = cordova.getActivity().getResources().getIdentifier(MAPBOX_ACCESSTOKEN_RESOURCE_KEY, "string", cordova.getActivity().getPackageName());
            accessToken = cordova.getActivity().getString(mapboxAccesstokenResourceId);
        } catch (Resources.NotFoundException e) {
            // we'll deal with this when the accessToken property is read, but for now let's dump the error:
            e.printStackTrace();
        }
    }

    @Override
    public boolean execute(final String action, final CordovaArgs args, final CallbackContext callbackContext) throws JSONException {

        this.callback = callbackContext;

        try {
            if (ACTION_SHOW.equals(action)) {
                final JSONObject options = args.getJSONObject(0);
                final String style = getStyle(options.optString("style", Style.MAPBOX_STREETS));

                final JSONObject margins = options.isNull("margins") ? null : options.getJSONObject("margins");
                final int left = applyRetinaFactor(margins == null || margins.isNull("left") ? 0 : margins.getInt("left"));
                final int right = applyRetinaFactor(margins == null || margins.isNull("right") ? 0 : margins.getInt("right"));
                final int top = applyRetinaFactor(margins == null || margins.isNull("top") ? 0 : margins.getInt("top"));
                final int bottom = applyRetinaFactor(margins == null || margins.isNull("bottom") ? 0 : margins.getInt("bottom"));

                final JSONObject center = options.isNull("center") ? null : options.getJSONObject("center");

                this.showUserLocation = !options.isNull("showUserLocation") && options.getBoolean("showUserLocation");

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (accessToken == null) {
                            callbackContext.error(MAPBOX_ACCESSTOKEN_RESOURCE_KEY + " not set in strings.xml");
                            return;
                        }
                        final MapboxMapOptions mapboxOptions = new MapboxMapOptions();
                        mapboxOptions.accessToken(accessToken);


                        final FrameLayout layout = (FrameLayout) webView.getView().getParent();

                        int webViewWidth = webView.getView().getWidth();
                        int webViewHeight = webView.getView().getHeight();

                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(webViewWidth - left - right, webViewHeight - top - bottom);
                        params.setMargins(left, top, right, bottom);


                        mapView = new MapView(webView.getContext(), mapboxOptions);

                        mapView.setStyleUrl(style);
                        mapView.setLayoutParams(params);

                        // https://www.mapbox.com/help/mapview-mapboxmap/
                        mapView.getMapAsync(new OnMapReadyCallback() {
                            @Override
                            public void onMapReady(@NonNull MapboxMap _mapboxMap) {
                                mapboxMap = _mapboxMap;

                                try {
                                    mapboxOptions.compassEnabled(options.isNull("hideCompass") || !options.getBoolean("hideCompass"));
                                    mapboxOptions.scrollGesturesEnabled(options.isNull("disableScroll") || !options.getBoolean("disableScroll"));
                                    mapboxOptions.zoomControlsEnabled(options.isNull("disableZoom") || !options.getBoolean("disableZoom"));
                                    mapboxOptions.tiltGesturesEnabled(options.isNull("disableTilt") || !options.getBoolean("disableTilt"));

                                    // placing these offscreen in case the user wants to hide them
                                    if (!options.isNull("hideAttribution") && options.getBoolean("hideAttribution")) {
                                        mapboxOptions.attributionMargins(new int[]{-300, 0, 0, 0});
                                    }
                                    if (!options.isNull("hideLogo") && options.getBoolean("hideLogo")) {
                                        mapboxOptions.logoMargins(new int[]{-300, 0, 0, 0});
                                    }

                                    if (showUserLocation) {
                                        showUserLocation();
                                    }

                                    Double zoom = options.isNull("zoomLevel") ? 10 : options.getDouble("zoomLevel");
                                    float zoomLevel = zoom.floatValue();
                                    if (center != null) {
                                        final double lat = center.getDouble("lat");
                                        final double lng = center.getDouble("lng");

                                        CameraPosition position = new CameraPosition.Builder()
                                                .target(new LatLng(lat, lng)) // Sets the new camera position
                                                .bearing(180) // Rotate the camera
                                                .zoom(zoomLevel)
                                                .build(); // Creates a CameraPosition from the builder
                                        mapboxMap.setCameraPosition(position);
                                    } else {
                                        if (zoomLevel > 18.0) {
                                            zoomLevel = 18.0f;
                                        }

                                        CameraPosition position = new CameraPosition.Builder()
                                                .zoom(zoomLevel)
                                                .build(); // Creates a CameraPosition from the builder
                                        mapboxMap.setCameraPosition(position);
                                    }

                                    if (options.has("markers")) {
                                        addMarkers(options.getJSONArray("markers"));
                                    }

                                } catch (JSONException e) {
                                    callbackContext.error(e.getMessage());
                                    e.printStackTrace();
                                }

                                callbackContext.success();

                            }
                        });

                        // Need to do this to register a receiver which onPause later needs
                        // If no style has been set by the time you call MapView#onResume, it will load the default style
                        mapView.onResume();
                        mapView.onCreate(null);

                        // position the mapView overlay
                        layout.addView(mapView);

                    }
                });

            } else if (ACTION_HIDE.equals(action)) {
                if (mapboxMap != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ViewGroup vg = (ViewGroup) mapView.getParent();
                            if (vg != null) {
                                vg.removeView(mapView);
                            }
                            callbackContext.success();
                        }
                    });
                }

            } else if (ACTION_GET_ZOOMLEVEL.equals(action)) {
                if (mapboxMap != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final double zoomLevel = mapView.getCameraDistance();
                            callbackContext.success("" + zoomLevel);
                        }
                    });
                }

            } else if (ACTION_SET_ZOOMLEVEL.equals(action)) {
                if (mapboxMap != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final JSONObject options = args.getJSONObject(0);
                                final double zoom = options.getDouble("level");
                                if (zoom >= 0 && zoom <= 20) {
                                    final boolean animated = !options.isNull("animated") && options.getBoolean("animated");
                                    CameraPosition position = new CameraPosition.Builder()
                                            .zoom(zoom)
                                            .build();
                                    CameraUpdate cameraUpdate = CameraUpdateFactory.newCameraPosition(position);
                                    if (animated) {
                                        mapboxMap.animateCamera(cameraUpdate);
                                    } else {
                                        mapboxMap.moveCamera(cameraUpdate);
                                    }
                                    callbackContext.success();
                                } else {
                                    callbackContext.error("invalid zoomlevel, use any double value from 0 to 20 (like 8.3)");
                                }
                            } catch (JSONException e) {
                                callbackContext.error(e.getMessage());
                            }
                        }
                    });
                }

            } else if (ACTION_GET_CENTER.equals(action)) {
                if (mapboxMap != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final LatLng center = mapboxMap.getCameraPosition().target;
                            Map<String, Double> result = new HashMap<String, Double>();
                            result.put("lat", center.getLatitude());
                            result.put("lng", center.getLongitude());
                            callbackContext.success(new JSONObject(result));
                        }
                    });
                }

            } else if (ACTION_SET_CENTER.equals(action)) {
                if (mapboxMap != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final JSONObject options = args.getJSONObject(0);
                                final boolean animated = !options.isNull("animated") && options.getBoolean("animated");
                                final double lat = options.getDouble("lat");
                                final double lng = options.getDouble("lng");

                                CameraPosition position = new CameraPosition.Builder()
                                        .target(new LatLng(lat, lng)) // Sets the new camera position
                                        .bearing(180) // Rotate the camera
                                        .build(); // Creates a CameraPosition from the builder

                                mapboxMap.animateCamera(CameraUpdateFactory
                                        .newCameraPosition(position), (options.optInt("duration", 3)) * 1000);

                                callbackContext.success();
                            } catch (JSONException e) {
                                callbackContext.error(e.getMessage());
                            }
                        }
                    });
                }

            } else if (ACTION_GET_TILT.equals(action)) {
                if (mapboxMap != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final double tilt = mapboxMap.getCameraPosition().tilt;
                            callbackContext.success("" + tilt);
                        }
                    });
                }

            } else if (ACTION_SET_TILT.equals(action)) {
                if (mapboxMap != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final JSONObject options = args.getJSONObject(0);

                                CameraPosition position = new CameraPosition.Builder()
                                        .tilt(options.optDouble("pitch", 20))
                                        .build();

                                mapboxMap.animateCamera(
                                        CameraUpdateFactory.newCameraPosition(position),
                                        (options.optInt("duration", 5)) * 1000, // default 5 seconds
                                        null);

                                callbackContext.success();
                            } catch (JSONException e) {
                                callbackContext.error(e.getMessage());
                            }
                        }
                    });
                }

            } else if (ACTION_ANIMATE_CAMERA.equals(action)) {
                if (mapboxMap != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // TODO check mandatory elements
                                final JSONObject options = args.getJSONObject(0);

                                final JSONObject target = options.getJSONObject("target");
                                final double lat = target.getDouble("lat");
                                final double lng = target.getDouble("lng");

                                final CameraPosition.Builder builder =
                                        new CameraPosition.Builder()
                                                .target(new LatLng(lat, lng));

                                if (options.has("bearing")) {
                                    builder.bearing(((Double) options.getDouble("bearing")).floatValue());
                                }
                                if (options.has("tilt")) {
                                    builder.tilt(((Double) options.getDouble("tilt")).floatValue());
                                }
                                if (options.has("zoomLevel")) {
                                    builder.zoom(((Double) options.getDouble("zoomLevel")).floatValue());
                                }

                                mapboxMap.animateCamera(
                                        CameraUpdateFactory.newCameraPosition(builder.build()),
                                        (options.optInt("duration", 3)) * 1000, // default 15 seconds
                                        null);

                                callbackContext.success();
                            } catch (JSONException e) {
                                callbackContext.error(e.getMessage());
                            }
                        }
                    });
                }

            } else if (ACTION_ADD_POLYGON.equals(action)) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final PolygonOptions polygon = new PolygonOptions();
                            final JSONObject options = args.getJSONObject(0);
                            final JSONArray points = options.getJSONArray("points");
                            for (int i = 0; i < points.length(); i++) {
                                final JSONObject marker = points.getJSONObject(i);
                                final double lat = marker.getDouble("lat");
                                final double lng = marker.getDouble("lng");
                                polygon.add(new LatLng(lat, lng));
                            }
                            mapboxMap.addPolygon(polygon);
                            callbackContext.success();
                        } catch (JSONException e) {
                            callbackContext.error(e.getMessage());
                        }
                    }
                });

            } else if (ACTION_ADD_GEOJSON.equals(action)) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // TODO implement
                        callbackContext.success();
                    }
                });

            } else if (ACTION_MOVE_MARKER.equals(action)) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            int index = args.getInt(0);
                            final JSONObject position = args.getJSONObject(1);
                            LatLng point = new LatLng(position.getDouble("lat"), position.getDouble("lng"));
                            Marker marker = (Marker) mapboxMap.getAnnotation(index);

                            if (marker == null) {
                                callbackContext.error("No Such Marker identified with id " + index);
                            }

                            // When the user clicks on the map, we want to animate the marker to that
                            // location.
                            ValueAnimator markerAnimator = ObjectAnimator.ofObject(marker, "position",
                                    new LatLngEvaluator(), marker.getPosition(), point);
                            markerAnimator.setDuration(0);
                            markerAnimator.start();

                            callbackContext.success();

                        } catch (JSONException e) {

                            final PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                            result.setKeepCallback(true);
                        }
                    }
                });

            } else if (ACTION_ADD_MARKERS.equals(action)) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject json = new JSONObject();
                            final List<Integer> markerList = addMarkers(args.getJSONArray(0));
                            json.put("size", markerList.size());
                            callbackContext.success(json);
                        } catch (JSONException e) {
                            callbackContext.error(e.getMessage());
                        }
                    }
                });

            } else if (ACTION_REMOVE_ALL_MARKERS.equals(action)) {
                if (mapboxMap != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mapboxMap.removeAnnotations();
                            callbackContext.success();
                        }
                    });
                }

            } else if (ACTION_ADD_MARKER_CALLBACK.equals(action)) {
                this.markerCallbackContext = callbackContext;
                mapboxMap.setOnInfoWindowClickListener(new MarkerClickListener());

            } else if (ACTION_ON_REGION_WILL_CHANGE.equals(action)) {
                if (mapboxMap != null) {
                    mapView.addOnMapChangedListener(new RegionWillChangeListener(callbackContext));
                }

            } else if (ACTION_ON_REGION_IS_CHANGING.equals(action)) {
                if (mapboxMap != null) {
                    mapView.addOnMapChangedListener(new RegionIsChangingListener(callbackContext));
                }

            } else if (ACTION_ON_REGION_DID_CHANGE.equals(action)) {
                if (mapboxMap != null) {
                    mapView.addOnMapChangedListener(new RegionDidChangeListener(callbackContext));
                }

            } else {
                return false;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            callbackContext.error(t.getMessage());
        }
        return true;
    }

    private List<Integer> addMarkers(JSONArray markers) throws JSONException {
        List<Integer> markerList = new ArrayList<Integer>();

        for (int i = 0; i < markers.length(); i++) {
            final JSONObject marker = markers.getJSONObject(i);
            final MarkerOptions mo = new MarkerOptions();
            mo.title(marker.isNull("title") ? null : marker.getString("title"));
            mo.snippet(marker.isNull("subtitle") ? null : marker.getString("subtitle"));
            mo.position(new LatLng(marker.getDouble("lat"), marker.getDouble("lng")));
            final Marker markerObject = mapboxMap.addMarker(mo);
            markerList.add(i, (int) markerObject.getId());
        }

        return markerList;
    }

    private class RegionWillChangeListener implements MapView.OnMapChangedListener {
        private CallbackContext callback;

        public RegionWillChangeListener(CallbackContext providedCallback) {
            this.callback = providedCallback;
        }

        @Override
        public void onMapChanged(int change) {
            if (change == MapView.REGION_WILL_CHANGE_ANIMATED) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
                pluginResult.setKeepCallback(true);
                callback.sendPluginResult(pluginResult);
            }
        }
    }

    private class RegionIsChangingListener implements MapView.OnMapChangedListener {
        private CallbackContext callback;

        public RegionIsChangingListener(CallbackContext providedCallback) {
            this.callback = providedCallback;
        }

        @Override
        public void onMapChanged(int change) {
            if (change == MapView.REGION_IS_CHANGING) {

                PluginResult pluginResult;
                final JSONObject json = new JSONObject();
                try {
                    CameraPosition cm = mapboxMap.getCameraPosition();
                    json.put("lat", cm.target.getLatitude());
                    json.put("lng", cm.target.getLongitude());

                    pluginResult = new PluginResult(PluginResult.Status.OK, json);
                    pluginResult.setKeepCallback(true);
                    callback.sendPluginResult(pluginResult);
                } catch (JSONException e) {
                    pluginResult = new PluginResult(PluginResult.Status.ERROR,
                            "Error in callback of " + ACTION_ON_REGION_IS_CHANGING + ": " + e.getMessage());
                    pluginResult.setKeepCallback(true);
                    callback.sendPluginResult(pluginResult);
                }

            }
        }
    }

    private class RegionDidChangeListener implements MapView.OnMapChangedListener {
        private CallbackContext callback;

        public RegionDidChangeListener(CallbackContext providedCallback) {
            this.callback = providedCallback;
        }

        @Override
        public void onMapChanged(int change) {
            if (change == MapView.REGION_DID_CHANGE_ANIMATED) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
                pluginResult.setKeepCallback(true);
                callback.sendPluginResult(pluginResult);
            }
        }
    }

    private class MarkerClickListener implements MapboxMap.OnInfoWindowClickListener {

        @Override
        public boolean onInfoWindowClick(@NonNull Marker marker) {
            // callback
            if (markerCallbackContext != null) {
                final JSONObject json = new JSONObject();
                try {
                    json.put("title", marker.getTitle());
                    json.put("subtitle", marker.getSnippet());
                    json.put("lat", marker.getPosition().getLatitude());
                    json.put("lng", marker.getPosition().getLongitude());
                } catch (JSONException e) {
                    PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR,
                            "Error in callback of " + ACTION_ADD_MARKER_CALLBACK + ": " + e.getMessage());
                    pluginResult.setKeepCallback(true);
                    markerCallbackContext.sendPluginResult(pluginResult);
                }
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, json);
                pluginResult.setKeepCallback(true);
                markerCallbackContext.sendPluginResult(pluginResult);
                return true;
            }
            return false;
        }
    }

    private static int applyRetinaFactor(int i) {
        return (int) (i * retinaFactor);
    }

    private static String getStyle(final String requested) {
        if ("light".equalsIgnoreCase(requested)) {
            return Style.LIGHT;
        } else if ("dark".equalsIgnoreCase(requested)) {
            return Style.DARK;
        } else if ("emerald".equalsIgnoreCase(requested)) {
            return Style.EMERALD;
        } else if ("satellite".equalsIgnoreCase(requested)) {
            return Style.SATELLITE;
            // TODO not currently supported on Android
//    } else if ("hybrid".equalsIgnoreCase(requested)) {
//      return Style.HYBRID;
        } else if ("streets".equalsIgnoreCase(requested)) {
            return Style.MAPBOX_STREETS;
        } else {
            return requested;
        }
    }

    private boolean permissionGranted(String... types) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        for (final String type : types) {
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this.cordova.getActivity(), type)) {
                return false;
            }
        }
        return true;
    }

    protected void showUserLocation() {
        if (permissionGranted(COARSE_LOCATION, FINE_LOCATION)) {
            //noinspection MissingPermission
            mapboxMap.setMyLocationEnabled(showUserLocation);
        } else {
            requestPermission(COARSE_LOCATION, FINE_LOCATION);
        }
    }


    private void requestPermission(String... types) {
        ActivityCompat.requestPermissions(
                this.cordova.getActivity(),
                types,
                LOCATION_REQ_CODE);
    }

    // TODO
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                this.callback.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
                return;
            }
        }
        switch (requestCode) {
            case LOCATION_REQ_CODE:
                showUserLocation();
                break;
        }
    }

    public void onPause(boolean multitasking) {
        if (mapboxMap != null) {
            mapView.onPause();
        }
    }

    public void onResume(boolean multitasking) {
        if (mapboxMap != null) {
            mapView.onResume();
        }
    }

    public void onDestroy() {
        if (mapboxMap != null) {
            mapView.onDestroy();
        }
    }

    private static class LatLngEvaluator implements TypeEvaluator<LatLng> {
        // Method is used to interpolate the marker animation.

        private LatLng latLng = new LatLng();

        @Override
        public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
            latLng.setLatitude(startValue.getLatitude()
                    + ((endValue.getLatitude() - startValue.getLatitude()) * fraction));
            latLng.setLongitude(startValue.getLongitude()
                    + ((endValue.getLongitude() - startValue.getLongitude()) * fraction));
            return latLng;
        }
    }

}

