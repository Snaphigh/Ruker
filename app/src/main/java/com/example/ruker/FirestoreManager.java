package com.example.ruker;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FirestoreManager {
    private static final String COLLECTION_PATHS = "recorded_paths";
    private final FirebaseFirestore db;
    private final String userId;

    public FirestoreManager() {
        this.db = FirebaseFirestore.getInstance();
        this.userId = FirebaseAuth.getInstance().getUid();
    }

    public void fetchCommunityPaths(OnSuccessListener<List<Map<String, Object>>> listener, OnFailureListener failureListener) {
        db.collection(COLLECTION_PATHS)
                .whereEqualTo("is_public", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Map<String, Object>> paths = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        paths.add(document.getData());
                    }
                    listener.onSuccess(paths);
                })
                .addOnFailureListener(failureListener);
    }

    public void fetchPathDetails(String docId, OnSuccessListener<DocumentSnapshot> listener) {
        db.collection(COLLECTION_PATHS).document(docId).get().addOnSuccessListener(listener);
    }

    public void saveRun(Map<String, Object> data, OnSuccessListener<DocumentReference> successListener, OnFailureListener failureListener) {
        data.put("user_id", userId);
        db.collection(COLLECTION_PATHS).add(data)
                .addOnSuccessListener(successListener)
                .addOnFailureListener(failureListener);
    }
}
