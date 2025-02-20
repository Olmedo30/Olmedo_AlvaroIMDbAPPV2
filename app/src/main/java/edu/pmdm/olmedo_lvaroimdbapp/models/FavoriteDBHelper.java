package edu.pmdm.olmedo_lvaroimdbapp.models;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clase para manejar la base de datos "favorites.db" que incluye
 * la tabla "favorites" y la tabla "user_sessions".
 */
public class FavoriteDBHelper extends SQLiteOpenHelper {

    private static final String TAG = "FavoriteDBHelper";
    private static FavoriteDBHelper instance;
    private final FirebaseFirestore firestore;
    // Nombre y versión de la base de datos
    private static final String DATABASE_NAME = "favorites.db";
    private static final int DATABASE_VERSION = 4; // Ajustar si necesitas cambios de esquema

    // Tabla "favorites"
    private static final String TABLE_FAVORITES = "favorites";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_MOVIE_ID = "movie_id";
    private static final String COLUMN_IMAGE_URL = "image_url";
    private static final String COLUMN_TITLE = "title";

    // Consulta para crear la tabla "favorites"
    private static final String CREATE_TABLE_FAVORITES =
            "CREATE TABLE " + TABLE_FAVORITES + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_USER_ID + " TEXT NOT NULL, " +
                    COLUMN_MOVIE_ID + " TEXT NOT NULL, " +
                    COLUMN_IMAGE_URL + " TEXT NOT NULL, " +
                    COLUMN_TITLE + " TEXT NOT NULL);";

    // Tabla "user_sessions"
    private static final String TABLE_USER_SESSIONS = "user_sessions";
    private static final String COLUMN_SESSION_USER_ID = "user_id";
    private static final String COLUMN_NOMBRE = "nombre";
    private static final String COLUMN_EMAIL = "email";
    private static final String COLUMN_LOGIN_TIME = "login_time";
    private static final String COLUMN_LOGOUT_TIME = "logout_time";
    private static final String COLUMN_ADDRESS = "address";
    private static final String COLUMN_PHONE = "phone";
    private static final String COLUMN_IMAGE = "image";

    // Consulta para crear la tabla "user_sessions"
    private static final String CREATE_TABLE_USER_SESSIONS =
            "CREATE TABLE " + TABLE_USER_SESSIONS + " (" +
                    COLUMN_SESSION_USER_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_NOMBRE + " TEXT NOT NULL, " +
                    COLUMN_EMAIL + " TEXT NOT NULL, " +
                    COLUMN_LOGIN_TIME + " TEXT, " +
                    COLUMN_LOGOUT_TIME + " TEXT, " +
                    COLUMN_ADDRESS + " TEXT, " +
                    COLUMN_PHONE + " TEXT, " +
                    COLUMN_IMAGE + " TEXT" +
                    ");";

    public static synchronized FavoriteDBHelper getInstance(Context context) {
        if (instance == null) {
            instance = new FavoriteDBHelper(context.getApplicationContext());
        }
        return instance;
    }

    public FavoriteDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        firestore = FirebaseFirestore.getInstance();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Creando tablas en la base de datos...");
        db.execSQL(CREATE_TABLE_FAVORITES);
        db.execSQL(CREATE_TABLE_USER_SESSIONS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Actualizando base de datos de la versión " + oldVersion + " a " + newVersion);

        // Eliminar las tablas existentes
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER_SESSIONS);

        // Crear las tablas de nuevo
        onCreate(db);
    }

    public boolean upsertUserSession(String userId,
                                     String nombre,
                                     String email,
                                     String loginTime,
                                     String address,
                                     String phone,
                                     String image) {

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SESSION_USER_ID, userId);
        values.put(COLUMN_NOMBRE, nombre);
        values.put(COLUMN_EMAIL, email);
        values.put(COLUMN_LOGIN_TIME, loginTime);
        // logout_time no lo estamos pasando, lo podrías actualizar si lo necesitas
        values.put(COLUMN_ADDRESS, address);
        values.put(COLUMN_PHONE, phone);
        values.put(COLUMN_IMAGE, image);

        // insertWithOnConflict con CONFLICT_IGNORE
        long result = db.insertWithOnConflict(
                TABLE_USER_SESSIONS,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
        if (result == -1) {
            // No se insertó => ya existe userId => hacemos update
            int rows = db.update(
                    TABLE_USER_SESSIONS,
                    values,
                    COLUMN_SESSION_USER_ID + "=?",
                    new String[]{userId}
            );
            return rows > 0;
        }
        return true;
    }

    public UserSession getUserSession(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_USER_SESSIONS,
                null,
                COLUMN_SESSION_USER_ID + "=?",
                new String[]{userId},
                null,
                null,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            String nombre     = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOMBRE));
            String email      = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL));
            String loginTime  = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOGIN_TIME));
            String logoutTime = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOGOUT_TIME));
            String address    = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADDRESS));
            String phone      = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHONE));
            String image      = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE));
            cursor.close();
            return new UserSession(
                    userId,
                    nombre,
                    email,
                    loginTime,
                    logoutTime,
                    address,
                    phone,
                    image
            );
        }

        if (cursor != null) {
            cursor.close();
        }
        return null; // Si no encontró nada
    }

    public boolean insertFavorite(String userId, String movieId, String imageUrl, String title) {
        // Verifica si ya es favorito
        if (isFavorite(userId, movieId)) {
            return false;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_USER_ID, userId);
        values.put(COLUMN_MOVIE_ID, movieId);
        values.put(COLUMN_IMAGE_URL, imageUrl);
        values.put(COLUMN_TITLE, title);
        long result = db.insert(TABLE_FAVORITES, null, values);
        return result != -1;
    }

    public boolean insertFavoriteToCloud(String userId, String movieId, String imageUrl, String title) {
        if (firestore == null) {
            Log.e(TAG, "Firestore no está inicializado");
            return false;
        }

        DocumentReference movieRef = firestore.collection("favorites")
                .document(userId)
                .collection("movies")
                .document(movieId);

        Map<String, Object> movieData = new HashMap<>();
        movieData.put("movie_id", movieId);
        movieData.put("poster", imageUrl);
        movieData.put("title", title);

        movieRef.set(movieData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Película añadida a la nube: " + movieId))
                .addOnFailureListener(e -> Log.e(TAG, "Error al añadir la película a la nube: " + movieId, e));

        return true; // Retorna true si la operación fue exitosa
    }

    public List<Movie> getAllMovies(String userId) {
        List<Movie> movies = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_FAVORITES + " WHERE " + COLUMN_USER_ID + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{userId});
        if (cursor.moveToFirst()) {
            do {
                String movieId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MOVIE_ID));
                String imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_URL));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                Movie movie = new Movie(movieId, title, imageUrl);
                movies.add(movie);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return movies;
    }

    public boolean deleteFavorite(String userId, String movieId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(
                TABLE_FAVORITES,
                COLUMN_USER_ID + "=? AND " + COLUMN_MOVIE_ID + "=?",
                new String[]{userId, movieId}
        );
        return result > 0;
    }

    public boolean isFavorite(String userId, String movieId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_FAVORITES,
                null,
                COLUMN_USER_ID + "=? AND " + COLUMN_MOVIE_ID + "=?",
                new String[]{userId, movieId},
                null,
                null,
                null
        );
        boolean exists = (cursor.getCount() > 0);
        cursor.close();
        return exists;
    }

    public boolean updateUserSession(String userId,
                                     String nombre,
                                     String email,
                                     String loginTime,
                                     String address,
                                     String phone,
                                     String image) {

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NOMBRE, nombre);
        values.put(COLUMN_EMAIL, email);
        values.put(COLUMN_LOGIN_TIME, loginTime);
        values.put(COLUMN_ADDRESS, address);
        values.put(COLUMN_PHONE, phone);
        values.put(COLUMN_IMAGE, image);

        // Actualiza la fila que coincida con el user_id
        int rowsAffected = db.update(TABLE_USER_SESSIONS,
                values,
                COLUMN_SESSION_USER_ID + "=?",
                new String[]{userId});

        return (rowsAffected > 0);  // true si se actualizó al menos 1 fila
    }

    public void deleteAllFavorites(String userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int deletedRows = db.delete("favorites", "user_id=?", new String[]{userId});
        db.close();
        Log.d("FavoriteDBHelper", "Se eliminaron " + deletedRows + " favoritos del usuario: " + userId);
    }
}
