# Đệ tử, trứng và boss hộ vệ — beta

## Luồng hoạt động

- Khi đăng nhập, nhân vật chưa có đệ được tặng một đệ thường nếu bật auto_normal. Vàng chào mừng mặc định 1 triệu, ghi nhận một lần theo tài khoản. Không thay đệ cũ bằng đệ thường.
- Trong Soạn gói quà: chọn NORMAL / MABU / JIREN / ZENO, nhận đệ trực tiếp hoặc trứng cần ấp. Quà có thể vào giftcode công khai/riêng, phát trực tiếp hoặc gói đã lưu. NORMAL không có trứng riêng.
- Nếu tặng trực tiếp mà đã có đệ, phải chọn nâng loại. Nâng loại/hatch giữ trang bị, kỹ năng, hành tinh, chỉ số đã luyện; nâng sức mạnh đệ đặc biệt ít nhất 1,5 triệu. Không giữ trạng thái đang hợp thể khi nâng loại qua lệnh quà trước đăng nhập.
- Một trứng đang ấp cho mỗi nhân vật. Quà trứng khác tiếp tục chờ tới khi ô ấp trống. Boss không thay trứng đang có. Thời điểm tạo, thời gian ấp và loài lưu trong data_mabu_egg (JSON bổ sung, không thay bảng player).
- Thời gian gốc mặc định 3600 giây: Ma Bư ×1, Jiren ×6, Zeno ×24. NPC Quả trứng dùng gói tin cũ; thân trứng chung để tương thích client. Tên loài ở thông báo/menu/đệ sau nở.
- Chat `detu`, `aptrung`, `notrung`; `apnhanh` trả phí vàng theo cấu hình hiện tại. Ấp nhanh kiểm tra số dư, ghi vàng + timer cùng một UPDATE, không trừ lại khi đã sẵn sàng. NPC ở nhà dùng cùng service. Hủy trứng có xác nhận tại NPC.
- Không cho nở khi chưa hết giờ hoặc đang hợp thể. Ghi đệ + xóa trứng cùng một UPDATE; thất bại giữ trứng. Bỏ thread/sleep 4 giây khi nở. Khóa cùng đối tượng Player với save định kỳ để không bị save cũ ghi đè.
- Theo/bảo vệ/tấn công/về nhà/hợp thể dùng cơ chế Pet có sẵn. Jiren thêm 40%, Zeno thêm 70% **phần HP/KI/sức đánh đóng góp của đệ** khi hợp thể; không cộng 70% toàn bộ chỉ số sư phụ. Đây là cân bằng riêng beta, không phải công thức chính thức Teamobi.

## Boss

Ba boss "Hộ vệ trứng Ma Bư / Jiren / Zeno", ID -91001..-91003, xuất hiện ở một trong các map 5, 13, 20, nghỉ hồi sinh 300 giây. HP 5/20/50 triệu; sát thương gốc 2/5/10 nghìn. Tỉ lệ trứng mặc định 25/10/5%, thay trong Vận hành. Trứng ghi thẳng vào ô ấp của người hạ boss; nếu đệ đánh hạ thì quy về sư phụ. Không có vật phẩm trứng rơi tự do để người khác lấy hoặc mất vì rương đầy. Mỗi đời boss thưởng tối đa một lần.

Cơ chế Super Broly/đệ cũ vẫn giữ. Nếu đổi bật/tắt bộ boss, khởi động lại nrolive-game để áp dụng; tỉ lệ rơi và thời gian/giá ấp đọc cấu hình mới ngay.

## Nguồn đối chiếu và sprite

Không sao chép nguyên hệ player/DB từ repo khác:

- `ahwuoc/GameNro@0f6b34c5feff6d096f32f2c4bccf99f67feff5ae`, src_java/nro/services/PetService.java và src_java/nro/player/Pet.java: tham khảo loài Jiren, head/body/leg 876/877/878. Kiểm tra cả ba type tương ứng trong Yanii trước khi dùng.
- `ducsunit/nrokid@ed050cb00934ec26ad73f166a4697a4fe3d133e1`, src/main/java/nro/models/player/Pet.java và bảng part ở nro_kid (1).sql: đối chiếu Zeno head/body/leg 1427/1428/1429. Các ID này **không** dùng trực tiếp trong Yanii vì va chạm type.
- Chỉ lấy 34 mảnh PNG ×4 zoom từ resources/normal/image/{zoom}/icon và 3 mô tả part. Manifest yanii/companion/assets.json ghi nguồn, commit, ánh xạ icon. PNG tải lúc build, kiểm tra định dạng và ghi SHA256 vào manifest đóng gói.
- Icon ánh xạ vào số chưa xuất hiện trong SQL baseline; phục vụ từ JAR, không ghi đè file ảnh cũ. Part nối cuối bảng runtime, không sửa row part của database. DataGame.vsData 7 buộc client nhận lại mô tả part; small-image versions cập nhật cho icon mới. Rollback JAR cũ không mất dữ liệu gốc/sprite gốc.
- Đã xem mảnh đầu Zeno, không giả dùng một hình khác rồi đổi tên. Chưa tuyên bố có giấy phép mới cho tài sản đồ họa từ upstream; giữ nguồn gốc.

## Ngọc đỏ trả phí

Bản này chưa có tiền tệ ngọc đỏ riêng hay xác nhận thanh toán thật; không đổi tên ruby/hồng ngọc thành tiền trả phí. Zeno nhận từ boss/giftcode để kiểm thử. Bán bằng tiền tệ trả phí là công việc riêng, cần ledger và quy tắc cấp tiền từ giao dịch đã xác nhận. Không coi thao tác nạp mô phỏng là doanh thu/thanh toán thật.

## Cập nhật

Dùng artifact admin-update của workflow mới đã đạt kiểm tra, không dùng lại artifact 513bf57 trước phần đệ tử. UPDATE.sh vẫn sao lưu SQL/JAR, thay app.jar chứa code/sprite và tạo bảng cấu hình/welcome IF NOT EXISTS khi khởi động; không nhập lại seed, không reset tài khoản/đệ/trang bị. Giữ bản sao lưu trước khi kiểm thử trên VPS. Phải kiểm tra hiển thị, chiến đấu boss và hành vi AI trên bản Windows thực tế trước khi khẳng định toàn bộ gameplay hoàn chỉnh.
