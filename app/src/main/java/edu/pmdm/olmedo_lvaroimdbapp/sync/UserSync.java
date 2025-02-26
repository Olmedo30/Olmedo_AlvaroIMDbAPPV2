package edu.pmdm.olmedo_lvaroimdbapp.sync;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import edu.pmdm.olmedo_lvaroimdbapp.models.FavoriteDBHelper;
import edu.pmdm.olmedo_lvaroimdbapp.models.UserSession;

public class UserSync {

    private static final String TAG = "UsersSync";
    private final FirebaseFirestore firestore;
    private final FavoriteDBHelper dbHelper;
    private final String userId;

    public UserSync(Context context, String userId) {
        this.firestore = FirebaseFirestore.getInstance();
        this.dbHelper = FavoriteDBHelper.getInstance(context);
        this.userId = userId;
    }

    /**
     * Sincroniza los datos del usuario desde SQLite a Firestore.
     */
    public void syncFromLocalToCloud() {
        UserSession userSession = dbHelper.getUserSession(userId);
        if (userSession == null) {
            Log.w(TAG, "No se encontraron datos locales para el usuario: " + userId);
            return;
        }

        DocumentReference userDocRef = firestore.collection("users").document(userId);
        Map<String, Object> userData = new HashMap<>();

        if (userSession.getNombre() != null && !userSession.getNombre().isEmpty()) {
            userData.put("nombre", userSession.getNombre());
        }
        if (userSession.getEmail() != null && !userSession.getEmail().isEmpty()) {
            userData.put("email", userSession.getEmail());
        }
        if (userSession.getAddress() != null && !userSession.getAddress().isEmpty()) {
            userData.put("address", userSession.getAddress());
        }
        if (userSession.getPhone() != null && !userSession.getPhone().isEmpty()) {
            userData.put("phone", userSession.getPhone());
        }
        if (userSession.getImage() != null && !userSession.getImage().isEmpty()) {
            userData.put("image", userSession.getImage());
        }
        if (userSession.getLoginTime() != null && !userSession.getLoginTime().isEmpty()) {
            userData.put("login_time", userSession.getLoginTime());
        }
        if (userSession.getLogoutTime() != null && !userSession.getLogoutTime().isEmpty()) {
            userData.put("logout_time", userSession.getLogoutTime());
        }

        if (!userData.isEmpty()) {
            userDocRef.set(userData, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Datos del usuario sincronizados con Firestore."))
                    .addOnFailureListener(e -> Log.e(TAG, "Error al sincronizar datos del usuario con Firestore.", e));
        } else {
            Log.w(TAG, "No hay datos válidos para sincronizar con Firestore.");
        }
    }

    /**
     * Sincroniza el registro de actividad del usuario (activity_log) entre SQLite y Firestore.
     */
    public void syncActivityLog() {
        DocumentReference userDocRef = firestore.collection("users").document(userId);
        UserSession userSession = dbHelper.getUserSession(userId);
        if (userSession == null) {
            Log.w(TAG, "No se encontraron datos locales para el usuario: " + userId);
            return;
        }

        Map<String, Object> activityLogEntry = new HashMap<>();
        activityLogEntry.put("login_time", userSession.getLoginTime());
        activityLogEntry.put("logout_time", userSession.getLogoutTime());

        userDocRef.update("activity_log", FieldValue.arrayUnion(activityLogEntry))
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Registro de actividad añadido a Firestore."))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al añadir registro de actividad a Firestore.", e);

                    Map<String, Object> userData = new HashMap<>();
                    userData.put("user_id", userId);
                    userData.put("activity_log", Arrays.asList(activityLogEntry));
                    userDocRef.set(userData, SetOptions.merge())
                            .addOnSuccessListener(aVoid2 -> Log.d(TAG, "Documento del usuario creado con activity_log."))
                            .addOnFailureListener(e2 -> Log.e(TAG, "Error al crear documento del usuario.", e2));
                });
    }
}