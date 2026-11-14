UPDATE week
SET is_presentation_week = FALSE,
    presentation_category = NULL
WHERE week_number = 28;

UPDATE week
SET is_presentation_week = TRUE,
    presentation_category = 'HACKATHON'
WHERE week_number = 27;
