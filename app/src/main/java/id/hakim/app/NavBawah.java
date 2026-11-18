package id.hakim.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Bilah menu bawah ala aplikasi dompet digital: Beranda, Mutasi, Cash Flow,
 * Aktivitas. Dipasang di keempat layar utama; item aktif berwarna biru
 * #0070E0, sisanya abu-abu.
 */
public class NavBawah {
    static final int BIRU = 0xFF0070E0;
    static final int ABU = 0xFF5B7186;
    static final int ABU_IKON = 0xFF8CA3B8;

    public static void pasang(Activity a, String aktif) {
        pasangSatu(a, "beranda", R.id.nav_beranda, R.id.nav_ic_beranda, R.id.nav_tx_beranda, aktif, MainActivity.class);
        pasangSatu(a, "mutasi", R.id.nav_mutasi, R.id.nav_ic_mutasi, R.id.nav_tx_mutasi, aktif, MutasiActivity.class);
        pasangSatu(a, "cashflow", R.id.nav_cashflow, R.id.nav_ic_cashflow, R.id.nav_tx_cashflow, aktif, ReportActivity.class);
        pasangSatu(a, "aktivitas", R.id.nav_aktivitas, R.id.nav_ic_aktivitas, R.id.nav_tx_aktivitas, aktif, AktivitasActivity.class);
    }

    private static void pasangSatu(Activity a, String kunci, int idWadah, int idIkon, int idTeks,
                                   String aktif, Class<?> tujuan) {
        android.view.View wadah = a.findViewById(idWadah);
        if (wadah == null) return;
        boolean on = kunci.equals(aktif);
        ImageView ikon = a.findViewById(idIkon);
        TextView teks = a.findViewById(idTeks);
        if (ikon != null) ikon.setColorFilter(on ? BIRU : ABU_IKON, PorterDuff.Mode.SRC_IN);
        if (teks != null) {
            teks.setTextColor(on ? BIRU : ABU);
            teks.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        wadah.setOnClickListener(v -> {
            if (on) return;
            Intent i = new Intent(a, tujuan);
            if (tujuan == MainActivity.class) {
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                a.startActivity(i);
            } else {
                a.startActivity(i);
                if (!(a instanceof MainActivity)) a.finish();
            }
        });
    }
}
