package com.example.medipoint_final;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    SupportMapFragment supportMapFragment;
    GoogleMap map;
    EditText searchInput;
    Button searchButton;
    Button logoutButton;
    double currentLat = 0;
    double currentLong = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        supportMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.google_map);
        searchInput = findViewById(R.id.search_input);
        searchButton = findViewById(R.id.search_button);
        logoutButton = findViewById(R.id.logout_button);

        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = searchInput.getText().toString();
                searchForHospitals(query);
            }
        });

        Objects.requireNonNull(supportMapFragment).getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull GoogleMap googleMap) {
                map = googleMap;
                findHospitals(true);
            }
        });

        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(getApplicationContext(), Login.class);
                startActivity(intent);
                finish();
            }
        });
    }

    private void findHospitals(boolean useCurrentLocation) {
        if (useCurrentLocation) {
            FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation(fusedLocationProviderClient);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 44);
            }
        } else {
            currentLat = 7.8731;
            currentLong = 80.7718;

            String url = "https://maps.google.com/maps/api/place/nearbysearch/json?" +
                    "location=" + currentLat + "," + currentLong +
                    "&radius=5000" +
                    "&types=hospital" +
                    "&sensor=true" +
                    "&key=" + getResources().getString(R.string.google_map_key);

            new PlaceTask().execute(url);
        }
    }

    private void searchForHospitals(String query) {
        String url = "https://maps.google.com/maps/api/place/textsearch/json?" +
                "query=" + query +
                "&location=" + currentLat + "," + currentLong +
                "&radius=5000" +
                "&types=hospital" +
                "&sensor=true" +
                "&key=" + getResources().getString(R.string.google_map_key);

        new PlaceTask().execute(url);
    }

    private void getCurrentLocation(FusedLocationProviderClient fusedLocationProviderClient) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Task<Location> task = fusedLocationProviderClient.getLastLocation();
        task.addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    currentLat = location.getLatitude();
                    currentLong = location.getLongitude();
                    supportMapFragment.getMapAsync(new OnMapReadyCallback() {
                        @Override
                        public void onMapReady(@NonNull GoogleMap googleMap) {
                            map = googleMap;
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(currentLat, currentLong), 10));
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 44) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
                getCurrentLocation(fusedLocationProviderClient);
            }
        }
    }

    private class PlaceTask extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... strings) {
            String data = null;
            try {
                data = downloadUrl(strings[0]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return data;
        }

        @Override
        protected void onPostExecute(String s) {
            List<HashMap<String, String>> nearbyPlacesList = null;
            JsonParser parser = new JsonParser();
            JSONObject object;

            try {
                object = new JSONObject(s);
                nearbyPlacesList = parser.parseResult(object);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if (nearbyPlacesList != null) {
                for (int i = 0; i < nearbyPlacesList.size(); i++) {
                    HashMap<String, String> googlePlace = nearbyPlacesList.get(i);
                    double lat = Double.parseDouble(googlePlace.get("lat"));
                    double lng = Double.parseDouble(googlePlace.get("lng"));
                    String placeName = googlePlace.get("name");

                    LatLng latLng = new LatLng(lat, lng);
                    MarkerOptions markerOptions = new MarkerOptions();
                    markerOptions.position(latLng);
                    markerOptions.title(placeName);

                    if (map != null) {
                        map.addMarker(markerOptions);
                    }
                }
            }
        }
    }

    private String downloadUrl(String string) throws IOException {
        URL url = new URL(string);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.connect();
        InputStream stream = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }
}
