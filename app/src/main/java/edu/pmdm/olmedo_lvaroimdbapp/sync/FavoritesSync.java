package edu.pmdm.olmedo_lvaroimdbapp.sync;

import android.content.Context;
import android.util.Log;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import edu.pmdm.olmedo_lvaroimdbapp.models.FavoriteDBHelper;
import edu.pmdm.olmedo_lvaroimdbapp.models.Movie;

public class FavoritesSync {
    private static final String TAG = "FavoritesSync";
    private final FirebaseFirestore firestore;
    private final FavoriteDBHelper dbHelper;
    private final String userId;

    public FavoritesSync(Context context, String userId) {
        this.firestore = FirebaseFirestore.getInstance();
        this.dbHelper = FavoriteDBHelper.getInstance(context);
        this.userId = userId;
    }

    public void syncFavorites(Runnable onComplete) {
        DocumentReference userDocRef = firestore.collection("favorites").document(userId);

        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    CollectionReference moviesCollection = userDocRef.collection("movies");
                    moviesCollection.get().addOnCompleteListener(taskMovies -> {
                        if (taskMovies.isSuccessful()) {
                            QuerySnapshot querySnapshot = taskMovies.getResult();

                            if (querySnapshot == null || querySnapshot.isEmpty()) {
                                // 🔥 Si la nube está vacía, borrar TODA la base de datos local
                                dbHelper.deleteAllFavorites(userId);
                                Log.d(TAG, "La nube está vacía. Se han eliminado todos los favoritos locales.");
                            } else {
                                // 🔥 Si hay datos en la nube, actualizar la base de datos local
                                dbHelper.deleteAllFavorites(userId); // Eliminamos todo antes de sincronizar
                                for (QueryDocumentSnapshot doc : querySnapshot) {
                                    String movieId = doc.getString("movie_id");
                                    String poster = doc.getString("poster");
                                    String title = doc.getString("title");
                                    dbHelper.insertFavorite(userId, movieId, poster, title);
                                }
                                Log.d(TAG, "Se han sincronizado los favoritos de la nube a la base de datos local.");
                            }
                        } else {
                            Log.e(TAG, "Error al obtener las películas de la nube", taskMovies.getException());
                        }
                        if (onComplete != null) {
                            onComplete.run(); // 🔥 IMPORTANTE: Se ejecuta cuando termina la sincronización
                        }
                    });
                } else {
                    // 🔥 Si no existe documento en la nube, eliminar todos los favoritos locales
                    dbHelper.deleteAllFavorites(userId);
                    Log.d(TAG, "No existe documento de favoritos en la nube. Se han eliminado todos los locales.");
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            } else {
                Log.e(TAG, "Error al obtener el documento 'favorites'", task.getException());
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    public void addMovieToCloud(Movie movie) {
        DocumentReference movieRef = firestore.collection("favorites")
                .document(userId)
                .collection("movies")
                .document(movie.getTconst());

        Map<String, Object> movieData = new HashMap<>();
        movieData.put("movie_id", movie.getTconst());
        movieData.put("title", movie.getTitle());
        movieData.put("poster", movie.getImageUrl());

        movieRef.set(movieData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Película añadida a la nube: " + movie.getTconst()))
                .addOnFailureListener(e -> Log.e(TAG, "Error al añadir la película a la nube: " + movie.getTconst(), e));
    }

    public void removeMovieFromCloud(String movieId) {
        DocumentReference movieRef = firestore.collection("favorites")
                .document(userId)
                .collection("movies")
                .document(movieId);

        movieRef.delete()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Película eliminada de la nube: " + movieId))
                .addOnFailureListener(e -> Log.e(TAG, "Error al eliminar la película de la nube: " + movieId, e));
    }
}
