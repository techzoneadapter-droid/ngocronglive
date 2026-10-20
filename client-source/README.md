# Client có mã nguồn — bản tích hợp thử nghiệm

Server và database: giữ Yanii đã vá trong `game-source/`, không nhập schema của server Clone.
Client: source C# từ duphatminh/NgocRongOnline_Clone, ghim `26cad142d657449bd27f694f0214a43c9382036d`.

## Có trong nhánh này
- Toàn bộ script C#, scene, cấu hình project và package của client tham chiếu.
- Đổi scene build sang SampleScene có thật, giữ GUID Main đúng với scene.
- Chọn Unity 2022.3.26f1 để khớp phiên bản player hiện đang dùng. Chuyển phiên bản này chưa được Unity Editor xác nhận.
- Adapter danh sách server JSON Yanii sang chuỗi mà client source đọc; mặc định chỉ localhost:14445, không dùng server cũ từ cache.
- Khai báo version giao thức 2.4.0. Đây chưa phải bằng chứng toàn bộ packet tương thích.
- Source bảng phím F10: đổi phím lên/xuống/trái/phải/hành động và 9 ô kỹ năng, chống gán trùng, lưu PlayerPrefs, phục hồi mặc định.
- Giữ phím nhập chat/menu gốc; đóng băng input game khi mở bảng, xả phím giữ khi mất focus.
- Thời gian Unity về 1x thay vì mặc định mod 1.5x, shader fallback Sprites/Default.

Các file này không tự thay binary client trong bản ZIP 840 MB đã phát hành.

## Tạo project và build trên Windows
Cần Git, Python 3, Unity Editor 2022.3.26f1 đã kích hoạt và Windows Build Support. Các tài nguyên Unity còn lại được lấy tự động từ upstream ghim; repo không chứa toàn bộ texture/audio và vẫn cần mạng khi chuẩn bị lần đầu.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\client-source\build-windows.ps1 -UnityEditor "C:\Program Files\Unity\Hub\Editor\2022.3.26f1\Editor\Unity.exe"
```

Script tạo thư mục build mới, không xóa thư mục game/data cũ. Không tự chạy player. Đọc đường dẫn SOURCE CLIENT BUILT khi build xong.

Để thử: giữ SERVER.cmd của bản local đang chạy; chạy client vừa build, tạo nhân vật thử riêng, kiểm tra login/map/di chuyển/NPC/đánh quái/nhặt đồ/nhiệm vụ và F10. Tuyệt đối chưa dùng dữ liệu chơi thật để đánh giá tương thích. Không thay server bằng NRO_SERVER của repo Clone.

## Trạng thái xác minh
- Có bài kiểm tra .NET cho core ánh xạ phím và adapter JSON; không thay cho Unity runtime.
- Có kiểm tra tĩnh scene/GUID và dây nối input.
- Chưa có Unity Editor trong môi trường triển khai thay đổi này, nên chưa compile toàn bộ C# hoặc chơi thử client source với Yanii.
- Bản PC hiện tại vẫn dùng client binary Yanii đã kiểm thử trước đó; chưa đưa preview này thành client mặc định.
- 15 chuỗi nhiệm vụ vẫn cần chơi kiểm chứng; thay client không tự sửa logic nhiệm vụ server.

## Nguồn và giấy phép
Giữ tác giả và nhận xét trong source. Repo tham chiếu không có file LICENSE tại commit đã ghim; việc đưa source vào nhánh thử nghiệm này không làm nó tự mang giấy phép MIT của phần khác trong repository. Đây không phải tuyên bố về quyền phát hành thương mại hay tài nguyên Dragon Ball. Xem ../docs/INTEGRATION.md để biết ranh giới nguồn.
