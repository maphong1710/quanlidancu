-- 1. Khởi tạo Database
DROP DATABASE IF EXISTS quanlidancu;
CREATE DATABASE quanlidancu;
USE quanlidancu;

-- 2. Bảng Accounts (Bảng cha)
CREATE TABLE accounts (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(255) NOT NULL,
    role ENUM('admin', 'tenant', 'new user') DEFAULT 'new user',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Bảng Residents (Bảng con - Thêm cột status để khớp giao diện Nhân khẩu)
CREATE TABLE residents (
	id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE,
    full_name NVARCHAR(100),
    age INT,
    phone VARCHAR(15),
    cccd VARCHAR(20),
    hometown NVARCHAR(200),
    job NVARCHAR(100),
    household_id VARCHAR(20),
    family_data TEXT, 
    photo_url VARCHAR(255),
    ethnicity NVARCHAR(50) default 'Trống',
    nation NVARCHAR(50) default 'Trống',
    relation_to_owner NVARCHAR(50),
    gender NVARCHAR(10),
    status NVARCHAR(50) DEFAULT 'Thường trú', -- Thêm cột này để khớp statusColors trong React
    FOREIGN KEY (username) REFERENCES accounts(username) ON DELETE CASCADE
);

-- 4. Bảng Update Requests
CREATE TABLE update_requests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    request_code VARCHAR(20),      -- Tương ứng {r.request_code}[cite: 17]
    sender_username VARCHAR(50),
    requester_name NVARCHAR(100),  -- Tên người yêu cầu[cite: 17]
    requester_phone VARCHAR(15),   -- SĐT người yêu cầu[cite: 17]
    request_type NVARCHAR(100),    -- Loại yêu cầu (Khai sinh, Tạm trú...)[cite: 17]
    target_name NVARCHAR(100),
    target_cccd VARCHAR(20),
    note TEXT,
    admin_notes TEXT,              -- Ghi chú của Admin khi duyệt/từ chối[cite: 17]
    status NVARCHAR(50) DEFAULT 'Chờ xử lý', -- Các trạng thái khớp statusConfig[cite: 17]
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_date DATE,           -- Ngày xử lý xong[cite: 17]
    FOREIGN KEY (sender_username) REFERENCES accounts(username) ON DELETE CASCADE
);

-- 5. Bảng Activities (Lịch sinh hoạt)
CREATE TABLE activities (
    id INT AUTO_INCREMENT PRIMARY KEY,
    time_display VARCHAR(50), 
    activity_type NVARCHAR(50), -- Tương ứng với {a.type}
    title NVARCHAR(200),        -- Tương ứng với {a.title}[cite: 17]
    location NVARCHAR(200),
    manager NVARCHAR(100),      -- Tương ứng với {a.organizer}[cite: 17]
    status NVARCHAR(50),        -- Phải là: 'Sắp diễn ra', 'Đang diễn ra', 'Đã kết thúc', 'Đã hủy'[cite: 17]
    details TEXT,               -- Tương ứng với {a.description}[cite: 17]
    start_date DATETIME         -- Dùng để format date trong React[cite: 17]
);
-- 1. Bảng Flats (Căn hộ)
CREATE TABLE flats (
    flatID VARCHAR(20) PRIMARY KEY,
    block VARCHAR(10) NOT NULL,
    bhkType VARCHAR(20),
    price DECIMAL(15, 2),
    -- Khớp với UC-NU-02: Sử dụng trạng thái 'Empty' thay vì 'Available'
    status ENUM('Empty', 'Booked', 'Sold') DEFAULT 'Empty', 
    description TEXT
);

-- 2. Bảng New Users (Tài khoản khách hàng)
CREATE TABLE new_users (
    username VARCHAR(50) PRIMARY KEY,
    contactInfo NVARCHAR(255), 
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (username) REFERENCES accounts(username) ON DELETE CASCADE
);

-- 3. Bảng Bookings (Đơn đặt chỗ)
CREATE TABLE bookings (
    bookingID INT AUTO_INCREMENT PRIMARY KEY,
    -- Cho phép NULL để khớp với luồng khách vãng lai nhập Tên/SĐT trên Form
    username VARCHAR(50) NULL, 
    customerName NVARCHAR(100) NOT NULL,
    phone VARCHAR(15),
    flatID VARCHAR(20),
    -- Đổi tên thành 'date' để khớp với logic Java Backend
    date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    status NVARCHAR(50) DEFAULT 'Pending', 
    FOREIGN KEY (flatID) REFERENCES flats(flatID) ON DELETE SET NULL,
    FOREIGN KEY (username) REFERENCES new_users(username) ON DELETE CASCADE
);

-- ==========================================================
-- DỮ LIỆU MẪU ĐÃ ĐƯỢC ĐỒNG BỘ
-- ==========================================================

-- Insert Căn hộ với trạng thái 'Empty' để hiển thị được trên House List
INSERT INTO flats (flatID, block, bhkType, price, status, description) VALUES 
('A-101', 'Block A', '2 BHK', 2500000000, 'Empty', 'Căn hộ tầng trệt, view sân vườn.'),
('A-502', 'Block A', '3 BHK', 3800000000, 'Booked', 'Căn hộ hướng Đông Nam, đầy đủ nội thất.'),
('B-205', 'Block B', '1 BHK', 1500000000, 'Empty', 'Căn hộ studio phù hợp cho người độc thân.'),
('B-910', 'Block B', 'Penthouse', 7500000000, 'Empty', 'Căn hộ cao cấp tầng mái, có hồ bơi riêng.'),
('C-304', 'Block C', '2 BHK', 2700000000, 'Sold', 'Đã bàn giao cho chủ nhà.');
-- Insert Đơn đặt chỗ mẫu
INSERT INTO bookings (customerName, phone, flatID, status) VALUES 
(N'Lê Văn Hùng', '0912345001', 'A-101', 'Pending'),
(N'Nguyễn Thị Mai', '0988223344', 'A-502', 'Approved'),
(N'Trần Văn Bình', '0905112233', 'B-910', 'Pending'),
(N'Hoàng Gia Bảo', '0977665544', 'B-205', 'Cancelled');
-- ==========================================================
-- BẮT ĐẦU INSERT DỮ LIỆU MẪU
-- ==========================================================

-- BƯỚC 1: TẠO ACCOUNTS
INSERT INTO accounts (username, password, role) VALUES 
('admin', '1', 'admin'),
('phongg', '2222', 'tenant'),
('tranc', '123', 'tenant'),
('led_temp', '123', 'tenant'),
('an_nv', '123', 'tenant'),
('binh_nv', '123', 'tenant'),
('shasvat', '123', 'tenant'),
('binhbom', '123', 'tenant'),
('thomthinh', '123', 'tenant'),
('khach_hang_1', '123', 'new user'),
('khach_hang_2', '123', 'new user');

INSERT INTO new_users (username, contactInfo) VALUES 
('khach_hang_1', '0912-333-444'),
('khach_hang_2', 'khachhang2@gmail.com');
-- BƯỚC 2: NẠP CƯ DÂN VÀO 3 HỘ KHẨU (Kèm tình trạng cư trú)
-- Hộ 1: HK00456 (3 người)
INSERT INTO residents (id,username, full_name, age, phone, cccd, hometown, job, household_id, relation_to_owner, gender, status, photo_url) VALUES 
(1,'phongg', 'Nguyễn Phong', 20, '0901234567', '001204001234', 'Hà Nội', 'Sinh viên', 'HK00456', 'Chủ hộ', 'Nam', 'Thường trú', 'https://i.imgur.com/8RK67fR.png'),
(2,'tranc', 'Trần C', 28, '0909998887', '001204009999', 'Hà Nội', 'Kỹ sư', 'HK00456', 'Anh', 'Nam', 'Tạm trú', 'https://i.imgur.com/X267vPh.png'),
(3,'led_temp', 'Lê D', 36, '0901112223', '001204008888', 'Hà Nội', 'Kế toán', 'HK00456', 'Chị', 'Nữ', 'Tạm vắng', 'https://i.imgur.com/8RK67fR.png'),
(4,'binhbom', 'Bình bờm', 47, '0901112223', '001204006888', 'Hà Nội', 'Cảnh sát', 'HK00456', 'Bố', 'Nam', 'Thường trú', null),
(5,'thomthinh', 'Nguyễn Thị Thơm', 47, '0901112623', '001204006878', 'Hà Nội', 'Hướng dẫn viên', 'HK00456', 'mẹ', 'Nữ', 'Thường trú', null);

-- Hộ 2: HGD001 (2 người)
INSERT INTO residents (id,username, full_name, age, phone, cccd, hometown, job, household_id, relation_to_owner, gender, status) VALUES 
(6,'an_nv', 'Nguyễn Văn An', 45, '0912345678', '001204000111', 'Phường 1', 'Kinh doanh', 'HK00001', 'Chủ hộ', 'Nam', 'Thường trú'),
(7,'binh_nv', 'Nguyễn Văn Bình', 18, '0912345679', '001204000222', 'Phường 1', 'Học sinh', 'HK00001', 'Con', 'Nam', 'Thường trú');

-- Hộ 3: HK00999 (1 người)
INSERT INTO residents (id,username, full_name, age, phone, cccd, hometown, job, household_id, relation_to_owner, gender, status) VALUES 
(8,'shasvat', 'Shasvat Kumar', 30, '0988776655', '001204000333', 'Tầng 9, Groupod Tower', 'Đầu bếp', 'HK00999', 'Chủ hộ', 'Nam', 'Thường trú');

-- BƯỚC 3: DỮ LIỆU HOẠT ĐỘNG MẪU
INSERT INTO activities (time_display, activity_type, title, location, manager, status, details, start_date) VALUES 
('08:00 - 20/04/2026', 'Hội họp', 'Họp tổ dân phố định kỳ', 'Nhà văn hóa', 'Nguyễn Hải', 'Sắp diễn ra', 'Bàn về vấn đề an ninh và vệ sinh môi trường tháng 4.', '2026-04-20 08:00:00'),
('19:30 - 25/04/2026', 'Văn nghệ', 'Giao lưu văn hóa thiếu nhi', 'Sân khấu trung tâm', 'Trần Phong', 'Đang diễn ra', 'Chương trình văn nghệ chào mừng ngày giải phóng.', '2026-04-25 19:30:00'),
('07:00 - 15/04/2026', 'Vệ sinh', 'Tổng vệ sinh toàn khu', 'Sân chung', 'Lê Hòa', 'Đã kết thúc', 'Hoạt động dọn dẹp thường niên.', '2026-04-15 07:00:00');

-- Nạp dữ liệu mẫu để test các trạng thái và loại yêu cầu[cite: 17]
INSERT INTO update_requests (request_code, sender_username, requester_name, requester_phone, request_type, note, status) VALUES 
('YC1', 'phongg', 'Nguyễn Phong', '0901234567', 'Đăng ký tạm trú', 'Yêu cầu đăng ký tạm trú cho bạn.', 'Chờ xử lý'),
('YC2','an_nv', 'Nguyễn Văn An', '0912345678', 'Thay đổi thông tin hộ khẩu', 'Cập nhật lại địa chỉ hometown.', 'Từ chối'),
('YC3', 'shasvat', 'Shasvat Kumar', '0988776655', 'Khai sinh', 'Đăng ký khai sinh cho con.', 'Đã duyệt');

select * from update_requests
