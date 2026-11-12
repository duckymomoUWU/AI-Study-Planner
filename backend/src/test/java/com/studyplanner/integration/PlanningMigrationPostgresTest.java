package com.studyplanner.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import java.sql.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@EnabledIfSystemProperty(named="planning.integration",matches="true")
class PlanningMigrationPostgresTest {
    @Test void migratesOldScopedDraftWithoutRemovingSavedTasksOrZeroDaySettings() throws Exception {
        String url=System.getProperty("planning.test-db-url","jdbc:postgresql://127.0.0.1:5432/study_planner_test");
        if(!url.contains("/study_planner_test")) throw new IllegalArgumentException("Migration rehearsal requires the isolated study_planner_test database");
        String schema="planning_migration_test_"+UUID.randomUUID().toString().replace("-","");
        var flyway=Flyway.configure().dataSource(url,"study_planner","unused-local").schemas(schema).defaultSchema(schema);
        try(var db=DriverManager.getConnection(url,"study_planner","unused-local")) {
            try {
                flyway.target("4").load().migrate();
                UUID owner=UUID.randomUUID(),goal=UUID.randomUUID();LocalDate date=LocalDate.now();
                try(var q=db.prepareStatement("INSERT INTO "+schema+".app_users(id,email,password_hash) VALUES (?,?,?)")){q.setObject(1,owner);q.setString(2,"migration@example.test");q.setString(3,"unused");q.executeUpdate();}
                try(var q=db.prepareStatement("INSERT INTO "+schema+".study_goals(id,user_id,subject,topics,start_date,exam_date,minutes_per_day) VALUES (?,?,'Math','Algebra',?,?,60)")){q.setObject(1,goal);q.setObject(2,owner);q.setObject(3,date);q.setObject(4,date.plusDays(6));q.executeUpdate();}
                var mapper=new ObjectMapper();var proposal=mapper.createObjectNode();var s=proposal.putObject("scope");s.putArray("selectedTopics").addObject().put("id","algebra").put("title","Algebra").put("priority","HIGH");
                s.putObject("dailyMinutes").put(date.plusDays(1).toString(),0).put(date.plusDays(2).toString(),30).put(date.plusDays(3).toString(),60);
                try(var q=db.prepareStatement("INSERT INTO "+schema+".plan_drafts(goal_id,goal_version,proposal,expires_at) VALUES (?,0,?::jsonb,now()+interval '1 day')")){q.setObject(1,goal);q.setString(2,proposal.toString());q.executeUpdate();}
                try(var q=db.prepareStatement("INSERT INTO "+schema+".study_tasks(goal_id,title,scheduled_date,estimated_minutes,completed) VALUES (?,'Finished history',?,30,true)")){q.setObject(1,goal);q.setObject(2,date);q.executeUpdate();}
                Flyway.configure().dataSource(url,"study_planner","unused-local").schemas(schema).defaultSchema(schema).load().migrate();
                // V6 must not guess availability or alter existing work while adding account settings.
                try(var q=db.createStatement();var r=q.executeQuery("SELECT study_daily_minutes,study_daily_overrides::text,study_budget_version FROM "+schema+".app_users")) {
                    assertTrue(r.next());assertNull(r.getObject(1));assertEquals("{}",r.getString(2));assertEquals(0,r.getInt(3));
                }
                try(var q=db.createStatement();var r=q.executeQuery("SELECT planning_scope::text FROM "+schema+".study_goals")){assertTrue(r.next());var scope=mapper.readTree(r.getString(1));assertEquals("HIGH",scope.at("/selectedTopics/0/priority").asText());assertEquals(0,scope.path("dailyMinutes").path(date.plusDays(1).toString()).asInt(-1));assertEquals(30,scope.path("dailyMinutes").path(date.plusDays(2).toString()).asInt());assertFalse(scope.path("dailyMinutes").has(date.plusDays(3).toString()));}
                try(var q=db.createStatement();var r=q.executeQuery("SELECT count(*) FROM "+schema+".study_tasks WHERE completed=true")){r.next();assertEquals(1,r.getInt(1));}
            } finally {try(var q=db.createStatement()){q.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}}
        }
    }
}
