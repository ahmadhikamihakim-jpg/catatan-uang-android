package id.hakim.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

public class FormActivity extends Activity {
    static final String[] KAT_MASUK = {"Gaji", "Usaha", "Toko 1", "Toko 2", "Toko 3", "Bonus", "Transfer Masuk", "Lainnya"};
    static final String[] KAT_KELUAR = {"Makan & Minum", "Transportasi", "Belanja", "Tagihan", "Kesehatan", "Hiburan", "Pendidikan", "Lainnya"};

    private DbHelper db;
    private String jenis = "masuk";
    private String tanggal = Util.today();
    private long editId = -1;
    private Spinner spKategori;
    private Button btnTanggal;

    // Pemisah ribuan otomatis: mengetik 1500000 tampil sebagai 1.500.000
    static String formatRibuan(String angka) {
        StringBuilder sb = new StringBuilder();
        int n = angka.length();
        for (int i = 0; i < n; i++) {
            if (i > 0 && (n - i) % 3 == 0) sb.append('.');
            sb.append(angka.charAt(i));
        }
        return sb.toString();
    }

    private void pasangPemisahRibuan(EditText kotak) {
        // Pola aman: pengamat dilepas dulu sebelum teks diganti (setText),
        // lalu dipasang lagi. Mengubah Editable di tengah pemberitahuan
        // perubahan teks bisa membuat aplikasi berhenti di sebagian HP.
        // Seluruh proses dibungkus try/catch: pemformatan tidak boleh
        // pernah membuat formulir error — terburuk, angka tampil apa adanya.
        TextWatcher pengamat = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                try {
                    String mentah = s.toString().replaceAll("[^0-9]", "");
                    if (mentah.length() > 12) mentah = mentah.substring(0, 12);
                    String hasil = mentah.isEmpty() ? "" : formatRibuan(mentah);
                    if (!hasil.equals(s.toString())) {
                        kotak.removeTextChangedListener(this);
                        try {
                            kotak.setText(hasil);
                            kotak.setSelection(hasil.length());
                        } finally {
                            kotak.addTextChangedListener(this);
                        }
                    }
                } catch (Throwable abaikan) {
                    // jangan sampai pemformatan membuat aplikasi berhenti
                }
            }
        };
        kotak.addTextChangedListener(pengamat);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_form);
        db = new DbHelper(this);
        pasangPemisahRibuan(findViewById(R.id.f_jumlah));
        spKategori = findViewById(R.id.f_kategori);
        btnTanggal = findViewById(R.id.f_tanggal);
        editId = getIntent().getLongExtra("id", -1);

        DbHelper.Tx existing = null;
        if (editId > 0) {
            existing = db.byId(editId);
            if (existing != null) jenis = existing.jenis;
        } else {
            jenis = getIntent().getStringExtra("jenis");
            if (jenis == null) jenis = "masuk";
        }

        ((TextView) findViewById(R.id.f_title)).setText(
            (editId > 0 ? "Ubah " : "Tambah ") + ("masuk".equals(jenis) ? "Uang Masuk ▼" : "Uang Keluar ▲"));
        spKategori.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            "masuk".equals(jenis) ? KAT_MASUK : KAT_KELUAR));

        if (existing != null) {
            ((EditText) findViewById(R.id.f_jumlah)).setText(String.valueOf(existing.jumlah));
            ((EditText) findViewById(R.id.f_catatan)).setText(existing.catatan == null ? "" : existing.catatan);
            tanggal = existing.tanggal;
            String[] kats = "masuk".equals(jenis) ? KAT_MASUK : KAT_KELUAR;
            for (int i = 0; i < kats.length; i++) if (kats[i].equals(existing.kategori)) spKategori.setSelection(i);
            Button hapus = findViewById(R.id.f_hapus);
            hapus.setVisibility(View.VISIBLE);
            final DbHelper.Tx lama = existing;
            hapus.setOnClickListener(v -> {
                if (lama.fid != null && !lama.fid.isEmpty()) Session.tambahAntrianHapus(this, lama.fid);
                db.delete(editId);
                Toast.makeText(this, "Transaksi dihapus", Toast.LENGTH_SHORT).show();
                finish();
            });
        }
        updateTanggalLabel();
        btnTanggal.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            try { c.set(Integer.parseInt(tanggal.substring(0, 4)), Integer.parseInt(tanggal.substring(5, 7)) - 1, Integer.parseInt(tanggal.substring(8, 10))); } catch (Exception ignored) {}
            new DatePickerDialog(this, (dp, y, m, d) -> {
                tanggal = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                updateTanggalLabel();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        findViewById(R.id.f_simpan).setOnClickListener(v -> simpan());
    }

    private void updateTanggalLabel() { btnTanggal.setText("📅 " + Util.tglCantik(tanggal)); }

    private void simpan() {
        String j = ((EditText) findViewById(R.id.f_jumlah)).getText().toString().trim().replace(".", "").replace(",", "");
        long jumlah;
        try { jumlah = Long.parseLong(j); } catch (Exception e) { jumlah = 0; }
        if (jumlah <= 0) { Toast.makeText(this, "Isi jumlahnya dulu ya (lebih dari 0)", Toast.LENGTH_SHORT).show(); return; }
        DbHelper.Tx t = new DbHelper.Tx();
        t.jenis = jenis; t.jumlah = jumlah;
        t.kategori = String.valueOf(spKategori.getSelectedItem());
        t.tanggal = tanggal;
        t.catatan = ((EditText) findViewById(R.id.f_catatan)).getText().toString().trim();
        if (editId > 0) { t.id = editId; db.update(t); } else { db.insert(t); }
        // Baris tersimpan dengan tersinkron=0; SyncEngine akan mengirimnya ke Firestore.
        Toast.makeText(this, "Tersimpan ✓", Toast.LENGTH_SHORT).show();
        finish();
    }
}
