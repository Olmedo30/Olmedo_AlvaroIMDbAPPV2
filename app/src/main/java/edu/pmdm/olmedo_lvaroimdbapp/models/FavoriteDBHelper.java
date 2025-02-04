package edu.pmdm.olmedo_lvaroimdbapp.models;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class FavoriteDBHelper extends SQLiteOpenHelper {

    // Nombre y versión de la base de datos (se incrementa la versión al agregar la nueva tabla)
    private static final String DATABASE_NAME = "favorites.db";
    private static final int DATABASE_VERSION = 2; // Versión incrementada

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

    private static final String TABLE_USER_SESSIONS = "user_sessions";
    private static final String COLUMN_SESSION_USER_ID = "user_id";
    private static final String COLUMN_NOMBRE = "nombre";
    private static final String COLUMN_EMAIL = "email";
    private static final String COLUMN_LOGIN_TIME = "login_time";
    private static final String COLUMN_LOGOUT_TIME = "logout_time";

    // Consulta para crear la tabla "user_sessions"
    private static final String CREATE_TABLE_USER_SESSIONS =
            "CREATE TABLE " + TABLE_USER_SESSIONS + " (" +
                    COLUMN_SESSION_USER_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_NOMBRE + " TEXT NOT NULL, " +
                    COLUMN_EMAIL + " TEXT NOT NULL, " +
                    COLUMN_LOGIN_TIME + " TEXT, " +
                    COLUMN_LOGOUT_TIME + " TEXT" +
                    ");";

    public FavoriteDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Se crea la base de datos con ambas tablas
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_FAVORITES);
        db.execSQL(CREATE_TABLE_USER_SESSIONS);
    }

    // Se actualiza la base de datos (en este ejemplo se eliminan las tablas y se vuelven a crear)
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Nota: En producción se recomienda migrar datos en lugar de borrarlos.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER_SESSIONS);
        onCreate(db);
    }

    // Métodos para la tabla "favorites"
    public boolean insertFavorite(String userId, String movieId, String imageUrl, String title) {
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
        int result = db.delete(TABLE_FAVORITES, COLUMN_USER_ID + "=? AND " + COLUMN_MOVIE_ID + "=?",
                new String[]{userId, movieId});
        return result > 0;
    }

    public boolean isFavorite(String userId, String movieId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_FAVORITES, null,
                COLUMN_USER_ID + "=? AND " + COLUMN_MOVIE_ID + "=?",
                new String[]{userId, movieId}, null, null, null);
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public boolean upsertUserSession(String userId, String nombre, String email, String loginTime) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SESSION_USER_ID, userId);
        values.put(COLUMN_NOMBRE, nombre);
        values.put(COLUMN_EMAIL, email);
        values.put(COLUMN_LOGIN_TIME, loginTime);

        long result = db.insertWithOnConflict(TABLE_USER_SESSIONS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (result == -1) {
            int rows = db.update(TABLE_USER_SESSIONS, values, COLUMN_SESSION_USER_ID + "=?", new String[]{userId});
            return rows > 0;
        }
        return true;
    }

    public boolean updateLogoutTime(String userId, String logoutTime) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_LOGOUT_TIME, logoutTime);

        int rows = db.update(TABLE_USER_SESSIONS, values, COLUMN_SESSION_USER_ID + "=?", new String[]{userId});
        return rows > 0;
    }

    public UserSession getUserSession(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USER_SESSIONS, null,
                COLUMN_SESSION_USER_ID + "=?", new String[]{userId},
                null, null, null);
        UserSession session = null;
        if (cursor.moveToFirst()) {
            String nombre = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOMBRE));
            String email = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL));
            String loginTime = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOGIN_TIME));
            String logoutTime = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOGOUT_TIME));
            session = new UserSession(userId, nombre, email, loginTime, logoutTime);
        }
        cursor.close();
        return session;
    }
}