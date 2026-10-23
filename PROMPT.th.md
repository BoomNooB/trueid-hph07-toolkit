# คัดลอกให้ Claude Code / Codex

```text
ช่วยทำกล่อง Android TV ของฉันตาม repo นี้ อ่าน AGENTS.md แล้วทำตาม
docs/agent-runbook.md และ docs/setup.md บนเครื่องจริงทีละขั้น

รุ่น: <กรอกให้ตรงกับกล่อง>
Android / firmware: <ถ้าทราบ>
IP หรือ VPN address ของกล่อง: <กรอก>
อุปกรณ์ USB: <กรอก>
เครือข่ายใช้ USB Ethernet หรือไม่: <ตอบแยกจากอุปกรณ์ USB>
แอปที่ต้องรักษาไว้: <เช่น TrueID Live TV, HBO Max, YouTube>
ขนาด dock และแอปตามลำดับ: <เว้นไว้ให้ถามครั้งเดียว>

ใช้ tools ที่มีใน repo และ launcher จาก cleanyourtv ที่ pin revision ไว้
ถ้ายังไม่มี ADB ให้อ่าน docs/bootstrap.md ก่อน เป้าหมายแรกคือให้คุณเห็นจอเอง:
pair รีโมท → ลง TV Bridge ผ่าน Downloader by AFTVnews (com.esaba.downloader)
→ ฉันยอมรับแชร์จอ → คุณดึงและดู screenshot จริง → ค่อยลงตัวปลด debugging
ถ้าพิมพ์ได้แต่เลข ใช้ numeric short code ตามคู่มือ ไม่วนส่ง URL ที่ไม่เข้า
แยกให้ชัดว่าขั้นไหนคุณทำบนคอม และขั้นไหนฉันต้องทำบนทีวี/ผ่าน CAPTCHA
ตรวจรุ่นก่อนใช้ vendor unlock ของ HPH07 ถ้าคนละรุ่นให้หยุดเส้นทางนี้
เก็บ backup/ledger/checkpoint ไว้ใน .state/ และเตรียม restore ก่อนแก้ package/Home
เปิด debugging ผ่าน helper แล้วปิด vendor API debug session ชั่วคราวให้เรียบร้อย
บอกฉันก่อนหน้าต่างอนุญาต ADB หรือ screen sharing เพื่อฉันกดยืนยัน

รักษา Live TV, DRM, remote/input, Play Services และบัญชีไว้
Home ของ TrueID เป็นส่วนเดียวกับแอปดูทีวี ห้ามปิดทั้ง package เพื่อเปลี่ยน launcher
stock_home_redirect ต้องว่างบนรุ่นนี้ ไม่เช่นนั้นเปิด Live TV ก็ถูกดึงกลับ Home
ดู screenshot หลังแต่ละการเปลี่ยน และให้ฉันทดสอบ Home/กดเสียงค้างจริง
ถ้า DRM ทำให้ capture ดำ ให้ฉันยืนยันภาพ/เสียงจากจอ ไม่เดาว่าเล่นเสีย
reboot ตรวจอีกครั้ง และปิด ADB พร้อมตรวจ ConnectionRefused ตอนเสร็จ

ทำขั้นตอนที่ทำเองได้ต่อเนื่อง ถามเฉพาะข้อมูล/ความชอบที่ยังขาดและสิ่งที่ต้องกดจริง
บอกผลที่ทดสอบจริง แยกจาก workaround และสิ่งที่ยังไม่ยืนยัน
```

ถ้าจะช่วยพัฒนาโค้ดโดยไม่ต่อกล่อง ให้บอก agent ว่าเป็นงาน tools/docs และให้รันเฉพาะ offline tests/build ไม่ต้องติดต่ออุปกรณ์
