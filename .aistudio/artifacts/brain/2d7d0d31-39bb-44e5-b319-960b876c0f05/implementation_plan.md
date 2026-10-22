# Kapsamlı Arayüz Sadeleştirme, Tek Kompakt Bar, Karanlık Tema ve PDF Kenar Genişletme Planı

Kullanıcının bildirdiği arayüz kalabalığını gidermek, tüm üst çubukları tek bir şık ve kompakt çubuğa dönüştürmek, ana ekranı ve yan menüyü derleyip toplamak, tam kapsamlı karanlık tema eklemek ve PDF sayfalarının çevresine not alma boşluğu (margin) kazandırmak için hazırlanan uygulama planı.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Kullanıcı Anketi Sonuçları & Tercihler:**
> 1. **PDF Kenar Boşluğu:** Sayfa ayarlarından her yöne özel (sol, sağ, üst, alt) ayarlanabilir boşluk.
> 2. **Ana Ekran Defter Koleksiyonu:** Kompakt ızgara (compact grid) ve kompakt liste görünümü arasında geçiş imkanı ile yer tasarrufu.
> 3. **Karanlık Tema:** Sistem ayarı ile senkronize, açık ve koyu tema arasında manuel geçiş seçeneği (Material 3 Dark Palette).

---

## 1. Mimari Bileşenler ve Tasarım Değişiklikleri

### A. Tuval Ekranında Tek Kompakt Bar (`UnifiedCanvasTopBar`)
- **Mevcut Durum:** `AppBar` (üst başlık + zoom + ses), `AudioReplayBar` (ses oynatma) ve `Toolbar` (kalemler, renkler, boyutlar, palet) alt alta 3 ayrı katman halinde dizilerek dikeyde 160dp'den fazla alan işgal ediyor.
- **Yeni Çözüm:** 56dp yüksekliğinde tek bir şık, modern ve fonksiyonel üst çubuk:
  - **Sol Bölüm:** Geri dönüş butonu, Defter Başlığı, Sayfa Sayacı (`Sayfa 1 / 10`) ve Son Kayıt rozeti.
  - **Orta Bölüm (Çizim Araçları):**
    - Geri Al / İleri Al butonları.
    - Kompakt Araç Grubu: Dolma Kalem, Tükenmez, Fırça, Vurgulayıcı, Silgi, Kement.
    - Hızlı Renk Göstergesi (aktif rengi gösteren ve tıklandığında mini palet açan nokta).
    - Çizgi Kalınlığı göstergesi (tıklandığında hızlı slider açar).
    - El Ayası Reddi (Palm Rejection / Stylus Only) toggle ikonu.
  - **Sağ Bölüm:**
    - Zoom rozeti (%100, Sığdır, +/-).
    - Ses Eşzamanlama Kayıt butonu (Mic).
    - PDF Kenar Boşluğu ayar butonu (`aspect_ratio` / `crop_free`).
    - Taşma Menüsü (Dışa Aktar, Ses Kayıtları, Tam Ekran).
  - *Ses Oynatma Replay Barı:* Ayrı bir devasa kart yerine, aktif olduğunda barın hemen altına 28dp'lik incecik bir oynatıcı çizgi olarak entegre edilecek.

### B. Ana Ekran ve Yan Menü Kompaktlaştırma (`Shell.kt` & `LibraryScreen.kt`)
- **Kompakt Yan Menü (Sidebar):** 96dp yerine 72dp'lik şık Material 3 Navigation Rail standardına çekilecek. Gereksiz dolgular azaltılacak.
- **Klasörler Bölümü:** Hantal kutular yerine ince, zarif ve kompakt klasör listesi.
- **Defter Koleksiyonu:**
  - Hantal büyük kartlar yerine daha sıkı, modern ve kompakt kart tasarımı (bilgiler derli toplu, gereksiz boşluklar yok).
  - Kullanıcının seçebileceği **Kompakt Liste** görünümü (tek satırda defter adı, sayfası, son düzenleme tarihi ve hızlı aç butonu).

### C. Karanlık Tema Desteği (`Theme.kt`, `AppViewModel.kt`, `SettingsScreen.kt`)
- Material 3 standartlarında yüksek kontrastlı ve göz yormayan `InkDarkScheme` renk paleti oluşturulacak:
  - Arka planlar: `#121216`, yüzeyler: `#1C1B20`, konteynerler: `#25242A`.
  - Canlı birincil renkler ve okunaklı açık tonlu metinler.
- `AppViewModel` içerisine `ThemeMode { SYSTEM, LIGHT, DARK }` durumu eklenecek ve `MainActivity` / `InkTheme` üzerinden anında tüm ekrana yansıtılacak.
- Yan menü veya ayarlara hızlı Tema Geçiş butonu yerleştirilecek.

### D. PDF Sayfalarını Genişletme (Kenar Boşluğu / Margin Expansion)
- `Notebook` modeline her yön için ayarlanabilir kenar payları eklenecek:
  - `pdfMarginLeft`, `pdfMarginRight`, `pdfMarginTop`, `pdfMarginBottom` (örneğin %0 ile %50 arası).
- **Tuval Çizim Alanı Genişletmesi:**
  - PDF sayfası ekranda gösterilirken, belirlenen kenar boşlukları oranında dış kapsayıcı genişletilecek.
  - PDF içeriği ortalanıp korunurken, sayfanın sağına/soluna/altına/üstüne orijinal kağıt renginde boş not alma alanları eklenecek.
  - `InkPageView` bu genişletilmiş alanın tamamını kapsayacak; böylece kullanıcılar PDF'in kenarlarına, sağına ve soluna özgürce el yazısı notlar, şekiller ve Cornell usulü özetler yazabilecek.
- PDF Kenar Ayarı için kullanıcı dostu bir ayar diyaloğu (`PdfMarginDialog`) eklenecek.
- `Exporter.kt` PDF dışa aktarma işlemi de bu kenar boşluklarını içerecek şekilde güncellenecek.

---

## 2. Değişiklik Yapılacak Dosyalar

1. **`app/src/main/java/com/inknote/app/data/Models.kt`:**
   - `Notebook` sınıfına `pdfMarginLeft`, `pdfMarginRight`, `pdfMarginTop`, `pdfMarginBottom` alanları ve JSON serileştirme desteği eklenecek.
2. **`app/src/main/java/com/inknote/app/ui/Theme.kt`:**
   - `InkDarkScheme` renk şeması tanımlanacak.
   - `InkTheme` fonksiyonu `darkTheme: Boolean` parametresini destekleyecek.
3. **`app/src/main/java/com/inknote/app/AppViewModel.kt`:**
   - `ThemeMode` enum ve durum yönetimi eklenecek.
   - Aktif defter için PDF kenar boşluklarını güncelleme fonksiyonu (`updatePdfMargins`) eklenecek.
4. **`app/src/main/java/com/inknote/app/ui/CanvasScreen.kt`:**
   - 3 parçalı dağınık çubuklar kaldırılarak tek satırlık `UnifiedCanvasTopBar` oluşturulacak.
   - `PageItem` içerisinde PDF kenar boşlukları (sol/sağ/üst/alt) uygulanacak ve çizim tuvali genişletilecek.
   - `PdfMarginDialog` bileşeni eklenecek.
5. **`app/src/main/java/com/inknote/app/ui/Shell.kt`:**
   - `Sidebar` genişliği 72dp'ye düşürülüp kompakt simgelerle sadeleştirilecek.
   - Hızlı karanlık tema geçiş butonu eklenecek.
6. **`app/src/main/java/com/inknote/app/ui/LibraryScreen.kt`:**
   - Defter kartları kompaktlaştırılacak, liste/ızgara görünümü temizlenecek.
   - Sol klasör paneli derli toplu kompakt hale getirilecek.
7. **`app/src/main/java/com/inknote/app/ui/SearchScreen.kt` (SettingsScreen):**
   - Tema seçimi (Sistem / Açık / Koyu) ayarlar ekranına eklenecek.
8. **`app/src/main/java/com/inknote/app/data/Exporter.kt`:**
   - PDF dışa aktarırken kenar boşluklarını hesaba katarak çıktı oluşturma desteği eklenecek.

---

## 3. Doğrulama ve Test Adımları

1. `gradle :app:testDebugUnitTest` ile birim testlerin doğrulanması.
2. `compile_applet` ile projenin sıfır hatayla derlendiğinin teyit edilmesi.
3. Canlı arayüzde:
   - Tek çubuğun tüm işlevleri (araçlar, renkler, kalınlık, geri/ileri al, zoom, ses) kompakt bir biçimde sunduğu,
   - Karanlık tema açıldığında renklerin kusursuz uyum sağladığı,
   - PDF kenar genişletme yapıldığında sağında/solunda/altında/üstünde geniş not alma marjlarının oluştuğu ve buralara çizim yapılabildiği test edilecek.
