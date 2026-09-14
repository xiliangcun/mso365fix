package hqr.o365.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import hqr.o365.dao.TaOfficeInfoRepo;
import hqr.o365.domain.TaOfficeInfo;

@Service
public class BatchManageOfficeUser {
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*_-";
    private final RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    private final SecureRandom random = new SecureRandom();

    @Autowired private TaOfficeInfoRepo repo;
    @Autowired private ValidateAppInfo vai;
    @Value("${UA}") private String ua;

    @CacheEvict(value={"cacheOfficeUser","cacheOfficeUserSearch","cacheLicense"}, allEntries=true)
    public HashMap<String,Object> updateUsers(String usersJson, String domain, String addLicenses,
            String removeLicenses, String passwordMode, String fixedPassword, int randomLength,
            String expirationMode, boolean forceChange) {
        JSONArray users = JSON.parseArray(usersJson);
        if (users == null || users.isEmpty()) throw new IllegalArgumentException("至少选择一个用户");
        if (users.size() > 1000) throw new IllegalArgumentException("单次最多编辑1000个用户");
        String normalizedDomain = normalizeDomain(domain);
        List<String> adds = skuIds(addLicenses);
        List<String> removes = skuIds(removeLicenses);
        validatePassword(passwordMode, fixedPassword, randomLength);
        for(String sku:adds) if(removes.contains(sku)) throw new IllegalArgumentException("同一订阅不能同时添加和删除: "+sku);
        String token = accessToken();
        int success=0, failed=0;
        JSONArray details = new JSONArray();
        for (Object value : users) {
            JSONObject source=(JSONObject)value;
            String id=trim(source.getString("id"));
            String upn=trim(source.getString("upn"));
            JSONObject detail=new JSONObject(); detail.put("id",id); detail.put("upn",upn);
            try {
                if(id.isEmpty()) throw new IllegalArgumentException("用户 id 为空");
                JSONObject patch=new JSONObject();
                if(!normalizedDomain.isEmpty()) {
                    if(upn.isEmpty() || !upn.contains("@")) throw new IllegalArgumentException("无法从当前账号确定邮箱前缀");
                    patch.put("userPrincipalName", upn.substring(0,upn.indexOf('@'))+normalizedDomain);
                }
                if(!"unchanged".equals(passwordMode)) {
                    JSONObject profile=new JSONObject();
                    String newPassword="random".equals(passwordMode)?randomPassword(randomLength):fixedPassword;
                    profile.put("password", newPassword);
                    if("random".equals(passwordMode)) detail.put("generatedPassword",newPassword);
                    profile.put("forceChangePasswordNextSignIn", forceChange);
                    patch.put("passwordProfile",profile);
                }
                if("expire".equals(expirationMode)) patch.put("passwordPolicies","");
                else if("never".equals(expirationMode)) patch.put("passwordPolicies","DisablePasswordExpiration");
                if(!patch.isEmpty()) patchUser(id,patch,token);
                if(!adds.isEmpty() || !removes.isEmpty()) assignLicenses(id,adds,removes,token);
                success++; detail.put("status","success");
            } catch(Exception e) {
                failed++; detail.put("status","failed"); detail.put("message",safeMessage(e));
            }
            details.add(detail);
        }
        HashMap<String,Object> result=new HashMap<String,Object>();
        result.put("total",users.size()); result.put("success",success); result.put("failed",failed); result.put("details",details);
        return result;
    }

    private void patchUser(String id, JSONObject body, String token) {
        String endpoint=UriComponentsBuilder.fromHttpUrl("https://graph.microsoft.com/v1.0/users").pathSegment(id).build().encode().toUriString();
        ResponseEntity<String> response=restTemplate.exchange(endpoint,HttpMethod.PATCH,new HttpEntity<String>(body.toJSONString(),headers(token)),String.class);
        if(response.getStatusCodeValue()!=204) throw new IllegalStateException("更新用户返回状态 "+response.getStatusCodeValue());
    }
    private void assignLicenses(String id,List<String> adds,List<String> removes,String token) {
        JSONArray addArray=new JSONArray(); for(String sku:adds){JSONObject x=new JSONObject();x.put("skuId",sku);x.put("disabledPlans",new JSONArray());addArray.add(x);}
        JSONArray removeArray=new JSONArray(); removeArray.addAll(removes);
        JSONObject body=new JSONObject();body.put("addLicenses",addArray);body.put("removeLicenses",removeArray);
        String endpoint=UriComponentsBuilder.fromHttpUrl("https://graph.microsoft.com/v1.0/users").pathSegment(id,"assignLicense").build().encode().toUriString();
        int attempts=0; while(true){try{ResponseEntity<String> r=restTemplate.postForEntity(endpoint,new HttpEntity<String>(body.toJSONString(),headers(token)),String.class);if(r.getStatusCodeValue()!=200)throw new IllegalStateException("订阅更新返回状态 "+r.getStatusCodeValue());return;}catch(HttpStatusCodeException e){attempts++;int c=e.getRawStatusCode();if(attempts>=6 || !(c==404||c==429||c>=500))throw e;sleep(Math.min(8000L,500L<<(attempts-1)));}}
    }
    private HttpHeaders headers(String token){HttpHeaders h=new HttpHeaders();h.set(HttpHeaders.USER_AGENT,ua);h.setBearerAuth(token);h.setContentType(MediaType.APPLICATION_JSON);return h;}
    private String accessToken(){List<TaOfficeInfo> list=repo.findBySelected("是");if(list==null||list.isEmpty())throw new IllegalStateException("没有选择 Office 配置");TaOfficeInfo t=list.get(0);if(!vai.checkAndGet(t.getTenantId(),t.getAppId(),t.getSecretId()))throw new IllegalStateException("无法获取 Microsoft Graph Token");String token=trim(vai.getAccessToken());if(token.isEmpty())throw new IllegalStateException("Microsoft Graph Token 为空");return token;}
    private List<String> skuIds(String csv){List<String> out=new ArrayList<String>();if(csv==null)return out;for(String raw:csv.split(",")){String s=trim(raw);if(s.isEmpty())continue;UUID.fromString(s);if(!out.contains(s))out.add(s);}return out;}
    private String normalizeDomain(String value){String d=trim(value);if(d.isEmpty())return "";if(!d.startsWith("@"))d="@"+d;if(!d.matches("@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"))throw new IllegalArgumentException("域名格式不正确");return d.toLowerCase();}
    private void validatePassword(String mode,String fixed,int length){if(!"unchanged".equals(mode)&&!"fixed".equals(mode)&&!"random".equals(mode))throw new IllegalArgumentException("密码模式不正确");if("fixed".equals(mode)&&fixed==null)throw new IllegalArgumentException("固定密码不能为空");if("fixed".equals(mode)&&fixed.isEmpty())throw new IllegalArgumentException("固定密码不能为空");if("random".equals(mode)&&(length<8||length>256))throw new IllegalArgumentException("随机密码长度必须在8到256之间");}
    private String randomPassword(int length){StringBuilder b=new StringBuilder();b.append('A').append('a').append('2').append('!');while(b.length()<length)b.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));for(int i=b.length()-1;i>0;i--){int j=random.nextInt(i+1);char c=b.charAt(i);b.setCharAt(i,b.charAt(j));b.setCharAt(j,c);}return b.toString();}
    private String trim(String s){return s==null?"":s.trim();}
    private String safeMessage(Exception e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._-]+","Bearer ***");}
    private void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("操作被中断",e);}}
}
