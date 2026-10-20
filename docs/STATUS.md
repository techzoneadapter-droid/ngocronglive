# Trạng thái nền tảng Yanii

Bản đã kiểm thử: `716e000822a1f2d40218bdef070c4d53706ac1a6`.
Run: https://github.com/techzoneadapter-droid/ngocronglive/actions/runs/39364727414
Nguồn upstream: https://github.com/yanii7905/Nro_Yanii — `a443509f203eb21b7f39bb5a5234df4b1ddcfbea`.

## Đã đạt trên Windows

- Biên dịch sạch mã Java của game và dịch vụ đăng nhập bằng JDK 17/Maven.
- Database MariaDB riêng cổng 13307, tạo tài khoản thử riêng, không nhập dữ liệu cá nhân upstream.
- Khởi động qua PowerShell 5 trong thư mục có dấu `Tài liệu`, dùng Java đi kèm.
- Handshake, đăng nhập và tải dữ liệu game, map, kỹ năng, ba phần vật phẩm qua giao thức thật.
- Tạo nhân vật `pcprobe`, nhận trạng thái nhân vật và map 39.
- Gửi ba bước di chuyển thông thường: vị trí từ (100,384) đến (124,384); database lưu `[124,384,39]` sau khi thoát.
- Đăng nhập lại và nhận nhân vật tại (124,384) trong map 39.
- Khởi tạo Unity 2022.3.26f1 và scene đầu với thiết bị đồ họa Null trong 20 giây, rồi dừng tiến trình test chủ động.

## Các chỉnh sửa để dựng bản local

- Chuẩn hóa UTF-8/BOM, sửa tham chiếu lớp LAN broadcaster bị thiếu.
- Dịch vụ login dùng nhãn cột SQL không kèm tên bảng; ẩn mật khẩu trong log config.
- Không để thông báo quản trị trống gây lỗi khi vào nhân vật.
- Hàng đợi gửi packet dùng ConcurrentLinkedQueue; khóa ghi socket để các đường gửi không xen byte vào nhau.
- Tắt vòng lặp lịch bảo trì không có thời gian nghỉ trong bản local; giảm pool DBHika.
- Đổi tên thư mục Unity `Data` thành `NroYanii_Data`, giữ nguyên executable và thư viện client.
- Bind TCP game/login về localhost; bản thử chưa mở multiplayer qua Internet.

## Chưa xác minh

Chưa chơi trực tiếp bằng giao diện: hình ảnh nhân vật, phím mũi tên, qua cổng map, đánh quái, NPC, nhiệm vụ và hai người cùng chơi. Phép thử Unity batch không kiểm tra đồ họa/đầu vào; client gốc không tự thoát bằng -quit trong môi trường CI.

Kết quả giao thức không phải cam kết toàn bộ game đã hoàn chỉnh. Nhân vật bản cũ không chuyển tự động; dùng thư mục riêng cho Yanii.
