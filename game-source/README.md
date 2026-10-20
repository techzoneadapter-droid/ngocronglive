# Mã nguồn game để rà soát nhiệm vụ

Nguồn: https://github.com/yanii7905/Nro_Yanii tại commit a443509f203eb21b7f39bb5a5234df4b1ddcfbea. Giữ giấy phép tại UPSTREAM-LICENSE.

- server/src: mã Java game server (419 lớp).
- login/src: mã Java dịch vụ đăng nhập (13 lớp).
- database/tasks.reference.sql: cấu trúc và dữ liệu mẫu của ba bảng nhiệm vụ từ upstream; KHÔNG phải migration và không nhập đè database đang chơi.
- ../yanii/admin: mã console chủ server.
- ../yanii/prepare.py: dùng các thư mục src ở đây trước khi áp dụng bản vá tương thích và tích hợp console. Sửa Java ở đây rồi chạy quy trình build hiện có để có JAR mới. Thay file .java trong gói chơi sẽ không đổi JAR.
- Maven pom, tài nguyên đồ họa và client binary vẫn lấy từ upstream đã ghim. Đây chưa phải bộ nguồn độc lập/offline. Upstream không cung cấp mã C# Unity client.

## Điểm vào hệ thống nhiệm vụ

- server/src/main/java/nro/services/TaskService.java: tiến trình, điều kiện và thưởng.
- server/src/main/java/nro/consts/ConstTask.java: hằng nhiệm vụ/bước.
- server/src/main/java/nro/models/task/: trạng thái nhiệm vụ.
- NPC trong server/src/main/java/nro/npc/: hội thoại và các đường bỏ qua nhiệm vụ.
- server/src/main/java/nro/server/Manager.java: nạp template từ database.

## Phát hiện ban đầu — 20/10/2026

Seed gốc có 33 dòng nhiệm vụ chính, ID 0–32; ID 32 chỉ là mục cuối mang tên Nro Yanii. Một số tên nhiệm vụ ghi rõ bỏ qua ở nhà. Trong khi đó TaskService/ConstTask có tham chiếu tới ID cao hơn, tới 50. Có tham chiếu không có nghĩa nhiệm vụ đã đủ dữ liệu hay hoạt động hoàn chỉnh.

Chưa sửa điều kiện, phần thưởng hoặc thêm nhiệm vụ trong lần đưa mã nguồn lên repo này. Cần đối chiếu tên/ID nhiệm vụ bị thiếu, chủng tộc, bước đang kẹt, NPC/map và hành động mong đợi trước khi sửa gameplay. Không dùng thay đổi task ID của nhân vật để giả lập việc hoàn thiện nhiệm vụ.

Chưa triển khai game lên VPS. Việc cài Java/MariaDB trên VPS là bước chuẩn bị riêng.
