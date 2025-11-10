WITH
    rep_offset
        AS (
        SELECT
            o.user_id,
            sum(o.amount) AS reputation
        FROM
            reputation_offset o
        WHERE o.added > :date_init
          AND ( added > :reset_date OR :reset_date::TIMESTAMP IS NULL )
          AND guild_id = :guild_id
        GROUP BY o.user_id
           ),
    raw_log
        AS (
        SELECT
            r.guild_id,
            r.receiver_id,
            r.donor_id,
            r.amount
        FROM
            reputation_log r
        WHERE r.received > :date_init
          AND ( received > :reset_date OR :reset_date::TIMESTAMP IS NULL )
          AND guild_id = :guild_id
           ),
    received_votes
        AS (
        SELECT
            r.receiver_id,
            count(1) AS vote_count,
            sum(r.amount) AS reputation
        FROM
            raw_log r
        GROUP BY r.receiver_id
           ),
    given_votes
        AS (
        SELECT
            r.donor_id,
            count(1) AS donated
        FROM
            raw_log r
        GROUP BY r.donor_id
           ),
    -- Build raw log with aggregated user reputation
    full_log
        AS (
        SELECT
            coalesce(rv.receiver_id, gv.donor_id) AS user_id,
            coalesce(rv.reputation, 0::BIGINT)     AS reputation,
            coalesce(rv.vote_count, 0::BIGINT)     AS received_votes,
            coalesce(gv.donated, 0::BIGINT)        AS given_votes
        FROM
            received_votes rv
                FULL JOIN given_votes gv
                ON rv.receiver_id = gv.donor_id
           ),
    filtered_log
        AS (
        SELECT
            user_id,
            reputation,
            received_votes,
            given_votes
        FROM
            full_log
        WHERE
            -- Remove entries scheduled for cleanup
            user_id NOT IN (
                SELECT
                    1
                FROM
                    cleanup_schedule clean
                WHERE guild_id = :guild_id
                           )
           ),
    offset_reputation
        AS (
        SELECT
            coalesce(f.user_id, o.user_id)                        AS user_id,
            -- apply offset to the normal reputation.
            coalesce(f.reputation, 0) + coalesce(o.reputation, 0) AS reputation,
            coalesce(o.reputation, 0)                             AS rep_offset,
            -- save raw reputation without the offset.
            coalesce(f.reputation, 0)                             AS raw_reputation,
            coalesce(f.received_votes, 0)                         AS received_votes,
            coalesce(f.given_votes, 0)                           AS given_votes
        FROM
            filtered_log f
                FULL JOIN rep_offset o
                ON f.user_id = o.user_id
           ),
    ranked AS (
        SELECT
            rank() OVER (ORDER BY reputation DESC) AS rank,
            rank() OVER (ORDER BY given_votes DESC)     AS rank_donated,
            user_id,
            raw_reputation                              AS raw_reputation,
            received_votes,
            given_votes,
            rep_offset::BIGINT                         AS rep_offset,
            reputation::BIGINT                         AS reputation
        FROM
            offset_reputation rank
           )
SELECT
    rank,
    rank_donated,
    user_id,
    raw_reputation,
    received_votes,
    given_votes,
    rep_offset,
    reputation
FROM
    ranked
WHERE user_id = :user_id
