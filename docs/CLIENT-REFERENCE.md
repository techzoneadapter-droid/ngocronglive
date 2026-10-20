# Đối chiếu client PC Hồi Ức — 20/10/2026

## File người dùng cung cấp
- pc-20261020155414-48eef700.zip, 41.638.626 byte.
- SHA-256: 3fb2f3b0f9c74c2b1a72261bdce818d8d5c2fe31d3f11e8a839b721200a3364d.
- 29 mục ZIP, tổng giải nén 92.670.503 byte.
- Windows Unity IL2CPP: executable, GameAssembly.dll, UnityPlayer.dll và global-metadata.dat. Không có project Unity C#.
- Không đưa binary của dịch vụ khác vào repository.

## Kết quả đọc string literals metadata
Client tham chiếu có 11.121 literal; client Yanii upstream ghim có 10.380 literal.

Các literal riêng của tham chiếu liên quan phím:
- Bật Phím Tắt
- Tắt Phím Tắt
- Xem Phím Tắt
- NRskillShortcut:
- ADD SKILL SHORTCUT TO SKILL

Cả hai đều chứa Gán ô phím tắt, Bàn phím ảo và Vào phím 0–11. Vì vậy gán kỹ năng có dấu hiệu tồn tại ở cả hai, trong khi menu bật/tắt/xem phím chỉ tìm thấy ở bản tham chiếu. Đây là bằng chứng tĩnh; không khẳng định menu hiển thị, hoạt động hay hỗ trợ tự đổi toàn bộ phím.

## Giới hạn kiểm tra
Môi trường hiện tại không có điều khiển ứng dụng Windows native, Wine hoặc Xvfb. Chưa chạy executable, chưa đăng nhập server tham chiếu, chưa chơi nhiệm vụ hoặc kiểm tra phím bằng giao diện. Không suy ra mức đầy đủ của nhiệm vụ trên server từ client ZIP.

Chưa xác nhận bản tham chiếu tương thích protocol, bootstrap, tài nguyên hay lưu nhân vật của server Yanii. Không thay client bằng cách chép đè exe/DLL và không kết nối bản tham chiếu với database/game hiện tại.

## Công việc còn lại
- Ghi nhận thao tác/menu thực tế của chức năng phím trên bản tham chiếu.
- Xác định bộ phím và yêu cầu có đổi phím/lưu cấu hình hay chỉ xem/bật tắt.
- Tìm hoặc xây client có mã nguồn và protocol phù hợp nếu cần bổ sung giao diện.
- Chơi và đối chiếu 15 chuỗi nhiệm vụ đầu; chưa coi việc sửa task khởi tạo là đã hoàn thành toàn bộ nhiệm vụ.
