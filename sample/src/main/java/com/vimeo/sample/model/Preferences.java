package com.vimeo.sample.model;

import com.vimeo.stag.GsonAdapterKey;

import java.util.HashMap;

public class Preferences {

    @GsonAdapterKey("videos")
    public HashMap<String, String> mVideosMap;

    @Override
    public String toString() {
        return "videos: " + mVideosMap.toString();
    }
}
