# Đệ tử và trứng — quy tắc hiện tại

Quy tắc mới do chủ server chốt ngày 21/10/2026 thay bản hộ vệ trứng ban đầu. Xem [toàn bộ cấu hình, giftcode, VIP và kiểm thử](TESTER-RULES-2026-10-21.md).

- Tặng đệ thường nếu chưa có và vàng chào mừng một lần; giữ đệ đang luyện.
- Hợp thể thường/Ma Bư/VIP tăng 30/50/70% trên tổng chỉ số chiến đấu, có trần; không cộng lại bonus phần đóng góp đệ cũ.
- Broly rơi trứng Ma Bư có tỉ lệ hỏng cấu hình riêng. ID568 nguyên vẹn nằm trong túi và giao dịch được trước khi dùng; ID2154 hỏng không ấp/giao dịch. Dùng trứng mới bắt đầu ấp ở nhà.
- Không tạo ba boss hộ vệ trứng cũ. Loài Jiren/Zeno và JSON ấp cũ vẫn đọc được để giữ dữ liệu, không phát chúng như đệ VIP mới.
- Chủ server tạo mẫu VIP và giá hồng ngọc trong Vận hành. VIP chỉ mua, không săn boss hoặc nhận giftcode. Gợi ý Bill Nhí dùng hình có sẵn, không tự nhận đã có sprite Beerus hoàn toàn mới.
- Nâng loại giữ đồ/kỹ năng/chỉ số; nở, ấp nhanh và mua VIP lưu trạng thái cùng chi phí trong transaction. Chặn thiếu tiền, nở sớm, stale menu và trừ hai lần.
- `santrung`, `aptrung`, `notrung`, `apnhanh`, `detu`, `vip`: các lệnh trong game. `khuup`, `buff`, `tanthu`: khu luyện, hiệu lực bùa, mã người mới.

## Nguồn đối chiếu và sprite

Không sao chép nguyên hệ player/DB từ repo khác:

- `ahwuoc/GameNro@0f6b34c5feff6d096f32f2c4bccf99f67feff5ae`, src_java/nro/services/PetService.java và src_java/nro/player/Pet.java: tham khảo loài Jiren, head/body/leg 876/877/878. Kiểm tra cả ba type tương ứng trong Yanii trước khi dùng.
- `ducsunit/nrokid@ed050cb00934ec26ad73f166a4697a4fe3d133e1`, src/main/java/nro/models/player/Pet.java và bảng part ở nro_kid (1).sql: đối chiếu Zeno head/body/leg 1427/1428/1429. Các ID này **không** dùng trực tiếp trong Yanii vì va chạm type.
- Chỉ lấy 34 mảnh PNG ×4 zoom từ resources/normal/image/{zoom}/icon và 3 mô tả part. Manifest yanii/companion/assets.json ghi nguồn, commit, ánh xạ icon. PNG tải lúc build, kiểm tra định dạng và ghi SHA256 vào manifest đóng gói.
- Icon ánh xạ vào số chưa xuất hiện trong SQL baseline; phục vụ từ JAR, không ghi đè file ảnh cũ. Part nối cuối bảng runtime, không sửa row part của database. DataGame.vsData 7 buộc client nhận lại mô tả part; small-image versions cập nhật cho icon mới. Rollback JAR cũ không mất dữ liệu gốc/sprite gốc.
- Đã xem mảnh đầu Zeno, không giả dùng một hình khác rồi đổi tên. Chưa tuyên bố có giấy phép mới cho tài sản đồ họa từ upstream; giữ nguồn gốc.

## Cập nhật

Dùng admin-update của workflow Online beta mới đã thành công. Không nhập lại seed. Script sao lưu SQL/JAR trước khi cập nhật. Nạp tiền thật chưa được tích hợp; hồng ngọc là ruby hiện có theo yêu cầu chủ server.

Kiểm tra hiển thị và chiến đấu trên Unity Windows thực tế trước khi mở đông người; giao thức và phép tính tự động không chứng minh toàn bộ AI/đồ họa đã được chơi thử.
