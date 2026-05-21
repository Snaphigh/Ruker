package com.example.ruker;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class MyPathsActivity extends AppCompatActivity
        implements PathAdapter.OnPathClickListener, PathAdapter.OnVisibilityChanged {

    public static final int REQUEST_SHOW_PATH = 101;
    public static final String EXTRA_PATH_DOC_ID = "path_doc_id";

    private FirebaseFirestore db;
    private String userId;

    private ProgressBar loadingSpinner;
    private TextView emptyText;
    private RecyclerView recyclerView;

    private final List<PathItem> pathList = new ArrayList<>();
    private PathAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_paths);

        Toolbar toolbar = findViewById(R.id.myPathsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.my_paths);
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        userId = user.getUid();
        db = FirebaseFirestore.getInstance();

        loadingSpinner = findViewById(R.id.loadingSpinner);
        emptyText      = findViewById(R.id.emptyText);
        recyclerView   = findViewById(R.id.pathsRecyclerView);

        adapter = new PathAdapter(pathList, this, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadPaths();
    }


    @SuppressLint("NotifyDataSetChanged")
    private void loadPaths() {
        loadingSpinner.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        db.collection("recorded_paths")
                .whereEqualTo("user_id", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    loadingSpinner.setVisibility(View.GONE);
                    pathList.clear();

                    if (queryDocumentSnapshots.isEmpty()) {
                        emptyText.setVisibility(View.VISIBLE);
                        return;
                    }

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Timestamp ts = doc.getTimestamp("start_time");
                        if (ts == null) continue;
                        String name = "Path from " + sdf.format(ts.toDate());
                        Boolean isPublic = doc.getBoolean("is_public");
                        List<Map<String, Object>> points = (List<Map<String, Object>>) doc.get("path");

                        pathList.add(new PathItem(
                                doc.getId(),
                                name,
                                Boolean.TRUE.equals(isPublic),
                                points != null ? points : new ArrayList<>()
                        ));
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, getString(R.string.error_loading_paths, e.getMessage()), Toast.LENGTH_SHORT).show()
                );
    }

    @Override
    public void onPathClick(PathItem path) {
        Intent result = new Intent();
        result.putExtra(EXTRA_PATH_DOC_ID, path.docId);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onVisibilityChanged(PathItem path, boolean isPublic) {
        db.collection("recorded_paths")
                .document(path.docId)
                .update("is_public", isPublic)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, isPublic ? getString(R.string.path_public) : getString(R.string.path_private), Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, getString(R.string.update_error, e.getMessage()), Toast.LENGTH_SHORT).show()
                );
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
