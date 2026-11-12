package com.studyplanner.calendar;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.studyplanner.goals.PlanException;
import java.util.*;

class CalendarTokenVaultTest {
    @Test void tokensAreEncryptedRandomizedAndBoundToTheirOwner() {
        var vault=new CalendarTokenVault(Base64.getEncoder().encodeToString(new byte[32]));var owner=UUID.randomUUID();
        String plain="test-access-and-refresh",sealed=vault.seal(owner,plain);
        assertFalse(sealed.contains(plain));assertEquals(plain,vault.open(owner,sealed));assertNotEquals(sealed,vault.seal(owner,plain));
        assertThrows(PlanException.class,()->vault.open(UUID.randomUUID(),sealed));
        byte[] bytes=Base64.getDecoder().decode(sealed);bytes[bytes.length-1]^=1;
        assertThrows(PlanException.class,()->vault.open(owner,Base64.getEncoder().encodeToString(bytes)));
    }
    @Test void missingOrInvalidEncryptionKeyDoesNotEnableCalendar() {
        assertFalse(new CalendarTokenVault("").configured()); assertFalse(new CalendarTokenVault("invalid").configured());
        assertThrows(PlanException.class,()->new CalendarTokenVault("").seal(UUID.randomUUID(),"test"));
    }
}
