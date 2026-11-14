ALTER TABLE week
    ADD COLUMN presentation_category ENUM('IDEATHON', 'HACKATHON', 'JUNGKATHON') NULL;

UPDATE week
SET is_presentation_week = FALSE
WHERE week_number = 27;

UPDATE week
SET is_presentation_week = TRUE,
    presentation_category = CASE week_number
        WHEN 13 THEN 'IDEATHON'
        WHEN 28 THEN 'HACKATHON'
        WHEN 38 THEN 'JUNGKATHON'
    END
WHERE week_number IN (13, 28, 38);

UPDATE week_part_schedule s
JOIN week w ON w.id = s.week_id
SET s.session_title = CASE w.week_number
    WHEN 13 THEN '아이디어톤 발표·회고'
    WHEN 28 THEN '해커톤 발표·회고'
    WHEN 38 THEN '중커톤 발표·회고'
END
WHERE w.week_number IN (13, 28, 38);
