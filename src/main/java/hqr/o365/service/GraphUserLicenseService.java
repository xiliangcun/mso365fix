package hqr.o365.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

@Service
public class GraphUserLicenseService {
    private static final Logger log = LoggerFactory.getLogger(GraphUserLicenseService.class);
    private final RestTemplate restTemplate;

    @Value("${UA}") private String ua;
    @Value("${graph.license.retry.max-attempts:6}") private int maxAttempts;
    @Value("${graph.license.retry.initial-delay-ms:500}") private long initialDelayMs;
    @Value("${graph.license.retry.max-delay-ms:8000}") private long maxDelayMs;

    public GraphUserLicenseService() { this(new RestTemplate()); }
    GraphUserLicenseService(RestTemplate restTemplate) { this.restTemplate = restTemplate; }

    public String extractCreatedUserId(ResponseEntity<String> response, String upn) {
        if (response != null && response.getBody() != null) {
            String id = JSON.parseObject(response.getBody()).getString("id");
            if (notBlank(id)) return id.trim();
        }
        throw new IllegalStateException("Microsoft Graph 创建用户成功，但响应中没有用户 id，UPN=" + safe(upn));
    }

    public List<String> normalizeSkuIds(String licenses) {
        Set<String> values = new LinkedHashSet<String>();
        if (licenses != null) {
            for (String raw : licenses.split(",")) {
                String value = raw == null ? "" : raw.trim();
                if (value.isEmpty()) continue;
                try { UUID.fromString(value); }
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("无效的许可证 skuId: [" + value + "]"); }
                values.add(value);
            }
        }
        return new ArrayList<String>(values);
    }

    public void assignLicenseWithRetry(String userId, String skuId, String accessToken) {
        require(userId, "userId"); require(skuId, "skuId"); require(accessToken, "accessToken");
        UUID.fromString(skuId.trim());
        URI endpoint = UriComponentsBuilder.fromHttpUrl("https://graph.microsoft.com/v1.0/users")
                .pathSegment(userId.trim(), "assignLicense").build().encode().toUri();
        JSONObject license = new JSONObject();
        license.put("disabledPlans", new JSONArray());
        license.put("skuId", skuId.trim());
        JSONArray adds = new JSONArray(); adds.add(license);
        JSONObject payload = new JSONObject(); payload.put("addLicenses", adds); payload.put("removeLicenses", new JSONArray());
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, ua); headers.setBearerAuth(accessToken.trim()); headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<String>(payload.toJSONString(), headers);
        long delay = Math.max(0, initialDelayMs);
        for (int attempt=1; attempt<=Math.max(1,maxAttempts); attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(endpoint, request, String.class);
                if (response.getStatusCodeValue()==200) { log.info("许可证分配成功 userId={} skuId={} attempt={}", userId, skuId, attempt); return; }
                throw new IllegalStateException("许可证分配返回非预期状态: " + response.getStatusCodeValue());
            } catch (HttpStatusCodeException e) {
                if (!retryable(e.getStatusCode()) || attempt>=maxAttempts) throw e;
                long wait = retryAfter(e.getResponseHeaders(), delay);
                log.warn("许可证分配暂时失败，将重试 userId={} skuId={} status={} attempt={} waitMs={} requestId={}", userId, skuId, e.getRawStatusCode(), attempt, wait, header(e.getResponseHeaders(), "request-id"));
                sleep(wait); delay=Math.min(maxDelayMs, Math.max(1,delay)*2);
            } catch (ResourceAccessException e) {
                if (attempt>=maxAttempts) throw e;
                sleep(delay); delay=Math.min(maxDelayMs, Math.max(1,delay)*2);
            }
        }
    }

    private boolean retryable(HttpStatus s) { int c=s.value(); return c==404 || c==408 || c==429 || c==500 || c==502 || c==503 || c==504; }
    private long retryAfter(HttpHeaders h,long fallback) { String v=header(h,"Retry-After"); if(v!=null) try{return Math.min(maxDelayMs,Long.parseLong(v.trim())*1000L);}catch(Exception ignore){} return Math.min(maxDelayMs,fallback); }
    private String header(HttpHeaders h,String n){return h==null?null:h.getFirst(n);}
    private void sleep(long ms){try{Thread.sleep(Math.max(0,ms));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("许可证分配重试被中断",e);}}
    private void require(String v,String n){if(!notBlank(v))throw new IllegalArgumentException(n+" 不能为空");}
    private boolean notBlank(String v){return v!=null&&!v.trim().isEmpty();}
    private String safe(String v){return v==null?"":v.replaceAll("[\\r\\n]","");}
}
