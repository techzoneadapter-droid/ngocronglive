# Quản trị local — Ngọc Rồng Live

Bản này chạy trên máy Windows hiện tại, chuẩn bị để tách lên VPS sau. Không mở cổng Internet trong giai đoạn này. Server nhận game tại 127.0.0.1:14445; console chủ server tại http://127.0.0.1:14447.

## Cài bản cập nhật

Thoát game và đợi PLAY kết thúc. Giải nén toàn bộ nội dung gói **NgocRongLive-Admin-update** vào thư mục Yanii đang có PLAY.cmd, chép đè các file trùng tên. Giữ nguyên local-data. Không dùng gói này với server Online_nro cũ.

Lần đầu chạy sẽ sao lưu SQL trước khi tạo các bảng live_admin_*. Dữ liệu tài khoản/nhân vật hiện có được giữ nguyên. Kiểm tra thư mục backups sau lần nâng cấp.

## Cách dùng

1. Mở SERVER.cmd. Server và database chạy trong cửa sổ này.
2. Mở QUAN-TRI.txt để lấy mật khẩu chủ server, sau đó mở ADMIN.cmd.
3. Mở CLIENT.cmd để chơi. Đóng client không tắt server.
4. Trong trang quản trị: tạo tài khoản; tìm nhân vật; cộng/trừ vàng, ngọc xanh, hồng ngọc; gửi vật phẩm/chỉ số vào rương.
5. Tiền và đồ được xếp hàng, nhận khi đăng nhập lại. Tài khoản chưa tạo nhân vật nhận ở lần đăng nhập sau khi đã tạo nhân vật. Nếu rương đầy, quà tiếp tục PENDING, không bị mất. Nếu số dư sau cộng/trừ âm hoặc vượt giới hạn, lệnh REJECTED và ghi lý do.
6. Tạo sự kiện tăng kinh nghiệm bằng giờ bắt đầu/kết thúc trên máy. Hệ số 2 nghĩa là nhân thêm 2 trên tỷ lệ nền. Nhiều lịch trùng nhau lấy hệ số cao nhất. Đây là mẫu sự kiện XP; boss/nhiệm vụ/tỷ lệ rơi mới chưa nằm trong bản này.
7. Giftcode bắt đầu LIVE, gồm chữ và số, tối đa 30 ký tự. Người chơi nhập trong chức năng giftcode hiện có của game. Mỗi tài khoản một lần, giới hạn tổng lượt và hạn dùng. Quà nhận khi đăng nhập lại.
8. Nạp thử: tạo đơn mô phỏng, chọn phần thưởng và duyệt. Một đơn chỉ tạo một lệnh thưởng dù duyệt lại. Không nhận tiền thật, không sửa số dư VNĐ/tổng nạp thương mại của tài khoản.
9. Dừng bằng STOP.cmd hoặc nút “Lưu và dừng server”; chờ cửa sổ SERVER kết thúc. Không tắt cưỡng bức cửa sổ khi có người online.

PLAY.cmd vẫn hỗ trợ cách cũ: mở game cùng server, thoát game sẽ lưu và dừng. Không mở PLAY đồng thời với SERVER. Dùng CLIENT sau khi SERVER đã sẵn sàng.

## Sao lưu và phục hồi

Chạy BACKUP.cmd khi server đang hoạt động. File backups/ngocrong-<giờ UTC>.sql và checksum SHA-256 lưu trạng thái database đã commit, gồm lệnh chờ/đơn thử/giftcode/sự kiện/nhật ký. Nhân vật đang online có thể chưa lưu thao tác mới nhất; cho mọi người thoát game trước khi lấy bản sao cuối ngày. Sao chép backups ra thiết bị khác; file chứa dữ liệu tài khoản, giữ riêng.

Phục hồi cần một database thử riêng trước: dừng server, sao lưu trạng thái hiện tại, nhập SQL bằng công cụ MariaDB và thông tin local-data/client.ini. Bản này chưa có nút phục hồi một chạm; không tự ghi đè database khi game đang hoạt động.

## Quyền truy cập và giới hạn

Mật khẩu chủ server được sinh riêng, lưu tại local-data/admin-password.txt và QUAN-TRI.txt; không dùng mật khẩu chơi game. Đổi mật khẩu bằng sửa admin-password.txt thành chuỗi ít nhất 16 ký tự rồi khởi động lại. Không đưa các file mật khẩu, local-data hoặc backups lên GitHub. Console chỉ bind loopback; cookie HttpOnly/SameSite, kiểm tra Host/Origin/CSRF và giới hạn thử mật khẩu.

Đây là một chủ server trên máy local, chưa có phân quyền nhiều quản trị viên, website người chơi công khai, thanh toán thật, tự động đối soát ngân hàng hoặc bản phát hành Internet. Tài khoản chơi game vẫn dùng cơ chế mật khẩu của upstream; cần nâng cấp xác thực trước khi public. Không gửi cổng database, login nội bộ hoặc console ra Internet.

Khi có VPS, bước tiếp theo là gói Linux/ARM, cấu hình endpoint client, HTTPS, đăng ký người chơi, xác thực quản trị cho môi trường public, sao lưu ngoài máy và kiểm thử nhiều máy. Có thể tiếp tục dùng cùng cơ sở dữ liệu sau khi di chuyển và kiểm tra tương thích.
