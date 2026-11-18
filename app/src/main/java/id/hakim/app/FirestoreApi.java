package id.hakim.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Akses Cloud Firestore lewat REST API resmi.
// Dokumen transaksi: projects/<proyek>/databases/(default)/documents/users/{uid}/transaksi/{fid}
public class FirestoreApi {

    static String dasar(String uid) {
        return "https://firestore.googleapis.com/v1/projects/" + FirebaseConfig.PROJECT_ID
            + "/databases/(default)/documents/users/" + uid + "/transaksi";
    }

    static class Resp { int code; String body; }

    static Resp panggil(String metode, String url, String token, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(metode);
        c.setConnectTimeout(20000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        }
        Resp r = new Resp();
        r.code = c.getResponseCode();
        java.io.InputStream in = (r.code >= 200 && r.code < 300) ? c.getInputStream() : c.getErrorStream();
        r.body = in == null ? "" : AuthApi.baca(in);
        return r;
    }

    static JSONObject fieldsDari(DbHelper.Tx t) throws Exception {
        JSONObject f = new JSONObject();
        f.put("jenis", new JSONObject().put("stringValue", t.jenis));
        f.put("jumlah", new JSONObject().put("integerValue", String.valueOf(t.jumlah)));
        f.put("kategori", new JSONObject().put("stringValue", t.kategori == null ? "" : t.kategori));
        f.put("tanggal", new JSONObject().put("stringValue", t.tanggal));
        f.put("catatan", new JSONObject().put("stringValue", t.catatan == null ? "" : t.catatan));
        return new JSONObject().put("fields", f);
    }

    static DbHelper.Tx txDari(JSONObject doc) throws Exception {
        DbHelper.Tx t = new DbHelper.Tx();
        String nama = doc.getString("name");
        t.fid = nama.substring(nama.lastIndexOf('/') + 1);
        JSONObject f = doc.getJSONObject("fields");
        t.jenis = f.getJSONObject("jenis").getString("stringValue");
        t.jumlah = Long.parseLong(f.getJSONObject("jumlah").getString("integerValue"));
        t.kategori = f.getJSONObject("kategori").optString("stringValue", "");
        t.tanggal = f.getJSONObject("tanggal").optString("stringValue", "");
        t.catatan = f.getJSONObject("catatan").optString("stringValue", "");
        return t;
    }

    // Membuat dokumen baru dengan ID tertentu. Mengembalikan 200/201 bila sukses.
    public static int buat(String uid, String token, String fid, DbHelper.Tx t) throws Exception {
        Resp r = panggil("POST", dasar(uid) + "?documentId=" + URLEncoder.encode(fid, "UTF-8"), token, fieldsDari(t));
        return r.code;
    }

    public static int ubah(String uid, String token, String fid, DbHelper.Tx t) throws Exception {
        Resp r = panggil("PATCH", dasar(uid) + "/" + URLEncoder.encode(fid, "UTF-8"), token, fieldsDari(t));
        return r.code;
    }

    public static int hapus(String uid, String token, String fid) throws Exception {
        Resp r = panggil("DELETE", dasar(uid) + "/" + URLEncoder.encode(fid, "UTF-8"), token, null);
        return r.code;
    }

    public static List<DbHelper.Tx> daftar(String uid, String token) throws Exception {
        Resp r = panggil("GET", dasar(uid) + "?pageSize=300", token, null);
        if (r.code == 404) return new ArrayList<>(); // koleksi belum ada
        if (r.code < 200 || r.code >= 300) throw new Exception("Firestore HTTP " + r.code + ": " + r.body);
        List<DbHelper.Tx> out = new ArrayList<>();
        JSONObject root = new JSONObject(r.body);
        JSONArray docs = root.optJSONArray("documents");
        if (docs != null) for (int i = 0; i < docs.length(); i++) out.add(txDari(docs.getJSONObject(i)));
        return out;
    }
}
