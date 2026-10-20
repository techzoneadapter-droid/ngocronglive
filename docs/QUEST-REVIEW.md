# Rà soát nhiệm vụ đầu game

## Lỗi đã sửa trong mã nguồn
PlayerDAO.createNewPlayer lưu data_task=[0,20,0], nên nhân vật mới bỏ qua các nhiệm vụ ID 0–19. Đổi mặc định thành [0,0,0], bắt đầu hướng dẫn rồi tới mộc nhân, đùi gà và sao băng. Không cập nhật nhân vật đã tồn tại.

Kiểm tra giao thức được bổ sung truy vấn data_task của nhân vật thật sau tạo, lưu và đăng nhập lại. Cần CI xanh trước khi phát bản chơi mới. Chưa kiểm thử trọn chuỗi bằng giao diện.

## Phạm vi người chơi yêu cầu
1. Mộc nhân: 5 con.
2. Đùi gà: 10 vật phẩm.
3. Sao băng: nâng tiềm năng một lần, nhặt em bé.
4. Ba loại quái mẹ: mỗi loại 3 con.
5. Giải cứu Appule: 16.000 sức mạnh và 20 quái.
6. Ngọc 7 sao, Bò Mộng, Thần Mèo và Tàu Pảy Pảy.
7. Truyện Đôrêmon tập 1 / bái sư.
8. Bang hội từ 5 người.
9. Ba loại heo: mỗi loại 30, nhóm cùng bang.
10. Truyện Đôrêmon tập 2.
11. Ba loại quái bang hội: mỗi loại 30, 500.000 sức mạnh.
12. Thắng 10 người.
13. Ba boss trùm và 1.500.000 sức mạnh.
14. Thắng vòng 2 đại hội, 5.000.000 sức mạnh.
15. Doanh trại: 15.000.000 sức mạnh, sức đánh gốc 300, Trung Úy Trắng.

Danh sách này là yêu cầu cần đối chiếu; chưa đánh dấu tất cả đã hoạt động. Task ID gốc không trùng số thứ tự trong danh sách. NPC gốc có đường bỏ qua nhiệm vụ; phải kiểm tra boss/đại hội/điều kiện trước khi bỏ các đường hỗ trợ đó.

## Chỉnh phím
Upstream chỉ cung cấp client Unity IL2CPP đã biên dịch, không có source C# Unity. Chưa bổ sung giao diện gán phím trong game. Không thể khẳng định sửa Java server sẽ khôi phục giao diện này. Cần kiểm tra khả năng client hiện có hoặc thay bằng client có mã nguồn tương thích; chưa lựa chọn phương án thay client.

## Trạng thái
Dừng triển khai VPS theo yêu cầu. Không reset tài khoản/nhân vật hay nhập SQL lên VPS trong thay đổi này.
