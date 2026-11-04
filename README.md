# Belge Tarayıcı — Android projesi (sürüm 1.1)

CamScanner benzeri uygulama. Bu sürümde eklenenler:

| Özellik | Nasıl çalışıyor |
|---|---|
| **Türkçe OCR** | ML Kit Metin Tanıma (Latin alfabesi, model uygulamaya gömülü → internet gerekmez). Ana ekrandan "Fotoğraftan Metin" (galeri/kamera) veya bir belgenin menüsünden "Metne çevir (OCR)". Sonuç düzenlenebilir, kopyalanır, paylaşılır; belge OCR'ı `ocr.txt` olarak saklanır. |
| **Belge arşivi** | Taranan PDF + sayfa JPEG'leri telefonun uygulama-özel depolamasında (`files/arsiv/`) saklanır. |
| **Klasörler** | Varsayılan: Faturalar, Kimlikler, Evraklar. Yeni klasör ekleme, yeniden adlandırma, silme; belgeyi klasörler arası taşıma. |
| **PDF yönetimi** | Aç (uygulama içi görüntüleyici), başka uygulamada aç, paylaş, yeniden adlandır, taşı, sil. |
| **Çevrimdışı** | Arşiv, PDF görüntüleme ve OCR internetsiz çalışır. Ayrıntı aşağıda. |

## Arşiv yapısı
```
files/arsiv/<Klasör>/<belge-kimliği>/
    belge.pdf   sayfa_001.jpg ...   ocr.txt (varsa)   meta.json
```
Veritabanı yoktur; klasör = dizin. `allowBackup=false` yapıldı: kimlik gibi belgeler yedekle buluta gitmez. Uygulama silinirse arşiv de silinir.

## Çevrimdışı kullanım — bilinmesi gerekenler
- Belge tarama ekranı (kırpma, perspektif düzeltme) Google Play hizmetlerinin modülüdür. Modül cihazda yoksa **ilk taramada bir kez internetle indirilir**; sonrasında çevrimdışı çalışır. Play hizmetleri olmayan cihazlarda tarayıcı açılmaz.
- OCR modeli uygulamaya gömülü olduğu için indirme gerektirmez (APK birkaç MB büyür).

## OCR doğruluğu hakkında
ML Kit'in Latin modeli ğ, ş, ı, İ, ç, ö, ü harflerini genelde tanır ama Türkçe'ye özel eğitilmiş değildir; bozuk/eğik fotoğraflarda ı/i ve ş/s karışabilir. Gerçek belgelerinizle deneyin. Yeterli değilse sonraki adım Tesseract + `tur.traineddata` (assets içine konur, yine çevrimdışı) olur; `Ocr.kt` bu yüzden tek dosyada tutuldu.

## Derleme
Android Studio (Giraffe+), JDK 17, Android SDK 35. Klasörü açın, Gradle senkronizasyonunu bekleyin, Run. (Bağımlılıkları indirmek için ilk derlemede internet gerekir.)

## Dosyalar
- `MainActivity` klasör listesi, tarama, fotoğraftan OCR
- `FolderActivity` belge listesi ve belge işlemleri
- `PdfViewerActivity` PdfRenderer ile görüntüleyici
- `TextActivity` OCR metni
- `BaseActivity` tarama akışı + ortak diyaloglar
- `Archive` dosya tabanlı arşiv, `Ocr` metin tanıma, `Ui` yardımcılar

## Henüz yok
Parola korumalı PDF, arşivde arama, PDF içe aktarma, bulut senkronizasyonu, uygulama içi yakınlaştırma (yakınlaştırmak için "Başka uygulamada aç").
