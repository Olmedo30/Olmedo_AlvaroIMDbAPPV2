package edu.pmdm.olmedo_lvaroimdbapp.ui.favoritos;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import edu.pmdm.olmedo_lvaroimdbapp.R;
import edu.pmdm.olmedo_lvaroimdbapp.models.FavoriteDBHelper;
import edu.pmdm.olmedo_lvaroimdbapp.models.Movie;
import edu.pmdm.olmedo_lvaroimdbapp.api.MovieDetails;
import edu.pmdm.olmedo_lvaroimdbapp.models.MovieDetailsActivity;
import edu.pmdm.olmedo_lvaroimdbapp.sync.FavoritesSync;

public class Favoritos extends Fragment {
    private List<Movie> movieList = new ArrayList<>();
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private FavoritesSync favoritesSync;
    private RecyclerView recyclerView;
    private MovieAdapter adapter;
    private FavoriteDBHelper dbHelper;
    private String uid;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_gallery, container, false);

        // Verificar si el usuario está autenticado
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        uid = (currentUser != null) ? currentUser.getUid() : null;
        if (uid == null) {
            Toast.makeText(requireContext(), "Usuario no autenticado", Toast.LENGTH_SHORT).show();
            return root;
        }

        dbHelper = new FavoriteDBHelper(requireContext());
        favoritesSync = new FavoritesSync(requireContext(), uid);

        recyclerView = root.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MovieAdapter();
        recyclerView.setAdapter(adapter);

        Button shareButton = root.findViewById(R.id.btn_share);
        shareButton.setOnClickListener(v -> handleShareButtonClick());

        initializePermissionLauncher();

        syncAndUpdateUI();

        return root;
    }

    // Sincroniza los favoritos
    private void syncAndUpdateUI() {
        favoritesSync.syncFavorites(() -> {
            requireActivity().runOnUiThread(() -> {
                movieList.clear();
                movieList.addAll(dbHelper.getAllMovies(uid));
                adapter.notifyDataSetChanged();
            });
        });
    }

    // Comprueba si tiene los permisos
    private void initializePermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        fetchMoviesAndShowDialog();
                    } else {
                        Toast.makeText(requireContext(), "Permisos denegados", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    //Controla el clic en el botón de compartir
    private void handleShareButtonClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED) {
                fetchMoviesAndShowDialog();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            fetchMoviesAndShowDialog();
        }
    }

    // Obtiene las películas y muestra un diálogo con la información
    private void fetchMoviesAndShowDialog() {
        if (movieList.isEmpty()) {
            Toast.makeText(requireContext(), "No hay películas en favoritos.", Toast.LENGTH_SHORT).show();
            return;
        }
        JSONArray moviesJsonArray = new JSONArray();
        executorService.execute(() -> {
            for (Movie movie : movieList) {
                try {
                    MovieDetails movieDetails = MovieDetails.getReleaseDateAndRating(movie.getTconst());
                    JSONObject movieJson = new JSONObject();
                    movieJson.put("id", movie.getTconst());
                    movieJson.put("title", movie.getTitle());
                    movieJson.put("releaseDate", movieDetails.getReleaseDate());
                    movieJson.put("rating", movieDetails.getRating());
                    movieJson.put("imageUrl", movie.getImageUrl());
                    moviesJsonArray.put(movieJson);
                } catch (Exception e) {
                    Log.e("Favoritos", "Error al procesar película: " + movie.getTconst(), e);
                }
            }
            requireActivity().runOnUiThread(() -> {
                try {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Información de Películas")
                            .setMessage(moviesJsonArray.toString(4))
                            .setPositiveButton("Cerrar", (dialog, which) -> dialog.dismiss())
                            .show();
                } catch (Exception e) {
                    Log.e("Favoritos", "Error al mostrar AlertDialog", e);
                }
            });
        });
    }

    private class MovieAdapter extends RecyclerView.Adapter<MovieViewHolder> {
        @NonNull
        @Override
        public MovieViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_movie, parent, false);
            return new MovieViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MovieViewHolder holder, int position) {
            Movie movie = movieList.get(position);
            Glide.with(holder.itemView.getContext())
                    .load(movie.getImageUrl())
                    .placeholder(R.drawable.placeholder)
                    .error(R.drawable.error_image)
                    .into(holder.moviePoster);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), MovieDetailsActivity.class);
                intent.putExtra("MOVIE_ID", movie.getTconst());
                intent.putExtra("IMAGE_URL", movie.getImageUrl());
                startActivity(intent);
            });

            holder.itemView.setOnLongClickListener(v -> {
                int itemPosition = holder.getAdapterPosition();
                if (itemPosition != RecyclerView.NO_POSITION && itemPosition < movieList.size()) {
                    Movie movieToDelete = movieList.get(itemPosition);

                    boolean deletedLocal = dbHelper.deleteFavorite(uid, movieToDelete.getTconst());
                    if (deletedLocal) {
                        favoritesSync.removeMovieFromCloud(movieToDelete.getTconst());

                        movieList.remove(itemPosition);
                        notifyItemRemoved(itemPosition);
                        notifyItemRangeChanged(itemPosition, movieList.size());

                        Toast.makeText(requireContext(), "Eliminado de favoritos", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Error al eliminar de favoritos", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return movieList.size();
        }
    }

    static class MovieViewHolder extends RecyclerView.ViewHolder {
        ImageView moviePoster;

        public MovieViewHolder(@NonNull View itemView) {
            super(itemView);
            moviePoster = itemView.findViewById(R.id.movie_poster);
        }
    }
}
