package edu.pmdm.olmedo_lvaroimdbapp.sync;

import android.content.Context;
import android.util.Log;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.HashMap;
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

    // Sincroniza los datos de las colecciones y documentos de la nube de firebase y lo guarda en la BD local
    public void syncFavorites(Runnable onComplete) {
        CollectionReference moviesCollection = firestore.collection("favorites").document(userId).collection("movies");
        moviesCollection.get().addOnCompleteListener(taskMovies -> {
            if (taskMovies.isSuccessful()) {
                QuerySnapshot querySnapshot = taskMovies.getResult();
                if (querySnapshot != null && !querySnapshot.isEmpty()) {
                    dbHelper.deleteAllFavorites(userId);
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String movieId = doc.getString("movie_id");
                        String poster = doc.getString("poster");
                        String title = doc.getString("title");
                        dbHelper.insertFavorite(userId, movieId, poster, title);
                    }
                } else {
                    dbHelper.deleteAllFavorites(userId);
                    Log.d(TAG, "No hay películas en la colección 'movies' de la nube. Se han eliminado los favoritos locales.");
                }
            } else {
                Log.e(TAG, "Error al obtener las películas de la nube", taskMovies.getException());
            }
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    // Añade una película a la nube
    public void addMovieToCloud(Movie movie) {
        DocumentReference userDocRef = firestore.collection("favorites").document(userId);
        userDocRef.set(new HashMap<>()).addOnSuccessListener(aVoid -> {
            DocumentReference movieRef = userDocRef.collection("movies").document(movie.getTconst());
            Map<String, Object> movieData = new HashMap<>();
            movieData.put("movie_id", movie.getTconst());
            movieData.put("title", movie.getTitle());
            movieData.put("poster", movie.getImageUrl());
            movieRef.set(movieData)
                    .addOnSuccessListener(aVoid2 -> Log.d(TAG, "Película añadida a la nube: " + movie.getTconst()))
                    .addOnFailureListener(e -> Log.e(TAG, "Error al añadir la película a la nube: " + movie.getTconst(), e));
        }).addOnFailureListener(e -> Log.e(TAG, "Error al crear el documento de usuario", e));
    }

    // Elimina una película de la nube
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