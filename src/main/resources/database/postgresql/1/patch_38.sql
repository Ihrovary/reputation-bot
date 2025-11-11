-- Add amount column to reputation_log table
-- feature/reputation-amount

-- Add the new column with a default value of 1 (to maintain backward compatibility with existing records)
ALTER TABLE repbot_schema.reputation_log
    ADD COLUMN IF NOT EXISTS amount INTEGER NOT NULL DEFAULT 1;

-- Add upvote and downvote amount columns to reputation_settings table
ALTER TABLE repbot_schema.reputation_settings
    ADD COLUMN IF NOT EXISTS upvote_amount INTEGER NOT NULL DEFAULT 1;
ALTER TABLE repbot_schema.reputation_settings
    ADD COLUMN IF NOT EXISTS downvote_amount INTEGER NOT NULL DEFAULT -1;

-- Add reaction_type column to guild_reactions table for upvote/downvote reactions
ALTER TABLE repbot_schema.guild_reactions
    ADD COLUMN IF NOT EXISTS reaction_type VARCHAR(50) NOT NULL DEFAULT 'UPVOTE';