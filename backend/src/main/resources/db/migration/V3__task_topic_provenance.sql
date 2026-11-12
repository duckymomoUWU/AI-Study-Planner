ALTER TABLE study_tasks ADD COLUMN topic_id VARCHAR(100);
ALTER TABLE study_tasks ADD COLUMN topic_title VARCHAR(200);
ALTER TABLE study_tasks ADD CONSTRAINT task_topic_pair CHECK ((topic_id IS NULL) = (topic_title IS NULL));
