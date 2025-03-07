package edu.pmdm.olmedo_lvaroimdbapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;

import java.util.Arrays;
import java.util.List;

public class SelectAddressActivity extends AppCompatActivity implements OnMapReadyCallback {

    private EditText edtAddress;
    private MapView mapView;
    private GoogleMap googleMap;

    private final ActivityResultLauncher<Intent> autocompleteLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            Intent data = result.getData();
                            if (data != null) {
                                Place place = Autocomplete.getPlaceFromIntent(data);
                                edtAddress.setText(place.getAddress());

                                LatLng selectedLocation = place.getLatLng();
                                if (selectedLocation != null && googleMap != null) {
                                    googleMap.clear();
                                    googleMap.addMarker(new MarkerOptions()
                                            .position(selectedLocation)
                                            .title("Ubicación seleccionada"));
                                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLocation, 15));
                                }
                            }
                        } else if (result.getResultCode() == AutocompleteActivity.RESULT_ERROR) {
                            Intent data = result.getData();
                            if (data != null) {
                                Status status = Autocomplete.getStatusFromIntent(data);
                                Log.e("SelectAddressActivity", status.getStatusMessage() != null ?
                                        status.getStatusMessage() : "Error desconocido en Autocomplete");
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_address);

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        }

        edtAddress = findViewById(R.id.edtAddress);
        Button btnSearchLocation = findViewById(R.id.btnSearchLocation);
        Button btnConfirmLocation = findViewById(R.id.btnConfirmLocation);
        mapView = findViewById(R.id.mapView);

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        btnSearchLocation.setOnClickListener(v -> {
            List<Place.Field> fields = Arrays.asList(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.ADDRESS,
                    Place.Field.LAT_LNG
            );
            Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                    .build(SelectAddressActivity.this);
            autocompleteLauncher.launch(intent);
        });

        btnConfirmLocation.setOnClickListener(v -> {
            String selectedAddress = edtAddress.getText().toString();
            Intent resultIntent = new Intent();
            resultIntent.putExtra("SELECTED_ADDRESS", selectedAddress);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

    // Métodos de ciclo de vida del MapView
    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        LatLng defaultLocation = new LatLng(-33.8688, 151.2093);
        googleMap.addMarker(new MarkerOptions()
                .position(defaultLocation)
                .title("Ubicación predeterminada"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}