UPDATE week
SET is_presentation_week = (week_number = 12),
    presentation_category = CASE WHEN week_number = 12 THEN 'IDEATHON' ELSE NULL END
WHERE week_number IN (12, 13);
