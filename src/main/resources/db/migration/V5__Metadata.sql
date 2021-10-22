CREATE TABLE metadata (
    photo_id integer NOT NULL,
    name text,
    CONSTRAINT P_METADATA PRIMARY KEY (photo_id),
    CONSTRAINT FK_71 FOREIGN KEY (photo_id) REFERENCES photo ("id")
);
