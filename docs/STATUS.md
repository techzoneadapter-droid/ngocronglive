# Trạng thái nền tảng Yanii

## Điều tra màn hình đen (20/10/2026)

Client gốc có scene WebIP tải danh sách server từ `https://raw.githubusercontent.com/xungdzvc/xunglordNro/main/IPLOCAL`; địa chỉ này trả 404 và coroutine thử lại liên tục. Thử bỏ scene chỉ hiện vòng tải, chưa vào đăng nhập, nên không phát hành thử nghiệm đó làm bản sửa.

Bản sửa mới giữ nguyên scene WebIP, chuyển URL được mã hóa trong metadata sang HTTP loopback 14446. Game server phục vụ danh sách JSON local tại `/IPLOCAL` và gửi cùng định dạng JSON qua packet danh sách server, bỏ hậu tố cũ. Shader `Lines/Colored Blended` không có trong player được thay bằng shader `Sprites/Default` đi kèm. Bỏ một lời gọi thông báo tùy chọn trong `ModFunc.LoadGame` vốn chạy trước khi Info được khởi tạo. Các thay đổi companion DLL/metadata đều kiểm tra SHA-256 nguồn và byte đích, không sửa NroYanii.exe. Đây là vá binary cung cấp sẵn; repo upstream không có mã C# client để biên dịch lại.

Windows CI mở cửa sổ đồ họa thật khi server đang chạy, chụp ảnh và lưu client.log. Nếu có NullReferenceException/ArgumentNullException thì chặn đóng gói. Kết quả giao thức bên dưới độc lập với kết quả giao diện.


Bản đã kiểm thử: `99d6a80cd8ea7100f33c6ba536d7d62f7dd40fc4`.
Run: https://github.com/techzoneadapter-droid/ngocronglive/actions/runs/39369730178
Nguồn upstream: https://github.com/yanii7905/Nro_Yanii — `a443509f203eb21b7f39bb5a5234df4b1ddcfbea`.

## Đã đạt trên Windows

- Biên dịch sạch mã Java của game và dịch vụ đăng nhập bằng JDK 17/Maven.
- Database MariaDB riêng cổng 13307, tạo tài khoản thử riêng, không nhập dữ liệu cá nhân upstream.
- Khởi động qua PowerShell 5 trong thư mục có dấu `Tài liệu`, dùng Java đi kèm.
- Handshake, đăng nhập và tải dữ liệu game, map, kỹ năng, ba phần vật phẩm qua giao thức thật.
- Tạo nhân vật `pcprobe`, nhận trạng thái nhân vật và map 39.
- Gửi ba bước di chuyển thông thường: vị trí từ (100,384) đến (124,384); database lưu `[124,384,39]` sau khi thoát.
- Đăng nhập lại và nhận nhân vật tại (124,384) trong map 39.
- Mở client đồ họa thật khi server hoạt động, bấm nút NgocRongLive và nhận menu Chơi mới / Đổi tài khoản với nền biển.
- Khởi tạo Unity 2022.3.26f1 và scene đầu với thiết bị đồ họa Null trong 20 giây, rồi dừng tiến trình test chủ động.

## Các chỉnh sửa để dựng bản local

- Chuẩn hóa UTF-8/BOM, sửa tham chiếu lớp LAN broadcaster bị thiếu.
- Dịch vụ login dùng nhãn cột SQL không kèm tên bảng; ẩn mật khẩu trong log config.
- Không để thông báo quản trị trống gây lỗi khi vào nhân vật.
- Hàng đợi gửi packet dùng ConcurrentLinkedQueue; khóa ghi socket để các đường gửi không xen byte vào nhau.
- Tắt vòng lặp lịch bảo trì không có thời gian nghỉ trong bản local; giảm pool DBHika.
- Đổi tên thư mục Unity `Data` thành `NroYanii_Data`, giữ nguyên executable; DLL/metadata được vá có kiểm tra hash như mô tả trên.
- Bind TCP game/login về localhost; bản thử chưa mở multiplayer qua Internet.

## Chưa xác minh

Chưa đăng nhập và chơi nhân vật trực tiếp bằng giao diện: hình ảnh nhân vật, phím mũi tên, qua cổng map, đánh quái, NPC, nhiệm vụ và hai người cùng chơi. Phép thử Unity batch không kiểm tra đồ họa/đầu vào; client gốc không tự thoát bằng -quit trong môi trường CI.

Kết quả giao thức không phải cam kết toàn bộ game đã hoàn chỉnh. Nhân vật bản cũ không chuyển tự động; dùng thư mục riêng cho Yanii.


## Console chủ server và chế độ độc lập — 20/10/2026

Mã kiểm thử: `9cfc0a783ef17b0bcc1f921e8273e10ca9721138`.
Windows run: https://github.com/techzoneadapter-droid/ngocronglive/actions/runs/39390037977

- Kiểm tra API với database MariaDB thật: chưa đăng nhập/CSRF sai bị từ chối; tạo, khóa/mở và đặt mật khẩu tài khoản.
- Xếp hàng cộng ngọc, phát vật phẩm kèm chỉ số; lệnh chưa sửa inventory trước khi đăng nhập lại. Sau đăng nhập, so sánh JSON đã lưu trong DB. Đăng nhập tiếp không cộng lần hai.
- Gửi lại cùng mã yêu cầu không tạo quà trùng; mã cũ với payload khác bị từ chối. Trừ quá số dư được REJECTED và không thay đổi tiền.
- Duyệt đơn nạp mô phỏng hai lần chỉ có một quà; cột VNĐ/tổng nạp thương mại không bị thay đổi.
- Giftcode một lần/tài khoản và giới hạn lượt toàn server; lịch XP bật/tắt, hệ số đọc đúng.
- Mở SERVER độc lập, đọc lại hàng đợi/sự kiện sau restart, dừng qua API chủ server; tất cả cổng game/login/DB/admin đã đóng sau khi launcher kết thúc.
- Sao lưu SQL trước lần migration đầu tiên, bao gồm trường hợp đường dẫn Windows có dấu.
- Edge thật: đăng nhập console, hiển thị tài khoản/nhật ký, bảy mục điều hướng, chiều rộng mobile 390px không tràn ngang, đăng xuất, không có JavaScript exception.

Phạm vi: một chủ server trên máy local; chưa phát hành VPS/public, chưa website đăng ký người chơi public, thanh toán thật hoặc mẫu sự kiện boss/nhiệm vụ mới. Giao diện game native chỉ kiểm tra khởi động/menu; quà/di chuyển/lưu nhân vật kiểm tra qua giao thức và DB, chưa tự động chơi toàn bộ game bằng chuột/phím.
