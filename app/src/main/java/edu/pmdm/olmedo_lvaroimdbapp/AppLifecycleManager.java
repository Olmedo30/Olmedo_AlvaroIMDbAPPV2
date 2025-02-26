package edu.pmdm.olmedo_lvaroimdbapp;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import edu.pmdm.olmedo_lvaroimdbapp.models.FavoriteDBHelper;
import edu.pmdm.olmedo_lvaroimdbapp.models.UserSession;
import edu.pmdm.olmedo_lvaroimdbapp.sync.UserSync;

public class AppLifecycleManager extends Application implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "AppLifecycleManager";
    private int activityReferences = 0;
    private boolean isActivityChangingConfigurations = false;
    private boolean isLoginTimeUpdated = false;
    private String lastUserId = null;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        lastUserId = prefs.getString("LAST_USER_ID", null);
        if (lastUserId != null) {
            forzarLogOut(lastUserId);
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {}

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            String userId = firebaseUser.getUid();

            if (!userId.equals(lastUserId) || !isLoginTimeUpdated) {
                actualizarLogIn(userId);
                lastUserId = userId;
                isLoginTimeUpdated = true;
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                prefs.edit().putString("LAST_USER_ID", userId).apply();
            }
        }

        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            Log.d(TAG, "App en primer plano.");
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {}

    @Override
    public void onActivityPaused(@NonNull Activity activity) {}

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        Log.d(TAG, activity.getLocalClassName() + " - onActivityStopped");
        isActivityChangingConfigurations = activity.isChangingConfigurations();

        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null) {
                actualizarLogout(firebaseUser.getUid());
            }
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {}

    // Registra el login del usuario.
    private void actualizarLogIn(String userId) {
        String currentTime = obtenerTiempo();
        FavoriteDBHelper dbHelper = FavoriteDBHelper.getInstance(this);
        UserSession user = dbHelper.getUser(userId);

        if (user == null) {
            user = new UserSession(userId, "", "", currentTime, "", "", "", "");
            dbHelper.addUser(user);
        } else {
            user.setLoginTime(currentTime);
            dbHelper.addUser(user);
        }

        Log.d(TAG, "Login registrado para usuario: " + userId);
        new UserSync(this, userId).syncActivityLog();
    }

    // Registra el logout del usuario.
    private void actualizarLogout(String userId) {
        String currentTime = obtenerTiempo();
        FavoriteDBHelper dbHelper = FavoriteDBHelper.getInstance(this);
        UserSession user = dbHelper.getUser(userId);

        if (user != null) {
            user.setLogoutTime(currentTime);
            dbHelper.addUser(user);
            Log.d(TAG, "Logout registrado para usuario: " + userId);
            new UserSync(this, userId).syncActivityLog();
        }
        isLoginTimeUpdated = false;
    }

    // Registra el cierre forzado del usuario.
    private void forzarLogOut(String userId) {
        Log.d(TAG, "Detectado cierre forzado. Registrando logout para " + userId);
        actualizarLogout(userId);
    }

    // Devuelve la fecha y hora actual en formato "yyyy-MM-dd HH:mm:ss"
    private String obtenerTiempo() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
}