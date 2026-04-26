import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class SimpleBackend {
    private static final String USER_FILE = "login_details.json";
    private static final String RESIDENT_FILE = "resident_info.json";
    private static final String HISTORY_FILE = "update_requests.json";
    private static final String ACTIVITY_FILE = "activities.json";

    private static Map<String, String> userDatabase = new HashMap<>();
    private static Map<String, String> residentInfo = new HashMap<>();
    private static List<String> updateRequests = new ArrayList<>();
    private static List<String> activityList = new ArrayList<>();

    static { loadAllData(); }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/tenant/info", new TenantInfoHandler());
        server.createContext("/api/tenant/update-request", new UpdateRequestHandler());
        server.createContext("/api/tenant/get-history", new GetHistoryHandler());
        server.createContext("/api/tenant/activities", new ActivitiesHandler());
        server.setExecutor(null);
        System.out.println(">>> BACKEND ALL-IN-ONE ĐANG CHẠY TẠI: http://localhost:8080");
        server.start();
    }

    // --- HỆ THỐNG LƯU TRỮ VĨNH VIỄN ---
    private static void loadAllData() {
        userDatabase = loadMap(USER_FILE, "phongg", "2222");
        residentInfo = loadMap(RESIDENT_FILE, "phongg", "Nguyễn Phong|20|0901234567|001204001234|Hồ Chí Minh|Sinh viên|HK00456|Trần C-Anh-Kỹ sư-tranc;Lê D-Chị-Kế toán-led|https://i.imgur.com/8RK67fR.png|Kinh|Việt Nam|Chủ hộ|Nam");

        // Thêm hồ sơ cho người thân để nhấn vào là xem được luôn
        if(!residentInfo.containsKey("tranc")) residentInfo.put("tranc", "Trần C|28|0909998887|001204009999|Hà Nội|Kỹ sư|HK00456|Nguyễn Phong-Em-Sinh viên-phongg|https://i.imgur.com/X267vPh.png|Kinh|Việt Nam|Thành viên|Nam");
        saveMap(RESIDENT_FILE, residentInfo);

        updateRequests = loadList(HISTORY_FILE);
        activityList = loadList(ACTIVITY_FILE);
        if (activityList.isEmpty()) {
            activityList.add("{\"time\":\"08:00 - 20/04/2026\",\"type\":\"Hội họp\",\"title\":\"Họp tổ dân phố\",\"location\":\"Nhà văn hóa\",\"manager\":\"Nguyễn Hải\",\"status\":\"Sắp tới\",\"details\":\"Bàn về an ninh.\" }");
            activityList.add("{\"time\":\"19:30 - 25/04/2026\",\"type\":\"Văn nghệ\",\"title\":\"Giao lưu văn hóa\",\"location\":\"Sân khấu\",\"manager\":\"Trần Phong\",\"status\":\"Sắp tới\",\"details\":\"Văn nghệ thiếu nhi.\" }");
            activityList.add("{\"time\":\"07:00 - 28/04/2026\",\"type\":\"Vệ sinh\",\"title\":\"Dọn rác Block A\",\"location\":\"Sân chung\",\"manager\":\"Lê Hòa\",\"status\":\"Đang chuẩn bị\",\"details\":\"Tổng vệ sinh.\" }");
            saveList(ACTIVITY_FILE, activityList);
        }
    }

    private static Map<String, String> loadMap(String fileName, String defK, String defV) {
        Map<String, String> map = new HashMap<>();
        File f = new File(fileName);
        if (!f.exists()) { map.put(defK, defV); saveMap(fileName, map); return map; }
        try (BufferedReader r = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
            String c = r.lines().collect(Collectors.joining()).replace("{","").replace("}","").replace("\"","");
            for (String p : c.split(",")) {
                String[] kv = p.split(":");
                if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
            }
        } catch (Exception e) {}
        return map;
    }

    private static synchronized void saveMap(String fileName, Map<String, String> map) {
        try (FileWriter w = new FileWriter(fileName, StandardCharsets.UTF_8)) {
            w.write("{" + map.entrySet().stream().map(e -> "\""+e.getKey()+"\":\""+e.getValue()+"\"").collect(Collectors.joining(",")) + "}");
        } catch (IOException e) {}
    }

    private static List<String> loadList(String fileName) {
        File f = new File(fileName);
        if (!f.exists()) return new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
            String c = r.lines().collect(Collectors.joining());
            if (c.startsWith("[") && c.endsWith("]")) {
                c = c.substring(1, c.length()-1);
                if (c.trim().isEmpty()) return new ArrayList<>();
                return new ArrayList<>(Arrays.asList(c.split("(?<=\\}),(?=\\{)")));
            }
        } catch (Exception e) {}
        return new ArrayList<>();
    }

    private static synchronized void saveList(String fileName, List<String> list) {
        try (FileWriter w = new FileWriter(fileName, StandardCharsets.UTF_8)) {
            w.write("[" + String.join(",", list) + "]");
        } catch (IOException e) {}
    }

    // --- HANDLERS ---
    static class ActivitiesHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException { setupCORS(e); sendResponse(e, "[" + String.join(",", activityList) + "]"); }
    }

    static class UpdateRequestHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                String b = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                updateRequests.add(b); saveList(HISTORY_FILE, updateRequests);
                sendResponse(e, "{\"status\":\"success\", \"message\":\"Đã gửi và lưu file JSON!\"}");
            }
        }
    }

    static class GetHistoryHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            String u = e.getRequestURI().getQuery().split("user=")[1];
            String r = "[" + updateRequests.stream().filter(s -> s.contains("\"sender\":\"" + u + "\"")).collect(Collectors.joining(",")) + "]";
            sendResponse(e, r);
        }
    }

    static class TenantInfoHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            String u = e.getRequestURI().getQuery().split("user=")[1];
            if (residentInfo.containsKey(u)) {
                String[] p = residentInfo.get(u).split("\\|");
                StringBuilder fam = new StringBuilder("[");
                String[] ms = p[7].split(";");
                for (int i=0; i<ms.length; i++) {
                    String[] m = ms[i].split("-");
                    fam.append(String.format("{\"name\":\"%s\",\"relation\":\"%s\",\"job\":\"%s\",\"username\":\"%s\"}", m[0], m[1], m[2], m[3]));
                    if (i<ms.length-1) fam.append(",");
                }
                fam.append("]");
                String res = String.format("{\"name\":\"%s\",\"age\":\"%s\",\"phone\":\"%s\",\"cccd\":\"%s\",\"hometown\":\"%s\",\"job\":\"%s\",\"household\":\"%s\",\"family\":%s,\"photo\":\"%s\",\"ethnicity\":\"%s\",\"nation\":\"%s\",\"relationToOwner\":\"%s\",\"gender\":\"%s\"}",
                        p[0], p[1], p[2], p[3], p[4], p[5], p[6], fam.toString(), p[8], p[9], p[10], p[11], p[12]);
                sendResponse(e, res);
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override public void handle(HttpExchange e) throws IOException {
            setupCORS(e);
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                String b = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String u = b.split("\"username\":\"")[1].split("\"")[0];
                String p = b.split("\"password\":\"")[1].split("\"")[0];
                String r = (userDatabase.getOrDefault(u, "").equals(p)) ? "{\"status\":\"success\",\"username\":\""+u+"\"}" : "{\"status\":\"fail\"}";
                sendResponse(e, r);
            }
        }
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
