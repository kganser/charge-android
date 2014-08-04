package com.kganser.charge;

import android.os.AsyncTask;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

public class HTTP {
    public static interface Callback {
        public void onResponse(Response response);
    }
    public static class Query {
        private StringBuilder query = new StringBuilder();
        public Query add(String param, String value) {
            if (query.length() > 0) query.append("&");
            query.append(URLEncoder.encode(param)).append("=").append(URLEncoder.encode(value));
            return this;
        }
        public Query add(String param, int value) {
            return add(param, String.valueOf(value));
        }
        public Query add(String param, double value) {
            return add(param, String.valueOf(value));
        }
        public Query addOpt(String param, String value) {
            if (value != null && value.length() > 0) add(param, value);
            return this;
        }
        public String toString() {
            return query.toString();
        }
    }
    public static class Response {
        private int status;
        private byte[] data;
        public Response(int status, InputStream input, int contentLength) {
            this.status = status;
            data = new byte[Math.max(contentLength, 256)];
            int position = 0, count;
            try {
                while ((count = input.read(data, position, data.length - position)) > -1) {
                    if ((position += count) == data.length) {
                        byte[] buf = new byte[data.length + 256];
                        System.arraycopy(data, 0, buf, 0, data.length);
                        data = buf;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public int getStatus() {
            return status;
        }
        public String toString() {
            return new String(data);
        }
    }
    private static class Request extends AsyncTask<HttpUriRequest, Void, Response> {
        private Callback callback;
        public Request(Callback callback) {
            this.callback = callback;
        }
        protected Response doInBackground(HttpUriRequest... request) {
            try {
                //System.err.println(request[0].getURI());
                HttpURLConnection connection = (HttpURLConnection) request[0].getURI().toURL().openConnection();
                return new Response(connection.getResponseCode(), connection.getInputStream(), connection.getContentLength());
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        protected void onPostExecute(Response result) {
            callback.onResponse(result);
        }
    }
    public static void get(String hostPath, Query query, Callback callback) {
        new Request(callback).execute(new HttpGet(hostPath + (query == null ? "" : "?" + query)));
    }
}
