# Trạng thái kiểm chứng

Build Windows được kiểm tra: `b32e7f3351161768a6a943581a1e4cda5de214bf`.
Run: https://github.com/techzoneadapter-droid/ngocronglive/actions/runs/39297074557

## Đạt kiểm tra tự động trên Windows

- Biên dịch server Java và chỉnh client sang localhost; cache riêng, không dùng chung thư mục dữ liệu client khác.
- Khởi tạo MariaDB riêng, import dữ liệu tĩnh và tạo tài khoản thử mới.
- Đọc đúng cache kỹ năng Ngọc Rồng; kiểm tra kỹ năng đầu tiên cho ba hệ (ID 0, 14, 28).
- Lưu các giá trị nhân vật và hành trang thưa vào database; đọc lại qua kết nối độc lập.
- Socket handshake và đăng nhập tài khoản vừa tạo tới màn hình tạo nhân vật.
- Tạo nhân vật qua giao thức và nhận gói trạng thái nhân vật.
- Giải mã phần đầu gói theo client Windows: tên, sức mạnh, kỹ năng, vàng 64 bit, thứ tự ngọc và số ô trang bị.

## Các sửa đổi so với source gốc

- Loại import Nashorn không dùng để hỗ trợ JDK mới; dùng UTF-8 khi xử lý mã tiếng Việt.
- Thay driver MySQL cũ bằng MariaDB Connector/J; dùng next() cho các kết quả truy vấn mới.
- Tách database local ở cổng 13306 và chỉ bind game server vào 127.0.0.1:14445.
- Loại các bản ghi tài khoản/nhân vật upstream khỏi seed.
- Bổ sung LocalPlayerStore lưu các cột JSON được Player.setup đọc và gọi khi logout.
- Mở rộng cột hành trang, giữ mặc định mảng rỗng.
- Sửa đọc cache kỹ năng, đăng ký kỹ năng và dữ liệu khởi đầu khi tạo nhân vật.
- Sửa kích thước vàng và thứ tự các trường ngọc trong gói trạng thái cho client đi kèm.
- Không ghi mật khẩu đăng nhập vào log.

## Còn phải nghiệm thu trước khi gọi bản game hoàn chỉnh

- Chạy GUI trên PC người dùng, kiểm tra hình ảnh, âm thanh, điều khiển và tải tài nguyên.
- Chơi thực tế: map/quái, kỹ năng, nhặt đồ, NPC/shop và nhiệm vụ.
- Thoát rồi đăng nhập lại trong GUI, reconnect và tình huống app/server bị đóng đột ngột.
- Chưa kiểm tra chơi nhiều máy; bản này cố ý chỉ chạy local.
- Chưa build lại toàn bộ Unity player từ Unity project; dùng player binary upstream và thay assembly chọn server/cache.
- Kiểm tra quyền sử dụng code/tài nguyên trước khi phân phối rộng.

Đây là bản PC để kiểm thử local, chưa phải bản nghiệm thu toàn bộ gameplay hoặc server vận hành lâu dài. Các commit chỉ sửa tài liệu sau build không thay đổi gói đã kiểm tra.
