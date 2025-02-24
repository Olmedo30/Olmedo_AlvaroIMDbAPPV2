package edu.pmdm.olmedo_lvaroimdbapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.bumptech.glide.Glide;
import com.facebook.AccessToken;
import com.facebook.login.LoginManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import edu.pmdm.olmedo_lvaroimdbapp.databinding.ActivityMainBinding;
import edu.pmdm.olmedo_lvaroimdbapp.models.FavoriteDBHelper;
import edu.pmdm.olmedo_lvaroimdbapp.models.UserSession;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.appBarMain.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setOpenableLayout(binding.drawerLayout)
                .build();
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // Configuración del Header en NavigationView
        NavigationView navigationView = binding.navView;
        Button logOutButton = navigationView.getHeaderView(0).findViewById(R.id.buttonLogOut);

        // Referencias a la foto y texto del Header
        ImageView profileImageView = navigationView.getHeaderView(0).findViewById(R.id.imageView);
        TextView emailTextView = navigationView.getHeaderView(0).findViewById(R.id.textView);
        TextView nameTextView = navigationView.getHeaderView(0).findViewById(R.id.nametextView);

        // Obtenemos el usuario logueado en Firebase
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String userId = user.getUid();

            // Verifica si el usuario inició sesión con Facebook
            boolean isFacebookUser = false;
            for (UserInfo provider : user.getProviderData()) {
                if ("facebook.com".equals(provider.getProviderId())) {
                    isFacebookUser = true;
                    break;
                }
            }

            // Muestra "Conectado con Facebook" si el usuario se autenticó con Facebook, de lo contrario muestra el email
            if (isFacebookUser) {
                emailTextView.setText("Conectado con Facebook");
            } else {
                emailTextView.setText(user.getEmail());
            }

            // Carga la foto de perfil
            FavoriteDBHelper dbHelper = new FavoriteDBHelper(this);
            UserSession userSession = dbHelper.getUserSession(userId);
            if (userSession != null && userSession.getImage() != null && !userSession.getImage().isEmpty()) {
                if (userSession.getImage().startsWith("http")) {
                    Glide.with(this)
                            .load(userSession.getImage())
                            .into(profileImageView);
                } else {
                    byte[] decodedBytes = Base64.decode(userSession.getImage(), Base64.DEFAULT);
                    Glide.with(this)
                            .load(decodedBytes)
                            .into(profileImageView);
                }
            } else if (user.getPhotoUrl() != null) {
                Glide.with(this)
                        .load(user.getPhotoUrl())
                        .into(profileImageView);
            } else {
                profileImageView.setImageResource(R.mipmap.ic_launcher_round);
            }
        }

        // Cierra sesión al darle al botón
        logOutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(MainActivity.this, GoogleSignInOptions.DEFAULT_SIGN_IN);
            googleSignInClient.signOut().addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    System.out.println("Sesión de Google cerrada exitosamente.");
                } else {
                    System.out.println("Error cerrando sesión de Google.");
                    task.getException().printStackTrace();
                }
            });
            if (AccessToken.getCurrentAccessToken() != null) {
                LoginManager.getInstance().logOut();
                System.out.println("Sesión de Facebook cerrada exitosamente.");
            }
            Intent intent = new Intent(MainActivity.this, SignIn.class);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            loadUserNameFromDB(user.getUid());
            loadUserImageFromDB(user.getUid());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration) || super.onSupportNavigateUp();
    }

    private ActivityResultLauncher<Intent> editUserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Recargar el nombre desde SQLite
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        loadUserNameFromDB(user.getUid());
                    }
                }
            }
    );

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_editUser) {
            // Iniciar la actividad EditUser
            Intent intent = new Intent(this, EditUser.class);
            editUserLauncher.launch(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadUserNameFromDB(String userId) {
        // Cargar datos desde SQLite
        FavoriteDBHelper dbHelper = new FavoriteDBHelper(this);
        UserSession userSession = dbHelper.getUserSession(userId);

        // Referencia al TextView del nombre en el Header
        NavigationView navigationView = binding.navView;
        TextView nameTextView = navigationView.getHeaderView(0).findViewById(R.id.nametextView);

        // Asigna el nombre al TextView
        if (userSession != null && userSession.getNombre() != null && !userSession.getNombre().isEmpty()) {
            // Priorizar el nombre de SQLite si existe
            nameTextView.setText(userSession.getNombre());
        } else {
            // Usar el nombre de Firebase si no hay nombre en SQLite
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                nameTextView.setText(user.getDisplayName());
            }
        }
    }

    private void loadUserImageFromDB(String userId) {
        // Cargar datos desde SQLite
        FavoriteDBHelper dbHelper = new FavoriteDBHelper(this);
        UserSession userSession = dbHelper.getUserSession(userId);

        // Referencia al ImageView del Header
        NavigationView navigationView = binding.navView;
        ImageView profileImageView = navigationView.getHeaderView(0).findViewById(R.id.imageView);

        // Asigna la imagen al ImageView
        if (userSession != null && userSession.getImage() != null && !userSession.getImage().isEmpty()) {
            if (userSession.getImage().startsWith("http")) {
                Glide.with(this)
                        .load(userSession.getImage())
                        .into(profileImageView);
            } else {
                // Decodificar Base64 y cargar la imagen
                byte[] decodedBytes = Base64.decode(userSession.getImage(), Base64.DEFAULT);
                Glide.with(this)
                        .load(decodedBytes)
                        .into(profileImageView);
            }
        } else {
            // Si no hay imagen ni en SQLite ni en Firebase, usar una imagen predeterminada
            profileImageView.setImageResource(R.mipmap.ic_launcher_round);
        }
    }
}