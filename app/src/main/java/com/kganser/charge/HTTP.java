package com.kganser.charge;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;

public class HTTP {
    private static RequestQueue queue;
    public static void cancel(Object tag) {
        if (queue != null) queue.cancelAll(tag);
    }
    public static Request<JSONArray> get(Context ctx, String url, Response.Listener<JSONArray> listener) {
        if (queue == null) queue = Volley.newRequestQueue(ctx);
        return queue.add(new JsonArrayRequest(url, listener, null));
    }
}
