package id.hakim.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;

/** Export laporan ke berkas PDF (A4) dengan kop RENS Kitchen dan tabel rapi. */
public class ExportUtil {
    private static final int REQ_SIMPAN = 7712;
    private static byte[] pdfMenunggu;

    private static final int LEBAR = 595, TINGGI = 842, MARGIN = 40;
    // Batas kolom X: Tanggal | Jenis | Kategori | Catatan | Jumlah (rata kanan)
    private static final int[] KOL_X = {MARGIN, 105, 160, 245, 415, LEBAR - MARGIN};

    public static byte[] buatPdf(List<DbHelper.Tx> rows, String judulPeriode) {
        PdfDocument doc = new PdfDocument();
        Paint judul = cat(20, 0xFFFFFFFF, true);
        Paint sub = cat(11, 0xFFCFE6F7, false);
        Paint kepala = cat(10.5f, 0xFFFFFFFF, true);
        Paint isi = cat(10.5f, 0xFF16324F, false);
        Paint isiMasuk = cat(10.5f, 0xFF1F9D63, true);
        Paint isiKeluar = cat(10.5f, 0xFFD64541, true);
        Paint totalCat = cat(12, 0xFF0B2239, true);
        Paint totalMasuk = cat(12, 0xFF1F9D63, true);
        Paint totalKeluar = cat(12, 0xFFD64541, true);
        Paint kaki = cat(9, 0xFF5B7186, false);
        Paint garis = new Paint(); garis.setColor(0xFFD8E4F0); garis.setStrokeWidth(0.8f);
        Paint pitaBiru = new Paint(); pitaBiru.setColor(0xFF0070E0);
        Paint pitaKepala = new Paint(); pitaKepala.setColor(0xFF0B2239);
        Paint selang = new Paint(); selang.setColor(0xFFF5F7FA);

        long masuk = 0, keluar = 0;
        for (DbHelper.Tx t : rows) { if ("masuk".equals(t.jenis)) masuk += t.jumlah; else keluar += t.jumlah; }

        int nomorHalaman = 0;
        int idx = 0;
        boolean selesai = false;
        while (!selesai) {
            nomorHalaman++;
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(LEBAR, TINGGI, nomorHalaman).create());
            Canvas c = page.getCanvas();
            int y;
            if (nomorHalaman == 1) {
                c.drawRect(0, 0, LEBAR, 86, pitaBiru);
                c.drawText("RENS Kitchen", MARGIN, 40, judul);
                c.drawText("Laporan Catatan Uang — " + judulPeriode, MARGIN, 60, sub);
                c.drawText("Diekspor: " + Util.tglCantik(Util.today()) + " • " + rows.size() + " transaksi", MARGIN, 76, sub);
                y = 108;
            } else {
                c.drawText("RENS Kitchen — " + judulPeriode + " (lanjutan)", MARGIN, 36, totalCat);
                y = 52;
            }
            // Kepala tabel
            c.drawRect(MARGIN, y - 14, LEBAR - MARGIN, y + 6, pitaKepala);
            c.drawText("Tanggal", KOL_X[0] + 3, y, kepala);
            c.drawText("Jenis", KOL_X[1] + 3, y, kepala);
            c.drawText("Kategori", KOL_X[2] + 3, y, kepala);
            c.drawText("Catatan", KOL_X[3] + 3, y, kepala);
            String j = "Jumlah (Rp)";
            c.drawText(j, KOL_X[5] - kepala.measureText(j), y, kepala);
            y += 20;

            int barisDiHalaman = 0;
            while (idx < rows.size() && y <= TINGGI - 60) {
                DbHelper.Tx t = rows.get(idx);
                boolean m = "masuk".equals(t.jenis);
                if (barisDiHalaman % 2 == 1) c.drawRect(MARGIN, y - 12, LEBAR - MARGIN, y + 5, selang);
                c.drawText(potong(isi, t.tanggal, KOL_X[1] - KOL_X[0] - 6), KOL_X[0] + 3, y, isi);
                c.drawText(m ? "Masuk" : "Keluar", KOL_X[1] + 3, y, m ? isiMasuk : isiKeluar);
                c.drawText(potong(isi, t.kategori, KOL_X[3] - KOL_X[2] - 6), KOL_X[2] + 3, y, isi);
                c.drawText(potong(isi, t.catatan == null ? "" : t.catatan, KOL_X[4] - KOL_X[3] - 6), KOL_X[3] + 3, y, isi);
                String nominal = (m ? "+ " : "- ") + angka(t.jumlah);
                Paint pc = m ? isiMasuk : isiKeluar;
                c.drawText(nominal, KOL_X[5] - pc.measureText(nominal), y, pc);
                y += 17;
                idx++;
                barisDiHalaman++;
            }

            if (idx >= rows.size()) {
                selesai = true;
                if (y > TINGGI - 130) {
                    // Blok total tidak muat: tutup halaman ini, total di halaman baru.
                    String kakiTeks = "RENS Kitchen • Halaman " + nomorHalaman;
                    c.drawText(kakiTeks, (LEBAR - kaki.measureText(kakiTeks)) / 2f, TINGGI - 24, kaki);
                    doc.finishPage(page);
                    nomorHalaman++;
                    page = doc.startPage(new PdfDocument.PageInfo.Builder(LEBAR, TINGGI, nomorHalaman).create());
                    c = page.getCanvas();
                    y = 60;
                }
                y += 16;
                c.drawLine(MARGIN, y - 12, LEBAR - MARGIN, y - 12, garis);
                gambarTotal(c, "Total Masuk", "+ " + angka(masuk), totalCat, totalMasuk, y + 6);
                gambarTotal(c, "Total Keluar", "- " + angka(keluar), totalCat, totalKeluar, y + 26);
                gambarTotal(c, "Selisih", angka(masuk - keluar), totalCat, totalCat, y + 46);
            }

            String kakiTeks = "RENS Kitchen • Halaman " + nomorHalaman;
            c.drawText(kakiTeks, (LEBAR - kaki.measureText(kakiTeks)) / 2f, TINGGI - 24, kaki);
            doc.finishPage(page);
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try { doc.writeTo(bos); } catch (Exception abaikan) {}
        doc.close();
        return bos.toByteArray();
    }

    private static void gambarTotal(Canvas c, String label, String nilai, Paint catLabel, Paint catNilai, int y) {
        c.drawText(label, KOL_X[3] + 3, y, catLabel);
        c.drawText(nilai, KOL_X[5] - catNilai.measureText(nilai), y, catNilai);
    }

    private static Paint cat(float ukuran, int warna, boolean tebal) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(ukuran);
        p.setColor(warna);
        if (tebal) p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return p;
    }

    private static String angka(long n) {
        String s = java.text.NumberFormat.getInstance(new java.util.Locale("id", "ID")).format(Math.abs(n));
        return (n < 0 ? "-Rp " : "Rp ") + s;
    }

    /** Potong teks agar muat selebar kolom (diakhiri …). */
    private static String potong(Paint p, String teks, float lebarMaks) {
        if (teks == null) return "";
        if (p.measureText(teks) <= lebarMaks) return teks;
        String el = "…";
        int n = p.breakText(teks, true, lebarMaks - p.measureText(el), null);
        return teks.substring(0, Math.max(n, 0)) + el;
    }

    /** Buka pemilih berkas sistem (SAF) untuk menyimpan PDF. */
    public static void mulaiSimpan(Activity a, String namaFile, byte[] pdf) {
        pdfMenunggu = pdf;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_TITLE, namaFile);
        a.startActivityForResult(i, REQ_SIMPAN);
    }

    /** Panggil dari onActivityResult activity pemanggil. */
    public static void selesaiSimpan(Activity a, int req, int result, Intent data) {
        if (req != REQ_SIMPAN) return;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(a, "Export dibatalkan.", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = data.getData();
        try (OutputStream os = a.getContentResolver().openOutputStream(uri)) {
            os.write(pdfMenunggu == null ? new byte[0] : pdfMenunggu);
            Toast.makeText(a, "✅ Laporan PDF tersimpan.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(a, "Export gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        pdfMenunggu = null;
    }
}
