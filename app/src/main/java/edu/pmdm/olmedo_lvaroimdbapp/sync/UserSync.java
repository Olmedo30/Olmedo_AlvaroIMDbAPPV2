package edu.pmdm.olmedo_lvaroimdbapp.sync;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

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
     * Sincroniza los datos del usuario desde Firestore a SQLite.
     */
    public void syncFromCloudToLocal(Runnable onComplete) {
        DocumentReference userDocRef = firestore.collection("users").document(userId);
        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    // Obtener datos del usuario desde Firestore
                    String nombre = document.getString("nombre");
                    String email = document.getString("email");
                    String address = document.getString("address");
                    String phone = document.getString("phone");
                    String image = document.getString("image");
                    String loginTime = document.getString("login_time");
                    String logoutTime = document.getString("logout_time");

                    // Actualizar o insertar el usuario en SQLite
                    dbHelper.upsertUserSession(
                            userId,
                            nombre != null ? nombre : "",
                            email != null ? email : "",
                            loginTime != null ? loginTime : "",
                            address != null ? address : "",
                            phone != null ? phone : "",
                            image != null ? image : ""
                    );

                    Log.d(TAG, "Datos del usuario sincronizados desde Firestore.");
                } else {
                    Log.d(TAG, "No hay datos en Firestore para este usuario: " + userId);
                }
            } else {
                Log.e(TAG, "Error al obtener datos desde Firestore.", task.getException());
            }

            if (onComplete != null) {
                onComplete.run();
            }
        });
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

        // Validar y agregar campos solo si no están vacíos
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

        // Solo sincronizar si hay datos válidos para actualizar
        if (!userData.isEmpty()) {
            userDocRef.set(userData, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Datos del usuario sincronizados con Firestore."))
                    .addOnFailureListener(e -> Log.e(TAG, "Error al sincronizar datos del usuario con Firestore.", e));
        } else {
            Log.w(TAG, "No hay datos válidos para sincronizar con Firestore.");
        }
    }
    /**
     * Elimina el usuario de Firestore y también lo elimina de SQLite.
     */
    public void removeUserFromCloud() {
        DocumentReference userDocRef = firestore.collection("users").document(userId);

        // Eliminar el usuario de Firestore
        userDocRef.delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Usuario eliminado de Firestore: " + userId);
                    // También eliminar el usuario localmente
                    dbHelper.deleteUserSession(userId);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error al eliminar usuario de Firestore: " + userId, e));
    }

    /**
     * Sincroniza el registro de actividad del usuario (activity_log) entre SQLite y Firestore.
     */
    public void syncActivityLog() {
        DocumentReference userDocRef = firestore.collection("users").document(userId);

        // Leer datos locales
        UserSession userSession = dbHelper.getUserSession(userId);
        if (userSession == null) {
            Log.w(TAG, "No se encontraron datos locales para el usuario: " + userId);
            return;
        }

        // Crear un nuevo registro de actividad
        Map<String, Object> activityLogEntry = new HashMap<>();
        activityLogEntry.put("login_time", userSession.getLoginTime());
        activityLogEntry.put("logout_time", userSession.getLogoutTime());

        // Añadir el nuevo registro a la lista 'activity_log' en Firestore
        userDocRef.update("activity_log", FieldValue.arrayUnion(activityLogEntry))
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Registro de actividad añadido a Firestore."))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al añadir registro de actividad a Firestore.", e);

                    // Si falla, intentar crear el documento del usuario con 'activity_log'
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("user_id", userId);
                    userData.put("activity_log", Arrays.asList(activityLogEntry)); // Crear lista inicial
                    userDocRef.set(userData, SetOptions.merge())
                            .addOnSuccessListener(aVoid2 -> Log.d(TAG, "Documento del usuario creado con activity_log."))
                            .addOnFailureListener(e2 -> Log.e(TAG, "Error al crear documento del usuario.", e2));
                });
    }

    private void updateLogoutTime(String userId) {
        // Obtener el tiempo actual
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Madrid"));
        String logoutTime = sdf.format(new Date());

        // Referencia al documento del usuario en Firestore
        DocumentReference userDocRef = FirebaseFirestore.getInstance().collection("users").document(userId);

        // Obtener el último registro de activity_log
        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    List<Map<String, Object>> activityLog = (List<Map<String, Object>>) document.get("activity_log");

                    if (activityLog != null && !activityLog.isEmpty()) {
                        // Obtener el último registro
                        Map<String, Object> lastEntry = activityLog.get(activityLog.size() - 1);

                        // Solo actualizar si el logout_time está vacío
                        if (lastEntry.get("logout_time") == null || lastEntry.get("logout_time").toString().isEmpty()) {
                            lastEntry.put("logout_time", logoutTime);

                            // Actualizar el documento en Firestore
                            userDocRef.update("activity_log", activityLog)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Logout time updated in Firestore.");
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error updating logout time in Firestore.", e);
                                    });
                        }
                    }
                }
            } else {
                Log.e(TAG, "Error getting user document from Firestore.", task.getException());
            }
        });
    }
}