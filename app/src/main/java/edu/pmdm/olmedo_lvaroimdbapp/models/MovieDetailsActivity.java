package edu.pmdm.olmedo_lvaroimdbapp.models;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;

import org.json.JSONObject;

import java.io.IOException;

import edu.pmdm.olmedo_lvaroimdbapp.R;
import edu.pmdm.olmedo_lvaroimdbapp.api.IMDbApiService;
import edu.pmdm.olmedo_lvaroimdbapp.api.TMDbApiService;

public class MovieDetailsActivity extends AppCompatActivity {

    private static final String TAG = "MovieDetailsActivity";
    private static final int REQUEST_CODE_CONTACTS_AND_SMS = 100;
    private static final int REQUEST_CODE_PICK_CONTACT = 101;

    private ImageView moviePoster;
    private TextView movieTitle;
    private TextView movieDescription;
    private TextView movieReleaseDate;
    private TextView movieRating;
    private Button sendSmsButton;

    private String title = "Título no disponible";
    private String description = "Descripción no disponible";
    private String releaseDate = "Fecha no disponible";
    private String ratingText = "Rating no disponible";
    private String imageUrl = "";

    private boolean isFromTMDb = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_details);

        moviePoster = findViewById(R.id.movie_poster);
        movieTitle = findViewById(R.id.movie_title);
        movieDescription = findViewById(R.id.movie_description);
        movieReleaseDate = findViewById(R.id.movie_release_date);
        movieRating = findViewById(R.id.movie_rating);
        sendSmsButton = findViewById(R.id.send_sms);

        //Obtiene datos del Intent
        String movieId = getIntent().getStringExtra("MOVIE_ID");
        if (getIntent().hasExtra("IS_FROM_TMDB")) {
            isFromTMDb = getIntent().getBooleanExtra("IS_FROM_TMDB", false);
        } else {
            Log.e(TAG, "IS_FROM_TMDB no está en el Intent. Tiene valor predeterminado.");
            isFromTMDb = false;
        }

        if (movieId!=null) {
            if (isFromTMDb) {
                fetchMovieDetailsFromTMDb(movieId);
            } else {
                fetchMovieDetailsFromIMDb(movieId);
            }
        } else {
            Log.e(TAG, "No se recibió ningún ID de película");
            Toast.makeText(this, "Error al cargar detalles de la película.", Toast.LENGTH_SHORT).show();
            finish();
        }

        //Configura acción del botón de para evniar SMS
        sendSmsButton.setOnClickListener(v -> {
            if (hasContactsAndSmsPermission()) {
                openContactPicker();
            } else {
                requestContactsAndSmsPermission();
            }
        });
    }

    //Método que obtiene la respuesta de la API de IMDb
    private void fetchMovieDetailsFromIMDb(String movieId) {
        new Thread(() -> {
            IMDbApiService apiService = new IMDbApiService();
            try {
                String response = apiService.getMovieOverview(movieId);
                parseAndDisplayMovieDetailsFromIMDb(response);
            } catch (IOException e) {
                Log.e(TAG, "Error al obtener detalles de IMDb", e);
                runOnUiThread(() -> Toast.makeText(this, "Error al obtener detalles de IMDb.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    //Método que obtiene la respuesta de la API de TMDb
    private void fetchMovieDetailsFromTMDb(String movieId) {
        new Thread(() -> {
            TMDbApiService apiService = new TMDbApiService();
            try {
                String response = apiService.getMovieDetails(movieId);
                parseAndDisplayMovieDetailsFromTMDb(response);
            } catch (IOException e) {
                Log.e(TAG, "Error al obtener detalles de TMDb", e);
                runOnUiThread(() -> Toast.makeText(this, "Error al obtener detalles de TMDb.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * Método que parsea el JSON que devuelve como respuesta la API que estemos
     * usando, que en este caso sería IMDb, y obtiene el título de la película, la fecha de salida,
     * la descripción, la valoración y la URL de la imagen de la portada.
     */
    private void parseAndDisplayMovieDetailsFromIMDb(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject data = jsonObject.getJSONObject("data");
            JSONObject titleObj = data.getJSONObject("title");

            if (titleObj.has("titleText")) {
                title = titleObj.getJSONObject("titleText").getString("text");
            }
            if (titleObj.has("releaseDate")) {
                JSONObject releaseDateObj = titleObj.getJSONObject("releaseDate");
                releaseDate = releaseDateObj.getInt("year") + "-" +
                        releaseDateObj.getInt("month") + "-" +
                        releaseDateObj.getInt("day");
            }
            if (titleObj.has("plot")) {
                description = titleObj.getJSONObject("plot").getJSONObject("plotText").getString("plainText");
            }
            if (titleObj.has("ratingsSummary")) {
                double rating = titleObj.getJSONObject("ratingsSummary").getDouble("aggregateRating");
                ratingText = String.format("Rating: %.1f", rating);
            }
            if (titleObj.has("primaryImage")) {
                imageUrl = titleObj.getJSONObject("primaryImage").getString("url");
            }
            updateDetails();
        } catch (Exception e) {
            Log.e(TAG, "Error al parsear detalles de IMDb", e);
        }
    }

    /**
     * Método que parsea el JSON que devuelve como respuesta la API que estemos
     * usando, que en este caso sería TMDb, y obtiene el título de la película, la fecha de salida,
     * la descripción, la valoración y la URL de la imagen de la portada.
     */
    private void parseAndDisplayMovieDetailsFromTMDb(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);

            title = jsonObject.getString("title");
            releaseDate = jsonObject.getString("release_date");
            description = jsonObject.getString("overview");
            double rating = jsonObject.getDouble("vote_average");
            ratingText = String.format("Rating: %.1f", rating);
            imageUrl = "https://image.tmdb.org/t/p/w500" + jsonObject.getString("poster_path");

            updateDetails();
        } catch (Exception e) {
            Log.e(TAG, "Error al parsear detalles de TMDb", e);
        }
    }

    //Método que establece los detalles de la película
    private void updateDetails() {
        runOnUiThread(() -> {
            movieTitle.setText(title);
            movieDescription.setText(description);
            movieReleaseDate.setText("Fecha de salida: " + releaseDate);
            movieRating.setText(ratingText);

            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.placeholder)
                    .error(R.drawable.error_image)
                    .into(moviePoster);
        });
    }

    //Comprueba si se tienen los permisos de READ_CONTACTS y SEND_SMS
    private boolean hasContactsAndSmsPermission() {
        int readContactsPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_CONTACTS);
        int sendSmsPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.SEND_SMS);

        return readContactsPermission == PackageManager.PERMISSION_GRANTED
                && sendSmsPermission == PackageManager.PERMISSION_GRANTED;
    }

    //Solicita los permisos de READ_CONTACTS y SEND_SMS.
    private void requestContactsAndSmsPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS},
                REQUEST_CODE_CONTACTS_AND_SMS
        );
    }

    //Abre la lista de contactos con un Intent de ACTION_PICK.
    private void openContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(intent, REQUEST_CODE_PICK_CONTACT);
    }

    //Comprueba que tenga los permisos aceptados de los contactos para poder acceder a la lista y mostrarlos
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CONTACTS_AND_SMS) {
            if (hasContactsAndSmsPermission()) {
                openContactPicker();
            } else {
                Toast.makeText(this, "Se requieren permisos para enviar SMS y leer contactos.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Maneja el resultado de las actividades iniciadas.
     * Se ejecuta cuando una actividad iniciada con `startActivityForResult` finaliza y
     * devuelve un resultado. En este caso, el método maneja el resultado del selector de contactos.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_CONTACT && resultCode == RESULT_OK) {
            if (data != null) {
                Uri contactUri = data.getData();
                if (contactUri != null) {
                    String phoneNumber = getPhoneNumberFromContactUri(contactUri);
                    if (phoneNumber != null && !phoneNumber.isEmpty()) {
                        openSmsApp(phoneNumber);
                    } else {
                        Toast.makeText(this, "No se encontró un número de teléfono para este contacto.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    //Obtiene el número de teléfono del contacto, para poder mandar un SMS posteriormente
    private String getPhoneNumberFromContactUri(Uri contactUri) {
        String phoneNumber = null;
        String contactId = null;
        Cursor cursor = getContentResolver().query(contactUri,
                new String[]{ContactsContract.Contacts._ID},
                null, null, null);
        if (cursor!=null) {
            if (cursor.moveToFirst()) {
                contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
            }
            cursor.close();
        }
        if (contactId!=null) {
            Cursor phones = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    new String[]{contactId},
                    null);
            if (phones != null) {
                while (phones.moveToNext()) {
                    phoneNumber = phones.getString(
                            phones.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    if (phoneNumber != null && !phoneNumber.isEmpty()) {
                        break;
                    }
                }
                phones.close();
            }
        }
        return phoneNumber;
    }

    //Abre la aplicación para mandar el SMS y muestra el mensaje con los datos de la película que queremos compartir.
    private void openSmsApp(String phoneNumber) {
        try {
            String message = "Te recomiendo la película '" + title + "' con " + ratingText;
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
            smsIntent.setData(Uri.parse("smsto:" + phoneNumber));
            smsIntent.putExtra("sms_body", message);
            startActivity(smsIntent);
        }catch (Exception e){
            Toast.makeText(this, "No hay aplicación de SMS disponible.", Toast.LENGTH_SHORT).show();

        }
    }
}