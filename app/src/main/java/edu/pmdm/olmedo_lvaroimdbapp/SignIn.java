package edu.pmdm.olmedo_lvaroimdbapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.SignInButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import edu.pmdm.olmedo_lvaroimdbapp.models.UserSession;
import edu.pmdm.olmedo_lvaroimdbapp.models.FavoriteDBHelper;
import edu.pmdm.olmedo_lvaroimdbapp.sync.UserSync;

public class SignIn extends AppCompatActivity {
    private static final String TAG = "SignIn";
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> signInLauncher;
    private CallbackManager callbackManager;
    private FirebaseAuth mAuth;
    private EditText editTextEmail, editTextPassword;
    private Button buttonLogin, buttonRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);
        getSupportActionBar().setTitle("Sign in IMDb");

        editTextEmail = findViewById(R.id.editTextNombre);
        editTextPassword = findViewById(R.id.editTextTextPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonRegister = findViewById(R.id.buttonRegister);

        buttonLogin.setOnClickListener(v -> loginWithEmail());
        buttonRegister.setOnClickListener(v -> registerWithEmail());

        if (!checkGooglePlayServices()) {
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        Log.d(TAG, "Firebase Auth initialized: " + (mAuth != null ? "OK" : "Failed"));

        SignInButton signInButton = findViewById(R.id.share_button);
        if (signInButton == null) {
            Log.e(TAG, "Google sign-in button no encontrado");
        } else {
            TextView textView = (TextView) signInButton.getChildAt(0);
            textView.setText("Sign in with Google");
            Log.d(TAG, "Google sign-in button configurado");

            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.client_id))
                    .requestEmail()
                    .build();
            Log.d(TAG, "GoogleSignInOptions creado con el id de cliente: " + getString(R.string.client_id));

            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
            Log.d(TAG, "GoogleSignInClient creado: " + (mGoogleSignInClient != null ? "OK" : "Error"));

            signInLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        try {
                            GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult();
                            handleGoogleSignInResult(account);
                        } catch (Exception e) {
                            Log.e(TAG, "Error al intentar iniciar sesión", e);
                        }
                    } else {
                        Log.e(TAG, "No se reciven datos de Google sign-in intent");
                    }
                } else {
                    Log.e(TAG, "Google sign-in da error. Resultado: " + result.getResultCode());
                }
            });

            signInButton.setOnClickListener(v -> {
                signInWithGoogle();
            });
        }

        callbackManager = CallbackManager.Factory.create();
        Log.d(TAG, "Facebook CallbackManager created: " + (callbackManager != null ? "OK" : "Failed"));

        LoginButton facebookLoginButton = findViewById(R.id.facebook_login_button);
        if (facebookLoginButton == null) {
            Log.e(TAG, "Facebook login button not found");
        } else {
            facebookLoginButton.setPermissions(Arrays.asList("email", "public_profile"));
            Log.d(TAG, "Facebook permissions set");

            facebookLoginButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
                @Override
                public void onSuccess(LoginResult loginResult) {
                    Log.d(TAG, "Facebook Login Success: " + (loginResult.getAccessToken() != null ? loginResult.getAccessToken().getToken() : "Null token"));
                    handleFacebookAccessToken(loginResult.getAccessToken());
                }

                @Override
                public void onCancel() {
                    Toast.makeText(SignIn.this, "Inicio de sesión cancelado", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(FacebookException error) {
                    Toast.makeText(SignIn.this, "Error en inicio de sesión con Facebook", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // Método auxiliar para verificar los servicios de Google Play
    private boolean checkGooglePlayServices() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                googleApiAvailability.getErrorDialog(this, resultCode, 9000).show();
            } else {
                Toast.makeText(this, "This device is not supported for Google Play Services", Toast.LENGTH_SHORT).show();
                finish();
            }
            return false;
        }
        return true;
    }

    // Método auxiliar para iniciar sesión con Google
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        Log.d(TAG, "Launching Google Sign In Intent: " + (signInIntent != null ? "Intent created" : "Failed to create intent"));
        Log.d(TAG, "Sign-in intent details: " + (signInIntent != null ? signInIntent.toString() : "Null intent"));
        if (signInIntent != null) {
            signInLauncher.launch(signInIntent);
        } else {
            Log.e(TAG, "Sign-in intent is null");
        }
    }

    // Método auxiliar para manejar el resultado del inicio de sesión con Google
    private void handleGoogleSignInResult(GoogleSignInAccount account) {
        if (account != null) {
            String idToken = account.getIdToken();
            if (idToken == null) {
                Log.e(TAG, "ID Token is null");
                return;
            }
            AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
            mAuth.signInWithCredential(credential)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                String userId = user.getUid();
                                String name = user.getDisplayName() != null ? user.getDisplayName() : "";
                                String email = user.getEmail() != null ? user.getEmail() : "";
                                String image = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "";

                                // Registrar el tiempo de inicio de sesión
                                registerLoginTime(userId, name, email);

                                // Guardar en Firestore
                                FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                                DocumentReference userDocRef = firestore.collection("users").document(userId);

                                Map<String, Object> userData = new HashMap<>();
                                userData.put("nombre", name);
                                userData.put("email", email);
                                userData.put("address", "");
                                userData.put("phone", "");
                                userData.put("image", image);

                                userDocRef.set(userData, SetOptions.merge())
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "Datos guardados en Firestore.");
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Error al guardar datos en Firestore.", e);
                                        });

                                navigateToMainActivity();
                            }
                        } else {
                            Exception exception = task.getException();
                            if (exception != null) {
                                Toast.makeText(SignIn.this, "Error en autenticación con Firebase: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                            } else {
                                Log.e(TAG, "Unknown error during Firebase authentication");
                            }
                        }
                    });
        } else {
            Log.e(TAG, "Google sign-in failed: account is null");
        }
    }

    // Método auxiliar para autenticar con Facebook
    private void handleFacebookAccessToken(AccessToken token) {
        if (token == null) {
            Log.e(TAG, "Access Token is null");
            return;
        }
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String userId = user.getUid();
                            String name = user.getDisplayName() != null ? user.getDisplayName() : "";
                            String email = user.getEmail() != null ? user.getEmail() : "";
                            String facebookImageUrl = "https://graph.facebook.com/" + userId + "/picture?type=large";

                            registerLoginTime(userId, name, email);

                            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                            DocumentReference userDocRef = firestore.collection("users").document(userId);

                            Map<String, Object> userData = new HashMap<>();
                            userData.put("nombre", name);
                            userData.put("email", email);
                            userData.put("address", "");
                            userData.put("phone", "");
                            userData.put("image", facebookImageUrl);

                            userDocRef.set(userData, SetOptions.merge())
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Datos guardados en Firestore.");
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error al guardar datos en Firestore.", e);
                                    });

                            navigateToMainActivity();
                        }
                    } else {
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthUserCollisionException) {
                            Log.e(TAG, "Error de colisión de cuentas. La cuenta ya existe con otro proveedor.", exception);
                            Toast.makeText(SignIn.this, "La cuenta ya existe con otro proveedor. Inicia sesión con Google para vincular la cuenta.", Toast.LENGTH_LONG).show();
                            LoginManager.getInstance().logOut();
                        } else {
                            Log.e(TAG, "Error en autenticación con Firebase", exception);
                            Toast.makeText(SignIn.this, "Error en autenticación con Firebase: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // Método auxiliar para registrar con email
    private void registerWithEmail() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(SignIn.this, "Registro exitoso", Toast.LENGTH_SHORT).show();

                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String uid = user.getUid();

                            // Registrar el tiempo de inicio de sesión
                            registerLoginTime(uid, "", email);

                            new UserSync(this, uid).syncFromLocalToCloud();
                        }

                        navigateToMainActivity();
                    } else {
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            Toast.makeText(SignIn.this, "Este correo ya está registrado", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(SignIn.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Error: " + task.getException().getMessage());
                        }
                    }
                });
    }

    // Método auxiliar para obtener la fecha y hora actual
    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Madrid"));
        return sdf.format(new Date());
    }

    // Método auxiliar para iniciar sesión con email
    private void loginWithEmail() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String uid = user.getUid();
                            String name = user.getDisplayName() != null ? user.getDisplayName() : "";

                            registerLoginTime(uid, name, email);

                            navigateToMainActivity();
                        }
                    } else {
                        if (task.getException() instanceof FirebaseAuthInvalidUserException) {
                            Toast.makeText(SignIn.this, "Este correo no está registrado. Por favor, regístrate antes de iniciar sesión.", Toast.LENGTH_LONG).show();
                        } else if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                            Toast.makeText(SignIn.this, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(SignIn.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // Método auxiliar para navegar a MainActivity
    private void navigateToMainActivity() {
        Log.d(TAG, "Navigating to MainActivity");
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String uid = user.getUid();
            String name = (user.getDisplayName() != null) ? user.getDisplayName() : "";
            String email = (user.getEmail() != null) ? user.getEmail() : "";

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("Europe/Madrid"));
            String loginTime = sdf.format(new Date());

            String address = null;
            String phone = null;
            String image = null;

            if (user.getPhotoUrl() != null) {
                image = user.getPhotoUrl().toString();
            }

            FavoriteDBHelper dbHelper = new FavoriteDBHelper(this);
            boolean result = dbHelper.upsertUserSession(uid, name, email, loginTime, address, phone, image);

            if (result) {
                Log.d(TAG, "User session saved successfully in DB.");
            } else {
                Log.e(TAG, "Failed to save user session in DB.");
            }

            Intent intent = new Intent(SignIn.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    // Método auxiliar para registrar el login
    private void registerLoginTime(String userId, String name, String email) {
        String currentTime = getCurrentTime();
        FavoriteDBHelper dbHelper = FavoriteDBHelper.getInstance(this);

        // Crear o actualizar la sesión del usuario
        UserSession user = dbHelper.getUser(userId);
        if (user == null) {
            user = new UserSession(userId, name, email, currentTime, "", "", "", "");
            dbHelper.addUser(user);
        } else {
            user.setLoginTime(currentTime);
            dbHelper.addUser(user);
        }

        Log.d(TAG, "Login registrado para usuario: " + userId);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            String name = (currentUser.getDisplayName() != null) ? currentUser.getDisplayName() : "";
            String email = (currentUser.getEmail() != null) ? currentUser.getEmail() : "";

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("Europe/Madrid"));
            String loginTime = sdf.format(new Date());

            FavoriteDBHelper dbHelper = FavoriteDBHelper.getInstance(this);
            boolean result = dbHelper.upsertUserSession(uid, name, email, loginTime, "", "", "");

            if (result) {
                Log.d(TAG, "User session saved successfully in SQLite.");
            } else {
                Log.e(TAG, "Failed to save user session in SQLite.");
            }

            new UserSync(this, uid).syncFromLocalToCloud();
            navigateToMainActivity();
        } else {
            Log.d(TAG, "No user currently signed in");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                for (String key : extras.keySet()) {
                    Log.d(TAG, "Extra: " + key + " = " + extras.get(key));
                }
            }
        }
        if (callbackManager != null) {
            callbackManager.onActivityResult(requestCode, resultCode, data);
        } else {
            Log.e(TAG, "CallbackManager is null");
        }
    }
}