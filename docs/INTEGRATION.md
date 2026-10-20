# Hợp nhất hai nền tảng

Nhánh: integration/full-source. Giữ riêng để xem xét trước khi thay main.

| Thành phần | Nguồn | Quyết định |
|---|---|---|
| Java game/login | Yanii, a443509f203eb21b7f39bb5a5234df4b1ddcfbea | Giữ bản vá đang chạy và khởi tạo nhiệm vụ ID 0 |
| Database, nhân vật | Yanii seed / dữ liệu local riêng | Không chép bảng Clone; tọa độ và mảng task khác thứ tự |
| Console chủ server | ngocronglive/yanii/admin | Giữ nguyên |
| Client native hiện hành | Yanii supplied player | Giữ làm bản chơi có bằng chứng kiểm thử |
| Client source để sửa UI | duphatminh/NgocRongOnline_Clone, 26cad142d657449bd27f694f0214a43c9382036d | Tích hợp thử nghiệm, thêm adapter và bảng phím |
| Texture/audio client source | NRO_UI tại commit Clone đã ghim | Lấy bằng script prepare, không copy tài nguyên server 74.000 file |

## Không trộn những gì
Không chép PlayerDAO/TaskService của Clone vào Yanii: package, boss ID, schema, packet khác nhau. Không nhập các SQL dump Clone lên database đang chơi. Không lấy client Hồi Ức binary làm client source. Không đổi cấu hình cổng public hoặc triển khai VPS.

## Kiểm tra và giới hạn phát hành
Server hiện tại đã có Windows build + protocol/DB/owner-console tests tại fb30143. Source client mới cần qua kiểm tra core, compile Unity thực sự và chơi qua 15 nhóm nhiệm vụ trước khi thay client mặc định. Các thay đổi source không tương đương bản PC có phím tắt đã phát hành. Chưa có tải thử nhiều người/Android/iOS.

## Điều kiện hoàn thành bản hợp nhất chơi được
1. Unity build thành công không lỗi C# hoặc missing assets.
2. Client mới login, tạo nhân vật 3 hành tinh, nhận map, đi lại mà không chuyển map sai.
3. NPC, quái, item, skill đọc đúng packet, không EndOfFile/exception.
4. Chuỗi nhiệm vụ được đối chiếu với docs/QUEST-REVIEW.md.
5. Phím tùy chỉnh không kích hoạt khi chat, menu không làm nhân vật di chuyển, thoát/restart giữ cấu hình.
6. Đăng nhập lại giữ vị trí/tiến độ; có kế hoạch rollback về client cũ.

Chưa đánh dấu các điều kiện runtime này đã đạt.
