# Trạng thái kiểm chứng

## Đã kiểm tra tại môi trường phát triển

- Repo upstream có server Java, SQL seed, Unity project 2021.3.16f1 và một Windows player cũ.
- 67 file Java upstream biên dịch thành công bằng Eclipse Java Compiler, target Java 8, trên Java 17 sau khi bỏ các Nashorn import không dùng.
- Kiểm tra tĩnh thấy client dùng server list bên ngoài và DBService có kết nối DB cố định; script tích hợp thay thành môi trường local.

## Kiểm tra Windows tự động

Kết quả có thẩm quyền nằm ở lần chạy Actions tương ứng commit. Không coi cấu hình workflow là bằng chứng đã chạy thành công.

Smoke test chỉ chứng minh database khởi tạo và game server mở cổng. Chưa chứng minh client GUI đăng nhập, tạo nhân vật hoặc gameplay.

## Còn phải hoàn thành trước khi gọi bản PC hoàn chỉnh

- Chạy client GUI, đăng nhập bằng tài khoản tạo riêng và tạo đủ hệ nhân vật cần dùng.
- Kiểm tra map/quái, kỹ năng, nhặt đồ và NPC/shop phù hợp phiên bản client.
- Đã bổ sung LocalPlayerStore lưu các cột JSON mà Player.setup đọc, gọi khi logout; có kiểm thử tích hợp dữ liệu/slot đồ qua kết nối database mới. Kết quả thực thi xem Actions; vẫn phải kiểm tra đăng nhập lại trong GUI và tình huống crash.
- Kiểm tra thoát và đăng nhập lại, reconnect, đổi map và dừng server an toàn.
- Đối chiếu quyền sử dụng code/tài nguyên trước khi phát hành rộng.
- Chưa có kiểm thử chơi nhiều máy; bản hiện tại cố ý chỉ bind loopback.

Đây là checkpoint phát triển và gói thử khi CI đạt, không phải tuyên bố đáp ứng toàn bộ mục tiêu sản phẩm.
