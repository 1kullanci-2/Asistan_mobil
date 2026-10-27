# E.D.I.T.H.

Sesli asistan projesi. Bu repo Android uygulamasıdır (Kotlin + Jetpack Compose).

## APK nasıl alınır
`main` dalına her gönderimde GitHub Actions uygulamayı derler. Bitince **Releases → Son sürüm** altındaki
`app-debug.apk` dosyasını telefondan indirip kurabilirsin (ilk kurulumda "bilinmeyen kaynaklara izin" gerekir).

## Aşamalar
1. İskelet, arayüz, ayarlar, Gemini ile yazılı sohbet
2. Mikrofon + Vosk (TR/EN) + uyandırma kelimesi + sesli cevap (Gemini TTS → ElevenLabs) + ön plan bildirimi
3. MCP bağlantıları ve araç çağırma (0.2'de ilk sürüm var)
4. İnce ayar

## 0.3
- Vişne/krem düz renk teması (simge ve logolarda kullanılmaz).
- Ayarlar: ana ekranda kategori kartları (Yapay zeka, Ses, Uyandırma, Mikrofon, Bağlantılar, Panel ve izinler).
- Ses: Gemini ve ElevenLabs için yatay kayan kare ses kartları; karta dokununca seçilir ve örnek çalar. ElevenLabs: hesap/kredi, sesler, modeller, karakter ayarları.
- Uyandırma kelimesi uygulama dışında söylenince dinleme paneli açılır (varsayılan dijital asistan veya "diğer uygulamaların üzerinde göster" izni; izin otomatik istenmez, Ayarlar → Panel ve izinler).

## Sesli kullanım (0.2)
- "Mikrofon" düğmesi dinlemeyi açar; ilk açılışta Vosk modelleri (Türkçe ~35 MB, İngilizce ~40 MB) indirilir.
- Bekleme: yalnızca İngilizce model "hey edit" / "edit" dinler. Duyunca Türkçe model komutu dinler; 1,5 sn sessizlikte gönderilir.
- Seslendirme sürerken "edit" denirse ses kesilir. Cevap soru içeriyorsa mikrofon yeniden açılır.
- "ayarlar" yazıp/söyleyip gönderince ayarlar paneli açılır.

API anahtarları uygulama içinden girilir, repoda tutulmaz.
