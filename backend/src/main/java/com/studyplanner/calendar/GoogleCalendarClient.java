package com.studyplanner.calendar;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studyplanner.goals.PlanException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Component
public class GoogleCalendarClient {
    public record CalendarInfo(String id, String name, boolean writable, boolean primary) {}
    private final CalendarStore store;
    private final CalendarTokenVault vault;
    private final ObjectMapper mapper;
    private final String clientId, clientSecret, redirectUri, appUrl;
    private final HttpClient http;
    private static final String API="https://www.googleapis.com/calendar/v3";
    static final String SCOPES="https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/calendar.events";

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleCalendarClient(CalendarStore store, CalendarTokenVault vault, ObjectMapper mapper,
            @Value("${GOOGLE_CLIENT_ID:}") String clientId, @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret,
            @Value("${GOOGLE_REDIRECT_URI:http://127.0.0.1:5173/api/v1/calendar/oauth/callback}") String redirectUri,
            @Value("${APP_URL:http://127.0.0.1:5173}") String appUrl) {
        this(store,vault,mapper,clientId,clientSecret,redirectUri,appUrl,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }
    GoogleCalendarClient(CalendarStore store,CalendarTokenVault vault,ObjectMapper mapper,String clientId,String clientSecret,String redirectUri,String appUrl,HttpClient http) {
        this.store=store; this.vault=vault; this.mapper=mapper; this.clientId=clientId; this.clientSecret=clientSecret; this.redirectUri=redirectUri; this.appUrl=appUrl;this.http=http;
    }
    public boolean configured() { return !clientId.isBlank() && !clientSecret.isBlank() && vault.configured(); }
    public boolean connected(UUID owner) { return configured() && store.tokens(owner).isPresent(); }
    public String returnUrl(boolean success) { return appUrl+"/?calendar="+(success?"connected":"error")+"#daily"; }
    public String authorizationUrl(String state, String challenge) {
        requireConfigured();
        return "https://accounts.google.com/o/oauth2/v2/auth?"+form(Map.of("client_id",clientId,"redirect_uri",redirectUri,
            "response_type","code","scope",SCOPES,"access_type","offline","prompt","consent","state",state,
            "code_challenge",challenge,"code_challenge_method","S256"));
    }
    public void exchange(UUID owner, String code, String verifier) {
        requireConfigured();
        var token=tokenRequest(Map.of("client_id",clientId,"client_secret",clientSecret,"code",code,
            "redirect_uri",redirectUri,"grant_type","authorization_code","code_verifier",verifier));
        if(token.path("refresh_token").asText().isBlank()) throw reconnect();
        saveToken(owner,token);
    }
    private void requireConfigured() { if(!configured()) throw new PlanException(503,"CALENDAR_NOT_CONFIGURED","Chưa cấu hình Google OAuth. Hãy thêm Client ID và Client Secret vào .env của backend."); }
    private void saveToken(UUID owner, JsonNode token) {
        if(token.path("access_token").asText().isBlank() || token.path("expires_in").asInt()<=0) throw reconnect();
        var copy=(ObjectNode)token.deepCopy(); copy.put("expires_at",Instant.now().plusSeconds(token.path("expires_in").asLong()).toString());
        store.tokens(owner,vault.seal(owner,copy.toString()));
    }
    private String access(UUID owner) {
        requireConfigured(); JsonNode saved;
        try { saved=mapper.readTree(vault.open(owner,store.tokens(owner).orElseThrow(GoogleCalendarClient::reconnect))); }
        catch(PlanException ex) { throw ex; } catch(Exception ex) { throw reconnect(); }
        if(Instant.parse(saved.path("expires_at").asText()).isAfter(Instant.now().plusSeconds(90))) return saved.path("access_token").asText();
        String refresh=saved.path("refresh_token").asText(); if(refresh.isBlank()) throw reconnect();
        var renewed=(ObjectNode)tokenRequest(Map.of("client_id",clientId,"client_secret",clientSecret,"refresh_token",refresh,"grant_type","refresh_token"));
        if(renewed.path("refresh_token").asText().isBlank()) renewed.put("refresh_token",refresh);
        saveToken(owner,renewed); return renewed.path("access_token").asText();
    }
    private JsonNode tokenRequest(Map<String,String> fields) {
        var req=HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token")).timeout(Duration.ofSeconds(20))
            .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(fields))).build();
        var response=send(req);
        if(response.statusCode()!=200) throw reconnect();
        return json(response.body());
    }
    public List<CalendarInfo> calendars(UUID owner) {
        var result=new ArrayList<CalendarInfo>(); String next="";
        do {
            var data=request(owner,"GET","/users/me/calendarList?maxResults=250&showHidden=true"+(next.isBlank()?"":"&pageToken="+encode(next)),null,false);
            for(var item:data.path("items")) if(!item.path("deleted").asBoolean()) {
                String role=item.path("accessRole").asText();
                result.add(new CalendarInfo(item.path("id").asText(),item.path("summary").asText(),
                    Set.of("owner","writer").contains(role),item.path("primary").asBoolean()));
            }
            next=data.path("nextPageToken").asText();
            if(result.size()>1000) throw unavailable();
        } while(!next.isBlank());
        return List.copyOf(result);
    }
    public List<CalendarScheduling.Busy> busy(UUID owner, List<String> calendars, Instant from, Instant to, String zone) {
        var body=mapper.createObjectNode().put("timeMin",from.toString()).put("timeMax",to.toString()).put("timeZone",zone);
        var items=body.putArray("items"); for(String id:new LinkedHashSet<>(calendars)) items.addObject().put("id",id);
        if(items.isEmpty() || items.size()>50) throw new PlanException(400,"CALENDAR_SELECTION_INVALID","Chọn từ 1 đến 50 lịch để kiểm tra giờ bận.");
        return parseBusy(request(owner,"POST","/freeBusy",body,false),calendars);
    }
    static List<CalendarScheduling.Busy> parseBusy(JsonNode data,List<String> calendars) {
        var result=new ArrayList<CalendarScheduling.Busy>();
        var failed=new ArrayList<String>();
        for(String id:calendars) {
            var calendar=data.path("calendars").path(id);
            if(calendar.isMissingNode() || calendar.path("errors").size()>0 || !calendar.path("busy").isArray())failed.add(id);
        }
        if(!failed.isEmpty())throw new PlanException(502,"CALENDAR_BUSY_UNAVAILABLE",
            "Không đọc được giờ bận của "+failed.size()+" lịch đã chọn. Hãy kiểm tra các lịch được đánh dấu bên dưới; app chưa xếp hoặc xuất lịch học.",Map.of("failedCalendarIds",List.copyOf(failed)));
        for(String id:calendars) {
            JsonNode calendar=data.path("calendars").path(id);
            for(var interval:calendar.path("busy")) {
                try {
                    var start=Instant.parse(interval.path("start").asText());var end=Instant.parse(interval.path("end").asText());
                    if(!end.isAfter(start)) throw new IllegalArgumentException();
                    result.add(new CalendarScheduling.Busy(start,end));
                } catch(Exception ex) { throw unavailable(); }
            }
        }
        return result;
    }
    public Optional<JsonNode> event(UUID owner,String calendar,String id) {
        var result=request(owner,"GET","/calendars/"+encode(calendar)+"/events/"+encode(id),null,true);
        return result==null?Optional.empty():Optional.of(result);
    }
    public JsonNode insert(UUID owner,String calendar,ObjectNode body) {
        return request(owner,"POST","/calendars/"+encode(calendar)+"/events",body,false);
    }
    public JsonNode patch(UUID owner,String calendar,String id,ObjectNode body,String etag) {
        return request(owner,"PATCH","/calendars/"+encode(calendar)+"/events/"+encode(id),body,false,etag);
    }
    public void delete(UUID owner,String calendar,String id,String etag) {
        request(owner,"DELETE","/calendars/"+encode(calendar)+"/events/"+encode(id),null,true,etag);
    }
    /** FreeBusy merges intervals and cannot safely subtract this event; list target events instead. */
    public List<CalendarScheduling.Busy> busyExceptEvent(UUID owner,String calendar,String excluded,Instant from,Instant to,String zone) {
        var result=new ArrayList<CalendarScheduling.Busy>();String next="";int count=0;
        do {
            var data=request(owner,"GET","/calendars/"+encode(calendar)+"/events?singleEvents=true&showDeleted=false&maxResults=2500&timeMin="+encode(from.toString())+"&timeMax="+encode(to.toString())
                +(next.isBlank()?"":"&pageToken="+encode(next)),null,false);
            if(!data.path("items").isArray())throw unavailable();
            result.addAll(eventBusy(data,excluded,zone));count+=data.path("items").size();
            next=data.path("nextPageToken").asText();if(count>10000)throw unavailable();
        }while(!next.isBlank());
        return result;
    }
    static List<CalendarScheduling.Busy> eventBusy(JsonNode data,String excluded,String zone) {
        var result=new ArrayList<CalendarScheduling.Busy>();
        try {
            var calendarZone=ZoneId.of(data.path("timeZone").asText(zone));
            for(var event:data.path("items")) {
                if(event.path("id").asText().equals(excluded) || event.path("status").asText().equals("cancelled") || event.path("transparency").asText().equals("transparent"))continue;
                Instant from=event.path("start").has("dateTime")?Instant.parse(event.path("start").path("dateTime").asText()):LocalDate.parse(event.path("start").path("date").asText()).atStartOfDay(calendarZone).toInstant();
                Instant to=event.path("end").has("dateTime")?Instant.parse(event.path("end").path("dateTime").asText()):LocalDate.parse(event.path("end").path("date").asText()).atStartOfDay(calendarZone).toInstant();
                if(!to.isAfter(from))throw unavailable();result.add(new CalendarScheduling.Busy(from,to));
            }
        }catch(PlanException ex){throw ex;}catch(Exception ex){throw unavailable();}
        return result;
    }
    public void disconnect(UUID owner) {
        // Revoke only this app's grant. Existing exported events are left intact.
        try { if(connected(owner)) {
            String token=access(owner);
            var req=HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/revoke")).timeout(Duration.ofSeconds(15))
                .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(Map.of("token",token)))).build();
            send(req);
        } } catch(PlanException ignored) { /* A revoked/offline grant must not prevent local disconnection. */ }
        finally { store.disconnect(owner); }
    }
    private JsonNode request(UUID owner,String method,String path,JsonNode body,boolean allowMissing) {
        return request(owner,method,path,body,allowMissing,null);
    }
    private JsonNode request(UUID owner,String method,String path,JsonNode body,boolean allowMissing,String etag) {
        var builder=HttpRequest.newBuilder(URI.create(API+path)).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+access(owner)).header("Accept","application/json");
        if(body!=null) builder.header("Content-Type","application/json");
        if(etag!=null && !etag.isBlank())builder.header("If-Match",etag);
        var response=send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body.toString())).build());
        int status=response.statusCode();
        if(allowMissing && (status==404 || status==410)) return null;
        if(status==412)throw new PlanException(409,"CALENDAR_REMOTE_CHANGED","Sự kiện vừa thay đổi trên Google. App sẽ đọc lại trước khi thử đồng bộ.");
        if(status==401) throw reconnect();
        if(status==403) throw new PlanException(403,"CALENDAR_PERMISSION_DENIED","Google chưa cấp đủ quyền. Hãy kết nối lại và kiểm tra quyền của lịch đã chọn.");
        if(status==409) throw new PlanException(409,"CALENDAR_EVENT_EXISTS","Sự kiện đã tồn tại. Hãy thử xác nhận lại để kiểm tra kết quả.");
        if(status<200 || status>=300) throw unavailable();
        return status==204?mapper.createObjectNode():json(response.body());
    }
    private HttpResponse<String> send(HttpRequest req) {
        try { return http.send(req,HttpResponse.BodyHandlers.ofString()); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch(Exception ex) { throw unavailable(); }
    }
    private JsonNode json(String value) { try { return mapper.readTree(value); } catch(Exception ex) { throw unavailable(); } }
    private static PlanException reconnect() { return new PlanException(401,"CALENDAR_RECONNECT","Kết nối Google đã hết hạn hoặc bị thu hồi. Hãy kết nối lại Google Calendar."); }
    private static PlanException unavailable() { return new PlanException(502,"CALENDAR_UNAVAILABLE","Google Calendar chưa phản hồi hợp lệ. Chưa xác nhận được kết quả; hãy thử lại."); }
    static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
    private static String form(Map<String,String> values) { return values.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&")); }
}
