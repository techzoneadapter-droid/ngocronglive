# Beta online — VPS Ubuntu 24.04

Host được chủ server xác nhận bằng ảnh: 160.250.180.107, HN03, 2 vCPU, 2GB RAM. Package cố định host này. Nếu IP đổi, phải build lại client endpoint và sửa cấu hình server đồng bộ.

## Phạm vi bản này
Server Yanii đã vá, khởi tạo nhiệm vụ 0 và console chủ server. Client binary Yanii đã có bản PC build thành công, chỉ đổi endpoint bootstrap. Source client F10 tại PR #1 chưa được build Unity nên không nằm trong gói beta này. Chưa xác minh toàn bộ 15 nhiệm vụ hay sức tải nhiều người.

## Hai file phân phối
- NgocRongLive-Linux-beta.tar.gz: game/login JAR, dữ liệu tĩnh, seed đã loại tài khoản upstream, installer, backup và status scripts. Chỉ chủ server dùng.
- NgocRongLive-PC-online.zip: client Windows và PLAY-ONLINE.cmd/REPORT.cmd. Chỉ gửi file này cho tester.

GitHub Actions bọc mỗi artifact trong một ZIP: giải nén lớp ngoài để lấy file tar.gz/ZIP bên trong.

## Cài lên VPS mới
Java 17/MariaDB có thể đã cài; installer giữ lại, tạo database riêng nrolive_beta. Không di chuyển nhân vật local, không nhập database Clone. Installer từ chối nếu /opt/nrolive-beta, /etc/nrolive-beta.env hoặc database nrolive_beta đã tồn tại, tránh ghi đè tiến độ khi chạy lại.

Sau khi tải server artifact, giải nén lớp ngoài trên Windows, mở PowerShell tại thư mục có file tar.gz:

```powershell
scp .\NgocRongLive-Linux-beta.tar.gz root@160.250.180.107:/root/
```

Trong SSH trên VPS, chạy từng dòng:

```bash
tar -xzf /root/NgocRongLive-Linux-beta.tar.gz -C /root
```

```bash
bash /root/NgocRongLive-Linux-beta/install.sh 160.250.180.107
```

```bash
bash /opt/nrolive-beta/status.sh
```

Installer cài gói hệ thống, tạo user dịch vụ riêng, dịch vụ systemd, bật firewall và mở SSH/game 14445/bootstrap 14446. Giữ cổng SSH từ SSH_CONNECTION, mặc định 22. Database, login 3105 và admin 14447 không mở public. Có cấu hình tự khởi động, dừng lưu qua stop.request, logrotate và backup hàng ngày. Trên máy dưới 1.5GB, thêm swap nếu chưa có; swap không làm tăng sức tải đảm bảo.

Máy 2GB: heap game 768MB, login 96MB, cap game 20, max 5 phiên/IP. Đây là giới hạn cấu hình thử nghiệm, không phải benchmark 20 người. Bắt đầu khoảng 10 tester, kiểm tra RAM/CPU/ping và log trước khi tăng. Dùng dữ liệu thử riêng, chưa nạp tiền thật.

Nếu trang nhà cung cấp có firewall riêng, mở TCP 14445 và 14446 ở đó. Không mở 3306, 3105 hoặc 14447.

## Tạo tài khoản tester bằng console
Đóng server local Windows để giải phóng cổng 14447 trước khi tạo tunnel. Trong cửa sổ PowerShell mới trên Windows:

```powershell
ssh -N -L 127.0.0.1:14447:127.0.0.1:14447 root@160.250.180.107
```

Giữ cửa sổ này mở. Trình duyệt mở http://127.0.0.1:14447 . Trong SSH khác, xem riêng mật khẩu chủ server:

```bash
grep '^NRO_ADMIN_PASSWORD=' /etc/nrolive-beta.env
```

Không gửi giá trị này cho tester hoặc đính kèm báo lỗi. Dùng mục Tài khoản trong console tạo tài khoản/password riêng cho mỗi tester. Không có tài khoản mặc định công khai trong package, chưa bật website tự đăng ký.

## Người chơi và báo lỗi
Giải nén gói PC online vào thư mục riêng, chạy PLAY-ONLINE.cmd, đăng nhập bằng tài khoản được chủ server cấp. Không chạy SERVER.cmd local. Khi lỗi, chạy REPORT.cmd: tạo ZIP trong reports/, mở mẫu issue GitHub. File không tự được gửi. Người chơi xem lại log, ghi bước tái hiện, ảnh, map/NPC/nhiệm vụ và đính kèm ZIP. GitHub cần tài khoản để gửi issue. Không gửi local-data, password, file env hoặc cấu hình server.

## Vận hành
```bash
systemctl restart nrolive-game
```

```bash
systemctl stop nrolive-game
```

```bash
sudo -u nrolive bash /opt/nrolive-beta/backup.sh
```

Backup nằm trên cùng VPS tại /opt/nrolive-beta/backups: chủ server cần sao chép ra máy khác để bảo vệ trước việc mất cả VPS. Không chia sẻ file backup cho tester. Không chạy installer để cập nhật phiên bản; cần quy trình backup/update riêng sau beta.

## Bằng chứng và giới hạn
CI phải hoàn tất trước khi tải artifacts: biên dịch Java trên Linux, DB MariaDB thật, kiểm tra bind public/loopback, protocol login/map/di chuyển, task 0 và đăng nhập lại. Đây không phải kiểm tra đồ họa Windows qua Internet hoặc tải nhiều người. Cần chủ server cài lên VPS, kiểm tra từ mạng bên ngoài và tạo tài khoản trước khi mời tester.
