package id.hakim.app;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// Pertukaran token memakai REST API resmi Firebase Auth (tanpa SDK Firebase):
// 1) Token ID dari Google Sign-In  -> accounts:signInWithIdp  -> token Firebase + uid
// 2) Refresh token                  -> securetoken.googleapis.com/v1/token
public class AuthApi {

    public static class Hasil {
        public String uid, email, nama, idToken, refreshToken;
        public long expiresInSec;
    }

    static String baca(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    static JSONObject postJson(String url, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = c.getOutputStream()) { os.write(data); }
        int code = c.getResponseCode();
        String resp = baca(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + resp);
        return new JSONObject(resp);
    }

    static JSONObject postForm(String url, String form) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        byte[] data = form.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = c.getOutputStream()) { os.write(data); }
        int code = c.getResponseCode();
        String resp = baca(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + resp);
        return new JSONObject(resp);
    }

    // Menukar token ID Google menjadi sesi Firebase. Jalankan di thread latar.
    public static Hasil masukDenganGoogle(String googleIdToken) throws Exception {
        JSONObject body = new JSONObject();
        body.put("postBody", "id_token=" + URLEncoder.encode(googleIdToken, "UTF-8") + "&providerId=google.com");
        body.put("requestUri", "http://localhost");
        body.put("returnIdpCredential", true);
        body.put("returnSecureToken", true);
        JSONObject r = postJson("https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=" + FirebaseConfig.API_KEY, body);
        Hasil h = new Hasil();
        h.idToken = r.getString("idToken");
        h.refreshToken = r.getString("refreshToken");
        h.uid = r.getString("localId");
        h.email = r.optString("email", "");
        h.nama = r.optString("displayName", "");
        h.expiresInSec = Long.parseLong(r.optString("expiresIn", "3600"));
        return h;
    }

    // Menyegarkan idToken dari refresh token yang tersimpan. Jalankan di thread latar.
    public static Hasil segarkan(Session s) throws Exception {
        String form = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(s.refreshToken, "UTF-8");
        JSONObject r = postForm("https://securetoken.googleapis.com/v1/token?key=" + FirebaseConfig.API_KEY, form);
        Hasil h = new Hasil();
        h.idToken = r.getString("id_token");
        h.refreshToken = r.optString("refresh_token", s.refreshToken);
        h.uid = r.optString("user_id", s.uid);
        h.expiresInSec = Long.parseLong(r.optString("expires_in", "3600"));
        return h;
    }
}
