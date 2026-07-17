package com.example.shixun.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WebSearchService {
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public List<SearchResult> search(String query, int limit) throws Exception {
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) return List.of();
        String url = "https://lite.duckduckgo.com/lite/?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("搜索引擎 HTTP " + response.statusCode());
        }
        return parseDuckDuckGoLite(response.body(), Math.max(1, limit));
    }

    public String formatResults(List<SearchResult> results) {
        if (results == null || results.isEmpty()) return "【联网搜索结果】未搜索到可用结果。";
        StringBuilder sb = new StringBuilder("【联网搜索结果】\n");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append(i + 1).append(". ").append(r.title()).append("\n")
                    .append("   链接：").append(r.url()).append("\n");
            if (r.snippet() != null && !r.snippet().isBlank()) {
                sb.append("   摘要：").append(r.snippet()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private List<SearchResult> parseDuckDuckGoLite(String html, int limit) {
        List<SearchResult> out = new ArrayList<>();
        Pattern linkPattern = Pattern.compile("<a([^>]*class=['\\\"]result-link['\\\"][^>]*)>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = linkPattern.matcher(html == null ? "" : html);
        while (m.find() && out.size() < limit) {
            String attrs = m.group(1);
            Matcher hm = Pattern.compile("href=['\\\"]([^'\\\"]+)['\\\"]", Pattern.CASE_INSENSITIVE).matcher(attrs);
            if (!hm.find()) continue;
            String rawHref = htmlDecode(stripTags(hm.group(1))).trim();
            String title = htmlDecode(stripTags(m.group(2))).trim();
            if (title.isBlank() || title.toLowerCase().contains("sponsored link") || title.equalsIgnoreCase("more info")) continue;
            String href = normalizeDuckUrl(rawHref);
            if (href.contains("duckduckgo.com/duckduckgo-help-pages") || href.contains("duckduckgo.com/y.js") || href.contains("ad_domain=") || href.isBlank()) continue;
            String snippet = "";
            int from = m.end();
            int to = Math.min(html.length(), from + 1800);
            Matcher sm = Pattern.compile("<td[^>]+class=['\\\"]result-snippet['\\\"][^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html.substring(from, to));
            if (sm.find()) snippet = htmlDecode(stripTags(sm.group(1))).replaceAll("\\s+", " ").trim();
            out.add(new SearchResult(title, href, snippet));
        }
        return out;
    }

    private String normalizeDuckUrl(String href) {
        String h = href == null ? "" : href;
        h = h.replace("&amp;", "&");
        if (h.startsWith("//")) h = "https:" + h;
        Matcher uddg = Pattern.compile("[?&]uddg=([^&]+)").matcher(h);
        if (uddg.find()) {
            try {
                return URLDecoder.decode(uddg.group(1), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }
        return h;
    }

    private String stripTags(String input) {
        return input == null ? "" : input.replaceAll("<[^>]*>", "");
    }

    private String htmlDecode(String input) {
        if (input == null) return "";
        return input.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }

    public record SearchResult(String title, String url, String snippet) {}
}
