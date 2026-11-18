# Catatan Uang — Aplikasi Pencatat Keuangan Android (Firebase)

Aplikasi Android untuk mencatat uang masuk dan keluar, dengan label aplikasi "RENS Kitchen". Pengguna masuk dengan akun Google; data tersimpan lokal di SQLite dan disinkronkan dua arah ke Cloud Firestore, dan tersedia juga mode lokal tanpa login.

## Fitur utama

- Login dengan akun Google (Firebase Authentication).
- Dashboard saldo dan total pemasukan/pengeluaran bulan berjalan, dengan tombol sembunyikan/tampilkan saldo.
- Form transaksi masuk/keluar dengan kategori, termasuk kategori pemasukan per toko (Toko 1, Toko 2, Toko 3).
- Halaman Mutasi dan Aktivitas per bulan.
- Laporan Cash Flow: ringkasan masuk/keluar/selisih, diagram donat, dan grafik garis pemasukan harian per toko.
- Ekspor laporan ke PDF.
- Sinkronisasi dua arah antara SQLite lokal dan Firestore.

## Teknologi

- Java native Android (minSdk 24), layout XML
- SQLite untuk penyimpanan lokal
- Firebase Authentication (Google Sign-In, play-services-auth 21.4.0) dan Cloud Firestore melalui REST API
- APK dibangun dengan pipeline manual: aapt2 → javac → d8 → zipalign → apksigner

## Cara menjalankan

Prasyarat: JDK 17 dan Android SDK 34 (build-tools 34.0.0). Sesuaikan jalur SDK/JDK di `build-firebase.sh` dengan komputer Anda, lalu jalankan:

```
./build-firebase.sh
```

Proyek juga dapat dibuka di Android Studio untuk membaca dan mengedit kodenya.

Konfigurasi Firebase tidak disertakan dalam repo ini. Untuk mengaktifkan login Google dan sinkronisasi:

1. Buat proyek Firebase sendiri di https://console.firebase.google.com
2. Daftarkan aplikasi Android dengan package `id.hakim.app`.
3. Aktifkan Sign-In Google di Authentication dan buat database Firestore.
4. Unduh `google-services.json` dan letakkan di folder `app/` (berkas ini termasuk `.gitignore`, jangan diterbitkan).
5. Isi nilai di `app/src/main/java/id/hakim/app/FirebaseConfig.java` dari `google-services.json` tersebut (project_id, current_key, dan client_id bertipe 3).

---
Proyek belajar dan portofolio oleh Andy Hakim.
