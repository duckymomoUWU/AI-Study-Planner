package com.studyplanner.calendar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.studyplanner.goals.PlanException;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Component
public class CalendarTokenVault {
    private final byte[] key;
    public CalendarTokenVault(@Value("${CALENDAR_TOKEN_KEY:}") String encoded) {
        byte[] parsed;
        try { parsed=Base64.getDecoder().decode(encoded); } catch (IllegalArgumentException ex) { parsed=new byte[0]; }
        key=parsed;
    }
    public boolean configured() { return key.length==32; }
    public String seal(UUID owner, String plain) { return crypt(owner, plain, true); }
    public String open(UUID owner, String encrypted) { return crypt(owner, encrypted, false); }
    private String crypt(UUID owner, String value, boolean encrypt) {
        if (!configured()) throw new PlanException(503,"CALENDAR_NOT_CONFIGURED","Chưa cấu hình khóa bảo vệ kết nối Google Calendar.");
        try {
            byte[] nonce=new byte[12], input;
            if (encrypt) { new SecureRandom().nextBytes(nonce); input=value.getBytes(StandardCharsets.UTF_8); }
            else { byte[] all=Base64.getDecoder().decode(value); if(all.length<28) throw new IllegalArgumentException(); System.arraycopy(all,0,nonce,0,12); input=Arrays.copyOfRange(all,12,all.length); }
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(owner.toString().getBytes(StandardCharsets.UTF_8)); byte[] result=cipher.doFinal(input);
            if(!encrypt) return new String(result,StandardCharsets.UTF_8);
            byte[] all=new byte[12+result.length]; System.arraycopy(nonce,0,all,0,12); System.arraycopy(result,0,all,12,result.length); return Base64.getEncoder().encodeToString(all);
        } catch(Exception ex) { throw new PlanException(503,"CALENDAR_RECONNECT","Không đọc được kết nối Google. Hãy kết nối lại."); }
    }
}
