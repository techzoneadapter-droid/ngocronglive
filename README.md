# Ngọc Rồng Live — bản PC thay thế từ Yanii

Nền tảng mặc định đã chuyển từ `nvt25/Online_nro` sang [yanii7905/Nro_Yanii](https://github.com/yanii7905/Nro_Yanii), cố định tại `a443509f203eb21b7f39bb5a5234df4b1ddcfbea`.

Repo này chứa mã đóng gói, chỉnh sửa server và kiểm thử. Script tải nguyên bộ upstream cố định, biên dịch mã Java của game/login bằng Maven và ghép client Windows **có sẵn** của upstream. Chưa có mã nguồn Unity client trong bộ Yanii; không tuyên bố đã build lại client. Giữ giấy phép upstream trong gói.

## Tải và chơi thử

Mở Actions → **Yanii Windows local test package** → lần chạy thành công → Artifacts → `NgocRongLive-Yanii-PC-test`.
Giải nén **toàn bộ vào thư mục mới**, chạy `PLAY.cmd`. Đọc tài khoản trong `TAI-KHOAN.txt` sau khi server sẵn sàng. Không cần cài Java hoặc database toàn hệ thống. Không trộn server/client của bản cũ vào bản Yanii.

Database riêng ở `127.0.0.1:13307`, dịch vụ login `127.0.0.1:3105`, game `127.0.0.1:14445`. Đóng bản cũ trước khi chạy. Gói có hai tiến trình Java, một database và client Unity. Dữ liệu mới nằm trong `local-data`; nhân vật của nền tảng cũ không được chuyển tự động vì schema khác.

Client nguyên bản có cơ chế khám phá server LAN; game/login của gói thử chỉ bind loopback. Đây là bản kiểm thử trên một máy, chưa phải gói multiplayer cho bạn bè qua Internet.

Thoát game bình thường rồi chờ cửa sổ PLAY kết thúc để lưu và tắt database. Nhật ký tại `logs/client.log`, `logs/server.log`, `logs/server-error.log`, `logs/login.log`.

## Dựng lại

Windows x64, Git, Python 3.12, JDK 17 và Maven. Chạy `pwsh -File yanii/build-windows.ps1` ở thư mục repo. Các script `scripts/prepare.py`, `scripts/build-windows.ps1` và client patcher cũ chỉ còn là mã nền tảng trước, không dùng trong workflow mặc định.

## Thay đổi có chủ đích

- Biên dịch server/login từ source cùng bộ upstream; giữ client binary nguyên bản.
- Tách database và dữ liệu lưu; sinh mật khẩu riêng khi chạy.
- Tắt thread lịch bảo trì local vốn chạy vòng lặp không nghỉ; giảm pool database cho máy cá nhân.
- Bind TCP game/login vào localhost; sửa nhãn cột SQL của dịch vụ login cho tương thích JDBC.
- Chỉ dùng dữ liệu game tĩnh, loại tài khoản/nhân vật/lịch sử của upstream khỏi seed.
- Giữ cách xử lý Java trong đường dẫn có dấu; lỗi khởi động hiện kèm nội dung log.

Kiểm thử tự động và kiểm thử giao diện là hai việc riêng: CI sẽ kiểm tra đăng nhập, tải dữ liệu, tạo nhân vật, nhận bản đồ, di chuyển cùng map, lưu vị trí và đăng nhập lại. Chế độ Unity batch kiểm tra engine và scene đầu trong 20 giây rồi dừng tiến trình chủ động; client gốc không tự thoát với -quit trên CI. Phép kiểm tra này không xác nhận hình ảnh, điều khiển hoặc đánh quái bằng giao diện. Xem `docs/STATUS.md` để biết trạng thái đã xác minh.
