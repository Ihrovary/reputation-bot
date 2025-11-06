-- Add amount column to reputation_log table
-- feature/reputation-amount

-- Add the new column with a default value of 1 (to maintain backward compatibility with existing records)
ALTER TABLE repbot_schema.reputation_log
    ADD COLUMN IF NOT EXISTS amount INTEGER NOT NULL DEFAULT 1;

-- Add positive and negative amount columns to reputation_settings table
ALTER TABLE repbot_schema.reputation_settings
    ADD COLUMN IF NOT EXISTS positive_amount INTEGER NOT NULL DEFAULT 1;
ALTER TABLE repbot_schema.reputation_settings
    ADD COLUMN IF NOT EXISTS negative_amount INTEGER NOT NULL DEFAULT -1;

-- Add reaction_type column to guild_reactions table for positive/negative reactions
ALTER TABLE repbot_schema.guild_reactions
    ADD COLUMN IF NOT EXISTS reaction_type VARCHAR(50) NOT NULL DEFAULT 'POSITIVE';