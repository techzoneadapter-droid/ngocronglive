# Ngọc Rồng Live — Windows trước

Dự án dựng bản PC để kiểm thử local trước; Android/iOS triển khai sau. Mục tiêu sử dụng: tải ZIP, giải nén, chạy PLAY.cmd; không cần launcher cửa hàng.

**Trạng thái: bản PC thử nghiệm local.** Build Windows đã vượt kiểm tra kỹ năng, database, lưu dữ liệu, đăng nhập, tạo nhân vật và bố cục gói trạng thái nhân vật. Giao diện/chiến đấu chưa được nghiệm thu trực tiếp. Xem [docs/STATUS.md](docs/STATUS.md).

[Bản build PC để tải thử](https://github.com/techzoneadapter-droid/ngocronglive/actions/runs/39359914275) → Artifacts → `NgocRongLive-PC-local-test`.

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

### Sửa lỗi Java trong đường dẫn có dấu

Đã tái hiện `could not find java.dll` với file DLL đầy đủ trong thư mục `Tài liệu`. Launcher mới tự dùng thư mục tạm không dấu cho Java khi cần và xóa bản tạm lúc thoát. Đã vượt kiểm thử PowerShell 5, khởi động database/server, đăng nhập, tạo nhân vật và lưu dữ liệu trong đường dẫn có dấu. Nếu báo thiếu file runtime, giải nén lại toàn bộ ZIP. Giữ nguyên thư mục `data` của bản cũ nếu cần giữ nhân vật; không ghi đè hoặc xóa dữ liệu đang dùng.

### Vá lỗi vào game chỉ thấy nền

[Tải gói vá khoảng 169 KB](https://github.com/techzoneadapter-droid/ngocronglive/actions/runs/39359914275/artifacts/12236389214).
Thoát game bình thường và chờ cửa sổ PLAY kết thúc. Sao lưu thư mục `data` rồi giải nén gói vá; chép `launch.ps1` và thư mục `server` vào thư mục chứa `PLAY.cmd`, chọn thay thế file khi được hỏi. Không xóa thư mục `server` cũ: chỉ gộp/chép đè các file trong gói vá. Gói vá không chứa thư mục `data` ở gốc nên giữ tài khoản và nhân vật. Chạy lại `PLAY.cmd`. Nhật ký client mới nằm tại `logs/client.log`.

Đã sửa tải template qua Session khi chưa có Player, chờ `clientOk` trước khi gửi nhân vật/bản đồ và bổ sung địa hình thiếu từ tài nguyên Unity cùng upstream. Kiểm thử socket trên Windows đạt tạo nhân vật, nhận map/ô địa hình và đăng nhập lại nhân vật cũ. Chưa nghiệm thu đồ họa hoặc chiến đấu trực tiếp.
