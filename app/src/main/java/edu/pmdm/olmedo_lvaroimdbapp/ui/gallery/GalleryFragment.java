package edu.pmdm.olmedo_lvaroimdbapp.ui.gallery;

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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.pmdm.olmedo_lvaroimdbapp.R;
import edu.pmdm.olmedo_lvaroimdbapp.models.FavoriteDBHelper;
import edu.pmdm.olmedo_lvaroimdbapp.models.Movie;
import edu.pmdm.olmedo_lvaroimdbapp.api.MovieDetails;
import edu.pmdm.olmedo_lvaroimdbapp.models.MovieDetailsActivity;

public class GalleryFragment extends Fragment {

    private List<Movie> movieList;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_gallery, container, false);

        //Recupera el RecyclerView
        RecyclerView recyclerView = root.findViewById(R.id.recycler_view);

        //Configura el botón de compartir
        Button shareButton = root.findViewById(R.id.btn_share);
        shareButton.setOnClickListener(v -> handleShareButtonClick());

        //Inicializa el lanzador para permisos
        initializePermissionLauncher();

        //Obtiene UID del usuario actual desde Firebase
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (currentUser != null) ? currentUser.getUid() : null;

        if(uid == null) {
            Toast.makeText(requireContext(), "Usuario no autenticado", Toast.LENGTH_SHORT).show();
            return root;
        }

        //Obtiene la lista de películas desde la base de datos
        FavoriteDBHelper dbHelper = new FavoriteDBHelper(requireContext());
        movieList = dbHelper.getAllMovies(uid);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(new RecyclerView.Adapter<MovieViewHolder>() {
            @NonNull
            @Override
            public MovieViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_movie, parent, false);
                return new MovieViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull MovieViewHolder holder, int position) {
                Movie movie = movieList.get(position);

                //Carga la imagen usando Glide
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
                        //Elimina la película de la base de datos
                        boolean deleted = dbHelper.deleteFavorite(uid, movie.getTconst());
                        if (deleted) {
                            //Elimina la película de la lista
                            movieList.remove(itemPosition);
                            notifyItemRemoved(itemPosition);
                            notifyItemRangeChanged(itemPosition, movieList.size()); // Ajustar las posiciones restantes.
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
        });
        return root;
    }

    //Inicia la solicitud para pedir los permisos
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

    //Maneja el click en el botón de compartir
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

    //Obtiene los datos de cada película y muestra un AlertDialog
    private void fetchMoviesAndShowDialog() {
        if (movieList==null||movieList.isEmpty()) {
            Toast.makeText(requireContext(), "No hay películas en favoritos.", Toast.LENGTH_SHORT).show();
            return;
        }
        JSONArray moviesJsonArray = new JSONArray();
        executorService.execute(() -> {
            for (Movie movie : movieList) {
                try {
                    // Obtener detalles de la película
                    MovieDetails movieDetails = MovieDetails.getReleaseDateAndRating(movie.getTconst());
                    // Generar el JSON de la película
                    JSONObject movieJson = new JSONObject();
                    movieJson.put("id", movie.getTconst());
                    movieJson.put("title", movie.getTitle());
                    movieJson.put("releaseDate", movieDetails.getReleaseDate());
                    movieJson.put("rating", movieDetails.getRating());
                    movieJson.put("imageUrl", movie.getImageUrl());
                    moviesJsonArray.put(movieJson);
                } catch (Exception e) {
                    Log.e("GalleryFragment", "Error al procesar película: " + movie.getTconst(), e);
                }
            }
            requireActivity().runOnUiThread(() -> {
                try {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Información de Películas")
                            .setMessage(moviesJsonArray.toString(4)) // Formatear JSON con indentación
                            .setPositiveButton("Cerrar", (dialog, which) -> dialog.dismiss())
                            .show();
                } catch (Exception e) {
                    Log.e("GalleryFragment", "Error al mostrar AlertDialog", e);
                }
            });
        });
    }

    static class MovieViewHolder extends RecyclerView.ViewHolder {
        ImageView moviePoster;

        public MovieViewHolder(@NonNull View itemView) {
            super(itemView);
            moviePoster = itemView.findViewById(R.id.movie_poster);
        }
    }
}
