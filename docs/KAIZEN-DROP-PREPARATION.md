# Chuẩn bị nguồn Thần Trang — 18/11/2026

Theo xác nhận của chủ game, ZIP và danh sách map/boss mới chỉ dùng để chuẩn bị thiết kế. Không tích hợp các map/boss đó trong đợt này. Không triển khai production.

## Đã nối trong source

- Cấu hình riêng 14 nguồn ở `game-source/server/resources/config/kaizen-drops.json`, mẫu số 10.000. Cả 14 nguồn đang tắt; chưa có binding map ID/class boss runtime.
- `RewardService` dùng `Util.nextInt` và tạo `ItemMap` gốc; không thêm bộ máy phân phối vật phẩm khác. Trang bị nguyên chiếc dùng cùng bộ chỉ số với công thức ghép, cấp +0. Boss có thể có một rule mảnh độc lập bên cạnh rule nguyên chiếc.
- Hook tiêu diệt cuối ở `Boss.injured` và `injuredExact`. Không thưởng ở chuyển giai đoạn. Lớp boss tự override `injured` chưa có adapter sẽ bị từ chối kích hoạt. Cấu hình xác minh tên map, spawn boss/mob và catalog vật phẩm.
- Quái thường/tinh anh chỉ được cấu hình mảnh; không có rule mặc định áp dụng toàn bộ game. Tinh anh theo `lvMob>0` hiện có.
- Chống gọi lặp trong cùng vòng đời đối tượng boss/mob, khóa dùng chung giữa các request, reset khi hồi sinh. Đây là chặn lặp trong tiến trình; không phải cam kết khôi phục vật phẩm mặt đất sau crash server. Vật phẩm vẫn theo thời hạn/nhặt/lưu hành trang của game cũ.
- Giữ người nhận do luồng thưởng gốc chọn, thêm kiểm tra cùng khu, session, tài khoản kích hoạt, loại bỏ boss/pet/minipet.
- Trang quản trị hiện có không có editor bảng rơi theo nguồn; không xây thêm trang quản trị. Thay cấu hình và kiểm tra lúc startup.

## Chỉ số chủ game xác nhận, nhân 100

| Món | Chỉ số 1 | Chỉ số 2 | Chỉ số 3 |
|---|---|---|---|
| Vương miện | HP +500% | Sức đánh +200% | KI +200% |
| Vòng tay | Sức đánh +500% | Chí mạng +100% | HP +200% |
| Vòng cổ | KI +500% | HP +200% | Sức đánh +200% |
| Áo choàng | HP +400% | Giáp +300% | Sức đánh +200% |
| Mặt nạ | Sức đánh +400% | HP +200% | Chí mạng +100% |
| Khăn choàng | KI +400% | Sức đánh +200% | Giáp +200% |
| Hào quang sau đầu | Sức đánh +400% | HP +300% | KI +300% |

Chí mạng vẫn dùng thuật toán game cũ, không đổi cơ chế xác suất. Không tự đổi các món người chơi đã sở hữu.

## Bằng chứng kiểm thử

- `TEST-KAIZEN-DROPS.ps1`: 140.000 giá trị ngẫu nhiên đầu vào để xác minh biên và đúng 14 tỷ lệ; từ chối map/boss chưa xác minh, nguồn chồng lấn và nguyên chiếc từ quái.
- Test gọi `RewardService` thật và `ItemMap` thật: 32 yêu cầu đồng thời cho boss/mob chỉ phát một lượt; nguyên chiếc đủ 3 chỉ số +0 và mảnh bổ sung độc lập; kiểm tra chủ sở hữu, kích hoạt, đổi khu, hồi sinh và mục tiêu còn sống.
- `TEST-PROGRESSION-DATABASE.ps1`: 7 công thức ghép, trừ vàng/mảnh nguyên tử, từ chối thiếu nguyên liệu/ghi đè hành trang cũ, retry không nhân đôi, restart database giữ biên nhận và catalog.

Chưa xác nhận giao dịch, nâng cấp +7, khảm sao và menu Kaizen qua thao tác client thật. Chưa có map/boss mới chạy. Không đánh dấu những mục này hoàn tất bằng các fixture kiểm thử.
