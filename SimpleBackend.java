import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public class SimpleBackend {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/tenant/info", new TenantInfoHandler());
        server.createContext("/api/tenant/activities", new ActivitiesHandler());
        server.createContext("/api/tenant/update-request", new UpdateRequestHandler());
        server.createContext("/api/tenant/get-history", new GetHistoryHandler());
        server.createContext("/api/admin/households", new HouseholdListHandler());
        server.createContext("/api/admin/residents", new ResidentListHandler());
        server.createContext("/api/admin/requests", new AdminRequestHandler());
        server.createContext("/api/admin/save-household", new SaveHouseholdHandler());
        server.createContext("/api/admin/delete-household", new DeleteHouseholdHandler());
        server.createContext("/api/admin/save-resident", new SaveResidentHandler());
        server.createContext("/api/admin/delete-resident", new DeleteResidentHandler());
        server.createContext("/api/admin/save-activity", new SaveActivityHandler());
        server.createContext("/api/admin/delete-activity", new DeleteActivityHandler());
        server.createContext("/api/admin/process-request", new ProcessRequestHandler());
        server.createContext("/api/tenant/cancel-request", new CancelRequestHandler());
        server.createContext("/api/flats", new GetFlatsHandler());
        server.createContext("/api/book-flat", new CreateBookingHandler());
        server.createContext("/api/bookings", new GetBookingsHandler());
        server.setExecutor(null);
        System.out.println(">>> SERVER DA KET NOI MYSQL TAI CONG 8080");
        server.start();
    }

    private static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // Thêm dòng này để nạp Driver
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return DriverManager.getConnection("jdbc:mysql://localhost:3306/quanlidancu", "root", "123456");
    }
    static class AdminRequestHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("GET".equalsIgnoreCase(e.getRequestMethod())) {
                StringBuilder sb = new StringBuilder("[");
                try (Connection conn = getConnection()) {
                    ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM update_requests ORDER BY created_date DESC");
                    while (rs.next()) {
                        // Ưu tiên lấy SĐT từ form yêu cầu (target_cccd), nếu không có mới lấy SĐT tài khoản
                        String phone = rs.getString("target_cccd");
                        if (phone == null || phone.isEmpty() || phone.equals("null")) {
                            phone = rs.getString("requester_phone");
                        }

                        sb.append("{");
                        sb.append("\"id\":\"").append(rs.getInt("id")).append("\",");
                        sb.append("\"request_code\":\"").append(rs.getString("request_code") != null ? rs.getString("request_code") : "YC"+rs.getInt("id")).append("\",");
                        sb.append("\"requester_name\":\"").append(rs.getString("target_name") != null ? rs.getString("target_name") : rs.getString("requester_name")).append("\",");
                        sb.append("\"phone\":\"").append(phone != null ? phone : "").append("\","); // Trả về trường phone
                        sb.append("\"request_type\":\"").append(rs.getString("request_type")).append("\",");
                        sb.append("\"created_date\":\"").append(rs.getString("created_date")).append("\",");
                        sb.append("\"note\":\"").append(rs.getString("note") != null ? rs.getString("note") : "").append("\",");
                        sb.append("\"admin_notes\":\"").append(rs.getString("admin_notes") != null ? rs.getString("admin_notes") : "").append("\",");
                        sb.append("\"status\":\"").append(rs.getString("status")).append("\"");
                        sb.append("},");
                    }
                    if (sb.length() > 1) sb.setLength(sb.length() - 1);
                } catch (SQLException ex) { ex.printStackTrace(); }
                sb.append("]");
                sendResponse(e, sb.toString());
            }
        }
    }
    static class ResidentListHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            StringBuilder sb = new StringBuilder("[");
            try (Connection conn = getConnection()) {
                String sql = "SELECT * FROM residents";
                ResultSet rs = conn.createStatement().executeQuery(sql);
                while (rs.next()) {
                    sb.append("{");
                    sb.append("\"id\":\"").append(rs.getString("username")).append("\",");
                    sb.append("\"full_name\":\"").append(rs.getString("full_name")).append("\",");
                    sb.append("\"cccd\":\"").append(rs.getString("cccd")).append("\","); // Key là cccd
                    sb.append("\"age\":").append(rs.getInt("age")).append(",");          // Tuổi số
                    sb.append("\"gender\":\"").append(rs.getString("gender")).append("\",");
                    sb.append("\"relationship\":\"").append(rs.getString("relation_to_owner")).append("\",");
                    sb.append("\"status\":\"").append(rs.getString("status")).append("\",");
                    sb.append("\"job\":\"").append(rs.getString("job") != null ? rs.getString("job") : "Khác").append("\",");
                    sb.append("\"household_id\":\"").append(rs.getString("household_id")).append("\"");
                    sb.append("},");
                }
                if (sb.length() > 1) sb.setLength(sb.length() - 1);
            } catch (SQLException ex) { ex.printStackTrace(); }
            sb.append("]");
            sendResponse(e, sb.toString());
        }
    }
    static class SaveResidentHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                String b = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                
                // Trích xuất các trường từ JSON (Hàm extractValue của bạn)
                String oldId = extractValue(b, "old_id"); // Nếu có là Sửa, không có là Thêm
                String fullName = extractValue(b, "full_name");
                String cccd = extractValue(b, "id_number");
                String phone = extractValue(b, "phone");
                String gender = extractValue(b, "gender");
                String job = extractValue(b, "occupation");
                String relation = extractValue(b, "relationship");
                String householdId = extractValue(b, "household_id");
                String address = extractValue(b, "address");
                String status = extractValue(b, "status");

                try (Connection conn = getConnection()) {
                    conn.setAutoCommit(false);
                    if (oldId == null || oldId.isEmpty()) {
                        // --- THÊM MỚI ---
                        String username = "res_" + (System.currentTimeMillis() % 100000);
                        // 1. Tạo tài khoản
                        PreparedStatement psAcc = conn.prepareStatement("INSERT INTO accounts (username, password) VALUES (?, '123')");
                        psAcc.setString(1, username);
                        psAcc.executeUpdate();

                        // 2. Tạo nhân khẩu
                        String sql = "INSERT INTO residents (username, full_name, cccd, phone, gender, job, relation_to_owner, household_id, hometown, status) VALUES (?,?,?,?,?,?,?,?,?,?)";
                        PreparedStatement psRes = conn.prepareStatement(sql);
                        psRes.setString(1, username); psRes.setString(2, fullName);
                        psRes.setString(3, cccd); psRes.setString(4, phone);
                        psRes.setString(5, gender); psRes.setString(6, job);
                        psRes.setString(7, relation); psRes.setString(8, householdId);
                        psRes.setString(9, address); psRes.setString(10, status);
                        psRes.executeUpdate();
                    } else {
                        // --- CHỈNH SỬA ---
                        String sql = "UPDATE residents SET full_name=?, cccd=?, phone=?, gender=?, job=?, relation_to_owner=?, household_id=?, hometown=?, status=? WHERE username=?";
                        PreparedStatement ps = conn.prepareStatement(sql);
                        ps.setString(1, fullName); ps.setString(2, cccd);
                        ps.setString(3, phone); ps.setString(4, gender);
                        ps.setString(5, job); ps.setString(6, relation);
                        ps.setString(7, householdId); ps.setString(8, address);
                        ps.setString(9, status); ps.setString(10, oldId);
                        ps.executeUpdate();
                    }
                    conn.commit();
                    sendResponse(e, "{\"status\":\"success\"}");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    sendResponse(e, "{\"status\":\"error\"}");
                }
            }
        }
    }
    static class DeleteResidentHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                String b = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String username = extractValue(b, "id"); // 'id' ở đây chính là username

                try (Connection conn = getConnection()) {
                    // Xóa ở bảng accounts, bảng residents sẽ tự động mất dữ liệu do ON DELETE CASCADE
                    PreparedStatement ps = conn.prepareStatement("DELETE FROM accounts WHERE username = ?");
                    ps.setString(1, username);
                    
                    int rows = ps.executeUpdate();
                    if (rows > 0) {
                        sendResponse(e, "{\"status\":\"success\"}");
                    } else {
                        sendResponse(e, "{\"status\":\"error\", \"message\":\"Không tìm thấy nhân khẩu\"}");
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    sendResponse(e, "{\"status\":\"error\"}");
                }
            }
        }
    }
    static class HouseholdListHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            StringBuilder sb = new StringBuilder("[");
            try (Connection conn = getConnection()) {
                String sql = "SELECT household_id, " +
                            "(SELECT full_name FROM residents r2 WHERE r2.household_id = r1.household_id AND r2.relation_to_owner = 'Chủ hộ' LIMIT 1) as head, " +
                            "ANY_VALUE(hometown) as addr, COUNT(*) as cnt " +
                            "FROM residents r1 GROUP BY household_id";
                ResultSet rs = conn.createStatement().executeQuery(sql);
                while (rs.next()) {
                    String headName = rs.getString("head") != null ? rs.getString("head") : "Chưa xác định";
                    sb.append(String.format("{\"code\":\"%s\",\"headName\":\"%s\",\"address\":\"%s\",\"memberCount\":%d},",
                        rs.getString("household_id"), headName, rs.getString("addr"), rs.getInt("cnt")));
                }
                if (sb.length() > 1) sb.setLength(sb.length() - 1);
            } catch (SQLException ex) { ex.printStackTrace(); }
            sb.append("]");
            sendResponse(e, sb.toString());
        }
    }
    static class SaveHouseholdHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                String b = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String oldCode = extractValue(b, "old_code");
                String newCode = extractValue(b, "new_code");
                String headName = extractValue(b, "head_name");
                String address = extractValue(b, "address");

                try (Connection conn = getConnection()) {
                    conn.setAutoCommit(false); // Bắt đầu Transaction để tránh lỗi nửa chừng

                    if (oldCode == null || oldCode.isEmpty() || oldCode.equals("")) {
                        // --- THÊM MỚI ---
                        String tempUser = "user_" + (System.currentTimeMillis() % 100000);
                        
                        // 1. Tạo Account trước (Bắt buộc vì có khóa ngoại)
                        PreparedStatement psAcc = conn.prepareStatement(
                            "INSERT INTO accounts (username, password, role) VALUES (?, '123', 'tenant')");
                        psAcc.setString(1, tempUser);
                        psAcc.executeUpdate();

                        // 2. Sau đó mới tạo Resident (Chủ hộ)
                        PreparedStatement psRes = conn.prepareStatement(
                            "INSERT INTO residents (username, full_name, household_id, hometown, relation_to_owner, status) VALUES (?, ?, ?, ?, 'Chủ hộ', 'Thường trú')");
                        psRes.setString(1, tempUser);
                        psRes.setString(2, headName);
                        psRes.setString(3, newCode);
                        psRes.setString(4, address);
                        psRes.executeUpdate();

                    } else {
                        // --- CHỈNH SỬA (Giữ nguyên logic cũ nhưng tối ưu hơn) ---
                        // Cập nhật địa chỉ cho tất cả thành viên trong hộ
                        PreparedStatement ps1 = conn.prepareStatement(
                            "UPDATE residents SET household_id = ?, hometown = ? WHERE household_id = ?");
                        ps1.setString(1, newCode);
                        ps1.setString(2, address);
                        ps1.setString(3, oldCode);
                        ps1.executeUpdate();

                        // Cập nhật tên chủ hộ
                        PreparedStatement ps2 = conn.prepareStatement(
                            "UPDATE residents SET full_name = ? WHERE household_id = ? AND relation_to_owner = 'Chủ hộ'");
                        ps2.setString(1, headName);
                        ps2.setString(2, newCode);
                        ps2.executeUpdate();
                    }

                    conn.commit(); // Hoàn tất mọi thay đổi
                    sendResponse(e, "{\"status\":\"success\"}");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    sendResponse(e, "{\"status\":\"error\", \"message\":\"" + ex.getMessage() + "\"}");
                }
            }
        }
    }
    static class DeleteHouseholdHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                // Đọc body để lấy mã hộ cần xóa
                String b = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String householdCode = extractValue(b, "code");

                try (Connection conn = getConnection()) {
                    // Bước 1: Tìm danh sách username thuộc hộ khẩu này
                    // Bước 2: Xóa các username đó ở bảng accounts 
                    // (Do có ON DELETE CASCADE nên residents sẽ tự bị xóa theo)
                    String sql = "DELETE FROM accounts WHERE username IN (SELECT username FROM residents WHERE household_id = ?)";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, householdCode);
                    
                    int rowsDeleted = ps.executeUpdate();
                    
                    if (rowsDeleted > 0) {
                        sendResponse(e, "{\"status\":\"success\", \"message\":\"Đã xóa hộ khẩu và các thành viên liên quan\"}");
                    } else {
                        sendResponse(e, "{\"status\":\"error\", \"message\":\"Không tìm thấy hộ khẩu\"}");
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    sendResponse(e, "{\"status\":\"error\", \"message\":\"Lỗi database\"}");
                }
            }
        }
    }
    static class LoginHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                String b = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String u = extractValue(b, "username");
                String p = extractValue(b, "password");
                String res = "{\"status\":\"fail\", \"message\":\"Sai tên đăng nhập hoặc mật khẩu!\"}";
                try (Connection conn = getConnection()) {
                    PreparedStatement ps = conn.prepareStatement("SELECT role FROM accounts WHERE username=? AND password=?");
                    ps.setString(1, u); ps.setString(2, p);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        String role = rs.getString("role"); // Lấy role từ DB
                        res = String.format("{\"status\":\"success\",\"username\":\"%s\",\"role\":\"%s\"}", u, role);
                    }
                } catch (SQLException ex) { ex.printStackTrace(); }
                sendResponse(e, res);
            }
        }
    }

    static class TenantInfoHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            String u = e.getRequestURI().getQuery().split("user=")[1];
            String res = "{}";
            try (Connection conn = getConnection()) {
                // Bước 1: Lấy thông tin cá nhân của người đang đăng nhập
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM residents WHERE username=?");
                ps.setString(1, u);
                ResultSet rs = ps.executeQuery();
                
                if (rs.next()) {
                    String householdId = rs.getString("household_id"); // Lấy mã hộ khẩu
                    
                    // Bước 2: Tìm tất cả thành viên có cùng mã hộ khẩu (loại trừ bản thân)
                    StringBuilder fam = new StringBuilder("[");
                    PreparedStatement psFam = conn.prepareStatement(
                        "SELECT full_name, relation_to_owner, job, username FROM residents WHERE household_id=? AND username != ?");
                    psFam.setString(1, householdId);
                    psFam.setString(2, u);
                    ResultSet rsFam = psFam.executeQuery();
                    
                    while (rsFam.next()) {
                        fam.append(String.format("{\"name\":\"%s\",\"relation\":\"%s\",\"job\":\"%s\",\"username\":\"%s\"},",
                            rsFam.getString("full_name"), rsFam.getString("relation_to_owner"), 
                            rsFam.getString("job"), rsFam.getString("username")));
                    }
                    if (fam.length() > 1) fam.setLength(fam.length() - 1); // Xóa dấu phẩy cuối
                    fam.append("]");

                    // Trả về JSON đầy đủ để Frontend hiển thị
                    res = String.format("{\"name\":\"%s\",\"gender\":\"%s\",\"ethnicity\":\"%s\",\"nation\":\"%s\",\"cccd\":\"%s\",\"relationToOwner\":\"%s\",\"phone\":\"%s\",\"photo\":\"%s\",\"family\":%s}",
                        rs.getString("full_name"), rs.getString("gender"), rs.getString("ethnicity"), 
                        rs.getString("nation"), rs.getString("cccd"), rs.getString("relation_to_owner"), 
                        rs.getString("phone"), rs.getString("photo_url"), fam.toString());
                }
            } catch (SQLException ex) { ex.printStackTrace(); }
            sendResponse(e, res);
        }
    }

    static class UpdateRequestHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                String b = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String res = "{\"status\":\"error\"}";
                
                try (Connection conn = getConnection()) {
                    // SỬA TÊN CỘT: target_type -> request_type
                    String sql = "INSERT INTO update_requests (sender_username, request_type, target_name, target_cccd, note, status) VALUES (?, ?, ?, ?, ?, 'Chờ xử lý')";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, extractValue(b, "sender"));
                    ps.setString(2, extractValue(b, "target")); // Loại yêu cầu
                    ps.setString(3, extractValue(b, "name"));   // Họ tên người hưởng
                    ps.setString(4, extractValue(b, "cccd"));   // SĐT (lưu vào cccd)
                    ps.setString(5, extractValue(b, "note"));
                    
                    int rows = ps.executeUpdate();
                    if (rows > 0) res = "{\"status\":\"success\"}";
                } catch (SQLException ex) { 
                    ex.printStackTrace(); // Xem lỗi ở Terminal Java
                }
                sendResponse(e, res); // Gửi kết quả thật sự của SQL
            }
        }
    }
    static class ProcessRequestHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                String b = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String id = extractValue(b, "id");
                String status = extractValue(b, "status");
                String adminNotes = extractValue(b, "admin_notes");

                try (Connection conn = getConnection()) {
                    String sql = "UPDATE update_requests SET status = ?, admin_notes = ?, processed_date = CURRENT_DATE WHERE id = ?";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, status);
                    ps.setString(2, adminNotes);
                    ps.setInt(3, Integer.parseInt(id));
                    ps.executeUpdate();
                    
                    sendResponse(e, "{\"status\":\"success\"}");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    sendResponse(e, "{\"status\":\"error\"}");
                }
            }
        }
    }
    static class GetHistoryHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            String query = e.getRequestURI().getQuery();
            String u = (query != null && query.contains("user=")) ? query.split("user=")[1].split("&")[0] : "";
            
            StringBuilder sb = new StringBuilder("[");
            try (Connection conn = getConnection()) {
                // Lấy dữ liệu từ bảng update_requests
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM update_requests WHERE sender_username=?");
                ps.setString(1, u);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    // Xử lý logic tên: Nếu target_name null thì lấy requester_name
                    String name = rs.getString("target_name");
                    if (name == null || name.isEmpty()) name = rs.getString("requester_name");

                    // Xử lý logic CCCD: Lấy từ cột target_cccd
                    String cccd = rs.getString("target_cccd");
                    if (cccd == null || cccd.isEmpty()) cccd = "Chưa cung cấp";

                    sb.append("{");
                    sb.append("\"id\":\"").append(rs.getString("request_code") != null ? rs.getString("request_code") : "YC" + rs.getInt("id")).append("\",");
                    sb.append("\"type\":\"").append(rs.getString("request_type")).append("\","); // Loại yêu cầu
                    sb.append("\"name\":\"").append(name).append("\",");
                    sb.append("\"cccd\":\"").append(cccd).append("\",");
                    sb.append("\"note\":\"").append(rs.getString("note")).append("\",");
                    sb.append("\"status\":\"").append(rs.getString("status")).append("\"");
                    sb.append("},");
                }
                if (sb.length() > 1) sb.setLength(sb.length() - 1);
            } catch (SQLException ex) { ex.printStackTrace(); }
            sb.append("]");
            sendResponse(e, sb.toString());
        }
    }

    static class ActivitiesHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            StringBuilder sb = new StringBuilder("[");
            try (Connection conn = getConnection()) {
                // Lấy dữ liệu sắp xếp theo ngày mới nhất[cite: 17]
                ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM activities ORDER BY start_date DESC");
                while (rs.next()) {
                    sb.append(String.format(
                        "{\"id\":%d,\"title\":\"%s\",\"type\":\"%s\",\"status\":\"%s\",\"description\":\"%s\",\"start_date\":\"%s\",\"location\":\"%s\",\"organizer\":\"%s\"},",
                        rs.getInt("id"), 
                        rs.getString("title"), 
                        rs.getString("activity_type"), 
                        rs.getString("status"), 
                        rs.getString("details"), 
                        rs.getString("start_date"), 
                        rs.getString("location"), 
                        rs.getString("manager")
                    ));
                }
                if (sb.length() > 1) sb.setLength(sb.length() - 1);
            } catch (SQLException ex) { ex.printStackTrace(); }
            sb.append("]");
            sendResponse(e, sb.toString());
        }
    }

    static class SaveActivityHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                String b = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String id = extractValue(b, "id");
                String title = extractValue(b, "title");
                String type = extractValue(b, "type");
                String status = extractValue(b, "status");
                String location = extractValue(b, "location");
                String date = extractValue(b, "date");
                String manager = extractValue(b, "manager");
                String details = extractValue(b, "details");

                try (Connection conn = getConnection()) {
                    if (id == null || id.isEmpty()) {
                        String sql = "INSERT INTO activities (title, activity_type, status, location, start_date, manager, details) VALUES (?,?,?,?,?,?,?)";
                        PreparedStatement ps = conn.prepareStatement(sql);
                        ps.setString(1, title); ps.setString(2, type); ps.setString(3, status);
                        ps.setString(4, location); ps.setString(5, date); ps.setString(6, manager); ps.setString(7, details);
                        ps.executeUpdate();
                    } else {
                        String sql = "UPDATE activities SET title=?, activity_type=?, status=?, location=?, start_date=?, manager=?, details=? WHERE id=?";
                        PreparedStatement ps = conn.prepareStatement(sql);
                        ps.setString(1, title); ps.setString(2, type); ps.setString(3, status);
                        ps.setString(4, location); ps.setString(5, date); ps.setString(6, manager); ps.setString(7, details);
                        ps.setInt(8, Integer.parseInt(id));
                        ps.executeUpdate();
                    }
                    sendResponse(e, "{\"status\":\"success\"}");
                } catch (Exception ex) { ex.printStackTrace(); sendResponse(e, "{\"status\":\"error\"}"); }
            }
        }
    }

    static class DeleteActivityHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                String b = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String id = extractValue(b, "id");
                try (Connection conn = getConnection()) {
                    PreparedStatement ps = conn.prepareStatement("DELETE FROM activities WHERE id = ?");
                    ps.setInt(1, Integer.parseInt(id));
                    ps.executeUpdate();
                    sendResponse(e, "{\"status\":\"success\"}");
                } catch (Exception ex) { sendResponse(e, "{\"status\":\"error\"}"); }
            }
        }
    }
    static class CancelRequestHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                String b = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String idStr = extractValue(b, "id");
                String res = "{\"status\":\"error\"}";

                try (Connection conn = getConnection()) {
                    // Chỉ cho phép xóa nếu trạng thái là 'Chờ xử lý' để đảm bảo tính logic
                    String sql = "DELETE FROM update_requests WHERE request_code = ? AND status = 'Chờ xử lý'";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, idStr);
                    
                    int rows = ps.executeUpdate();
                    if (rows > 0) res = "{\"status\":\"success\"}";
                    else res = "{\"status\":\"error\", \"message\":\"Không thể hủy yêu cầu đã được xử lý\"}";
                } catch (SQLException ex) { ex.printStackTrace(); }
                sendResponse(e, res);
            }
        }
    }
    // new user
    // 1. API Lấy danh sách căn hộ
static class GetFlatsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        setupCORS(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/quanlidancu", "root", "123456")) {

            // Truy vấn khớp với cột status ENUM('Empty', 'Booked', 'Sold')
            String sql = "SELECT * FROM flats WHERE status = 'Empty'"; 
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                
                String desc = rs.getString("description");
                if (desc == null) desc = "";
                desc = desc.replace("\"", "\\\"");

                json.append(String.format(
                    "{\"flatID\":\"%s\",\"block\":\"%s\",\"bhkType\":\"%s\",\"price\":%d,\"status\":\"%s\",\"desc\":\"%s\"}",
                    rs.getString("flatID"),
                    rs.getString("block"),
                    rs.getString("bhkType"),
                    rs.getLong("price"),
                    rs.getString("status"),
                    desc
                ));
                first = false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, "{\"error\":\"Database error\"}");
            return;
        }
        json.append("]");
        sendResponse(exchange, json.toString());
    }
}
static class CreateBookingHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        setupCORS(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String name = extractValue(body, "customerName");
            String phone = extractValue(body, "phone");
            String flatID = extractValue(body, "flatID");
            String username = extractValue(body, "username");

            try (Connection conn = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/quanlidancu", "root", "123456")) {

                conn.setAutoCommit(false);

                // 1. Kiểm tra trạng thái thực tế trong DB (Sửa thành 'Empty')
                String checkSql = "SELECT status FROM flats WHERE flatID = ?";
                PreparedStatement check = conn.prepareStatement(checkSql);
                check.setString(1, flatID);
                ResultSet rs = check.executeQuery();

                // Logic so sánh khớp với Database ENUM
                if (!rs.next() || !"Empty".equals(rs.getString("status"))) {
                    sendResponse(exchange, "{\"status\":\"error\",\"message\":\"Căn hộ không khả dụng hoặc đã được đặt!\"}");
                    return;
                }

                // 2. Thêm bản ghi vào bảng bookings (Cột 'date' đã khớp DB)
                String insertBooking = "INSERT INTO bookings (customerName, phone, flatID, username, status) VALUES (?, ?, ?, ?, 'Pending')";
                PreparedStatement ps1 = conn.prepareStatement(insertBooking);
                
                ps1.setString(1, name);
                ps1.setString(2, phone);
                ps1.setString(3, flatID);
                ps1.setString(4, username); // 👈 THÊM USERNAME Ở ĐÂY

                ps1.executeUpdate();

                // 3. Cập nhật trạng thái căn hộ sang 'Booked'
                String updateFlat = "UPDATE flats SET status = 'Booked' WHERE flatID = ?";
                PreparedStatement ps2 = conn.prepareStatement(updateFlat);
                ps2.setString(1, flatID);
                ps2.executeUpdate();

                conn.commit();
                sendResponse(exchange, "{\"status\":\"success\"}");

            } catch (SQLException e) {
                e.printStackTrace();
                sendResponse(exchange, "{\"status\":\"error\",\"message\":\"Lỗi cơ sở dữ liệu\"}");
            }
        }
    }
}
static class GetBookingsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        setupCORS(exchange);
        

        StringBuilder json = new StringBuilder("[");

        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/quanlidancu", "root", "123456")) {

            String sql = "SELECT * FROM bookings ORDER BY bookingID DESC";
            PreparedStatement stmt = conn.prepareStatement(sql);
            
            ResultSet rs = stmt.executeQuery();

            boolean first = true;

            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                json.append("{")
                    .append("\"order_id\":").append(rs.getInt("bookingID")).append(",")
                    .append("\"flatID\":\"").append(rs.getString("flatID")).append("\",")
                    .append("\"customerName\":\"").append(rs.getString("customerName")).append("\",")
                    .append("\"phone\":\"").append(rs.getString("phone")).append("\",")
                    .append("\"bookingDate\":\"").append(rs.getTimestamp("date")).append("\",")
                    .append("\"status\":\"").append(rs.getString("status")).append("\"")
                .append("}");
            }

        } catch (Exception e) {
    e.printStackTrace(); // QUAN TRỌNG NHẤT
    sendResponse(exchange,
        "{\"error\":\"DB error\", \"detail\":\"" + e.getMessage() + "\"}");
}

        json.append("]");

        sendResponse(exchange, json.toString());
    }
}
    private static String extractValue(String json, String key) {
        try { return json.split("\"" + key + "\":\"")[1].split("\"")[0]; } catch (Exception e) { return ""; }
    }

    private static void setupCORS(HttpExchange e) throws IOException {
        e.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        e.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        e.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(e.getRequestMethod())) e.sendResponseHeaders(204, -1);
    }

    private static void sendResponse(HttpExchange e, String r) throws IOException {
        e.getResponseHeaders().set("Content-Type", "application/json");
        byte[] b = r.getBytes(StandardCharsets.UTF_8);
        e.sendResponseHeaders(200, b.length);
        try (OutputStream os = e.getResponseBody()) { os.write(b); }
    }
}