package edu.pmdm.olmedo_lvaroimdbapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.ArrayList;
import edu.pmdm.olmedo_lvaroimdbapp.R;
import edu.pmdm.olmedo_lvaroimdbapp.models.FavoriteDBHelper;
import edu.pmdm.olmedo_lvaroimdbapp.models.Movie;
import edu.pmdm.olmedo_lvaroimdbapp.models.MovieDetailsActivity;

public class SearchResultsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ArrayList<Movie> movies = new ArrayList<>();
    private FavoriteDBHelper favoriteDBHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        favoriteDBHelper = new FavoriteDBHelper(this);
        ArrayList<Movie> receivedMovies = getIntent().getParcelableArrayListExtra("MOVIES_LIST");
        if (receivedMovies == null || receivedMovies.isEmpty()) {
            Toast.makeText(this, "No se encontraron portadas para mostrar.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        movies.addAll(receivedMovies);
        recyclerView.setAdapter(new RecyclerView.Adapter<MovieViewHolder>() {
            @NonNull
            @Override
            public MovieViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ImageView imageView = new ImageView(parent.getContext());
                imageView.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT
                ));
                imageView.setAdjustViewBounds(true);
                imageView.setPadding(
                        dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8)
                );
                return new MovieViewHolder(imageView);
            }

            @Override
            public void onBindViewHolder(@NonNull MovieViewHolder holder, int position) {
                Movie movie = movies.get(position);
                Glide.with(SearchResultsActivity.this)
                        .load(movie.getImageUrl())
                        .placeholder(R.drawable.placeholder)
                        .error(R.drawable.error_image)
                        .into(holder.imageView);

                holder.imageView.setOnClickListener(v -> {
                    Intent intent = new Intent(SearchResultsActivity.this, MovieDetailsActivity.class);
                    intent.putExtra("MOVIE_ID", movie.getTconst());
                    intent.putExtra("IS_FROM_TMDB", true);
                    startActivity(intent);
                });

                holder.imageView.setOnLongClickListener(v -> {
                    handleLongClick(movie);
                    return true;
                });
            }
            @Override
            public int getItemCount() {
                return movies.size();
            }
        });
    }

    // Maneja el evento de hacer click largo
    private void handleLongClick(Movie movie) {
        if (movie == null) {
            Toast.makeText(this, "Película no disponible para insertar", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (currentUser != null) ? currentUser.getUid() : "usuario_sin_uid";

        if (favoriteDBHelper.isFavorite(uid, movie.getTconst())) {
            Toast.makeText(this, "La película ya está en favoritos", Toast.LENGTH_SHORT).show();
        } else {
            boolean isInsertedLocal = favoriteDBHelper.insertFavorite(
                    uid,
                    movie.getTconst(),
                    movie.getImageUrl(),
                    movie.getTitle()
            );

            if (isInsertedLocal) {
                boolean isInsertedCloud = favoriteDBHelper.insertFavoriteToCloud(
                        uid,
                        movie.getTconst(),
                        movie.getImageUrl(),
                        movie.getTitle()
                );

                if (isInsertedCloud) {
                    Toast.makeText(this, "Añadido a favoritos: " + movie.getTitle(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Error al añadir a favoritos en la nube", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Error al añadir a favoritos localmente", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Convierte dp a píxeles
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

    private static class MovieViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public MovieViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = (ImageView) itemView;
        }
    }
}