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

        NavigationView navigationView = binding.navView;
        Button logOutButton = navigationView.getHeaderView(0).findViewById(R.id.buttonLogOut);

        ImageView profileImageView = navigationView.getHeaderView(0).findViewById(R.id.imageView);
        TextView emailTextView = navigationView.getHeaderView(0).findViewById(R.id.textView);
        TextView nameTextView = navigationView.getHeaderView(0).findViewById(R.id.nametextView);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String userId = user.getUid();

            boolean isFacebookUser = false;
            for (UserInfo provider : user.getProviderData()) {
                if ("facebook.com".equals(provider.getProviderId())) {
                    isFacebookUser = true;
                    break;
                }
            }

            if (isFacebookUser) {
                emailTextView.setText("Conectado con Facebook");
            } else {
                emailTextView.setText(user.getEmail());
            }

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
            Intent intent = new Intent(this, EditUser.class);
            editUserLauncher.launch(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Carga el nombre de usuario desde SQLite
    private void loadUserNameFromDB(String userId) {
        FavoriteDBHelper dbHelper = new FavoriteDBHelper(this);
        UserSession userSession = dbHelper.getUserSession(userId);

        NavigationView navigationView = binding.navView;
        TextView nameTextView = navigationView.getHeaderView(0).findViewById(R.id.nametextView);

        if (userSession != null && userSession.getNombre() != null && !userSession.getNombre().isEmpty()) {
            nameTextView.setText(userSession.getNombre());
        } else {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                nameTextView.setText(user.getDisplayName());
            }
        }
    }

    // Carga la foto de perfil desde SQLite
    private void loadUserImageFromDB(String userId) {
        FavoriteDBHelper dbHelper = new FavoriteDBHelper(this);
        UserSession userSession = dbHelper.getUserSession(userId);

        NavigationView navigationView = binding.navView;
        ImageView profileImageView = navigationView.getHeaderView(0).findViewById(R.id.imageView);

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
        } else {
            profileImageView.setImageResource(R.mipmap.ic_launcher_round);
        }
    }
}