-- Active Recall Quality Improvements: store AI-evaluated warnings and review suggestions for cards
ALTER TABLE recall_cards ADD COLUMN quality_warning VARCHAR(500);

