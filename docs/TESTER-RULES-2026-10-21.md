# Quy tắc beta theo phản hồi tester — 21/10/2026

Nguồn yêu cầu: Google Sheets, tab Trang tính1, A1:E9:
https://docs.google.com/spreadsheets/d/1fUdNxCM2mPU3HdOsuKmuirM6i81mbdavnx5aMT3D0IE/edit
Các quyết định người dùng chốt trong chat ngày 21/10/2026 được ưu tiên hơn cơ chế của upstream.

## Hợp thể

Lấy tổng chỉ số chiến đấu sư phụ và đệ rồi tăng 30% (thường), 50% (Ma Bư), 70% (mẫu VIP tự tạo). Ví dụ 1.000 HP + 500 HP thành 1.950/2.250/2.550 HP. Không tiếp tục áp dụng các bonus phần đóng góp đệ cũ để tránh nhân hai lần. Đệ Jiren/Zeno cũ được giữ để tương thích dữ liệu, không tự coi là VIP bán mới.

Gồm HP/KI tối đa, sức đánh, giáp, chí mạng, tốc độ, hồi HP/KI, hút HP/KI, hút khi đánh quái, hút xung quanh, phản sát thương, né đòn, giảm sát thương, miễn sát thương chưởng, thể lực tối đa và các phần trăm TN/SM/chí mạng khi có. Chặn trần theo kiểu dữ liệu: chí mạng/né/hút/phản tối đa 100%, giảm sát thương 60%, tốc độ 20, thể lực short, HP/KI/damage/giáp int. Chỉ số gốc, sức mạnh, tiềm năng đã tích lũy, tiền, cấp kỹ năng và số lượng vật phẩm là dữ liệu tiến trình, không nhân rồi ghi ngược vào tài khoản khi hợp thể. Lưu thể lực theo giá trị trước hợp thể, tránh lưu chỉ số tạm thành chỉ số gốc.

Cộng điểm dùng công thức số học long có kiểm tra tràn; mua nhiều điểm giáp/chí mạng phải trả đủ tổng chi phí. Không chạy vòng lặp theo số tiềm năng mà người dùng có.

## Trứng Ma Bư

Broly/Super Broly có hai tỉ lệ riêng: rơi trứng mặc định 25%, rồi trong số trứng rơi có 30% bị hỏng. Tương đương 17,5% lượt hạ có trứng nguyên vẹn ở cấu hình này. Chủ server chỉnh cả hai ở Vận hành. Mỗi đời boss chỉ xử lý thưởng trứng một lần.

- ID 568: trứng Ma Bư nguyên vẹn, nằm trong hành trang, giao dịch được trước khi dùng.
- ID 2154: trứng Ma Bư hỏng, không ấp/không giao dịch được; thông báo rõ, không giả làm trứng dùng được.
- Dùng trứng nguyên vẹn mới chuyển vào ô ấp ở nhà. Mỗi nhân vật một trứng đang ấp; nếu đã có thì không trừ trứng mới.
- Cấp trứng qua trình soạn quà mới tạo vật phẩm 568, không tự kích hoạt ấp. Định dạng JSON trứng Ma Bư/Jiren/Zeno cũ vẫn đọc được.
- Trứng đã bắt đầu ấp gắn với nhân vật, không chuyển nhượng. VIP không có trứng rơi/giftcode.

Giao dịch thường được sửa để cần hai người khóa và xác nhận riêng; bấm lặp một phía không hoàn tất. Hai hành trang và tiền được lưu trong một transaction. Nếu đã nhận code/dùng đồ làm thay đổi hành trang kể từ lúc mở giao dịch, hủy lần giao dịch cũ thay vì ghi đè quà mới.

## Đệ VIP

Danh mục trống khi mới cài, chủ server tự tạo ở Vận hành → Tự tạo đệ VIP. Chọn ID VIP_..., tên, part đầu/thân/chân đã có, giá hồng ngọc và trạng thái mở bán. Có gợi ý hình Bill Nhí sẵn trong Yanii, Jiren, Ma Bư; không quảng cáo hình Bill Nhí là sprite Beerus mới. Thêm hình hoàn toàn mới vẫn cần asset có thể kiểm tra, không chỉ đặt tên.

Chat `vip` mở cửa hàng; `muavip VIP_ID` là lệnh mua trực tiếp bằng chính số hồng ngọc của nhân vật. Chặn thiếu tiền/đang hợp thể trước khi đổi đệ. Giữ đồ, kỹ năng, hành tinh, chỉ số đã luyện; sức mạnh tối thiểu 1,5 triệu khi nâng loại. Mua VIP lưu trừ hồng ngọc + đệ trong cùng transaction, không trừ tiền khi thất bại. Không phát mẫu VIP qua giftcode. Không xóa ID mẫu đã tạo; tắt mở bán để giữ dữ liệu người đã mua.

Hồng ngọc là currency ruby hiện có, đúng yêu cầu mới; không có đồng ngọc đỏ khác hay xác nhận nạp tiền thật.

## Giftcode

Mã 2–30 ký tự chữ/số/_/-, không cần tiền tố LIVE. Nhập ở Nhận quà sẵn có hoặc chat `code TEST`. Quà được chuẩn bị trên bản sao, ghi DB + lượt nhận trong cùng transaction rồi cập nhật nhân vật đang chơi. Túi và rương đều đầy hoặc số dư sẽ vượt trần thì chưa mất lượt; người chơi dọn chỗ rồi nhập lại. Lệnh hỗ trợ quản trị offline cũ vẫn xếp hàng, không ghi đè nhân vật đang online.

Có trình soạn/lưu bộ quà dùng chung. Chỉnh mã cũ bằng nút Chỉnh mã và tích Cập nhật mã đã có: giữ used/lịch sử nhận, không cho người cũ nhận lần nữa. Chỉ tạo mã mới khi không chọn cập nhật.

Bảy mã dùng chung, mỗi tài khoản một lần; chat `tanthu` xem danh sách. Seed INSERT IGNORE một lần, không thay quà chủ server đã chỉnh:

| Mã | Gói mặc định beta |
|---|---|
| TANTHUVANG | 10 triệu vàng |
| TANTHUNGOC | 1.000 ngọc xanh |
| TANTHUTHOIVANG | 2 thỏi vàng ID 457; giá đổi theo shop hiện có |
| TANTHUPHUKIEN | Cánh dơi, gậy như ý và pet thỏ, khóa giao dịch |
| TANTHUBUFF | 5 cuồng nộ, 5 bổ huyết, 5 bổ khí; mỗi món 10 phút theo dữ liệu Yanii |
| TANTHUTIEMNANG | 1 bùa mỗi loại x2/x3/x4/x5, mỗi món 1 giờ |
| TANTHU8SAO | 5 món cơ bản đúng hành tinh, mỗi món 8 lỗ/8 sao đã khảm và +40% TN/SM (=8×5%); khóa giao dịch |

Các số này là cân bằng beta tự chọn, không khẳng định là quà/chỉ số máy chủ chính thức. Trang bị cơ bản mặc được từ mốc 800–1.500 SM; không phát nhầm đồ khác hành tinh hoặc đòi bỏ điều kiện đồ cao cấp.

## Tăng TN/SM

Nền x2; khác nguồn cộng phần tăng theo quyết định người dùng. Ví dụ x2 nền + bùa x2 (+100%) + bùa x3 (+200%) = x5, không phải x12. Thêm trang bị/cờ/vệ tinh/sự kiện vào tổng phần tăng. Cùng loại dùng tiếp chỉ nối thời gian. Các bùa trí tuệ cũ cùng cấp và bùa vật phẩm mới dùng một hiệu lực, không đếm trùng; nối từ thời điểm kết thúc muộn hơn. Giảm TN ở sức mạnh cao/giới hạn tiến trình vốn có vẫn áp dụng. Chặn tổng hệ số cấu hình, mặc định x100.

ID 2155–2158 là bùa x2–x5 mới; dùng trong hành trang. `muabua 2` / `3` / `4` / `5` mua bằng hồng ngọc, giá gốc mặc định 100 × (hệ số−1). Giá và thời lượng chỉnh ở quản trị. `buff` xem hệ số hiện tại và giờ còn lại. Timer lưu theo thời điểm hết hạn tuyệt đối, đăng nhập lại không mất/reset.

## Khu luyện và sự kiện

Chat `khuup` chọn khu/về nhà. Hai khu mới độc lập tái sử dụng địa hình, không thay map gốc:

- 176 Ngũ Hành Sơn: miễn phí, không PK, quái 200.000 HP; nền x2, không tự tặng hệ số cao hơn.
- 177 Địa Ngục: 500 hồng ngọc/1 giờ, từ 1,5 triệu SM, không PK, quái 1 triệu HP. Lượt còn hạn vào lại không trừ thêm tiền; hết hạn bị đưa về nhà.

Ở Sự kiện & giftcode tạo map ID 178–230, chọn địa hình nguồn có sẵn, HP/sức đánh quái, phí/thời gian, SM tối thiểu, PK và lịch mở/kết thúc, phần trăm TN cộng thêm. Tên/địa hình/HP cố định sau khi tạo; tạo ID mới để thay chúng. Điều kiện, phí, PK, sức đánh, lịch và bonus cập nhật runtime. Chặn đường vào bằng capsule/đổi map nếu không có quyền trả phí; đăng nhập ở khu hết quyền cũng được đưa về nhà. Phải kiểm tra hình ảnh/địa hình/chiến đấu trên Windows trước khi mở đông người.

## Nguồn tham khảo và khác biệt

- Teamobi, Giới thiệu: https://ngocrongonline.com/gioi-thieu — phân biệt Broly cho đệ thường và trứng Ma Bư từ Hirudegarn; beta dùng Broly rơi trứng theo yêu cầu riêng của chủ server, không tự nhận giống chính thức.
- SQL/source Yanii pinned a443509f203eb21b7f39bb5a5234df4b1ddcfbea: các vật phẩm 381/382/383 mô tả +100% trong 10 phút; bùa 213/671/672 mô tả x2/x3/x4; option 102/107 đếm sao đã khảm/tổng lỗ, option101 TN/SM.
- Kho SQL công khai: https://github.com/baodubaiiii/NgocRongOnline_Data/blob/main/ItemTemplate/ItemTemplates1.sql — đối chiếu tên/cấp bùa, không chép ID vào Yanii một cách mù quáng.
- Cơ chế máy chủ tham khảo: https://www.nrokhainguyen.fun/trang-chu (bùa/cờ/vệ tinh và giảm TN ở SM cao), https://dragoncharming.com/co-che (khu luyện có phí hồng ngọc). Chỉ tham khảo cấu trúc; phí/thời lượng/balance ở trên là cấu hình beta, không sao chép toàn bộ kinh tế của máy chủ khác.

## Cập nhật và giới hạn

Dùng artifact admin-update của commit mới đã qua CI, không dùng bản trước thay đổi này. UPDATE.sh sao lưu SQL/JAR, thay JAR, không reset player. Bảng mới additive; item ID mới được giữ khi rollback. Terrain khu luyện tạo từ tài nguyên hiện có lúc khởi động. Rollback khi đang ở map 176+ cần đưa người chơi về nhà trước vì JAR cũ không tạo các map này.

Đã có ô link Zalo và lệnh `zalo`; chưa tự tạo nhóm hoặc tự gửi tin vào tài khoản Zalo. Không có phiên Zalo được kết nối. Không tuyên bố đã chơi thử toàn bộ đồ họa/AI/boss combat trong Unity Windows chỉ từ các bài kiểm tra giao thức.
