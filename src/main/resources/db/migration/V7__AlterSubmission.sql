ALTER TABLE submission
ADD COLUMN latitude text NULL,
ADD COLUMN longitude text NULL,
ADD COLUMN photo_year integer NULL;

ALTER TABlE metadata
ADD COLUMN latitude text NULL,
ADD COLUMN longitude text NULL,
ADD COLUMN photo_year integer NULL;