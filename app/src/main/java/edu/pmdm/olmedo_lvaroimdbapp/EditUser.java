package edu.pmdm.olmedo_lvaroimdbapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.hbb20.CountryCodePicker;
import edu.pmdm.olmedo_lvaroimdbapp.models.FavoriteDBHelper;
import edu.pmdm.olmedo_lvaroimdbapp.models.UserSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditUser extends AppCompatActivity {

    private static final String TAG = "EditUserActivity";
    private static final String PERMISSION_IMAGE_MESSAGE =
            "Los permisos de cámara y almacenamiento son necesarios para seleccionar una imagen. Por favor, actívalos en Ajustes.";

    // Referencias a vistas
    private EditText edtName, edtEmail, edtAddress, edtPhone;
    private ImageView userImageView;
    private CountryCodePicker countryCodePicker;

    // Control de permisos y lanzadores
    private ActivityResultLauncher<Intent> cameraResult;         // Nueva forma de capturar miniatura
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;
    // Lanzador para seleccionar dirección (nueva funcionalidad)
    private ActivityResultLauncher<Intent> selectAddressLauncher;

    // Variables de estado
    private String externalPhotoUrl = "";
    private FirebaseAuth mAuth;
    private FavoriteDBHelper dbHelper;
    private String currentUserId;

    // Ejecutores para operaciones en segundo plano
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_user);

        // 1) Inicializar FirebaseAuth
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "No hay usuario logueado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = firebaseUser.getUid();

        // 2) Inicializar DB local
        dbHelper = new FavoriteDBHelper(this);

        // 3) Vincular vistas
        edtName = findViewById(R.id.etName);
        edtEmail = findViewById(R.id.etEmail);
        edtAddress = findViewById(R.id.etAddress);
        edtPhone = findViewById(R.id.etPhone);
        userImageView = findViewById(R.id.ivProfileImage);

        Button btnSelectAddress = findViewById(R.id.btnSelectAddress);
        Button btnSelectImage   = findViewById(R.id.btnSelectImage);
        Button btnSave          = findViewById(R.id.btnSave);
        countryCodePicker       = findViewById(R.id.countryCodePicker);

        // 4) Guardar y recuperar el código de país en SharedPreferences
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        int lastCode = prefs.getInt("LAST_COUNTRY_CODE", -1);
        if (lastCode != -1) {
            countryCodePicker.setCountryForPhoneCode(lastCode);
        } else {
            countryCodePicker.setCountryForPhoneCode(
                    Integer.parseInt(countryCodePicker.getDefaultCountryCode())
            );
        }
        countryCodePicker.setOnCountryChangeListener(() -> {
            int selectedCode = countryCodePicker.getSelectedCountryCodeAsInt();
            prefs.edit().putInt("LAST_COUNTRY_CODE", selectedCode).apply();
        });

        // 5) Configurar ActivityResultLaunchers
        // Cámara (usando miniatura)
        cameraResult = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            // Al capturar la foto como miniatura, la cámara la pasa en data.getExtras().get("data")
                            Bitmap photo = (Bitmap) data.getExtras().get("data");
                            if (photo != null) {
                                userImageView.setImageBitmap(photo);
                                externalPhotoUrl = "";
                            }
                        }
                    }
                }
        );

        // Galería
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        loadImageIntoView(uri);
                        externalPhotoUrl = "";
                    }
                }
        );

        // Permisos de cámara / galería
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    boolean shouldShowSettings = false;
                    for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                        if (!entry.getValue()) {
                            allGranted = false;
                            if (!shouldShowRequestPermissionRationale(entry.getKey())) {
                                shouldShowSettings = true;
                            }
                        }
                    }
                    if (allGranted) {
                        showImageOptionsDialog();
                    } else if (shouldShowSettings) {
                        showSettingsDialog();
                    } else {
                        Toast.makeText(this,
                                "Permisos necesarios para seleccionar imagen.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // NUEVO: Configurar ActivityResultLauncher para seleccionar dirección en el mapa
        selectAddressLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String selectedAddress = result.getData().getStringExtra("SELECTED_ADDRESS");
                        if (selectedAddress != null && !selectedAddress.isEmpty()) {
                            edtAddress.setText(selectedAddress);
                        }
                    }
                }
        );

        // 6) Revisar si traemos una imagen por Intent
        String photoUriString = getIntent().getStringExtra("EXTRA_PROFILE_PICTURE_URI");
        if (photoUriString != null && !photoUriString.isEmpty()) {
            Uri photoUri = Uri.parse(photoUriString);
            loadImageIntoView(photoUri);
            if (photoUriString.startsWith("http")) {
                externalPhotoUrl = photoUriString;
            }
        }

        // 7) Cargar datos del usuario desde la BD local
        loadUserDataFromLocalDB(currentUserId);

        // 8) Listeners de botones
        // Al pulsar el botón de dirección se abrirá la nueva actividad para seleccionar dirección
        btnSelectAddress.setOnClickListener(v -> abrirSelectAddressActivity());
        btnSelectImage.setOnClickListener(v -> checkImagePermissionsAndShowOptions());
        btnSave.setOnClickListener(v -> saveUserData());
    }

    /**
     * Abre la actividad para seleccionar dirección usando Google Maps.
     */
    private void abrirSelectAddressActivity() {
        Intent intent = new Intent(this, SelectAddressActivity.class);
        selectAddressLauncher.launch(intent);
    }

    /**
     * Carga los datos del usuario desde la tabla user_sessions (local DB).
     */
    private void loadUserDataFromLocalDB(String userId) {
        UserSession session = dbHelper.getUserSession(userId);
        if (session != null) {
            // Rellenar campos
            edtName.setText(session.getNombre());
            edtEmail.setText(session.getEmail());
            edtAddress.setText(session.getAddress());

            // Si el teléfono en BD se guardó como "+34 600123456" (prefijo + espacio + número)
            String phoneFull = session.getPhone();
            if (phoneFull != null && !phoneFull.isEmpty()) {
                // Quitar el signo + inicial
                phoneFull = phoneFull.replace("+", ""); // p.ej. "34 600123456"

                // Dividir en dos partes (el prefijo y el número)
                if (phoneFull.contains(" ")) {
                    String[] parts = phoneFull.split(" ", 2);
                    String codePart   = parts[0];
                    String numberPart = parts[1];

                    try {
                        int codeInt = Integer.parseInt(codePart);
                        countryCodePicker.setCountryForPhoneCode(codeInt);
                        edtPhone.setText(numberPart);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parseando prefijo de teléfono", e);
                        // fallback: lo dejamos tal cual
                        edtPhone.setText(phoneFull);
                    }
                } else {
                    // Caso de que no haya espacio
                    edtPhone.setText(phoneFull);
                }
            }

            // Cargar imagen (URL o Base64)
            if (session.getImage() != null && !session.getImage().isEmpty()) {
                if (session.getImage().startsWith("http")) {
                    loadImageFromUrl(userImageView, session.getImage());
                } else {
                    decodeBase64Image(session.getImage());
                }
            }
        } else {
            Log.w(TAG, "No se encontró usuario en BD local con userId: " + userId);
        }
    }

    /**
     * Guarda/actualiza los datos del usuario en la tabla user_sessions local.
     */
    private void saveUserData() {
        // 1. Obtener datos
        String name    = edtName.getText().toString().trim();
        String email   = edtEmail.getText().toString().trim();
        String address = edtAddress.getText().toString().trim();

        // Combinar prefijo + número con espacio
        String phoneNumber = edtPhone.getText().toString().trim();
        String countryCode = countryCodePicker.getSelectedCountryCode();
        // Ej: "+34 600123456"
        String phoneToSave = "+" + countryCode + " " + phoneNumber;

        // Validaciones simples
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convertir la imagen a Base64
        String base64Image = convertImageToBase64(userImageView);

        // Crear timestamp
        String loginTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()).format(new Date());

        // 2. Hacer upsert en la tabla user_sessions
        boolean success = dbHelper.upsertUserSession(
                currentUserId,
                name,
                email,
                loginTime,
                address,
                phoneToSave,
                base64Image
        );

        if (success) {
            Toast.makeText(this, "Datos guardados correctamente", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Error al guardar datos", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Verifica permisos e invoca el diálogo de opciones de imagen.
     */
    private void checkImagePermissionsAndShowOptions() {
        ArrayList<String> permissionsList = new ArrayList<>();
        permissionsList.add(Manifest.permission.CAMERA);

        // Para la galería
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        boolean permissionsGranted = true;
        for (String permission : permissionsList) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false;
                break;
            }
        }
        if (permissionsGranted) {
            showImageOptionsDialog();
        } else {
            permissionLauncher.launch(permissionsList.toArray(new String[0]));
        }
    }

    /**
     * Muestra un diálogo con las opciones: Cámara, Galería, URL.
     */
    private void showImageOptionsDialog() {
        String[] items = {"Cámara", "Galería", "URL externa"};
        new AlertDialog.Builder(this)
                .setTitle("Seleccionar imagen")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: // Cámara
                            // Versión simple (miniatura)
                            Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                            cameraResult.launch(cameraIntent);
                            break;
                        case 1: // Galería
                            galleryLauncher.launch("image/*");
                            break;
                        case 2: // URL externa
                            showUrlDialog();
                            break;
                    }
                })
                .create()
                .show();
    }

    /**
     * Muestra un diálogo para introducir la URL de la imagen.
     */
    private void showUrlDialog() {
        final EditText input = new EditText(this);
        input.setHint("https://ejemplo.com/foto.png");

        new AlertDialog.Builder(this)
                .setTitle("Introduce la URL de la imagen")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) {
                        externalPhotoUrl = url;
                        loadImageFromUrl(userImageView, url);
                    } else {
                        Toast.makeText(this, "URL vacía", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Carga la imagen desde la galería o una URI local en segundo plano.
     */
    private void loadImageIntoView(Uri uri) {
        executorService.execute(() -> {
            Bitmap bitmap = null;
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                if (inputStream != null) {
                    bitmap = BitmapFactory.decodeStream(inputStream);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error cargando imagen desde URI", e);
            }
            final Bitmap finalBitmap = bitmap;
            runOnUiThread(() -> {
                if (finalBitmap != null) {
                    userImageView.setImageBitmap(finalBitmap);
                } else {
                    userImageView.setImageResource(R.mipmap.ic_launcher_round);
                }
            });
        });
    }

    /**
     * Carga la imagen desde URL en segundo plano.
     */
    private void loadImageFromUrl(ImageView imageView, String url) {
        if (url == null || url.isEmpty()) {
            imageView.setImageResource(R.mipmap.ic_launcher_round);
            return;
        }
        executorService.execute(() -> {
            Bitmap bitmap = null;
            InputStream inputStream = null;
            try {
                inputStream = new java.net.URL(url).openStream();
                // Opcional: ajustar inSampleSize, etc.
                bitmap = BitmapFactory.decodeStream(inputStream);
            } catch (IOException e) {
                Log.e(TAG, "Error loading image from URL", e);
            } finally {
                if (inputStream != null) {
                    try { inputStream.close(); } catch (IOException ignored) {}
                }
            }
            Bitmap finalBitmap = bitmap;
            runOnUiThread(() -> {
                if (finalBitmap != null) {
                    imageView.setImageBitmap(finalBitmap);
                } else {
                    imageView.setImageResource(R.mipmap.ic_launcher_round);
                }
            });
        });
    }

    /**
     * Convierte el Drawable del ImageView a un Bitmap sin cast directo.
     */
    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    /**
     * Convierte la imagen del ImageView a Base64 para la BD.
     */
    private String convertImageToBase64(ImageView imageView) {
        if (imageView.getDrawable() == null) {
            return "";
        }
        Bitmap bitmap = drawableToBitmap(imageView.getDrawable());
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    /**
     * Decodifica una cadena Base64 y la muestra en el ImageView.
     */
    private void decodeBase64Image(String base64Image) {
        try {
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            userImageView.setImageBitmap(bitmap);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error decodificando imagen Base64", e);
            userImageView.setImageResource(R.mipmap.ic_launcher_round);
        }
    }

    /**
     * Muestra un diálogo para ir a Ajustes si faltan permisos.
     */
    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permisos necesarios")
                .setMessage(PERMISSION_IMAGE_MESSAGE)
                .setPositiveButton("Ir a Ajustes", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        );
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "No se pudo abrir la configuración", e);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
