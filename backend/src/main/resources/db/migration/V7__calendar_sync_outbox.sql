CREATE TABLE calendar_sync_jobs (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    task_id UUID NOT NULL,
    calendar_id TEXT NOT NULL,
    event_id TEXT NOT NULL,
    title TEXT NOT NULL,
    operation VARCHAR(8) NOT NULL CHECK (operation IN ('UPDATE','DELETE')),
    revision BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RETRY','BLOCKED','IN_SYNC','DELETED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    message TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id,task_id)
);
CREATE INDEX calendar_sync_due ON calendar_sync_jobs(next_attempt_at) WHERE status IN ('PENDING','RETRY');

CREATE FUNCTION enqueue_calendar_sync(p_task UUID,p_title TEXT,p_operation TEXT) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO calendar_sync_jobs(user_id,task_id,calendar_id,event_id,title,operation)
    SELECT e.user_id,e.task_id,e.calendar_id,e.event_id,p_title,p_operation
    FROM calendar_exports e JOIN app_users u ON u.id=e.user_id WHERE e.task_id=p_task
    ON CONFLICT(user_id,task_id) DO UPDATE SET
        title=excluded.title,operation=excluded.operation,revision=calendar_sync_jobs.revision+1,
        status='PENDING',attempts=0,next_attempt_at=now(),message='',updated_at=now();
    -- Preserve any live lease: a newer revision waits until the current attempt finishes/expires.
END $$;

CREATE FUNCTION task_calendar_sync_intent() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        PERFORM enqueue_calendar_sync(OLD.id,OLD.title,'DELETE'); RETURN OLD;
    END IF;
    IF (NEW.title,NEW.scheduled_date,NEW.estimated_minutes) IS DISTINCT FROM (OLD.title,OLD.scheduled_date,OLD.estimated_minutes) THEN
        PERFORM enqueue_calendar_sync(NEW.id,NEW.title,'UPDATE');
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER study_task_calendar_sync AFTER UPDATE OR DELETE ON study_tasks FOR EACH ROW EXECUTE FUNCTION task_calendar_sync_intent();

CREATE FUNCTION goal_calendar_sync_intent() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE t RECORD;
BEGIN
    IF NEW.subject IS DISTINCT FROM OLD.subject THEN
        FOR t IN SELECT id,title FROM study_tasks WHERE goal_id=NEW.id LOOP
            PERFORM enqueue_calendar_sync(t.id,t.title,'UPDATE');
        END LOOP;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER study_goal_calendar_sync AFTER UPDATE OF subject ON study_goals FOR EACH ROW EXECUTE FUNCTION goal_calendar_sync_intent();
