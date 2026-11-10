import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ResiHub 团购并发压测：N 个用户同时抢 M 个名额。
 * 校验：成功数 == 名额数、无超卖、无重复下单、P99 延迟。
 *
 * 用法：java LoadTest.java <baseUrl> <groupId> <tokensFile> <stock> [concurrency]
 * 例：  java LoadTest.java http://127.0.0.1:8082 5 out/tokens.txt 10 200
 */
public class LoadTest {

    public static void main(String[] args) throws Exception {
        String base = args[0];
        long groupId = Long.parseLong(args[1]);
        Path tokenFile = Path.of(args[2]);
        int stock = Integer.parseInt(args[3]);
        int concurrency = args.length > 4 ? Integer.parseInt(args[4]) : 200;
        boolean singleUser = args.length > 5 && "single".equals(args[5]);

        List<String> tokens = new ArrayList<>();
        for (String line : Files.readAllLines(tokenFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                tokens.add(line.trim());
            }
        }
        if (tokens.size() < concurrency) {
            System.out.println("警告：token 只有 " + tokens.size() + " 个，压测并发调整为 " + tokens.size());
            concurrency = tokens.size();
        }
        System.out.println("目标: " + base + "  groupId=" + groupId + "  名额=" + stock + "  并发=" + concurrency
                + (singleUser ? "（同一用户重复抢）" : "（不同用户抢名额）"));

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .executor(java.util.concurrent.Executors.newFixedThreadPool(Math.max(concurrency, 16)))
                .build();

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger duplicated = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        long[] latencies = new long[concurrency];
        Thread[] threads = new Thread[concurrency];

        for (int i = 0; i < concurrency; i++) {
            final int idx = i;
            final String token = singleUser ? tokens.get(0) : tokens.get(i);
            threads[i] = new Thread(() -> {
                try {
                    // 预热：先把 TCP 连接和 JIT 顶上去，避免握手时间混入延迟统计
                    HttpRequest warm = HttpRequest.newBuilder()
                            .uri(URI.create(base + "/community/list"))
                            .timeout(Duration.ofSeconds(10))
                            .header("authorization", token)
                            .GET()
                            .build();
                    client.send(warm, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    start.await();
                    long t0 = System.nanoTime();
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(base + "/group-buy/" + groupId + "/join"))
                            .timeout(Duration.ofSeconds(15))
                            .header("authorization", token)
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    latencies[idx] = (System.nanoTime() - t0) / 1_000_000;
                    String body = resp.body();
                    if (body.contains("\"success\":true")) {
                        success.incrementAndGet();
                    } else if (body.contains("名额已抢完")) {
                        soldOut.incrementAndGet();
                    } else if (body.contains("参加过")) {
                        duplicated.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                        if (other.get() <= 3) {
                            System.out.println("  其他返回: " + body);
                        }
                    }
                } catch (Exception e) {
                    other.incrementAndGet();
                    System.out.println("  请求异常: " + e);
                }
            });
            threads[i].start();
        }

        long begin = System.nanoTime();
        start.countDown();
        for (Thread t : threads) {
            t.join();
        }
        long elapsedMs = (System.nanoTime() - begin) / 1_000_000;

        long[] sorted = latencies.clone();
        Arrays.sort(sorted);
        long p50 = percentile(sorted, 50);
        long p95 = percentile(sorted, 95);
        long p99 = percentile(sorted, 99);

        System.out.println();
        System.out.println("================ 压测结果 ================");
        System.out.println("总请求数      : " + concurrency);
        System.out.println("成功参团      : " + success.get() + "  (名额 " + stock + ")");
        System.out.println("名额不足被拒  : " + soldOut.get());
        System.out.println("重复参团被拒  : " + duplicated.get());
        System.out.println("其他失败      : " + other.get());
        System.out.println("总耗时        : " + elapsedMs + " ms");
        System.out.printf("吞吐          : %.0f req/s%n", concurrency * 1000.0 / Math.max(elapsedMs, 1));
        System.out.println("延迟 P50/P95/P99: " + p50 + " / " + p95 + " / " + p99 + " ms");
        System.out.println();
        boolean pass = singleUser
                ? (success.get() == 1 && success.get() + duplicated.get() == concurrency)
                : (success.get() == stock && other.get() == 0);
        System.out.println(pass
                ? (singleUser ? "【判定】通过：同一用户只成功 1 单，其余全部判重复"
                              : "【判定】通过：成功数 == 名额数，零超卖")
                : "【判定】失败：成功 " + success.get() + " / 期望 " + (singleUser ? 1 : stock)
                  + "，其他失败 " + other.get());
        System.exit(pass ? 0 : 1);
    }

    private static long percentile(long[] sorted, int p) {
        if (sorted.length == 0) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }
}
