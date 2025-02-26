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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hbb20.CountryCodePicker;
import edu.pmdm.olmedo_lvaroimdbapp.models.FavoriteDBHelper;
import edu.pmdm.olmedo_lvaroimdbapp.models.UserSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditUser extends AppCompatActivity {

    private static final String TAG = "EditUserActivity";
    private static final String PERMISSION_IMAGE_MESSAGE =
            "Los permisos de cámara y almacenamiento son necesarios para seleccionar una imagen. Por favor, actívalos en Ajustes.";

    private EditText edtName, edtEmail, edtAddress, edtPhone;
    private ImageView userImageView;
    private CountryCodePicker countryCodePicker;

    private ActivityResultLauncher<Intent> cameraResult;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> selectAddressLauncher;

    private String externalPhotoUrl = "";
    private FirebaseAuth mAuth;
    private FavoriteDBHelper dbHelper;
    private String currentUserId;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_user);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "No hay usuario logueado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = firebaseUser.getUid();
        Log.d(TAG, "Usuario actual: " + currentUserId);

        dbHelper = new FavoriteDBHelper(this);

        edtName = findViewById(R.id.etName);
        edtEmail = findViewById(R.id.etEmail);
        edtAddress = findViewById(R.id.etAddress);
        edtPhone = findViewById(R.id.etPhone);
        userImageView = findViewById(R.id.ivProfileImage);
        countryCodePicker = findViewById(R.id.countryCodePicker);

        Button btnSelectAddress = findViewById(R.id.btnSelectAddress);
        Button btnSelectImage = findViewById(R.id.btnSelectImage);
        Button btnSave = findViewById(R.id.btnSave);

        // Configuración del CountryCodePicker
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

        // Registro de resultados para cámara, galería y permisos
        cameraResult = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getExtras() != null) {
                            Bitmap photo = (Bitmap) data.getExtras().get("data");
                            if (photo != null) {
                                userImageView.setImageBitmap(photo);
                                externalPhotoUrl = "";
                                Log.d(TAG, "Foto capturada desde la cámara.");
                            }
                        }
                    }
                }
        );

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        loadImageIntoView(uri);
                        externalPhotoUrl = "";
                        Log.d(TAG, "Imagen seleccionada desde la galería.");
                    }
                }
        );

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

        selectAddressLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String selectedAddress = result.getData().getStringExtra("SELECTED_ADDRESS");
                        if (!TextUtils.isEmpty(selectedAddress)) {
                            edtAddress.setText(selectedAddress);
                            Log.d(TAG, "Dirección seleccionada: " + selectedAddress);
                        }
                    }
                }
        );

        // Cargar imagen inicial si viene por intent
        String photoUriString = getIntent().getStringExtra("EXTRA_PROFILE_PICTURE_URI");
        if (!TextUtils.isEmpty(photoUriString)) {
            Uri photoUri = Uri.parse(photoUriString);
            loadImageIntoView(photoUri);
            if (photoUriString.startsWith("http")) {
                externalPhotoUrl = photoUriString;
            }
        }

        loadUserDataFromLocalDB(currentUserId);

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
     * Carga los datos del usuario desde Firestore y actualiza la base de datos local.
     */
    private void loadUserDataFromLocalDB(String userId) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        DocumentReference userDocRef = firestore.collection("users").document(userId);
        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (task.getResult().exists()) {
                    Log.d(TAG, "Datos encontrados en Firestore.");
                    String nombre = task.getResult().getString("nombre");
                    String email = task.getResult().getString("email");
                    String encryptedAddress = task.getResult().getString("address");
                    String encryptedPhone = task.getResult().getString("phone");
                    String image = task.getResult().getString("image");

                    // Desencriptar campos
                    String address = encryptedAddress != null ? EncryptionHelper.decryptAddress(encryptedAddress) : "";
                    String phone = encryptedPhone != null ? EncryptionHelper.decryptPhone(encryptedPhone) : "";

                    // Guardar en SQLite (ya encriptados)
                    dbHelper.upsertUserSession(
                            userId,
                            nombre != null ? nombre : "",
                            email != null ? email : "",
                            "", // login_time no se utiliza aquí
                            encryptedAddress != null ? encryptedAddress : "",
                            encryptedPhone != null ? encryptedPhone : "",
                            image != null ? image : ""
                    );

                    edtName.setText(nombre != null ? nombre : "");
                    edtEmail.setText(email != null ? email : "");
                    edtAddress.setText(address);

                    if (!TextUtils.isEmpty(phone)) {
                        phone = phone.replace("+", "");
                        if (phone.contains(" ")) {
                            String[] parts = phone.split(" ", 2);
                            try {
                                int codeInt = Integer.parseInt(parts[0]);
                                countryCodePicker.setCountryForPhoneCode(codeInt);
                                edtPhone.setText(parts[1]);
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Error parseando el prefijo del teléfono.", e);
                                edtPhone.setText(phone);
                            }
                        } else {
                            edtPhone.setText(phone);
                        }
                    }

                    if (!TextUtils.isEmpty(image)) {
                        if (image.startsWith("http")) {
                            loadImageFromUrl(userImageView, image);
                        } else {
                            decodeBase64Image(image);
                        }
                    }
                } else {
                    Log.w(TAG, "No existen datos en Firestore. Se carga desde SQLite.");
                    cargarDesdeSQLite(userId);
                }
            } else {
                Log.e(TAG, "Error obteniendo datos de Firestore.", task.getException());
                cargarDesdeSQLite(userId);
            }
        });
    }

    private void cargarDesdeSQLite(String userId) {
        UserSession session = dbHelper.getUserSession(userId);
        if (session != null) {
            edtName.setText(session.getNombre());
            edtEmail.setText(session.getEmail());
            String decryptedAddress = EncryptionHelper.decryptAddress(session.getAddress());
            String decryptedPhone = EncryptionHelper.decryptPhone(session.getPhone());
            edtAddress.setText(decryptedAddress);
            if (!TextUtils.isEmpty(decryptedPhone)) {
                String phoneTemp = decryptedPhone.replace("+", "");
                if (phoneTemp.contains(" ")) {
                    String[] parts = phoneTemp.split(" ", 2);
                    try {
                        int codeInt = Integer.parseInt(parts[0]);
                        countryCodePicker.setCountryForPhoneCode(codeInt);
                        edtPhone.setText(parts[1]);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parseando el prefijo del teléfono desde SQLite.", e);
                        edtPhone.setText(decryptedPhone);
                    }
                } else {
                    edtPhone.setText(decryptedPhone);
                }
            }
            if (!TextUtils.isEmpty(session.getImage())) {
                if (session.getImage().startsWith("http")) {
                    loadImageFromUrl(userImageView, session.getImage());
                } else {
                    decodeBase64Image(session.getImage());
                }
            }
        } else {
            Log.w(TAG, "No se encontró el usuario en SQLite con userId: " + userId);
        }
    }

    private void saveUserData() {
        String name = edtName.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String address = edtAddress.getText().toString().trim();
        String phoneNumber = edtPhone.getText().toString().trim();
        String countryCode = countryCodePicker.getSelectedCountryCode();
        String countryNameCode = countryCodePicker.getSelectedCountryNameCode();
        String phoneToSave = "+" + countryCode + " " + phoneNumber;

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isPhoneValid = false;
        if (!phoneNumber.isEmpty()) {
            String expectedPrefix = "+" + countryCode;
            if (phoneToSave.startsWith(expectedPrefix)) {
                String numericPart = phoneToSave.substring(expectedPrefix.length()).replaceAll("\\s+", "");
                int expectedLength = 0;
                switch (countryNameCode) {
                    case "ES":
                        expectedLength = 9;
                        break;
                    case "US":
                        expectedLength = 10;
                        break;
                    default:
                        expectedLength = numericPart.length();
                        break;
                }
                if (numericPart.length() == expectedLength) {
                    isPhoneValid = true;
                }
            }
        }

        if (!isPhoneValid) {
            Toast.makeText(this, "El número de teléfono no es válido para el país seleccionado", Toast.LENGTH_SHORT).show();
            return;
        }

        String encryptedAddress = address.isEmpty() ? "" : EncryptionHelper.encryptAddress(address);
        String encryptedPhone = phoneToSave.isEmpty() ? "" : EncryptionHelper.encryptPhone(phoneToSave);

        if (encryptedAddress == null) {
            Log.e(TAG, "Encryption de Address devolvió null. Usando cadena vacía.");
            encryptedAddress = "";
        }
        if (encryptedPhone == null) {
            Log.e(TAG, "Encryption de Phone devolvió null. Usando cadena vacía.");
            encryptedPhone = "";
        }

        Log.d(TAG, "Datos encriptados - Address: " + encryptedAddress + ", Phone: " + encryptedPhone);

        String base64Image = convertImageToBase64(userImageView);
        Log.d(TAG, "Imagen convertida a Base64.");

        String loginTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        Log.d(TAG, "Timestamp generado: " + loginTime);

        boolean localSaved = dbHelper.upsertUserSession(
                currentUserId,
                name,
                email,
                loginTime,
                encryptedAddress,
                encryptedPhone,
                base64Image
        );

        if (!localSaved) {
            Toast.makeText(this, "Error al guardar datos en SQLite", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Fallo al guardar en SQLite.");
            return;
        }

        Log.d(TAG, "Datos guardados en SQLite correctamente.");

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        DocumentReference userDocRef = firestore.collection("users").document(currentUserId);
        Map<String, Object> userData = new HashMap<>();
        userData.put("nombre", name);
        userData.put("email", email);
        userData.put("address", encryptedAddress);
        userData.put("phone", encryptedPhone);
        userData.put("image", base64Image);

        userDocRef.set(userData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Datos guardados correctamente en Firestore.");
                    Toast.makeText(EditUser.this, "Datos guardados correctamente en la nube", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al guardar datos en Firestore.", e);
                    Toast.makeText(EditUser.this, "Error al guardar datos en la nube", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Verifica permisos e invoca el diálogo de selección de imagen.
     */
    private void checkImagePermissionsAndShowOptions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE};
        }

        boolean permissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false;
                break;
            }
        }
        if (permissionsGranted) {
            showImageOptionsDialog();
        } else {
            permissionLauncher.launch(permissions);
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
                        case 0:
                            Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                            cameraResult.launch(cameraIntent);
                            break;
                        case 1:
                            galleryLauncher.launch("image/*");
                            break;
                        case 2:
                            showUrlDialog();
                            break;
                    }
                })
                .create()
                .show();
    }

    /**
     * Muestra un diálogo para introducir la URL de una imagen.
     */
    private void showUrlDialog() {
        final EditText input = new EditText(this);
        input.setHint("https://ejemplo.com/foto.png");

        new AlertDialog.Builder(this)
                .setTitle("Introduce la URL de la imagen")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(url)) {
                        externalPhotoUrl = url;
                        loadImageFromUrl(userImageView, url);
                        Log.d(TAG, "URL externa ingresada: " + url);
                    } else {
                        Toast.makeText(this, "URL vacía", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Carga la imagen desde una URI en segundo plano.
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
            Bitmap finalBitmap = bitmap;
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
     * Carga la imagen desde una URL en segundo plano.
     */
    private void loadImageFromUrl(ImageView imageView, String url) {
        if (TextUtils.isEmpty(url)) {
            imageView.setImageResource(R.mipmap.ic_launcher_round);
            return;
        }
        executorService.execute(() -> {
            Bitmap bitmap = null;
            try (InputStream inputStream = new java.net.URL(url).openStream()) {
                bitmap = BitmapFactory.decodeStream(inputStream);
            } catch (IOException e) {
                Log.e(TAG, "Error cargando imagen desde URL", e);
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
     * Convierte un Drawable a Bitmap.
     */
    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) {
            Log.e(TAG, "Drawable nulo.");
            return null;
        }
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            Log.e(TAG, "Dimensiones inválidas del Drawable.");
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    /**
     * Convierte la imagen del ImageView a Base64.
     */
    private String convertImageToBase64(ImageView imageView) {
        Drawable drawable = imageView.getDrawable();
        if (drawable == null) {
            Log.w(TAG, "No hay imagen asignada. Se asigna imagen predeterminada.");
            imageView.setImageResource(R.mipmap.ic_launcher_round);
            drawable = imageView.getDrawable();
        }
        Bitmap bitmap = drawableToBitmap(drawable);
        if (bitmap == null) {
            Log.e(TAG, "Error al convertir Drawable a Bitmap.");
            return "";
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    /**
     * Decodifica una imagen Base64 y la muestra en el ImageView.
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
     * Muestra un diálogo para que el usuario vaya a Ajustes y active los permisos necesarios.
     */
    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permisos necesarios")
                .setMessage(PERMISSION_IMAGE_MESSAGE)
                .setPositiveButton("Ir a Ajustes", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
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