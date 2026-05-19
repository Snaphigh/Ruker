package com.example.ruker;

import java.util.List;
import java.util.Map;


public class PathItem {

    public String docId;

    public String displayName;

    public boolean isPublic;

    public List<Map<String, Object>> pathPoints;

    public PathItem(String docId, String displayName, boolean isPublic,
                    List<Map<String, Object>> pathPoints) {
        this.docId = docId;
        this.displayName = displayName;
        this.isPublic = isPublic;
        this.pathPoints = pathPoints;
    }
}