package com.studyplanner.calendar;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="calendar.sync.enabled",havingValue="true",matchIfMissing=true)
public class CalendarSyncWorker {
    private final CalendarSyncService service;
    public CalendarSyncWorker(CalendarSyncService service){this.service=service;}
    @Scheduled(initialDelayString="${calendar.sync.initial-delay-ms:30000}",fixedDelayString="${calendar.sync.delay-ms:15000}")
    public void tick(){for(int i=0;i<3;i++)if(!service.processNext())break;}
}
