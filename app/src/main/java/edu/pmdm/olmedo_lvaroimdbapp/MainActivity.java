package edu.pmdm.olmedo_lvaroimdbapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
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
            // Asigna el nombre al TextView
            nameTextView.setText(user.getDisplayName());

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

            // Carga la foto de perfil con Glide (si existe)
            if (user.getPhotoUrl() != null) {
                Glide.with(this)
                        .load(user.getPhotoUrl())
                        .into(profileImageView);
            }
        }

        // Cierra sesión al darle al botón
        logOutButton.setOnClickListener(v -> {
            // Cierra sesión en Firebase
            FirebaseAuth.getInstance().signOut();

            // Cierra sesión de Google si el usuario estaba autenticado con Google
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Manejar clics en los ítems del menú
        int id = item.getItemId();
        if (id == R.id.action_editUser) {
            // Iniciar la actividad EditUser
            Intent intent = new Intent(this, EditUser.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}