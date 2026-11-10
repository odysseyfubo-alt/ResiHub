import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** 顺序基线：分别测「免登录接口」「登录态接口」，排除 PowerShell 客户端开销 */
public class Bench {
    public static void main(String[] args) throws Exception {
        String base = args[0];
        String token = args.length > 1 ? args[1] : null;
        String path = args.length > 2 ? args[2] : "/community/list";
        int n = args.length > 3 ? Integer.parseInt(args[3]) : 20;

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        List<Long> list = new ArrayList<>();
        // 预热 3 次
        for (int i = 0; i < 3; i++) {
            send(client, base + path, token);
        }
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            send(client, base + path, token);
            list.add((System.nanoTime() - t0) / 1_000_000);
        }
        list.sort(Long::compareTo);
        long sum = list.stream().mapToLong(Long::longValue).sum();
        System.out.println("path=" + path + " n=" + n
                + "  min=" + list.get(0) + "ms"
                + "  avg=" + (sum / n) + "ms"
                + "  p50=" + list.get(n / 2) + "ms"
                + "  max=" + list.get(n - 1) + "ms");
    }

    private static void send(HttpClient client, String url, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET();
        if (token != null) {
            b.header("authorization", token);
        }
        client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
