package id.hakim.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Aktivitas ala myBCA: transaksi dikelompokkan per bulan dengan judul bagian. */
public class AktivitasActivity extends Activity {
    private DbHelper db;
    private final List<Object> baris = new ArrayList<>(); // String = judul bulan, Tx = transaksi
    private AktAdapter adapter;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aktivitas);
        NavBawah.pasang(this, "aktivitas");
        db = new DbHelper(this);
        adapter = new AktAdapter();
        ListView list = findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Object o = baris.get(pos);
            if (o instanceof DbHelper.Tx) {
                Intent i = new Intent(this, FormActivity.class);
                i.putExtra("id", ((DbHelper.Tx) o).id);
                startActivity(i);
            }
        });
        segarkan();
    }

    private void segarkan() {
        baris.clear();
        String bulanBerjalan = null;
        for (DbHelper.Tx t : db.all()) {
            String ym = t.tanggal != null && t.tanggal.length() >= 7 ? t.tanggal.substring(0, 7) : "";
            if (!ym.equals(bulanBerjalan)) {
                bulanBerjalan = ym;
                baris.add(Util.bulanNama(ym));
            }
            baris.add(t);
        }
        findViewById(R.id.empty).setVisibility(baris.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    @Override protected void onResume() { super.onResume(); segarkan(); }

    private class AktAdapter extends BaseAdapter {
        public int getCount() { return baris.size(); }
        public Object getItem(int p) { return baris.get(p); }
        public long getItemId(int p) { return p; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int p) { return baris.get(p) instanceof String ? 0 : 1; }
        @Override public boolean isEnabled(int p) { return baris.get(p) instanceof DbHelper.Tx; }

        public View getView(int p, View convert, ViewGroup parent) {
            Object o = baris.get(p);
            if (o instanceof String) {
                if (convert == null) convert = getLayoutInflater().inflate(R.layout.item_bagian, parent, false);
                ((TextView) convert.findViewById(R.id.judul_bagian)).setText((String) o);
                return convert;
            }
            if (convert == null) convert = getLayoutInflater().inflate(R.layout.item_aktivitas, parent, false);
            DbHelper.Tx t = (DbHelper.Tx) o;
            boolean masuk = "masuk".equals(t.jenis);
            String hari = "?", bulan = "";
            try {
                Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(t.tanggal);
                hari = new SimpleDateFormat("dd", Locale.US).format(d);
                bulan = new SimpleDateFormat("MMM yyyy", new Locale("id", "ID")).format(d);
            } catch (Exception abaikan) {}
            ((TextView) convert.findViewById(R.id.hari)).setText(hari);
            ((TextView) convert.findViewById(R.id.bulan)).setText(bulan);
            String nama = (t.catatan != null && !t.catatan.trim().isEmpty()) ? t.catatan : t.kategori;
            ((TextView) convert.findViewById(R.id.nama)).setText(nama);
            TextView j = convert.findViewById(R.id.jumlah);
            j.setText(Util.rp(t.jumlah));
            j.setTextColor(masuk ? 0xFF1F9D63 : 0xFFD64541);
            ((TextView) convert.findViewById(R.id.sub)).setText(
                (masuk ? "Uang masuk" : "Uang keluar") + " • " + t.kategori + " • " + Util.tglCantik(t.tanggal));
            return convert;
        }
    }
}
