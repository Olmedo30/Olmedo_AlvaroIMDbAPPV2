package edu.pmdm.olmedo_lvaroimdbapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
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
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.GoogleAuthProvider;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import edu.pmdm.olmedo_lvaroimdbapp.models.FavoriteDBHelper;

public class SignIn extends AppCompatActivity {
    private static final String TAG = "SignIn";

    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> signInLauncher;
    private CallbackManager callbackManager;
    private FirebaseAuth mAuth;
    private AuthCredential pendingFacebookCredential = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);
        getSupportActionBar().setTitle("Sign in IMDb");

        if (!checkGooglePlayServices()) {
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        //Log.d(TAG, "Firebase Auth initialized: " + (mAuth != null ? "OK" : "Failed"));

        SignInButton signInButton = findViewById(R.id.share_button);
        if (signInButton == null) {
            Log.e(TAG, "Google sign-in button no encontrado");
        } else {
            TextView textView = (TextView) signInButton.getChildAt(0);
            textView.setText("Sign in with Google");
            //Log.d(TAG, "Google sign-in button configurado");

            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.client_id))
                    .requestEmail()
                    .build();
            //Log.d(TAG, "GoogleSignInOptions creado con el id de cliente: " + getString(R.string.client_id));

            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
            //Log.d(TAG, "GoogleSignInClient creado: " + (mGoogleSignInClient != null ? "OK" : "Error"));

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
                    Log.d(TAG, "Inicio de sesión con Facebook cancelado");
                    Toast.makeText(SignIn.this, "Inicio de sesión cancelado", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(FacebookException error) {
                    Log.e(TAG, "Error en inicio de sesión con Facebook", error);
                    Toast.makeText(SignIn.this, "Error en inicio de sesión con Facebook", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

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

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        //Log.d(TAG, "Launching Google Sign In Intent: " + (signInIntent != null ? "Intent created" : "Failed to create intent"));
        //Log.d(TAG, "Sign-in intent details: " + (signInIntent != null ? signInIntent.toString() : "Null intent"));
        if (signInIntent != null) {
            signInLauncher.launch(signInIntent);
        } else {
            Log.e(TAG, "Sign-in intent is null");
        }
    }

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
                            if (pendingFacebookCredential != null) {
                                //Log.d(TAG, "Existe una credencial de Facebook pendiente. Se intenta vincular.");
                                FirebaseUser user = mAuth.getCurrentUser();
                                if (user != null) {
                                    user.linkWithCredential(pendingFacebookCredential)
                                            .addOnCompleteListener(linkTask -> {
                                                if (linkTask.isSuccessful()) {
                                                    //Log.d(TAG, "Cuenta de Facebook vinculada exitosamente.");
                                                    pendingFacebookCredential = null;
                                                    navigateToMainActivity();
                                                } else {
                                                    //Log.e(TAG, "Error al vincular la cuenta de Facebook", linkTask.getException());
                                                    Toast.makeText(SignIn.this, "Error al vincular la cuenta de Facebook: " + linkTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                }
                            } else {
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

    private void handleFacebookAccessToken(AccessToken token) {
        if (token == null) {
            Log.e(TAG, "Access Token is null");
            return;
        }
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Facebook Sign In Success");
                        navigateToMainActivity();
                    } else {
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthUserCollisionException) {
                            Log.e(TAG, "Error de colisión de cuentas. La cuenta ya existe con otro proveedor.", exception);
                            Toast.makeText(SignIn.this, "La cuenta ya existe con otro proveedor. Inicia sesión con Google para vincular la cuenta.", Toast.LENGTH_LONG).show();
                            pendingFacebookCredential = credential;
                        } else {
                            Log.e(TAG, "Error en autenticación con Firebase", exception);
                            Toast.makeText(SignIn.this, "Error en autenticación con Firebase: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

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
            FavoriteDBHelper dbHelper = new FavoriteDBHelper(this);
            boolean result = dbHelper.upsertUserSession(uid, name, email, loginTime);
            if (result) {
                Log.d(TAG, "User session saved successfully in DB.");
            } else {
                Log.e(TAG, "Failed to save user session in DB.");
            }
        }
        Intent intent = new Intent(SignIn.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            if (pendingFacebookCredential != null) {
                Log.d(TAG, "Intentando vincular la credencial de Facebook pendiente.");
                currentUser.linkWithCredential(pendingFacebookCredential)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "Cuenta de Facebook vinculada exitosamente en onStart.");
                                pendingFacebookCredential = null;
                                navigateToMainActivity();
                            } else {
                                Log.e(TAG, "Error al vincular la cuenta de Facebook en onStart", task.getException());
                            }
                        });
            } else {
                navigateToMainActivity();
            }
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