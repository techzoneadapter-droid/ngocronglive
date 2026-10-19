# Ngọc Rồng Live — Windows trước

Dự án dựng bản PC để kiểm thử local trước; Android/iOS triển khai sau. Mục tiêu sử dụng: tải ZIP, giải nén, chạy PLAY.cmd; không cần launcher cửa hàng.

**Trạng thái hiện tại: bản phát triển, chưa nghiệm thu gameplay.** Chưa có bản phát hành hoàn chỉnh. Xem [docs/STATUS.md](docs/STATUS.md) trước khi thử.

## Tải bản thử khi build đạt

Mở tab **Actions → Windows local test package → lần chạy xanh → Artifacts → NgocRongLive-PC-local-test**. Giải nén toàn bộ; giữ client, server và runtime cùng nhau. Chạy `PLAY.cmd` trên Windows x64. Tài khoản thử được tạo riêng trong `TAI-KHOAN.txt` sau lần khởi động đầu; không dùng mật khẩu cá nhân.

Nếu Actions chưa xanh hoặc không có artifact, chưa có gói chạy được để tải. Artifact chẩn đoán không phải game.

Gói thử chạy server ở 127.0.0.1:14445, database riêng ở 127.0.0.1:13306. Dữ liệu nằm trong `data/`, nhật ký trong `logs/`. Chưa mở cho bạn bè qua mạng; không mở cổng bằng tay cho bản này. Không cần cài Java/MySQL toàn hệ thống nếu gói đầy đủ đã build thành công. Không yêu cầu tắt phần mềm bảo vệ hoặc cài với quyền quản trị.

Đóng game bình thường trước khi đóng cửa sổ chạy. **Lưu tiến trình chưa được nghiệm thu; không dùng để chơi dài hạn.** Khi cập nhật, giữ nguyên bản cũ và thư mục data trước; chưa có migration dữ liệu giữa các bản.

## Build dành cho người phát triển

Cần Git, Python 3.12, JDK 17 và .NET SDK 8 trên Windows. Chạy `pwsh -File scripts/build-windows.ps1` trong checkout sạch. Script lấy upstream ở commit cố định và tải runtime từ Microsoft/MariaDB; cần mạng trong lúc build. Script không ghi đè thư mục output có sẵn.

- `scripts/prepare.py`: kiểm tra commit, tạo server config local, bỏ Nashorn import cũ, tách DB, lọc dữ liệu tài khoản/nhân vật upstream.
- `tools/ClientPatcher`: sửa phương thức chọn server trong Assembly-CSharp.dll để trỏ localhost. Dùng player binary cũ đi kèm upstream; **không tuyên bố đã build lại toàn bộ Unity player từ source**.
- `scripts/launch.ps1`: khởi tạo database riêng, tạo tài khoản thử ngẫu nhiên, chạy server/client và dừng database khi thoát.
- Workflow Windows: compile Java, patch client, ghép runtime, kiểm tra database/port trước khi xuất artifact.

## Nguồn và phạm vi tái sử dụng

Upstream: https://github.com/nvt25/Online_nro tại commit `1732e1f258b69dd812de96d35fd223624b950128`.
Repo này lưu mã tích hợp/đóng gói và tham chiếu upstream thay vì chép toàn bộ kho/binary vào lịch sử Git. Giữ nguyên ghi nhận nguồn. Upstream chưa thấy LICENSE ở gốc; không gán MIT hoặc cấp lại quyền cho code/tài nguyên của họ. Cần xác minh quyền sử dụng trước khi phát hành rộng rãi.

Các file tải từ nroluachua.online chỉ dùng nghiên cứu định dạng, không được đưa vào gói này. Không dùng server, tài khoản hoặc chứng thư của game mẫu.
