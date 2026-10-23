# เปลี่ยน Home และปลดสวิตช์ USB debugging บน TrueID Skyworth HPH07

บันทึกการทดลองกับกล่องของเจ้าของเครื่อง วันที่ 23 ตุลาคม 2026: Android 9, firmware P2.2.0_20231207, security patch 5 ธันวาคม 2023, kernel 4.9.116_hi3798mv2x และแอป TrueID รุ่น 25.02.21.22 ผลนี้จำกัดอยู่ที่รุ่นและเฟิร์มแวร์ที่ทดสอบ ไม่ใช่สูตรสำหรับกล่อง Android ทุกเครื่อง

## ปัญหาที่เห็นกับสาเหตุที่พบ

เปิด Developer options ได้ แต่ USB debugging เป็นสีเทา การเปลี่ยน MTP/PTP ไม่ได้ตอบโจทย์ เพราะการเลือกโหมด USB กับสิทธิ์เปิด ADB เป็นคนละเรื่อง

ตรวจ APK Settings ที่ดึงจากกล่อง พบว่า `DevelopmentFragment.onResume` อนุญาตสวิตช์เมื่อ system property `persist.truetv.adbcommand` เท่ากับ `true` เท่านั้น ค่าเดิมของกล่องว่าง จึงมีหลักฐานตรงจากโค้ดว่าตัวเลือกถูกกั้นด้วย vendor property

ติดตั้ง APK ที่เขียนเองได้ในภายหลัง แม้ก่อนหน้านั้น X-plore ผ่าน Play Store จะติดตั้งไม่สำเร็จ ข้อผิดพลาด Play Store เป็นข้อความทั่วไป จึงยังสรุปไม่ได้ว่าระบบห้ามติดตั้ง APK ทุกชนิด

## ช่องทางที่ใช้ได้จริง

ใช้ [REA](https://github.com/morluto/rea) เป็นแหล่งอ้างอิงงานวิเคราะห์ และใช้ JADX แบบ CLI ตรวจ APK ที่ติดตั้งจริง REA ไม่ได้เป็นตัวเจาะกล่องผ่านเครือข่าย การวิเคราะห์ที่ทำใช้เฉพาะคลาสและ DEX ที่เกี่ยวข้อง เพราะการ decompile ทั้งแอปขนาดใหญ่ใช้หน่วยความจำมาก

พบ exported service `com.truedmp.ottapp.TrueDmpSystemService` และช่องทาง Binder สำหรับอ่าน/เขียน property ระบบ ตัว service เชื่อ extra `show_build_type` ที่ส่งมาตอน bind และเส้นทางตรวจ caller ใน debug mode ยอมรับ process name ที่มี `com.truedmp.idtv` อยู่ภายใน

แอปช่วยของเราจึงใช้ process ส่วนตัวที่มีข้อความนั้นในชื่อ และ bind ด้วย extra ดังกล่าว เมื่อผ่านตรวจ API แล้ว manager Binder เปิดทางไปยัง utility Binder ที่มี `getProp` และ `setProp` ใช้เปลี่ยนเฉพาะ `persist.truetv.adbcommand` จากค่าว่างเป็น `true` จากนั้นอ่านกลับตรวจสอบ

ผลบนเครื่องจริง: สวิตช์ USB debugging กดได้ ยอมรับหน้าต่างอนุญาตกุญแจ ADB ของ Mac และสั่ง `id` ได้เป็น uid 2000 (`shell`) ไม่ได้ root ค่า debug ชั่วคราวของตัว service ถูกคืนเป็น release หลังใช้งาน ไม่ได้แก้ `ro.debuggable` หรือแฟลชเฟิร์มแวร์

การส่ง protected broadcast ที่ลองก่อนหน้านั้นถูกปฏิเสธ จึงไม่ใช่วิธีที่ทำสำเร็จ ไม่มีการใช้ Dirty Pipe, UART หรือบูต SD card และยังไม่มีหลักฐานว่า SD image ของรุ่นอื่นใช้กับบอร์ดนี้ได้

## ทำไมกลับ Home แล้ว ADB หายอีก

การปลดสวิตช์ไม่ได้ลบนโยบายของแอป TrueID พบโค้ด `EnableDisableAdbUseCase` และการตัดสินใจจาก remote configuration ที่ส่งคำสั่งผ่าน privileged OTT gateway เพื่อปิด ADB ได้อีก

ทดลองถอน `WRITE_SECURE_SETTINGS` จากแอป TrueID แล้ว cold-start หนึ่งครั้ง ตัวนับใน maintenance helper ยังพบการปิด ADB เพิ่มขึ้น จึงคืน permission ทันที ไม่ถอนสิทธิ์จาก OTT gateway เพราะใช้ shared UID 1000 ของระบบ

ทางออกที่ใช้ระหว่างทำงานคือแอป **TrueTV ADB Control** มีปุ่มเปิด ADB สำหรับ session หกชั่วโมงและปุ่มปิดพร้อมหยุด session ใช้ foreground service กับ ContentObserver ไม่มี network polling หรือ boot receiver และมี rate limit เพื่อไม่วนสู้กับระบบไม่สิ้นสุด ทดสอบปิดแล้วพอร์ต 5555 ตอบ Connection refused และเปิดกลับด้วยรีโมทแล้ว shell ใช้งานได้

นี่เป็น **workaround ที่ผู้ใช้ควบคุมได้** นโยบายอัตโนมัติใน TrueID ยังอยู่ ไม่ควรเขียนว่าปลดข้อจำกัด ADB ถาวรทั้งหมดแล้ว

## Launcher ใหม่ถูกลบเองด้วย

ใช้ launcher จาก [cleanyourtv](https://github.com/Gathin23/cleanyourtv) แก้เฉพาะ `config.xml` ให้เป็น dock สี่ช่องและหน้าดำแบบเบา แต่พบว่าแอปหายจริงหลังเข้า TrueID

ตรวจโค้ด `a8l.d3()` พบการหาแอปที่ประกาศ MAIN/HOME แล้วสั่งถอนแอปที่ไม่ใช่ system/updated-system ยกเว้นรายการ test package บางตัว งานนี้ถูกตั้งเวลาไว้หลังเริ่ม SDK ประมาณ 30 วินาที

แก้ด้วย helper แบบรันครั้งเดียวเรียก package manager ให้บล็อก uninstall เฉพาะ `com.gathin.tvhome` บันทึกค่าเดิม false และมีคำสั่งคืน false ใน restore ไม่ได้ปิด TrueID ไม่ได้ตั้งข้อห้ามถอนแอปทั้งเครื่อง และไม่มี background loop คอยติดตั้งใหม่ ทดสอบ cold-start TrueID รอเกินเวลาล้าง แล้ว launcher ยังอยู่

อีกปัญหาคือ HOME intent ของ TrueID priority สูงกว่า แอปทั่วไปตั้ง priority เท่ากันไม่ได้ใน Android 9 แม้ `set-home-activity` พิมพ์ Success ตัว resolver ยังเลือก TrueID จึงใช้ `HomeKeyService` ที่ launcher มีอยู่แล้ว พร้อม `KEYCODE_HOME:com.gathin.tvhome`

เจ้าของกดปุ่ม Home บนรีโมทจริงแล้วเข้า launcher ใหม่ได้ ตรวจ activity ที่ foreground และภาพจากเครื่องยืนยันตรงกัน เจ้าของยังทดสอบกดเพิ่มเสียงค้างและเสียงไล่ขึ้นต่อเนื่องได้ ส่วนการ inject Home ผ่าน ADB ไม่ใช่หลักฐานเทียบเท่าปุ่มจริงในเส้นทางนี้

## สิ่งที่เก็บไว้และผลที่ยังไม่ควรสรุป

เก็บ package list, disabled list, getprop, settings ทุก namespace และสถานะสำคัญก่อนแก้ ปิดเพียง Google Feedback กับ TV Bug Report Sender ใช้ `pm disable-user --user 0` ไม่ถอน system app ทดลอง restore เฉพาะสองตัวแล้ว disabled list ตรงกับก่อนแก้ จากนั้นใช้รายการเดิมอีกครั้ง

ทั้งสอง service ไม่ได้ทำงานใน baseline จึงไม่มีหลักฐานว่าปิดแล้ว RAM หรือ CPU ดีขึ้นทันที กล่องนี้มี RAM ประมาณ 2 GB และ snapshot ที่วัดไม่ได้แสดง CPU เต็ม การใช้ Home ที่เบาขึ้นไม่ได้รับประกันว่าโฆษณาหรือความช้าภายในแอป Live TV จะหาย

Live TV แสดงภาพ Thai PBS เปลี่ยนจริงหลังแก้ Netflix เปิดได้และเคยรายงาน media session เป็น PLAYING ไม่มี error แต่ต่อมาเป็น STOPPED ภาพ capture ดำ เจ้าของไม่มีสมาชิก Netflix จึงเปลี่ยนไปทดสอบ HBO Max ติดตั้งแอปทางการจาก Play Store แล้วเจ้าของยืนยันว่าเล่นเนื้อหาจริงมีภาพและเสียงปกติหลัง reboot ยืนยันเส้นทางเล่นนี้ได้ แต่ไม่ได้ทดสอบทุกบริการ ทุกความละเอียด หรือ HDR ไม่แตะ provisioning, DRM keys, บัญชี หรือ firmware

หลัง reboot ตรวจแล้วว่า launcher ยังติดตั้งอยู่ ค่า block-uninstall ยัง true รายการแอปที่ disable ไม่เปลี่ยน และเจ้าของรายงานว่าเข้า launcher ใหม่ได้อีกครั้ง ตอนจบหยุด maintenance service ยืนยันยกเลิกสิทธิ์ USB debugging ในเมนู Android ปิด debugging และ reboot แล้ว ตรวจพบพอร์ต 5555 ตอบ Connection refused ขณะที่บริการรีโมทกลับมาออนไลน์ พอร์ต screen sharing ปิดด้วย กด Home ผ่านรีโมทที่ pair ไว้แล้วกลับ launcher ใหม่ได้อีกครั้ง ตอนบูตอาจเห็น TrueID ก่อนกด Home รายการคืนค่าอยู่ใน `tools/setup.py restore` ส่วนรายละเอียดใช้งานและข้อจำกัดอยู่ใน `../README.md` ก่อนนำไปเผยแพร่ต้องตัด token, private key, serial, บัญชี, MAC, IP ภายใน และชื่อ Wi-Fi ออกจากหลักฐานทั้งหมด

## Live TV ตรงๆ โดยไม่ผ่านเมนู TrueID — และบั๊ก crash ที่เจอระหว่างทาง

แอป TrueID TV เองมี deep link `truedmpatv://trueidapp/LIVE/<เลขช่อง>` ที่พาเข้า live player ตรงๆ ข้ามหน้า home grid/VOD/cloud gaming/social ทั้งหมด พบค่าคงที่นี้ในซอร์ส (`xy3.n3 = "truedmpatv://trueidapp/LIVE/"` และพี่น้อง `/VOD/`, `/CW/`, `/EPG/`) ตัวแปร `xy3.h3 = "LIVE"` ถูกเช็คใน `r2.h()` (ATVHelper.java)

ลองยิงด้วย URI prefix เฉยๆ (ไม่มีเลขช่อง) พบว่า **แอป crash วนซ้ำจริง**: `IndexOutOfBoundsException` ที่ `android.net.Uri$PathSegments.get(Uri.java:997)` เรียกจาก `r2.h(ATVHelper.java:47)` — โค้ดเช็ค `getPathSegments().get(0).equals("LIVE")` แล้วอ่าน `getPathSegments().get(1)` ต่อทันทีโดยไม่เช็คว่ามี segment ที่สองจริงไหม เป็นบั๊กจริงของแอปเวอร์ชัน `25.02.21.22` ไม่ใช่ที่เครื่องมือนี้ทำ ยืนยันด้วย `logcat -b crash` ซ้ำ 4 ครั้งติดกันจากการยิง URI รูปแบบเดิม แก้โดยใส่เลขช่องเสมอ เช่น `truedmpatv://trueidapp/LIVE/1`

`android/livetv-shortcut/` เป็นแอป trampoline เล็กๆ ที่เปิดแล้วยิง deep link ที่ถูกต้องทันทีแล้ว `finish()` ตัวเอง ใช้แทนตำแหน่ง TrueID บน dock ของ launcher ได้ (ตั้ง `dock_apps`/`hidden_apps`/`live_tv_package` ใน cleanyourtv's `config.json` ให้ชี้มาที่ `local.tv.livetv` แทน `com.truedmp.idtv`) ทดสอบกดจริงจากรีโมทจนเห็นภาพสดเต็มจอแล้ว เลขช่อง (ค่า default ในซอร์สคือ `1`) เป็น index ภายในของแอป ไม่ใช่เลขช่องออกอากาศ และต่างกันไปตาม region/ผู้ให้บริการ ต้องลองเองเพื่อหาเลขที่ตรงกับช่องที่ต้องการ

## TrueID ค้าง RAM เพราะอะไร และทำไม `pm disable` ปิดบางส่วนไม่ได้

วัด `dumpsys meminfo com.truedmp.idtv` ตอน idle พบใช้ ~88MB (Native Heap ~52MB + Dalvik ~31MB + Dalvik Other ~4.5MB) ที่น่าแปลกคือ `killBackgroundProcesses` เรียกแล้วไม่มีผลเลย ตรวจ `dumpsys activity services com.truedmp.idtv` พบสาเหตุ: แอปรัน **สอง foreground service แบบไม่มี notification ให้เห็น** (`channel=NeverDisplayThisNotification`) ค้างตลอดเวลาคือ `com.truedigital.remoteservicelib.core.AppRemoteService` (รับคำสั่งจากแอปมือถือ TrueID ที่ใช้เป็นรีโมท) และ `com.truedmp.idtv.tv_settings.TvSettingsMenuService` — Android ปกป้อง process ที่ถือ foreground service จาก `killBackgroundProcesses` โดยดีไซน์ ไม่ใช่บั๊ก

ลองปิดสอง service นี้แยกด้วย `pm disable <component>` และ `pm disable-user --user 0 <component>` ทั้งคู่ถูก Android เองปฏิเสธ: `SecurityException: Shell cannot change component state for ... to 2/3` — shell ปิดทั้งแอปได้ (ใช้ได้กับ Google Feedback/Bug Report Sender ในหัวข้อก่อนหน้า) แต่ปิด component เดี่ยวของแอปอื่นไม่ได้ เป็นข้อจำกัดของ Android เองที่กันไม่ให้ ADB ไปรื้อบางส่วนของแอปอื่นแบบเจาะจุด

ทางที่ใช้ได้จริงคือ `am force-stop` ซึ่งข้ามการป้องกัน foreground-service ได้ (เป็นคนละ API/เส้นทางจาก `killBackgroundProcesses`) แต่ `force-stop` เป็นสิทธิ์ระดับ shell เท่านั้น แอปทั่วไปเรียกเองไม่ได้ จึงต้องรันจากนอกเครื่อง (`tools/ram_guard.py`, ดูคอมเมนต์ในไฟล์) แทนที่จะพยายามทำทั้งหมดบนตัวทีวีเอง

วัดก่อน/หลัง `force-stop`: `MemFree` จาก 112MB ขึ้นเป็น 352MB, `MemAvailable` จาก 647MB เป็น 887MB — เพิ่มขึ้นจริง ~240MB มากกว่าที่ idtv กินเองเกือบ 3 เท่า เพราะตอนมันทำงานมันดึง cached page อื่นๆ ของระบบติดไปด้วย ตัวเลขจะต่างกันไปตามเครื่องและแอปที่ลงเพิ่ม วัดของตัวเองด้วย `tools/tv.py` ก่อนเชื่อตัวเลขนี้ตรงๆ

## สืบโฆษณา Live TV — ลองผิดสองรอบก่อนเจอของจริง

สมมติฐานแรก (ยังไม่ได้วัด): เดาว่าเป็น server-side ad insertion เย็บเข้ากับสตรีมจากโดเมนเดียวกับ content — **ผิด** ตรวจ decompile ย้อนไปที่ use case จริง (`GetPreRollAdsParamsUseCase`/`j09.java`, `ShouldLoadAdsUseCase`/`bpi.java`, `PrepareAdURLUseCase`/`b1g.java`) พบว่าการตัดสินใจว่าจะมีโฆษณาไหมมาจาก **Firebase Remote Config** (`gr7.o8()` คืน rule ตาม content type + channel id, fallback เป็น wildcard `"*"`) ผสมกับ cache ท้องถิ่นใน **CouchBase Lite** (`gsj`, log บรรทัด `[CouchBaseData]`) ไม่ใช่ network call สดตอนสลับช่องเลย

ลองบล็อก DNS โดเมนที่ดูเกี่ยวกับ ads ของ True เอง (`cms-sccmixer-dmpapi.trueid.net`) ที่ router — **ไม่ได้ผล** โฆษณายังขึ้นเหมือนเดิม เพราะดักผิดจุด (ตัวตัดสินใจไม่ใช่ network call ต่อครั้ง)

ดักจับ DNS จริงของทีวี (MikroTik `/tool sniffer`) ให้ตรงจังหวะที่ผู้ใช้ยืนยันว่าเห็นโฆษณาขึ้นจริง — คราวนี้เจอชุดโดเมนโฆษณาจริง ยิงพร้อมกันเป็นพรวด: `pagead2.googlesyndication.com`, `pubads.g.doubleclick.net`, `bid.g.doubleclick.net`, `securepubads.g.doubleclick.net`, `gcdn.2mdn.net`, `ow.pubmatic.com` (PubMatic SSP), `marker.konograma.com` (ad verification) — เป็น Google IMA SDK ยิง real-time ad auction จริง แยกขาดจากโดเมน `*.trueid.net` (CDN/API) อย่างสิ้นเชิง บล็อกชุดนี้ที่ระดับ DNS (`match-subdomain=yes`) ไม่กระทบ content เพราะคนละโดเมนกับสตรีมจริง — ยังไม่ได้ยืนยันผลจริงจากฝั่งเจ้าของเครื่องว่าโฆษณาหายไปจริงหรือไม่ (ตรวจแค่ query ถูกบล็อกสำเร็จที่ router)

บทเรียน: การดัก DNS แค่ครั้งเดียวโดยไม่ยืนยันว่า "โฆษณาขึ้นจริงไหมตอนนั้น" ให้ false negative ได้ง่าย เพราะกฎมาจาก Remote Config เป็นรายช่อง ไม่ใช่ทุกครั้งที่สลับ
