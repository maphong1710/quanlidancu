import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class SimpleBackend {
    private static final String USER_FILE = "login_details.json";
    private static final String HISTORY_FILE = "update_requests.json";

    private static Map<String, String> userDatabase = new HashMap<>();
    private static Map<String, String> residentInfo = new HashMap<>();
    private static List<String> updateRequests = new ArrayList<>();

    static {
        loadUserData();
        loadHistoryData();

        // Cấu trúc: Tên|Tuổi|SĐT|CCCD|Quê quán|Nghề nghiệp|Hộ khẩu|Gia đình|Link Ảnh|Dân tộc|Quốc tịch|Quan hệ|Giới tính
        residentInfo.put("phongg", "Nguyễn Phong|20|0901234567|001204001234|Hồ Chí Minh|Sinh viên|HK00456|Trần C-Anh-Kỹ sư-tranc;Lê D-Chị-Kế toán-led|https://i.imgur.com/8RK67fR.png|Kinh|Việt Nam|Chủ hộ|Nam");
        residentInfo.put("tranc", "Trần C|28|0909998887|001204009999|Hồ Chí Minh|Kỹ sư phần mềm|HK00456|Nguyễn Phong-Em-Sinh viên-phongg;Lê D-Em-Kế toán-led|https://i.imgur.com/X267vPh.png|Kinh|Việt Nam|Thành viên|Nam");
        residentInfo.put("led", "Lê D|24|0901112223|001204008888|Hồ Chí Minh|Kế toán|HK00456|Nguyễn Phong-Em-Sinh viên-phongg;Trần C-Anh-Kỹ sư-tranc|https://i.imgur.com/X267vPh.png|Kinh|Việt Nam|Thành viên|Nữ");
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/tenant/info", new TenantInfoHandler());
        server.createContext("/api/tenant/update-request", new UpdateRequestHandler());
        server.createContext("/api/tenant/get-history", new GetHistoryHandler());
        server.setExecutor(null);
        System.out.println("Backend đang chạy tại: http://localhost:8080");
        server.start();
    }

    // --- LOGIC FILE ---
    private static void loadHistoryData() {
        File file = new File(HISTORY_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String content = reader.lines().collect(Collectors.joining());
            if (content.startsWith("[") && content.endsWith("]")) {
                content = content.substring(1, content.length() - 1);
                if (content.trim().isEmpty()) return;
                String[] parts = content.split("(?<=\\}),(?=\\{)");
                updateRequests.addAll(Arrays.asList(parts));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static synchronized void saveHistoryData() {
        try (FileWriter writer = new FileWriter(HISTORY_FILE)) {
            writer.write("[" + String.join(",", updateRequests) + "]");
        } catch (IOException e) { e.printStackTrace(); }
    }

    // --- HANDLERS ---
    static class UpdateRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setupCORS(exchange);
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                updateRequests.add(body);
                saveHistoryData();
                sendResponse(exchange, "{\"status\":\"success\", \"message\":\"Gửi và lưu thành công!\"}");
            }
        }
    }

    static class GetHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setupCORS(exchange);
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                String user = (query != null && query.contains("user=")) ? query.split("user=")[1] : "";
                String historyJson = "[" + updateRequests.stream()
                        .filter(req -> req.contains("\"sender\":\"" + user + "\""))
                        .collect(Collectors.joining(",")) + "]";
                sendResponse(exchange, historyJson);
            }
        }
    }

    static class TenantInfoHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            String query = e.getRequestURI().getQuery();
            String user = (query != null && query.contains("user=")) ? query.split("user=")[1] : "phongg";
            if (residentInfo.containsKey(user)) {
                String[] p = residentInfo.get(user).split("\\|");
                StringBuilder fam = new StringBuilder("[");
                String[] members = p[7].split(";");
                for (int i = 0; i < members.length; i++) {
                    String[] m = members[i].split("-");
                    fam.append(String.format("{\"name\":\"%s\",\"relation\":\"%s\",\"job\":\"%s\",\"username\":\"%s\"}", m[0], m[1], m[2], m[3]));
                    if (i < members.length - 1) fam.append(",");
                }
                fam.append("]");
                String response = String.format("{\"name\":\"%s\",\"age\":\"%s\",\"phone\":\"%s\",\"cccd\":\"%s\",\"hometown\":\"%s\",\"job\":\"%s\",\"household\":\"%s\",\"family\":%s,\"photo\":\"%s\",\"ethnicity\":\"%s\",\"nation\":\"%s\",\"relationToOwner\":\"%s\",\"gender\":\"%s\"}",
                        p[0], p[1], p[2], p[3], p[4], p[5], p[6], fam.toString(), p[8], p[9], p[10], p[11], p[12]);
                sendResponse(e, response);
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String user = body.split("\"username\":\"")[1].split("\"")[0];
                String pass = body.split("\"password\":\"")[1].split("\"")[0];
                String resp = (userDatabase.containsKey(user) && userDatabase.get(user).equals(pass)) ?
                        "{\"status\":\"success\", \"role\":\"tenant\", \"username\":\"" + user + "\"}" :
                        "{\"status\":\"fail\", \"message\":\"Sai!\"}";
                sendResponse(e, resp);
            }
        }
    }

    private static void loadUserData() {
        File f = new File(USER_FILE);
        if (!f.exists()) { userDatabase.put("phongg", "2222"); saveUserData(); return; }
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String c = r.lines().collect(Collectors.joining());
            c = c.replace("{", "").replace("}", "").replace("\"", "");
            for (String p : c.split(",")) {
                String[] kv = p.split(":");
                if (kv.length == 2) userDatabase.put(kv[0].trim(), kv[1].trim());
            }
        } catch (Exception e) {}
    }

    private static synchronized void saveUserData() {
        try (FileWriter w = new FileWriter(USER_FILE)) {
            StringBuilder j = new StringBuilder("{");
            int count = 0;
            for (Map.Entry<String, String> e : userDatabase.entrySet()) {
                j.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
                if (++count < userDatabase.size()) j.append(",");
            }
            j.append("}");
            w.write(j.toString());
        } catch (IOException e) {}
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