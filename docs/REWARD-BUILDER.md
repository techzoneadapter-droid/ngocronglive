# Gói quà, giftcode và tài khoản kiểm thử

**Cập nhật 21/10/2026:** giftcode cho phép tên tự chọn 2–30 ký tự và nhận ngay trong phiên chơi; không còn yêu cầu LIVE/đăng nhập lại. Xem [quy tắc tester mới](TESTER-RULES-2026-10-21.md). Các đoạn về hàng đợi bên dưới chỉ áp dụng lệnh hỗ trợ quản trị cũ.

Trong **Sự kiện & giftcode**, trình soạn gói dùng chung cho phát trực tiếp, lưu gói và tạo mã:

- Tân thủ, chào mừng, tester nâng cao và bá chủ là **mẫu của server thử nghiệm**, không phải gói quà chính thức Teamobi.
- Chọn vàng/ngọc xanh/hồng ngọc; chọn toàn bộ tiền để nâng số dư tới trần server (không phải vô hạn). Nếu cộng làm vượt trần thì lệnh bị từ chối, không tự tràn số.
- SM/TN là **nâng lên ít nhất mức yêu cầu**, không cộng lặp mỗi lần đăng nhập. Chọn giới hạn trong bảng power_limit đã nạp của chính server. Nâng chỉ số gốc dùng trần HP/KI/sức đánh/giáp/chí mạng của giới hạn đó.
- Chọn từng ID, mọi kết quả lọc hoặc mọi mẫu vật phẩm trong server. Danh mục lấy trực tiếp từ Manager.ITEM_TEMPLATES; nhóm theo tên/type là bộ lọc tiện dụng, không là phân loại chính thức tuyệt đối.
- Dòng thường/thần linh/hủy diệt/thiên sứ theo tên mẫu có trong server. Cấp ghi trong tên (đậu thần, sách...) khác mức nâng trang bị và số lỗ sao. Schema item_template của server này không có trường cấp trang bị 1–14 đáng tin cậy, không lấy ID từ repo khác để gán cấp.
- Chỉ số gốc vật phẩm lấy từ mẫu shop đầu tiên có options; hiển thị rõ nếu không có. +1–7 áp dụng công thức CombineServiceNew: chỉ số chính tăng 10% mỗi lần, chí mạng tăng 1, chỉ số hồi phục tăng 10%. Đồ không có mẫu gốc cần phát +0 hoặc dùng công cụ tùy chọn chỉ số riêng; không tự bịa chỉ số. Lỗ sao 0–8 dùng option 107, không tự khảm hoặc thêm hiệu ứng pha lê.
- “Tất cả” gồm mỗi mẫu có trong server, không bảo đảm mọi vật phẩm có cơ chế sử dụng hoàn chỉnh. Không tự sinh đệ tử, học kỹ năng, cộng thành tựu, hoàn thành nhiệm vụ hoặc cấp is_admin.
- Lệnh hỗ trợ lớn tách thành phần thưởng có mã con cố định. Rương đầy: quà còn chờ; lấy đồ ra và đăng nhập lại. Mỗi lần xử lý tối đa 50 lệnh, ưu tiên tiền/chỉ số. Mã dùng một lần mỗi tài khoản và còn chịu tổng lượt/hạn dùng.
- Mã riêng: chọn danh sách tài khoản. Biết mã vẫn không nhận được nếu ngoài danh sách. Mẫu bá chủ yêu cầu mã riêng trong UI. Admin quà là tên gói; không phải quyền quản trị.
- Xem trước trước khi tạo. Lưu gói là snapshot của phần thưởng đã xác minh; chọn gói lưu để tái sử dụng. Gói lưu không sửa trực tiếp danh mục game.
- Lệnh hỗ trợ quản trị thay đổi tiền/chỉ số/vật phẩm được áp dụng trước khi tải nhân vật khi đăng nhập lại; không ghi đè phiên người chơi đang online. Nhân vật chưa tạo thì quà tiếp tục chờ lần đăng nhập sau khi tạo.

## Đối chiếu chính thức, kiểm tra ngày 21/10/2026

- https://ngocrongonline.com/gioi-thieu: Titan/áo, Ruby/quần, thạch anh tím/găng, Saphia/giày, lục bảo/rada; đá bảo vệ.
- https://forum.ngocrongonline.com/post/cap-nhat-chuc-nang-sao-pha-le-10-19526582: thông báo 31/07/2026, áp dụng 01/08/2026, sao pha lê 10 và bảy loại ngọc xanh khảm.
- https://forum.ngocrongonline.com/post/su-kien-he-2-19526516: có bông tai cấp 3 và giáp luyện tập cấp 5.

Thông tin chính thức không chứng minh bộ server clone đã triển khai cơ chế mới. Bản nguồn hiện có MAX_LEVEL_ITEM=7 và MAX_STAR_ITEM=8 trong CombineServiceNew; UI giới hạn theo bản đó. Bông tai/đậu/sách cấp nào có thực trong catalog mới được chọn. Không nhập dữ liệu ngoài làm lệch ID/asset/protocol.

## Cập nhật VPS đang chạy

Tải artifact **NgocRongLive-admin-update** của workflow Online beta đã thành công. Giải nén ZIP lấy NgocRongLive-admin-update.tar.gz. Chuyển lên VPS, giải nén vào thư mục mới, chạy UPDATE.sh bằng root.

UPDATE.sh kiểm tra SHA256, dừng lưu nhân vật, sao lưu SQL và JAR cũ, thay duy nhất server/app.jar rồi kiểm tra sẵn sàng; thất bại thì trả lại JAR cũ. Các bảng admin bổ sung tạo tự động IF NOT EXISTS. Không nhập seed, không thay tài khoản, cấu hình hoặc mật khẩu. Có gián đoạn ngắn khi cập nhật. Không chạy lại install.sh.
